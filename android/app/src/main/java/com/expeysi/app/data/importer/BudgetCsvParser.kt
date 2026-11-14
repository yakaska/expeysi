package com.expeysi.app.data.importer

import com.expeysi.app.data.csv.CsvFormat
import com.expeysi.app.domain.Money
import com.expeysi.app.domain.TxType
import java.io.InputStream
import java.math.BigDecimal
import java.math.RoundingMode
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.ZoneId
import java.time.format.DateTimeParseException
import javax.inject.Inject

data class BudgetTransaction(
    val accountName: String,
    val categoryName: String,
    val type: TxType,
    val amountMinor: Long,
    val currencyCode: String,
    val date: Long,
    val note: String,
    // Атрибуты оформления из файла экспорта Expeysi; null — колонки в файле нет.
    val categoryIconKey: String? = null,
    val categoryColorKey: String? = null,
    val accountIconKey: String? = null,
    val accountColorKey: String? = null,
    val balanceName: String? = null,
    val balanceIconKey: String? = null,
    val balanceColorKey: String? = null,
    // Связка перевода: ноги одного перевода делят одно значение; null — колонки нет.
    val transferLinkId: String? = null,
)

class BudgetCsvParser @Inject constructor() {
    private val zone: ZoneId = ZoneId.systemDefault()

