package com.expeysi.app.data.csv

import com.expeysi.app.data.db.AccountDao
import com.expeysi.app.data.db.AccountEntity
import com.expeysi.app.data.db.BalanceDao
import com.expeysi.app.data.db.BalanceEntity
import com.expeysi.app.data.db.CategoryDao
import com.expeysi.app.data.db.CategoryEntity
import com.expeysi.app.data.db.TransactionDao
import com.expeysi.app.data.db.TransactionEntity
import com.expeysi.app.domain.TxType
import com.expeysi.app.ui.icons.CategoryPalette
import com.expeysi.app.ui.icons.IconCatalog
import kotlinx.coroutines.flow.first
import java.io.OutputStream
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import javax.inject.Inject
import kotlin.math.abs

/** Результат экспорта: [transactionCount] — число выгруженных строк операций. */
data class CsvExportResult(val transactionCount: Int)

/** Файл экспорта: содержимое CSV без BOM и число операций в нём. */
internal data class CsvExportFile(val text: String, val transactionCount: Int)

/**
 * Экспорт всех данных в CSV: операции всех активных счетов (выбранных типов)
 * плюс служебные строки начальных остатков с оформлением счетов и балансов.
 * Файл возвращается через «Импорт из Бюджета», поэтому формат задаёт [CsvFormat],
 * а разделитель распознаёт парсер импорта. Необязательные параметры фильтруют
 * выгрузку ([fromMillis], [toMillisExclusive], [accountIds]) и настраивают файл
 * ([delimiter], [bom]); значения по умолчанию повторяют прежнее поведение —
 * все операции, разделитель «;», файл с BOM.
 */
class CsvExportService @Inject constructor(
    private val accountDao: AccountDao,
    private val balanceDao: BalanceDao,
    private val categoryDao: CategoryDao,
    private val transactionDao: TransactionDao,
) {

    /** Полный экспорт в [output]; поток закрывается после записи. */
    suspend fun write(
        output: OutputStream,
        types: Set<TxType> = setOf(TxType.INCOME, TxType.EXPENSE),
        zone: ZoneId = ZoneId.systemDefault(),
        fromMillis: Long? = null,
        toMillisExclusive: Long? = null,
        accountIds: Set<String>? = null,
        delimiter: Char = ';',
        bom: Boolean = true,
    ): CsvExportResult {
        val file = buildCsv(
            accounts = accountDao.listActive(),
            balances = balanceDao.observeAll().first(),
            categories = categoryDao.observeAll().first(),
            transactions = transactionDao.observeAll().first(),
            nowMillis = System.currentTimeMillis(),
            zone = zone,
            types = types,
            fromMillis = fromMillis,
            toMillisExclusive = toMillisExclusive,
            accountIds = accountIds,
            delimiter = delimiter,
        )
        // BOM: парсер его срезает, Excel без него путает кодировку.
        output.use {
            if (bom) it.write(byteArrayOf(0xEF.toByte(), 0xBB.toByte(), 0xBF.toByte()))
            it.write(file.text.toByteArray(Charsets.UTF_8))
        }
        return CsvExportResult(file.transactionCount)
    }
}

private val DATE_FORMAT = DateTimeFormatter.ofPattern("yyyy-MM-dd")
private val TIME_FORMAT = DateTimeFormatter.ofPattern("HH:mm")

/** Полный снимок данных собирается в файл; DAO и потоки остаются снаружи. */
internal fun buildCsv(
    accounts: List<AccountEntity>,
    balances: List<BalanceEntity>,
    categories: List<CategoryEntity>,
    transactions: List<TransactionEntity>,
    nowMillis: Long,
    zone: ZoneId,
    types: Set<TxType>,
    fromMillis: Long? = null,
    toMillisExclusive: Long? = null,
    accountIds: Set<String>? = null,
    delimiter: Char = ';',
): CsvExportFile {
    // null — фильтр счетов не задан, выгружаются все активные счета.
    val activeAccounts = accounts.filterNot { it.deleted }
    val selectedAccounts = if (accountIds == null) {
        activeAccounts
    } else {
        activeAccounts.filter { it.id in accountIds }
    }
    val balanceById = balances.associateBy { it.id }
    val accountById = selectedAccounts.associateBy { it.id }
    val categoryById = categories.associateBy { it.id }
    val includedTransactions = transactions
        .filterNot { it.deleted }
        .filter {
            it.accountId in accountById &&
                TxType.fromWire(it.type) in types &&
                (fromMillis == null || it.date >= fromMillis) &&
                (toMillisExclusive == null || it.date < toMillisExclusive)
        }

    // Заголовок CsvFormat не содержит разделителя в именах колонок, замена безопасна.
    val rows = mutableListOf(CsvFormat.EXTENDED_HEADER.replace(';', delimiter))
    // Служебные строки остатков идут до операций, порядок — по имени счёта.
    selectedAccounts
        .filter { it.openingBalanceMinor != 0L }
        .sortedBy { it.name }
        .forEach { account ->
            // Дата служебной строки — самая ранняя из выгруженных операций счёта,
            // чтобы строка не выглядела датой из будущего; у счёта без операций —
            // время экспорта.
            val date = includedTransactions
                .filter { it.accountId == account.id }
                .minOfOrNull { it.date }
                ?: nowMillis
            rows += serviceRow(account, balanceById[account.balanceId], date, zone, delimiter)
        }
    includedTransactions
        .sortedWith(compareBy({ it.date }, { it.id }))
        .forEach { tx ->
            val account = accountById.getValue(tx.accountId)
            rows += transactionRow(tx, account, balanceById[account.balanceId], categoryById[tx.categoryId], zone, delimiter)
        }
    return CsvExportFile(
        text = rows.joinToString("\r\n") + "\r\n",
        transactionCount = includedTransactions.size,
    )
}

