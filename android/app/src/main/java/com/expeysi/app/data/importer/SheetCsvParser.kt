package com.expeysi.app.data.importer

import com.expeysi.app.data.csv.CsvFormat
import com.expeysi.app.domain.TxType
import java.io.InputStream
import java.math.BigDecimal
import java.math.RoundingMode
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.ZoneId
import java.time.format.DateTimeParseException
import java.util.UUID
import javax.inject.Inject

/**
 * Импортер внешнего табличного формата (выгрузка внешнего приложения,
 * пример — test-import.csv). Признак формата — первая колонка «ID»;
 * дальше идут «Дата», «Время», «Название счета», «Владелец счета», «Группа»,
 * «Категория», «Сумма», «Валюта», «Комментарий», числовые «ЦВЕТ» и «ИКОНКА»
 * оформления категории и служебные колонки внешнего приложения (метка
 * времени, признак запланированной строки), которые импортер не читает.
 *
 * Отличия от формата «Бюджета» (один заголовок в [CsvFormat]):
 * - суммы приходят в записи Excel, включая научную нотацию («-8e+06»,
 *   «1.27533e+06»); копейки сверх двух знаков не допускаются, как и в
 *   основном импорте;
 * - баланс файла — значение «Владелец счета»; пустой владелец заменяется
 *   значением «Группы», оба пустых — автосозданным «Бюджет (валюта)»;
 * - оформление категории задают числовые «ЦВЕТ» и «ИКОНКА»: коды переводятся
 *   в ключи палитры по таблицам соответствия в [companion object] — цвета
 *   к [CategoryPalette], иконки к [IconCatalog]; неизвестный или нечисловой
 *   код ключа не задает — категория остается с оформлением по умолчанию,
 *   существующая не перекрашивается.
 * - колонка «Связка перевода» отсутствует: ноги переводов (категория
 *   «Перевод») сводятся парами — один и тот же момент «дата+время»,
 *   противоположные знаки, при нескольких кандидатах предпочтение сумме,
 *   равной сумме списания. Свежая связка каждой пары восстанавливает
 *   настоящий перевод; непарные строки остаются обычными операциями.
 *
 * Разделитель опознаётся по заголовку. На выходе — те же
 * [BudgetTransaction] в [BudgetImportService]: дальше импорт идёт общим
 * потоком (балансы с префиксом, счета, категории, ошибки).
 */
class SheetCsvParser @Inject constructor() {

    private val zone: ZoneId = ZoneId.systemDefault()

    fun parse(input: InputStream): List<BudgetTransaction> = parseText(
        input.bufferedReader(Charsets.UTF_8).use { it.readText() },
    )

    fun parseText(text: String): List<BudgetTransaction> {
        val rows = parseCsvRows(text, detectCsvDelimiter(text)).filter { row -> row.values.any { it.isNotBlank() } }
        if (rows.isEmpty()) throw BudgetImportException(BudgetImportError.EMPTY_FILE)

        val header = rows.first().values.mapIndexed { index, name -> name.trim() to index }.toMap()
        val columns = REQUIRED_COLUMNS.mapValues { (_, name) ->
            header[name] ?: throw BudgetImportException(BudgetImportError.MISSING_COLUMN, column = name)
        }
        // Комментарий, владелец, группа и оформление присутствуют не в каждой
        // выгрузке; их отсутствие читается как пустые значения.
        val optionalColumns = OPTIONAL_COLUMNS
            .mapNotNull { (key, name) -> header[name]?.let { index -> key to index } }
            .toMap()
        val lastColumn = columns.values.max()

        return rows.drop(1).map { row ->
            if (row.values.size <= lastColumn) {
                throw BudgetImportException(BudgetImportError.TOO_FEW_COLUMNS, line = row.line)
            }
            parseTransaction(row.values, columns, optionalColumns, row.line)
        }.also {
            if (it.isEmpty()) throw BudgetImportException(BudgetImportError.NO_TRANSACTIONS)
        }.let(::linkTransferPairs)
    }

