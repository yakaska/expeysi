package com.expeysi.app.ui.home

import com.expeysi.app.data.db.AccountEntity
import com.expeysi.app.data.db.BalanceEntity
import com.expeysi.app.data.db.TransactionEntity
import com.expeysi.app.domain.TxType
import org.junit.Assert.assertEquals
import org.junit.Test

class BalanceTotalsTest {

    private fun balance(id: String) = BalanceEntity(
        id = id,
        name = "Баланс $id",
        currencyCode = "RUB",
        updatedAt = 0,
    )

    private fun account(
        id: String,
        balanceId: String,
        opening: Long,
        deleted: Boolean = false,
    ) = AccountEntity(
        id = id,
        name = "Счёт $id",
        iconKey = "credit_card",
        openingBalanceMinor = opening,
        balanceId = balanceId,
        deleted = deleted,
        updatedAt = 0,
    )

    private fun transaction(
        id: String,
        accountId: String,
        type: TxType,
        amountMinor: Long,
        deleted: Boolean = false,
    ) = TransactionEntity(
        id = id,
        type = type.wire,
        amountMinor = amountMinor,
        accountId = accountId,
        categoryId = "c",
        date = 0,
        deleted = deleted,
        updatedAt = 0,
    )

    @Test
    fun `итоги считаются по счетам каждого баланса`() {
        val balances = listOf(balance("b1"), balance("b2"))
        val accounts = listOf(
            account("a1", "b1", opening = 1_000),
            account("a2", "b2", opening = 500),
        )
        val transactions = listOf(
            transaction("t1", "a1", TxType.EXPENSE, 250),
            transaction("t2", "a2", TxType.INCOME, 300),
        )
        assertEquals(
            mapOf("b1" to 750L, "b2" to 800L),
            balanceTotals(balances, accounts, transactions),
        )
    }

    @Test
    fun `отрицательные суммы сохраняются`() {
        val balances = listOf(balance("b1"))
        val accounts = listOf(account("a1", "b1", opening = 100))
        val transactions = listOf(transaction("t1", "a1", TxType.EXPENSE, 500))
        assertEquals(mapOf("b1" to -400L), balanceTotals(balances, accounts, transactions))
    }

    @Test
    fun `баланс без счетов даёт ноль`() {
        val balances = listOf(balance("b1"), balance("b2"))
        val accounts = listOf(account("a1", "b1", opening = 1_000))
        assertEquals(
            mapOf("b1" to 1_000L, "b2" to 0L),
            balanceTotals(balances, accounts, emptyList()),
        )
    }

    @Test
    fun `удалённые счета и операции не учитываются`() {
        val balances = listOf(balance("b1"))
        val accounts = listOf(
            account("a1", "b1", opening = 1_000),
            account("a2", "b1", opening = 5_000, deleted = true),
        )
        val transactions = listOf(
            transaction("t1", "a1", TxType.EXPENSE, 400),
            transaction("t2", "a1", TxType.EXPENSE, 9_000, deleted = true),
        )
        assertEquals(mapOf("b1" to 600L), balanceTotals(balances, accounts, transactions))
    }

    @Test
    fun `счета без баланса не попадают ни в один итог`() {
        val balances = listOf(balance("b1"))
        val accounts = listOf(
            account("a1", "b1", opening = 100),
            account("a2", "", opening = 9_000),
        )
        assertEquals(
            mapOf("b1" to 100L),
            balanceTotals(balances, accounts, emptyList()),
        )
    }
}
