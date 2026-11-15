package com.expeysi.app.ui.history

import com.expeysi.app.ui.home.TransactionItem
import com.expeysi.app.ui.home.TransferAccount
import com.expeysi.app.ui.home.TransferCard
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

/** Запись списка истории одного дня: одиночная операция или свёрнутая серия. */
sealed interface HistoryListEntry {
    data class Single(val item: TransactionItem) : HistoryListEntry

    /**
     * Серия одинаковых переводов: id — id первой операции серии (ключ элемента
     * списка — «group-$id»), items — члены серии по порядку.
     */
    data class Group(val id: String, val items: List<TransactionItem>) : HistoryListEntry
}

/** Минимальное число одинаковых переводов подряд, сворачиваемых в серию. */
private const val TRANSFER_GROUP_MIN_SIZE = 3

/**
 * Сводка серии для строки списка: счета-участники, валюты ног и суммы.
 * Строится один раз по членам серии.
 */
data class TransferGroupModel(
    val source: TransferAccount,
    val destination: TransferAccount,
    /** Серия обменивает валюты: итоги показываются обеими суммами. */
    val conversion: Boolean,
    val sentSymbol: String,
    val sentCurrencyCode: String,
    val receivedSymbol: String,
    val receivedCurrencyCode: String,
    val count: Int,
    val totalSentMinor: Long,
    val totalReceivedMinor: Long,
    /**
     * Сумма комиссий членов серии. Комиссия живёт в валюте счёта-источника,
     * а он у всех членов общий (ключ серии), поэтому валюта ровно одна.
     */
    val totalFeeMinor: Long,
    val items: List<TransactionItem>,
)

/**
 * Обмен валют: различаются ISO-коды; без кодов — различаются символы.
 * По нему карточка решает, показывать ли строку курса: одновалютный
 * перевод («1 ₫ = 1,00 ₫») курса не имеет.
 */
fun isConversion(
    sourceCode: String,
    receivedCode: String,
    sourceSymbol: String,
    receivedSymbol: String,
): Boolean = when {
    sourceCode.isNotBlank() && receivedCode.isNotBlank() -> sourceCode != receivedCode
    else -> sourceSymbol.isNotBlank() && sourceSymbol != receivedSymbol
}

/** Сводка по серии: у всех членов есть карточки, это гарантирует groupItems. */
fun buildTransferGroupModel(items: List<TransactionItem>): TransferGroupModel {
    val card = items.first().transferCard
        ?: error("Серия собирается только из карточек переводов")
    return TransferGroupModel(
        source = card.source,
        destination = card.destination,
        conversion = isConversion(
            sourceCode = card.sourceCurrencyCode,
            receivedCode = card.receivedCurrencyCode,
            sourceSymbol = card.sourceCurrencySymbol,
            receivedSymbol = card.receivedCurrencySymbol,
        ),
        sentSymbol = card.sourceCurrencySymbol,
        sentCurrencyCode = card.sourceCurrencyCode,
        receivedSymbol = card.receivedCurrencySymbol,
        receivedCurrencyCode = card.receivedCurrencyCode,
        count = items.size,
        totalSentMinor = items.sumOf { it.transferCard?.sentMinor ?: 0L },
        totalReceivedMinor = items.sumOf { it.transferCard?.receivedMinor ?: 0L },
        totalFeeMinor = items.sumOf { it.transferCard?.feeMinor ?: 0L },
        items = items,
    )
}

/**
 * Разбивает операции на строки показа: одиночные записи и серии переводов.
 * Серию собирают одинаковые переводы — совпадают счета-участники и валюты ног
 * ([TransferCard.groupKey]) — если идут подряд не меньше трёх; более короткие
 * пробеги остаются одиночными карточками. Серию прерывают не-переводы,
 * другие серии и граница суток: группа никогда не пересекает стик-заголовок
 * дня, даже если ей передать несколько дней сразу.
 */
fun groupItems(items: List<TransactionItem>): List<HistoryListEntry> {
    val result = mutableListOf<HistoryListEntry>()
    var series = mutableListOf<TransactionItem>()
    var seriesKey = ""
    var seriesDate: LocalDate? = null

    fun closeSeries() {
        if (series.size >= TRANSFER_GROUP_MIN_SIZE) {
            result += HistoryListEntry.Group(id = series.first().transaction.id, items = series.toList())
        } else {
            series.forEach { result += HistoryListEntry.Single(it) }
        }
        series = mutableListOf()
        seriesKey = ""
        seriesDate = null
    }

    fun dateOf(millis: Long): LocalDate =
        Instant.ofEpochMilli(millis).atZone(ZoneId.systemDefault()).toLocalDate()

    items.forEach { item ->
        val key = item.transferCard?.groupKey.orEmpty()
        val date = dateOf(item.transaction.date)
        val continues = key.isNotEmpty() && key == seriesKey && date == seriesDate
        if (continues) {
            series += item
        } else {
            if (series.isNotEmpty()) closeSeries()
            if (key.isNotEmpty()) {
                seriesKey = key
                seriesDate = date
                series += item
            } else {
                // Не-перевод или одиночная нога без карточки: серия не собирается.
                result += HistoryListEntry.Single(item)
            }
        }
    }
    if (series.isNotEmpty()) closeSeries()
    return result
}