    private fun parseTransaction(
        values: List<String>,
        columns: Map<String, Int>,
        optionalColumns: Map<String, Int>,
        line: Int,
    ): BudgetTransaction {
        fun value(column: String) = values[columns.getValue(column)].trim()

        // Колонки могут отсутствовать; строка может быть короче заявленных колонок.
        fun optionalValue(column: String): String? = optionalColumns[column]
            ?.let { values.getOrNull(it) }
            ?.trim()
            ?.takeIf { it.isNotEmpty() }

        /**
         * Групповая колонка без литерального «null»: внешний экспорт пишет
         * такое значение вместо отсутствующей группы.
         */
        fun meaningfulValue(column: String): String? = optionalValue(column)
            ?.takeIf { !it.equals("null", ignoreCase = true) }

        val accountName = value("account")
        val categoryName = value("category")
        val currencyCode = value("currency").uppercase()
        if (accountName.isEmpty()) throw BudgetImportException(BudgetImportError.EMPTY_ACCOUNT, line = line)
        if (categoryName.isEmpty()) throw BudgetImportException(BudgetImportError.EMPTY_CATEGORY, line = line)
        if (!CURRENCY_REGEX.matches(currencyCode)) {
            throw BudgetImportException(BudgetImportError.BAD_CURRENCY, line = line)
        }

        val amount = try {
            parseAmount(value("amount"))
        } catch (_: NumberFormatException) {
            throw BudgetImportException(BudgetImportError.BAD_AMOUNT, line = line)
        }
        if (amount.signum() == 0) throw BudgetImportException(BudgetImportError.ZERO_AMOUNT, line = line)
        val amountMinor = try {
            amount.abs().movePointRight(2).setScale(0, RoundingMode.UNNECESSARY).longValueExact()
        } catch (_: ArithmeticException) {
            throw BudgetImportException(BudgetImportError.AMOUNT_TOO_LARGE, line = line)
        }

        val date = try {
            LocalDateTime.of(
                LocalDate.parse(value("date")),
                LocalTime.parse(value("time")),
            ).atZone(zone).toInstant().toEpochMilli()
        } catch (_: DateTimeParseException) {
            throw BudgetImportException(BudgetImportError.BAD_DATE, line = line)
        }

        // Баланс файла: значение «Владелец счета», при пустом — «Группы».
        // Оба пустых дают null — сервис создаст автосозданный «Бюджет (валюта)».
        // Внешний экспорт пишет в пустые групповые колонки строку «null» —
        // значение считается пустым.
        val balanceName = meaningfulValue("owner") ?: meaningfulValue("group")

        // Оформление категории: числовые коды «ЦВЕТ» и «ИКОНКА». Колонки может
        // не быть; нечисловое или неизвестное значение ключа не задает.
        val categoryColorKey = optionalValue("color")?.let { colorKey(it) }
        val categoryIconKey = optionalValue("icon")?.let { iconKey(it) }

        return BudgetTransaction(
            accountName = accountName,
            categoryName = categoryName,
            type = if (amount.signum() < 0) TxType.EXPENSE else TxType.INCOME,
            amountMinor = amountMinor,
            currencyCode = currencyCode,
            date = date,
            note = optionalValue("note").orEmpty(),
            balanceName = balanceName,
            categoryIconKey = categoryIconKey,
            categoryColorKey = categoryColorKey,
        )
    }

    /**
     * Сводит ноги переводов пары: категория «Перевод» у обеих строк, один и тот
     * же момент (дата+время — до миллисекунд уже разобранных дат) и
     * противоположные знаки. Списание берёт в пару зачисление с равной суммой,
     * иначе первое свободное противоположное в порядке файла. Каждой паре
     * достаётся свежая связка — по ней сервис соберёт настоящий перевод,
     * в том числе из пары на одном счёте. Непарные строки связки не получают
     * и остаются обычными операциями — строки файла не выбрасываются.
     */
    private fun linkTransferPairs(items: List<BudgetTransaction>): List<BudgetTransaction> {
        val transferCategory = normalizeBudgetCategoryName(CsvFormat.TRANSFER_ROW_CATEGORY)
        val byMoment = items.withIndex()
            .filter { (_, item) -> normalizeBudgetCategoryName(item.categoryName) == transferCategory }
            .groupBy { (_, item) -> item.date }
        val result = items.toMutableList()
        byMoment.values.forEach { legs ->
            val outIndexes = legs.filter { it.value.type == TxType.EXPENSE }.map { it.index }
            val freeInIndexes = legs.filter { it.value.type == TxType.INCOME }.map { it.index }.toMutableList()
            outIndexes.forEach { out ->
                val linked = freeInIndexes.firstOrNull { result[it].amountMinor == result[out].amountMinor }
                    ?: freeInIndexes.firstOrNull()
                    ?: return@forEach
                freeInIndexes.remove(linked)
                // Свежая связка на каждую пару: копии импорта независимы
                // от пар в живых данных и от других загрузок.
                val link = UUID.randomUUID().toString()
                result[out] = result[out].copy(transferLinkId = link)
                result[linked] = result[linked].copy(transferLinkId = link)
            }
        }
        return result
    }

    /**
     * Число листа: научная нотация Excel («-8e+06», «1.27533e+06»), запятая
     * как десятичный разделитель русские таблицы пишут вместо точки, пробелы
     * группировки разрядов — мусор, подлежащий удалению.
     */
    private fun parseAmount(text: String): BigDecimal = numeric(text)

