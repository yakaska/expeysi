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
import kotlinx.coroutines.flow.first
import java.io.OutputStream
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import javax.inject.Inject
import kotlin.math.abs

/** Результат экспорта: [transactionCount] — выгружено обычных операций. */
data class CsvExportResult(val transactionCount: Int)

/** Файл экспорта: содержимое CSV без BOM и число операций в нём. */
internal data class CsvExportFile(val text: String, val transactionCount: Int)

/**
 * Экспорт всех данных в CSV: операции всех активных счетов (выбранных типов)
 * плюс служебные строки начальных остатков с оформлением счетов и балансов.
 * Файл возвращается через «Импорт из Бюджета», поэтому формат задаёт [CsvFormat].
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
    ): CsvExportResult {
        val file = buildCsv(
            accounts = accountDao.listActive(),
            balances = balanceDao.observeAll().first(),
            categories = categoryDao.observeAll().first(),
            transactions = transactionDao.observeAll().first(),
            nowMillis = System.currentTimeMillis(),
            zone = zone,
            types = types,
        )
        // BOM: парсер его срезает, Excel без него путает кодировку.
        output.use {
            it.write(byteArrayOf(0xEF.toByte(), 0xBB.toByte(), 0xBF.toByte()))
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
): CsvExportFile {
    val activeAccounts = accounts.filterNot { it.deleted }
    val balanceById = balances.associateBy { it.id }
    val accountById = activeAccounts.associateBy { it.id }
    val categoryById = categories.associateBy { it.id }
    val includedTransactions = transactions
        .filterNot { it.deleted }
        .filter { it.accountId in accountById && TxType.fromWire(it.type) in types }

    val rows = mutableListOf(CsvFormat.EXTENDED_HEADER)
    // Служебные строки остатков идут до операций, порядок — по имени счёта.
    activeAccounts
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
            rows += serviceRow(account, balanceById[account.balanceId], date, zone)
        }
    includedTransactions
        .sortedWith(compareBy({ it.date }, { it.id }))
        .forEach { tx ->
            val account = accountById.getValue(tx.accountId)
            rows += transactionRow(tx, account, balanceById[account.balanceId], categoryById[tx.categoryId], zone)
        }
    return CsvExportFile(
        text = rows.joinToString("\r\n") + "\r\n",
        transactionCount = includedTransactions.size,
    )
}

/** Валюта счёта — валюта его баланса; в accounts.currencyCode живёт резервный дефолт. */
private fun currencyOf(account: AccountEntity, balance: BalanceEntity?): String =
    balance?.currencyCode ?: account.currencyCode

private fun serviceRow(account: AccountEntity, balance: BalanceEntity?, dateMillis: Long, zone: ZoneId): String =
    csvRow(
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
    )

private fun transactionRow(
    tx: TransactionEntity,
    account: AccountEntity,
    balance: BalanceEntity?,
    category: CategoryEntity?,
    zone: ZoneId,
): String = csvRow(
    formatDate(tx.date, zone),
    formatTime(tx.date, zone),
    account.name,
    category?.name ?: CsvFormat.FALLBACK_CATEGORY,
    formatAmount(signedMinor(tx)),
    currencyOf(account, balance),
    tx.note,
    category?.iconKey ?: "",
    category?.colorKey ?: "",
    account.iconKey,
    account.colorKey,
    balance?.name ?: "",
    balance?.iconKey ?: "",
    balance?.colorKey ?: "",
)

/** Доход пишется положительной суммой, расход — отрицательной, как в формате «Бюджета». */
private fun signedMinor(tx: TransactionEntity): Long = when (TxType.fromWire(tx.type)) {
    TxType.INCOME -> tx.amountMinor
    TxType.EXPENSE -> -tx.amountMinor
}

private fun csvRow(vararg fields: String): String = fields.joinToString(";") { escape(it) }

/** 123456 → "1234.56", -10010 → "-100.10": точка и два знака, без группировки. */
internal fun formatAmount(minor: Long): String {
    val sign = if (minor < 0) "-" else ""
    val absolute = abs(minor)
    return "$sign${absolute / 100}.${(absolute % 100).toString().padStart(2, '0')}"
}

/** RFC 4180: поле с разделителем, кавычкой или переводом строки — в кавычках, кавычки удваиваются. */
internal fun escape(field: String): String =
    if (field.none { it == ';' || it == '"' || it == '\n' || it == '\r' }) {
        field
    } else {
        "\"" + field.replace("\"", "\"\"") + "\""
    }

private fun formatDate(millis: Long, zone: ZoneId): String =
    DATE_FORMAT.format(Instant.ofEpochMilli(millis).atZone(zone))

private fun formatTime(millis: Long, zone: ZoneId): String =
    TIME_FORMAT.format(Instant.ofEpochMilli(millis).atZone(zone))
