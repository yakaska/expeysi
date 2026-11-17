package com.expeysi.app.domain

import com.expeysi.app.data.db.DebtAdjustmentEntity
import com.expeysi.app.data.db.TransactionEntity
import com.expeysi.app.ui.home.FeedEntries
import com.expeysi.app.ui.home.TransactionItem
import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.LocalDate
import java.time.ZoneId

/**
 * Связка «баланс — это наличные на руках, доходы и расходы — по начислению»:
 * одни и те же операции прогоняются через [BalanceCalculator] (движение
 * остатка счёта) и [ProfitAndLoss] вместе с FeedEntries (сводка и шапки
 * дней). Долг двигает баланс, но не доходы и не расходы; учтённое списание —
 * наоборот, доход или расход без движения наличных.
 */
class ProfitAndLossTest {

    private val zone: ZoneId = ZoneId.of("Europe/Moscow")

    /** «Сегодня» теста: все окна отсчитываются от него. */
    private val today: LocalDate = LocalDate.of(2026, 11, 17)

    private val todayStart: Long = Periods.day(today, zone).startMillis

    private fun tx(
        type: TxType,
        amountMinor: Long,
        date: Long = todayStart + 3_600_000L,
        feeMinor: Long = 0L,
        accountId: String = "acc",
    ) = TransactionEntity(
        id = "$type-$amountMinor-$feeMinor-$date",
        type = type.wire,
        amountMinor = amountMinor,
        accountId = accountId,
        categoryId = "",
        date = date,
        feeMinor = feeMinor,
        updatedAt = 0L,
    )

    private fun adjustment(
        amountMinor: Long,
        date: Long = todayStart,
        countInStats: Boolean = true,
        currency: String = "RUB",
    ) = DebtAdjustmentEntity(
        id = "adj-$amountMinor-$date-$currency",
        counterpartyId = "c1",
        kind = DebtAdjustmentKind.WRITE_OFF.wire,
        amountMinor = amountMinor,
        currencyCode = currency,
        date = date,
        countInStats = countInStats,
        updatedAt = 0L,
    )

    /** Обычный день: доход, расход, выданный долг и перевод с комиссией. */
    private val operations = listOf(
        tx(TxType.INCOME, 50_000L),
        tx(TxType.EXPENSE, 20_000L),
        tx(TxType.DEBT_LEND, 30_000L),
        // amountMinor — всего списано (отправлено + комиссия).
        tx(TxType.TRANSFER_OUT, 10_100L, feeMinor = 100L),
    )

    @Test
    fun `баланс двигают все потоки, доходы и расходы - нет`() {
        val opening = 100_000L
        val balance = BalanceCalculator.balanceMinor(
            opening,
            operations.map { TxSum(TxType.fromWire(it.type), it.amountMinor) },
        )

        // Наличные на руках: +50 000 − 20 000 − 30 000 (долг) − 10 100
        // (перевод вместе с комиссией).
        assertEquals(89_900L, balance)

        val window = ProfitAndLoss.windowSums(
            transactions = operations,
            adjustments = emptyList(),
            accountIds = setOf("acc"),
            balanceCurrencyCode = "RUB",
            todayStartMillis = todayStart,
            zone = zone,
        )

        // Долг и перевод не доход и не расход: только 50 000 и 20 000 + комиссия.
        assertEquals(50_000L, window.income.dayMinor)
        assertEquals(20_100L, window.expense.dayMinor)
    }

    @Test
    fun `учтённое списание попадает в сводку без движения наличных`() {
        val window = ProfitAndLoss.windowSums(
            transactions = operations,
            adjustments = listOf(adjustment(amountMinor = 40_000L), adjustment(amountMinor = -8_000L)),
            accountIds = setOf("acc"),
            balanceCurrencyCode = "RUB",
            todayStartMillis = todayStart,
            zone = zone,
        )

        assertEquals(90_000L, window.income.dayMinor)
        assertEquals(28_100L, window.expense.dayMinor)
        // Наличные не двигались: баланс операции не меняют вовсе.

        val untouched = ProfitAndLoss.windowSums(
            transactions = operations,
            adjustments = listOf(
                adjustment(amountMinor = 40_000L, countInStats = false),
                adjustment(amountMinor = 40_000L, currency = "USD"),
                adjustment(amountMinor = 0L).copy(deleted = true, amountMinor = 40_000L),
            ),
            accountIds = setOf("acc"),
            balanceCurrencyCode = "RUB",
            todayStartMillis = todayStart,
            zone = zone,
        )

        assertEquals(50_000L, untouched.income.dayMinor)
        assertEquals(20_100L, untouched.expense.dayMinor)
    }