    /**
     * Код оформления из «ЦВЕТ» и «ИКОНКА»: то же число листа, приведенное
     * к целому. Нечисловое или дробное значение кода не задает.
     */
    private fun styleCode(text: String): Long? = try {
        numeric(text).setScale(0, RoundingMode.UNNECESSARY).longValueExact()
    } catch (_: NumberFormatException) {
        null
    } catch (_: ArithmeticException) {
        null
    }

    /** Число листа без мусора разрядов и с точкой-разделителем дробей. */
    private fun numeric(text: String): BigDecimal = BigDecimal(
        text.filterNot { it.isWhitespace() || Character.isSpaceChar(it) }.replace(',', '.'),
    )

    private fun colorKey(code: String): String? = COLOR_CODES[styleCode(code)]

    private fun iconKey(code: String): String? = ICON_CODES[styleCode(code)]

    companion object {

        /** Первая колонка внешнего табличного формата, не встречается в файлах «Бюджета». */
        private const val ID_COLUMN = "ID"

        private const val OWNER_COLUMN = "Владелец счета"
        private const val GROUP_COLUMN = "Группа"

        private val CURRENCY_REGEX = Regex("[A-Z]{3}")
        private val REQUIRED_COLUMNS = mapOf(
            "date" to "Дата",
            "time" to "Время",
            "account" to "Название счета",
            "category" to "Категория",
            "amount" to "Сумма",
            "currency" to "Валюта",
        )
        private val OPTIONAL_COLUMNS = listOf(
            "note" to "Комментарий",
            "owner" to OWNER_COLUMN,
            "group" to GROUP_COLUMN,
            "color" to "ЦВЕТ",
            "icon" to "ИКОНКА",
        )

        /**
         * Числовые коды оформления внешнего приложения → наши ключи.
         * Документации на коды нет: таблица снята с реальных выгрузок
         * (test-import.csv, экспорт ноября, полная история 2024.csv),
         * соответствие подобрано по смыслу категорий. Неизвестный код ключа не задает — категория остается
         * с оформлением по умолчанию, существующая не перекрашивается. Коды
         * цвета 10 и 58 (ноги строк «Перевод») не читаются: у ног перевода
         * категории нет.
         */
        private val COLOR_CODES = mapOf(
            1L to "pink",
            2L to "teal",
            4L to "pink_light",
            8L to "red",
            11L to "pink_light",
            16L to "green",
            17L to "purple",
            22L to "brown",
            28L to "blue",
            34L to "deep_purple",
            35L to "teal",
            39L to "blue",
            40L to "teal",
            41L to "deep_purple",
            47L to "orange",
            53L to "pink",
            56L to "amber",
            59L to "orange",
            63L to "deep_purple",
            64L to "purple",
            65L to "brown",
            70L to "amber",
            71L to "green",
            76L to "blue",
            83L to "teal",
            87L to "purple",
            88L to "blue",
            97L to "brown",
            99L to "brown",
            107L to "purple",
        )

        /** Числовые коды иконок внешнего приложения → ключи [IconCatalog]. */
        private val ICON_CODES = mapOf(
            101L to "swap_horiz",
            203L to "attach_money",
            204L to "attach_money",
            205L to "attach_money",
            209L to "work",
            210L to "swap_horiz",
            300L to "restaurant",
            301L to "restaurant",
            302L to "local_cafe",
            312L to "restaurant",
            400L to "checkroom",
            403L to "checkroom",
            410L to "face",
            502L to "medical_services",
            600L to "home",
            601L to "home",
            800L to "directions_bus",
            802L to "home",
            903L to "star",
            905L to "directions_bus",
            909L to "smartphone",
            1004L to "face",
            1100L to "smartphone",
            1301L to "sports_esports",
            1504L to "card_giftcard",
            1507L to "sports_esports",
            1600L to "card_giftcard",
            1601L to "work",
            1612L to "shopping_cart",
        )

        /**
         * Распознаёт файл внешнего табличного формата по заголовку: первая
         * колонка — «ID», дальше обязательные для чтения колонки. Файлы
         * «Бюджета» начинаются колонкой «Дата» и так формата не дают.
         */
        fun canParse(content: String): Boolean {
            val head = content.removePrefix("\uFEFF").substringBefore('\n', "").ifEmpty { content }
            val cells = parseCsvRows(head, detectCsvDelimiter(head)).firstOrNull()?.values ?: return false
            val names = cells.map { it.trim() }
            return names.firstOrNull() == ID_COLUMN &&
                REQUIRED_COLUMNS.values.all { it in names }
        }
    }
}
