package com.expeysi.app.ui.add

import com.expeysi.app.data.db.AccountEntity
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

    private fun account(deleted: Boolean = false) = AccountEntity(
        id = "account",
        name = "Счёт",
        iconKey = "account",
        openingBalanceMinor = 0,
        deleted = deleted,
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