    @Test
    fun `шапка дня ленты сходится со сводкой`() {
        val adjustments = listOf(adjustment(amountMinor = 40_000L))
        val window = ProfitAndLoss.windowSums(
            transactions = operations,
            adjustments = adjustments,
            accountIds = setOf("acc"),
            balanceCurrencyCode = "RUB",
            todayStartMillis = todayStart,
            zone = zone,
        )
        // Строки ленты: операции скоупа плюс псевдо-строки учтённых списаний —
        // ровно то, что HomeViewModel кладёт в transactionFeed.
        val feedItems = operations.map {
            TransactionItem(
                transaction = it,
                categoryName = "",
                categoryIconKey = "",
                categoryColorKey = "",
                accountName = "",
                currencySymbol = "₽",
            )
        } + adjustments.map { FeedEntries.writeOffItem(it, "₽") }
        val daySums = FeedEntries.daySums(feedItems)

        assertEquals(window.income.dayMinor, daySums.incomeMinor)
        assertEquals(window.expense.dayMinor, daySums.expenseMinor)
    }

    @Test
    fun `окна считаются от даты операции в поясе телефона`() {
        // Мобильное окно: сегодня — 0,6 дня; недельное окно начинается
        // 6 дней назад, месячное — 29 дней назад. Доход 10-дневной давности
        // попадает только в месяц, 40-дневной — ни в одно окно.
        val window = ProfitAndLoss.windowSums(
            transactions = listOf(
                tx(TxType.INCOME, 300L),
                tx(TxType.INCOME, 500L, date = todayStart - 10 * 86_400_000L),
                tx(TxType.INCOME, 7_000L, date = todayStart - 40 * 86_400_000L),
            ),
            adjustments = emptyList(),
            accountIds = setOf("acc"),
            balanceCurrencyCode = "RUB",
            todayStartMillis = todayStart,
            zone = zone,
        )

        assertEquals(300L, window.income.dayMinor)
        assertEquals(300L, window.income.weekMinor)
        assertEquals(800L, window.income.monthMinor)
    }

    @Test
    fun `чужие счета в сводку баланса не попадают`() {
        val window = ProfitAndLoss.windowSums(
            transactions = listOf(
                tx(TxType.INCOME, 300L),
                tx(TxType.INCOME, 1_111L, accountId = "other"),
            ),
            adjustments = emptyList(),
            accountIds = setOf("acc"),
            balanceCurrencyCode = "RUB",
            todayStartMillis = todayStart,
            zone = zone,
        )

        assertEquals(300L, window.income.dayMinor)
    }

    @Test
    fun `начальный остаток с счётчиком в отчётах не считается`() {
        // Форма начальному остатку счётчик не включает, но синк мог принести
        // такую запись: в P&L попадают только WRITE_OFF, как и в разрезках.
        val opening = adjustment(amountMinor = 40_000L)
            .copy(kind = DebtAdjustmentKind.OPENING_BALANCE.wire)

        val window = ProfitAndLoss.windowSums(
            transactions = emptyList(),
            adjustments = listOf(opening),
            accountIds = setOf("acc"),
            balanceCurrencyCode = "RUB",
            todayStartMillis = todayStart,
            zone = zone,
        )

        assertEquals(0L, window.income.dayMinor)
        assertEquals(0L, window.expense.dayMinor)
    }

    @Test
    fun `удалённая операция в окна не попадает`() {
        val window = ProfitAndLoss.windowSums(
            transactions = listOf(
                tx(TxType.INCOME, 300L),
                tx(TxType.INCOME, 900L).copy(deleted = true),
            ),
            adjustments = emptyList(),
            accountIds = setOf("acc"),
            balanceCurrencyCode = "RUB",
            todayStartMillis = todayStart,
            zone = zone,
        )

        assertEquals(300L, window.income.dayMinor)
    }
}
