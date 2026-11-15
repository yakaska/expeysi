package com.expeysi.app.ui.add

import com.expeysi.app.data.db.AccountEntity
import com.expeysi.app.data.db.BalanceEntity
import com.expeysi.app.data.db.CategoryEntity
import com.expeysi.app.domain.TxType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class TransactionSelectionTest {

    @Test
    fun `активный счёт можно восстановить`() {
        assertEquals("account", account().selectableId())
    }

    @Test
    fun `удалённый счёт не восстанавливается`() {
        assertNull(account(deleted = true).selectableId())
    }

    @Test
    fun `активная категория нужного типа восстанавливается`() {
        assertEquals("category", category().selectableId(TxType.EXPENSE))
    }

    @Test
    fun `удалённая категория не восстанавливается`() {
        assertNull(category(deleted = true).selectableId(TxType.EXPENSE))
    }

    @Test
    fun `категория другого типа не восстанавливается`() {
        assertNull(category().selectableId(TxType.INCOME))
    }

    @Test
    fun `валюта счёта берётся из его баланса`() {
        val balances = mapOf("balance" to balance("VND"))
        assertEquals("VND", currencyCodeOf(account(balanceId = "balance"), balances))
    }

    @Test
    fun `валюта счёта без баланса — резерв из счёта`() {
        assertEquals("RUB", currencyCodeOf(account(balanceId = "missing", currencyCode = "RUB"), emptyMap()))
    }

    @Test
    fun `валюта без счёта не резолвится`() {
        assertNull(currencyCodeOf(null, mapOf("balance" to balance("VND"))))
    }

    private fun account(deleted: Boolean = false, balanceId: String = "balance", currencyCode: String = "RUB") = AccountEntity(
        id = "account",
        name = "Счёт",
        iconKey = "account",
        openingBalanceMinor = 0,
        currencyCode = currencyCode,
        balanceId = balanceId,
        deleted = deleted,
        updatedAt = 0,
    )

    private fun balance(currencyCode: String) = BalanceEntity(
        id = "balance",
        name = "Баланс",
        currencyCode = currencyCode,
        updatedAt = 0,
    )

    private fun category(deleted: Boolean = false) = CategoryEntity(
        id = "category",
        name = "Категория",
        iconKey = "category",
        type = TxType.EXPENSE.wire,
        deleted = deleted,
        updatedAt = 0,
    )
}
