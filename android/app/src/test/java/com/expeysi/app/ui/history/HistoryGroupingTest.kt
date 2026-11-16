package com.expeysi.app.ui.history

import com.expeysi.app.data.db.TransactionEntity
import com.expeysi.app.domain.TxType
import com.expeysi.app.ui.home.TransactionItem
import com.expeysi.app.ui.home.TransferAccount
import com.expeysi.app.ui.home.TransferCard
import com.expeysi.app.ui.home.TransferSide
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate
import java.time.ZoneId

class HistoryGroupingTest {

    /** Полдень суток в локальном времени: безопасно внутри дня в любой зоне. */
    private fun noon(year: Int, month: Int, day: Int): Long = LocalDate
        .of(year, month, day)
        .atTime(12, 0)
        .atZone(ZoneId.systemDefault())
        .toInstant()
        .toEpochMilli()

    private val day1: Long = noon(2026, 1, 15)
    private val day2: Long = noon(2026, 1, 16)

    private fun transferItem(id: String, date: Long, feeMinor: Long = 0): TransactionItem = TransactionItem(
        transaction = TransactionEntity(
            id = id,
            type = TxType.TRANSFER_OUT.wire,
            amountMinor = 100_000 + feeMinor,
            accountId = "acc-a",
            categoryId = "",
            date = date,
            updatedAt = 0,
        ),
        categoryName = "",
        categoryIconKey = "",
        categoryColorKey = "default",
        accountName = "A",
        currencySymbol = "₽",
        isTransfer = true,
        // Одиночная нога истории счёта; для группировки важен groupKey карточки.
        isTransferPair = false,
        transferCard = TransferCard(
            source = TransferAccount("acc-a", "A", "credit_card", "blue"),
            destination = TransferAccount("acc-b", "B", "payments", "amber"),
            sentMinor = 100_000,
            sourceCurrencySymbol = "₽",
            sourceCurrencyCode = "RUB",
            feeMinor = feeMinor,
            receivedMinor = 100_000,
            receivedCurrencySymbol = "₽",
            receivedCurrencyCode = "RUB",
            ownSide = TransferSide.SOURCE,
            date = date,
            note = "",
            groupKey = "acc-a|acc-b|RUB|RUB",
        ),
    )

    private fun expenseItem(id: String, date: Long): TransactionItem = TransactionItem(
        transaction = TransactionEntity(
            id = id,
            type = TxType.EXPENSE.wire,
            amountMinor = 50_000,
            accountId = "acc-a",
            categoryId = "cat-1",
            date = date,
            updatedAt = 0,
        ),
        categoryName = "Еда",
        categoryIconKey = "restaurant",
        categoryColorKey = "orange",
        accountName = "A",
        currencySymbol = "₽",
    )

    @Test
    fun `пробег из двух остаётся одиночными карточками`() {
        val entries = groupItems(
            listOf(
                transferItem("t1", day1),
                transferItem("t2", day1),
            ),
        )
        assertEquals(2, entries.size)
        assertTrue(entries.all { it is HistoryListEntry.Single })
    }

    @Test
    fun `пробег из трёх сворачивается в группу`() {
        val entries = groupItems(
            listOf(
                transferItem("t1", day1),
                transferItem("t2", day1),
                transferItem("t3", day1),
            ),
        )
        assertEquals(1, entries.size)
        val group = entries.single() as HistoryListEntry.Group
        // Id группы — id первой операции серии.
        assertEquals("t1", group.id)
        assertEquals(listOf("t1", "t2", "t3"), group.items.map { it.transaction.id })
    }

    @Test
    fun `серия прерывается посторонней операцией`() {
        val entries = groupItems(
            listOf(
                transferItem("t1", day1),
                transferItem("t2", day1),
                transferItem("t3", day1),
                expenseItem("e1", day1),
                transferItem("t4", day1),
                transferItem("t5", day1),
                transferItem("t6", day1),
            ),
        )
        assertEquals(3, entries.size)
        val first = entries[0] as HistoryListEntry.Group
        assertEquals("t1", first.id)
        assertEquals(3, first.items.size)
        val middle = entries[1] as HistoryListEntry.Single
        assertEquals("e1", middle.item.transaction.id)
        val second = entries[2] as HistoryListEntry.Group
        assertEquals("t4", second.id)
        assertEquals(3, second.items.size)
    }

    @Test
    fun `серия не пересекает границу дня`() {
        // Два перевода в первый день и три во второй: слияние дней дало бы
        // группу из пяти, а граница дня оставляет первый день одиночным.
        val entries = groupItems(
            listOf(
                transferItem("t1", day1),
                transferItem("t2", day1),
                transferItem("t3", day2),
                transferItem("t4", day2),
                transferItem("t5", day2),
            ),
        )
        assertEquals(3, entries.size)
        assertTrue(entries[0] is HistoryListEntry.Single)
        assertTrue(entries[1] is HistoryListEntry.Single)
        val group = entries[2] as HistoryListEntry.Group
        assertEquals("t3", group.id)
        assertEquals(3, group.items.size)
    }

    @Test
    fun `комиссии серии суммируются в один чип`() {
        // Комиссия есть не у всех членов — чип считается только по непустым.
        val entries = groupItems(
            listOf(
                transferItem("t1", day1, feeMinor = 5_000),
                transferItem("t2", day1),
                transferItem("t3", day1, feeMinor = 7_000),
            ),
        )
        val group = entries.single() as HistoryListEntry.Group
        val model = buildTransferGroupModel(group.items)
        assertEquals(12_000L, model.totalFeeMinor)
        // Сами суммы ног комиссию не включают: списание — 100 000, не с комиссией.
        assertEquals(300_000L, model.totalSentMinor)
        assertEquals(3, model.count)
    }

    @Test
    fun `группа без комиссий показывает нулевую сумму`() {
        val entries = groupItems(
            listOf(
                transferItem("t1", day1),
                transferItem("t2", day1),
                transferItem("t3", day1),
            ),
        )
        val group = entries.single() as HistoryListEntry.Group
        // Нулевое значение — признак скрыть чип вместе с его местом.
        assertEquals(0L, buildTransferGroupModel(group.items).totalFeeMinor)
    }

    @Test
    fun `строка курса видна только при обмене валют`() {
        // Разные ISO-коды — курс показывается.
        assertTrue(isConversion("USD", "VND", "$", "₫"))
        // Одинаковая валюта — строки «1 VND = 1,00 VND» не бывает.
        assertTrue(!isConversion("VND", "VND", "₫", "₫"))
        // Без ISO-кодов серия ориентируется на символы валют.
        assertTrue(isConversion("", "", "$", "₫"))
        assertTrue(!isConversion("", "", "₫", "₫"))
        // Коды есть хотя бы у одной ноги: приоритет у кодов.
        assertTrue(isConversion("USD", "", "$", "₫"))
        assertTrue(!isConversion("", "VND", "₫", "₫"))
    }
}
