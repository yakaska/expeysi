package com.expeysi.app.ui.home

import com.expeysi.app.data.db.DebtAdjustmentEntity
import com.expeysi.app.data.db.TransactionEntity
import com.expeysi.app.domain.DebtAdjustmentKind
import com.expeysi.app.domain.DebtWriteOffs
import com.expeysi.app.domain.TxType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class FeedEntriesTest {

    private fun item(
        type: TxType,
        amountMinor: Long = 10_000L,
        date: Long = 1_000L,
        feeMinor: Long = 0L,
    ) = TransactionItem(
        transaction = TransactionEntity(
            id = "$type-$amountMinor-$feeMinor-$date",
            type = type.wire,
            amountMinor = amountMinor,
            accountId = "acc",
            categoryId = "",
            date = date,
            feeMinor = feeMinor,
            updatedAt = 0L,
        ),
        categoryName = "Категория",
        categoryIconKey = "",
        categoryColorKey = "",
        accountName = "Счёт",
        currencySymbol = "₽",
    )

    private fun adjustment(
        amountMinor: Long,
        date: Long = 1_000L,
        countInStats: Boolean = true,
    ) = DebtAdjustmentEntity(
        id = "adj-$amountMinor-$date",
        counterpartyId = "c1",
        kind = DebtAdjustmentKind.WRITE_OFF.wire,
        amountMinor = amountMinor,
        currencyCode = "RUB",
        date = date,
        countInStats = countInStats,
        updatedAt = 0L,
    )

    /* -------------------------------- daySums -------------------------------- */

    @Test
    fun `переводы и долговые потоки нейтральны в шапке дня`() {
        val sums = FeedEntries.daySums(
            listOf(
                item(TxType.DEBT_LEND),
                item(TxType.DEBT_REPAY_IN),
                item(TxType.DEBT_BORROW),
                item(TxType.DEBT_REPAY_OUT),
                item(TxType.TRANSFER_OUT),
                item(TxType.TRANSFER_IN),
            ),
        )

        assertEquals(FeedEntries.DaySums(incomeMinor = 0L, expenseMinor = 0L), sums)
    }

    @Test
    fun `доходы и расходы дня - по знаку операции`() {
        val sums = FeedEntries.daySums(
            listOf(
                item(TxType.INCOME, amountMinor = 12_000L),
                item(TxType.EXPENSE, amountMinor = 5_000L),
            ),
        )

        assertEquals(12_000L, sums.incomeMinor)
        assertEquals(5_000L, sums.expenseMinor)
    }

    @Test
    fun `комиссия перевода входит в расход дня`() {
        // Сам перевод нейтрален, но комиссия — реальная потеря денег семьи.
        val sums = FeedEntries.daySums(
            listOf(item(TxType.TRANSFER_OUT, amountMinor = 10_100L, feeMinor = 100L)),
        )

        assertEquals(100L, sums.expenseMinor)
    }

    @Test
    fun `псевдостроки списаний считаются доходом и расходом дня`() {
        // Те же строки, что ViewModel кладёт в ленту: шапка дня сходится
        // со сводкой, где countInStats-списания уже в окнах.
        val sums = FeedEntries.daySums(
            listOf(
                FeedEntries.writeOffItem(adjustment(amountMinor = 30_000L), "₽"),
                FeedEntries.writeOffItem(adjustment(amountMinor = -50_000L), "₽"),
            ),
        )

        assertEquals(30_000L, sums.incomeMinor)
        assertEquals(50_000L, sums.expenseMinor)
    }

    /* ------------------------------- writeOffItem ------------------------------ */

    @Test
    fun `псевдострока списания собирается из корректировки со плюсом`() {
        val entry = FeedEntries.writeOffItem(adjustment(amountMinor = 30_000L, date = 7_000L), "₽")

        assertEquals("writeoff-adj-30000-7000", entry.transaction.id)
        assertEquals(TxType.INCOME.wire, entry.transaction.type)
        assertEquals(30_000L, entry.transaction.amountMinor)
        // У корректировки нет счёта: за строкой нет записи в журнале операций.
        assertEquals("", entry.transaction.accountId)
        assertEquals(DebtWriteOffs.CATEGORY_ID, entry.transaction.categoryId)
        assertEquals(DebtWriteOffs.ICON_KEY, entry.categoryIconKey)
        assertEquals(DebtWriteOffs.COLOR_KEY, entry.categoryColorKey)
        assertEquals(7_000L, entry.transaction.date)
        assertTrue(entry.isWriteOffEntry)
        assertFalse(entry.isTransfer)
        assertEquals("₽", entry.currencySymbol)
    }

    @Test
    fun `списание в минус пишется расходом по модулю`() {
        val entry = FeedEntries.writeOffItem(adjustment(amountMinor = -50_000L), "₽")

        assertEquals(TxType.EXPENSE.wire, entry.transaction.type)
        assertEquals(50_000L, entry.transaction.amountMinor)
    }
}