/** Валюта счёта — валюта его баланса; в accounts.currencyCode живёт резервный дефолт. */
private fun currencyOf(account: AccountEntity, balance: BalanceEntity?): String = balance?.currencyCode ?: account.currencyCode

private fun serviceRow(
    account: AccountEntity,
    balance: BalanceEntity?,
    dateMillis: Long,
    zone: ZoneId,
    delimiter: Char,
): String = csvRow(
    delimiter,
    formatDate(dateMillis, zone),
    "00:00",
    account.name,
    CsvFormat.OPENING_ROW_CATEGORY,
    formatAmount(account.openingBalanceMinor),
    currencyOf(account, balance),
    "",
    // У служебной строки категории нет.
    "",
    "",
    account.iconKey,
    account.colorKey,
    balance?.name ?: "",
    balance?.iconKey ?: "",
    balance?.colorKey ?: "",
    // Связка перевода есть только у ног переводов.
    "",
)

private fun transactionRow(
    tx: TransactionEntity,
    account: AccountEntity,
    balance: BalanceEntity?,
    category: CategoryEntity?,
    zone: ZoneId,
    delimiter: Char,
): String {
    // Нога перевода несёт категорию «Перевод» вместо пустого categoryId: файл
    // «Бюджета» пустую категорию не понимает. Связка и иконка перевода дают
    // импорту приложения восстановить настоящий перевод, а читателю файла —
    // увидеть оформление даже там, где категории нет.
    val isTransfer = TxType.fromWire(tx.type).isTransfer
    return csvRow(
        delimiter,
        formatDate(tx.date, zone),
        formatTime(tx.date, zone),
        account.name,
        when {
            isTransfer -> CsvFormat.TRANSFER_ROW_CATEGORY
            else -> category?.name ?: CsvFormat.FALLBACK_CATEGORY
        },
        formatAmount(signedMinor(tx)),
        currencyOf(account, balance),
        tx.note,
        if (isTransfer) IconCatalog.TRANSFER_KEY else category?.iconKey ?: "",
        if (isTransfer) CategoryPalette.DEFAULT_KEY else category?.colorKey ?: "",
        account.iconKey,
        account.colorKey,
        balance?.name ?: "",
        balance?.iconKey ?: "",
        balance?.colorKey ?: "",
        if (isTransfer) tx.linkedId else "",
    )
}

/**
 * Доход пишется положительной суммой, расход — отрицательной, как в формате
 * «Бюджета». Ноги перевода идут в ту же знаковую логику: списание — минус,
 * зачисление — плюс, поэтому остатки счетов после реимпорта совпадают.
 */
private fun signedMinor(tx: TransactionEntity): Long = when (TxType.fromWire(tx.type)) {
    TxType.INCOME, TxType.TRANSFER_IN -> tx.amountMinor
    TxType.EXPENSE, TxType.TRANSFER_OUT -> -tx.amountMinor
}

private fun csvRow(delimiter: Char, vararg fields: String): String = fields.joinToString(delimiter.toString()) { escape(it, delimiter) }

/** 123456 → "1234.56", -10010 → "-100.10": точка и два знака, без группировки. */
internal fun formatAmount(minor: Long): String {
    val sign = if (minor < 0) "-" else ""
    val absolute = abs(minor)
    return "$sign${absolute / 100}.${(absolute % 100).toString().padStart(2, '0')}"
}

/** RFC 4180: поле с разделителем, кавычкой или переводом строки — в кавычках, кавычки удваиваются. */
internal fun escape(field: String, delimiter: Char = ';'): String = if (field.none { it == delimiter || it == '"' || it == '\n' || it == '\r' }) {
    field
} else {
    "\"" + field.replace("\"", "\"\"") + "\""
}

private fun formatDate(millis: Long, zone: ZoneId): String = DATE_FORMAT.format(Instant.ofEpochMilli(millis).atZone(zone))

private fun formatTime(millis: Long, zone: ZoneId): String = TIME_FORMAT.format(Instant.ofEpochMilli(millis).atZone(zone))
