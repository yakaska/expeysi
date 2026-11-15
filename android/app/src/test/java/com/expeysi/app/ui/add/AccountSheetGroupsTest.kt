package com.expeysi.app.ui.add

import com.expeysi.app.data.db.AccountEntity
import com.expeysi.app.data.db.BalanceEntity
import org.junit.Assert.assertEquals
import org.junit.Test

class AccountSheetGroupsTest {

    private fun account(id: String, name: String, balanceId: String) = AccountEntity(
        id = id,
        name = name,
        iconKey = "wallet",
        openingBalanceMinor = 0,
        balanceId = balanceId,
        updatedAt = 0,
    )

    private fun balance(id: String, name: String) = BalanceEntity(
        id = id,
        name = name,
        currencyCode = "RUB",
        updatedAt = 0,
    )

    @Test
    fun `счета группируются по балансам в порядке списка балансов`() {
        val balances = mapOf(
            "b2" to balance("b2", "Вьетнам"),
            "b1" to balance("b1", "Основной"),
        )
        val accounts = listOf(
            account("a1", "Наличные", "b1"),
            account("a2", "Карта", "b2"),
            account("a3", "Запасы", "b1"),
        )
        val groups = accountSheetGroups(accounts, balances)
        assertEquals(listOf("b2", "b1"), groups.map { it.balanceId })
        assertEquals(listOf("a2"), groups.first().accounts.map { it.id })
        assertEquals(listOf("a1", "a3"), groups.last().accounts.map { it.id })
    }

    @Test
    fun `баланс без счетов группы не создаёт`() {
        val balances = mapOf(
            "b1" to balance("b1", "Основной"),
            "b2" to balance("b2", "Пустой"),
        )
        val groups = accountSheetGroups(listOf(account("a1", "Наличные", "b1")), balances)
        assertEquals(listOf("b1"), groups.map { it.balanceId })
    }

    @Test
    fun `счёт без известного баланса уходит в безымянную группу в конце`() {
        val balances = mapOf("b1" to balance("b1", "Основной"))
        val accounts = listOf(
            account("a1", "Осиротевший", "bX"),
            account("a2", "Наличные", "b1"),
        )
        val groups = accountSheetGroups(accounts, balances)
        assertEquals(listOf("b1", "bX"), groups.map { it.balanceId })
        assertEquals("", groups.last().balanceName)
    }
}