    fun parse(input: InputStream): List<BudgetTransaction> {
        val text = input.bufferedReader(Charsets.UTF_8).use { it.readText() }.removePrefix("\uFEFF")
        val rows = parseRows(text, detectDelimiter(text)).filter { row -> row.values.any { it.isNotBlank() } }
        if (rows.isEmpty()) throw BudgetImportException(BudgetImportError.EMPTY_FILE)

        val header = rows.first().values.mapIndexed { index, name -> name.trim() to index }.toMap()
        val columns = REQUIRED_COLUMNS.mapValues { (_, name) ->
            header[name] ?: throw BudgetImportException(BudgetImportError.MISSING_COLUMN, column = name)
        }
        // Колонки оформления экспорта Expeysi читаются, только если заявлены в заголовке.
        val optionalColumns = OPTIONAL_COLUMNS.mapNotNull { header[it]?.let { index -> it to index } }.toMap()
        val lastColumn = columns.values.max()

        return rows.drop(1).map { row ->
            if (row.values.size <= lastColumn) {
                throw BudgetImportException(BudgetImportError.TOO_FEW_COLUMNS, line = row.line)
            }
            parseTransaction(row.values, columns, optionalColumns, row.line)
        }.also {
            if (it.isEmpty()) throw BudgetImportException(BudgetImportError.NO_TRANSACTIONS)
        }
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

        val accountName = value("account")
        val categoryName = value("category")
        val currencyCode = value("currency").uppercase()
        if (accountName.isEmpty()) throw BudgetImportException(BudgetImportError.EMPTY_ACCOUNT, line = line)
        if (categoryName.isEmpty()) throw BudgetImportException(BudgetImportError.EMPTY_CATEGORY, line = line)
        if (!CURRENCY_REGEX.matches(currencyCode)) {
            throw BudgetImportException(BudgetImportError.BAD_CURRENCY, line = line)
        }

        val amount = try {
            BigDecimal(value("amount"))
        } catch (_: NumberFormatException) {
            throw BudgetImportException(BudgetImportError.BAD_AMOUNT, line = line)
        }
        if (amount.signum() == 0) throw BudgetImportException(BudgetImportError.ZERO_AMOUNT, line = line)
        val amountMinor = try {
            amount.abs().movePointRight(Money.SCALE_DIGITS).setScale(0, RoundingMode.UNNECESSARY).longValueExact()
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

        return BudgetTransaction(
            accountName = accountName,
            categoryName = categoryName,
            type = if (amount.signum() < 0) TxType.EXPENSE else TxType.INCOME,
            amountMinor = amountMinor,
            currencyCode = currencyCode,
            date = date,
            note = value("note"),
            categoryIconKey = optionalValue(CsvFormat.CATEGORY_ICON_COLUMN),
            categoryColorKey = optionalValue(CsvFormat.CATEGORY_COLOR_COLUMN),
            accountIconKey = optionalValue(CsvFormat.ACCOUNT_ICON_COLUMN),
            accountColorKey = optionalValue(CsvFormat.ACCOUNT_COLOR_COLUMN),
            balanceName = optionalValue(CsvFormat.BALANCE_NAME_COLUMN),
            balanceIconKey = optionalValue(CsvFormat.BALANCE_ICON_COLUMN),
            balanceColorKey = optionalValue(CsvFormat.BALANCE_COLOR_COLUMN),
            transferLinkId = optionalValue(CsvFormat.TRANSFER_LINK_COLUMN),
        )
    }

    /**
     * Разделитель опознаётся по заголовочной строке: где встречается чаще — тот и
     * разделитель. Запятые файлы пишут Excel; имена колонок не содержат ни запятых,
     * ни точек с запятой, поэтому подсчёт однозначен.
     */
    private fun detectDelimiter(text: String): Char {
        val head = text.substringBefore('\n', "").ifEmpty { text }
        return if (head.count { it == ',' } > head.count { it == ';' }) ',' else ';'
    }

    private fun parseRows(text: String, delimiter: Char): List<CsvRow> {
        val rows = mutableListOf<CsvRow>()
        var row = mutableListOf<String>()
        var field = StringBuilder()
        var line = 1
        var rowLine = 1
        var inQuotes = false
        var index = 0

        fun endField() {
            row.add(field.toString())
            field = StringBuilder()
        }

        fun endRow() {
            endField()
            rows.add(CsvRow(row, rowLine))
            row = mutableListOf()
            rowLine = line + 1
        }

        while (index < text.length) {
            val char = text[index]
            when {
                inQuotes && char == '"' && text.getOrNull(index + 1) == '"' -> {
                    field.append('"')
                    index++
                }
                char == '"' -> inQuotes = !inQuotes
                !inQuotes && char == delimiter -> endField()
                !inQuotes && (char == '\n' || char == '\r') -> {
                    endRow()
                    if (char == '\r' && text.getOrNull(index + 1) == '\n') index++
                    line++
                    rowLine = line
                }
                else -> field.append(char)
            }
            index++
        }
        if (inQuotes) throw BudgetImportException(BudgetImportError.UNCLOSED_QUOTE, line = rowLine)
        if (field.isNotEmpty() || row.isNotEmpty()) endRow()
        return rows
    }

    private data class CsvRow(val values: List<String>, val line: Int)

    private companion object {
        val CURRENCY_REGEX = Regex("[A-Z]{3}")
        val REQUIRED_COLUMNS = mapOf(
            "date" to "Дата",
            "time" to "Время",
            "account" to "Название счета",
            "category" to "Категория",
            "amount" to "Сумма",
            "currency" to "Валюта",
            "note" to "Комментарий",
        )
        val OPTIONAL_COLUMNS = listOf(
            CsvFormat.CATEGORY_ICON_COLUMN,
            CsvFormat.CATEGORY_COLOR_COLUMN,
            CsvFormat.ACCOUNT_ICON_COLUMN,
            CsvFormat.ACCOUNT_COLOR_COLUMN,
            CsvFormat.BALANCE_NAME_COLUMN,
            CsvFormat.BALANCE_ICON_COLUMN,
            CsvFormat.BALANCE_COLOR_COLUMN,
            CsvFormat.TRANSFER_LINK_COLUMN,
        )
    }
}

/** Причина ошибки импорта CSV «Бюджета»; текст показа резолвится в UI-слое. */
enum class BudgetImportError {
    EMPTY_FILE,
    MISSING_COLUMN,
    TOO_FEW_COLUMNS,
    NO_TRANSACTIONS,
    EMPTY_ACCOUNT,
    EMPTY_CATEGORY,
    BAD_CURRENCY,
    BAD_AMOUNT,
    ZERO_AMOUNT,
    AMOUNT_TOO_LARGE,
    BAD_DATE,
    UNCLOSED_QUOTE,
}

/**
 * Ошибка разбора файла импорта: [reason] — что именно не так,
 * [line] — номер строки CSV, если ошибка построчная,
 * [column] — имя ожидаемой колонки, если ошибка в заголовке.
 */
class BudgetImportException(
    val reason: BudgetImportError,
    val line: Int? = null,
    val column: String? = null,
) : Exception()
