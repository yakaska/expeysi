package com.expeysi.app.data.remote

import com.expeysi.app.data.db.AccountEntity
import com.expeysi.app.data.db.BalanceEntity
import com.expeysi.app.data.db.CategoryEntity
import com.expeysi.app.data.db.TransactionEntity
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Wire-формат синхронизации: наборы полей повторяют текущие upload* из
 * SyncEngine и docs/data-model.md; currencyCode счёта в синк не попадает.
 */
class SyncMapperTest {

    // Golden-тесты: точный набор ключей и типов значений для каждого типа.

    @Test
    fun `wire-поля баланса совпадают с текущим форматом`() {
        val change = SyncMapper.toChange(balance())

        assertEquals(
            setOf("name", "currencyCode", "iconKey", "colorKey", "deleted", "updatedAt"),
            change.fields.keys,
        )
        assertEquals("Основной", change.fields["name"])
        assertEquals("RUB", change.fields["currencyCode"])
        assertEquals("payments", change.fields["iconKey"])
        assertEquals("blue", change.fields["colorKey"])
        assertTrue(change.fields["deleted"] is Boolean)
        assertTrue(change.fields["updatedAt"] is Long)
        assertEquals(5L, change.updatedAt)
    }

    @Test
    fun `wire-поля счёта совпадают с текущим форматом`() {
        val change = SyncMapper.toChange(account())

        assertEquals(
            setOf("name", "iconKey", "colorKey", "openingBalanceMinor", "balanceId", "deleted", "updatedAt"),
            change.fields.keys,
        )
        // Валюта счёта в синк не попадает: валюта счёта — валюта его баланса.
        assertFalse(change.fields.containsKey("currencyCode"))
        assertTrue(change.fields["openingBalanceMinor"] is Long)
        assertEquals(1500L, change.fields["openingBalanceMinor"])
    }

    @Test
    fun `wire-поля категории совпадают с текущим форматом`() {
        val change = SyncMapper.toChange(category())

        assertEquals(
            setOf("name", "iconKey", "colorKey", "type", "deleted", "updatedAt"),
            change.fields.keys,
        )
        assertEquals("expense", change.fields["type"])
    }

    @Test
    fun `wire-поля транзакции совпадают с текущим форматом`() {
        val change = SyncMapper.toChange(transaction(), currentUserId = "current-uid")

        assertEquals(
            setOf(
                "type", "amountMinor", "accountId", "categoryId", "date",
                "note", "deleted", "updatedAt", "updatedBy",
            ),
            change.fields.keys,
        )
        // id не сериализуется: он адресуется ключом.
        assertFalse(change.fields.containsKey("id"))
        assertTrue(change.fields["amountMinor"] is Long)
        assertTrue(change.fields["date"] is Long)
    }

    // Round-trip Entity -> SyncChange -> Entity.

    @Test
    fun `round-trip баланса сохраняет значения`() {
        val rebuilt = SyncMapper.balance(SyncMapper.toChange(balance()))
        assertEquals(balance(), rebuilt)
    }

    @Test
    fun `round-trip счёта сохраняет значения`() {
        // currencyCode не часть wire-формата: как и сегодня в mergeAccount,
        // при получении удалённой версии он восстанавливается дефолтом RUB
        // (валюта счёта всегда берётся из его баланса, а не из синка).
        val rebuilt = SyncMapper.account(SyncMapper.toChange(account()))
        assertEquals(account().copy(currencyCode = "RUB"), rebuilt)
    }

    @Test
    fun `round-trip категории сохраняет значения`() {
        val rebuilt = SyncMapper.category(SyncMapper.toChange(category()))
        assertEquals(category(), rebuilt)
    }

    @Test
    fun `round-trip транзакции сохраняет значения`() {
        val rebuilt = SyncMapper.transaction(SyncMapper.toChange(transaction(), currentUserId = null))
        assertEquals(transaction(), rebuilt)
    }

    // Значения по умолчанию при отсутствии необязательных полей (инвариант 2).

    @Test
    fun `баланс без необязательных полей получает дефолты`() {
        val entity = SyncMapper.balance(change(SyncEntityType.BALANCE, "b1", mapOf("name" to "X")))!!
        assertEquals("RUB", entity.currencyCode)
        assertEquals("", entity.iconKey)
        assertEquals("default", entity.colorKey)
        assertFalse(entity.deleted)
        assertEquals(0L, entity.updatedAt)
    }

    @Test
    fun `счёт без необязательных полей получает дефолты`() {
        val entity = SyncMapper.account(
            change(SyncEntityType.ACCOUNT, "a1", mapOf("name" to "Y", "balanceId" to "b1")),
        )!!
        assertEquals("", entity.iconKey)
        assertEquals("default", entity.colorKey)
        assertEquals(0L, entity.openingBalanceMinor)
        assertFalse(entity.deleted)
        assertEquals(0L, entity.updatedAt)
    }

    @Test
    fun `категория без необязательных полей получает дефолты`() {
        val entity = SyncMapper.category(
            change(SyncEntityType.CATEGORY, "c1", mapOf("name" to "Z", "type" to "income")),
        )!!
        assertEquals("", entity.iconKey)
        assertEquals("default", entity.colorKey)
        assertFalse(entity.deleted)
        assertEquals(0L, entity.updatedAt)
    }

    @Test
    fun `транзакция без необязательных полей получает дефолты`() {
        val entity = SyncMapper.transaction(minimalTxChange())!!
        assertEquals("", entity.note)
        assertFalse(entity.deleted)
        assertEquals(0L, entity.updatedAt)
        assertEquals("", entity.updatedBy)
    }

    // Обязательные поля: отсутствие значения даёт null (запись пропускается).

    @Test
    fun `баланс без имени не валиден`() {
        assertNull(SyncMapper.balance(change(SyncEntityType.BALANCE, "b1", balanceFields().minus("name"))))
    }

    @Test
    fun `счёт без имени не валиден`() {
        assertNull(SyncMapper.account(change(SyncEntityType.ACCOUNT, "a1", accountFields().minus("name"))))
    }

    @Test
    fun `счёт без balanceId не валиден`() {
        assertNull(SyncMapper.account(change(SyncEntityType.ACCOUNT, "a1", accountFields().minus("balanceId"))))
    }

    @Test
    fun `категория без имени не валидна`() {
        assertNull(SyncMapper.category(change(SyncEntityType.CATEGORY, "c1", categoryFields().minus("name"))))
    }

    @Test
    fun `категория без типа не валидна`() {
        assertNull(SyncMapper.category(change(SyncEntityType.CATEGORY, "c1", categoryFields().minus("type"))))
    }

    @Test
    fun `транзакция без типа не валидна`() {
        assertNull(SyncMapper.transaction(change(SyncEntityType.TRANSACTION, "t1", txFields().minus("type"))))
    }

    @Test
    fun `транзакция без суммы не валидна`() {
        assertNull(SyncMapper.transaction(change(SyncEntityType.TRANSACTION, "t1", txFields().minus("amountMinor"))))
    }

    @Test
    fun `транзакция без счета не валидна`() {
        assertNull(SyncMapper.transaction(change(SyncEntityType.TRANSACTION, "t1", txFields().minus("accountId"))))
    }

    @Test
    fun `транзакция без категории не валидна`() {
        assertNull(SyncMapper.transaction(change(SyncEntityType.TRANSACTION, "t1", txFields().minus("categoryId"))))
    }

    @Test
    fun `транзакция без даты не валидна`() {
        assertNull(SyncMapper.transaction(change(SyncEntityType.TRANSACTION, "t1", txFields().minus("date"))))
    }

    // Числа wire-формата приходят как Int, Long и целое Double.

    @Test
    fun `числа Int, Long и целое Double читаются одинаково`() {
        val fromInt = SyncMapper.transaction(change(SyncEntityType.TRANSACTION, "t1", txFields(minor = 250, stamp = 7)))!!
        val fromLong = SyncMapper.transaction(change(SyncEntityType.TRANSACTION, "t1", txFields(minor = 250L, stamp = 7L)))!!
        val fromDouble = SyncMapper.transaction(
            change(SyncEntityType.TRANSACTION, "t1", txFields(minor = 250.0, stamp = 7.0)),
        )!!
        assertEquals(250L, fromInt.amountMinor)
        assertEquals(7L, fromInt.updatedAt)
        assertEquals(42L, fromInt.date)
        assertEquals(fromInt, fromLong)
        assertEquals(fromInt, fromDouble)
    }

    @Test
    fun `начальный остаток счёта читается из Int и Double`() {
        val fromInt = SyncMapper.account(change(SyncEntityType.ACCOUNT, "a1", accountFields(opening = 1500)))!!
        val fromDouble = SyncMapper.account(change(SyncEntityType.ACCOUNT, "a1", accountFields(opening = 15.0 * 100)))!!
        assertEquals(1500L, fromInt.openingBalanceMinor)
        assertEquals(fromInt, fromDouble)
    }

    // updatedBy при отправке (инвариант 3).

    @Test
    fun `updatedBy отправки берётся из текущего пользователя`() {
        val fields = SyncMapper.toChange(transaction(), currentUserId = "current-uid").fields
        assertEquals("current-uid", fields["updatedBy"])
    }

    @Test
    fun `updatedBy отправки без текущего пользователя берётся из строки`() {
        val fields = SyncMapper.toChange(transaction(), currentUserId = null).fields
        assertEquals("old-uid", fields["updatedBy"])
    }

    // Локальный флаг dirty — не часть wire-формата.

    @Test
    fun `dirty не попадает в fields и не восстанавливается при разборе`() {
        val balanceChange = SyncMapper.toChange(balance().copy(dirty = true))
        assertFalse(balanceChange.fields.containsKey("dirty"))
        assertFalse(SyncMapper.balance(balanceChange)!!.dirty)

        val transactionChange = SyncMapper.toChange(transaction().copy(dirty = true), currentUserId = null)
        assertFalse(transactionChange.fields.containsKey("dirty"))
        assertFalse(SyncMapper.transaction(transactionChange)!!.dirty)
    }

    // Хелперы.

    private fun balance() = BalanceEntity(
        id = "b1",
        name = "Основной",
        currencyCode = "RUB",
        iconKey = "payments",
        colorKey = "blue",
        deleted = false,
        updatedAt = 5,
    )

    private fun account() = AccountEntity(
        id = "a1",
        name = "Карта",
        iconKey = "credit_card",
        colorKey = "blue",
        openingBalanceMinor = 1500,
        currencyCode = "USD",
        balanceId = "b1",
        deleted = false,
        updatedAt = 5,
    )

    private fun category() = CategoryEntity(
        id = "c1",
        name = "Еда",
        iconKey = "restaurant",
        colorKey = "orange",
        type = "expense",
        deleted = false,
        updatedAt = 5,
    )

    private fun transaction() = TransactionEntity(
        id = "t1",
        type = "expense",
        amountMinor = 250,
        accountId = "a1",
        categoryId = "c1",
        date = 42,
        note = "кофе",
        deleted = false,
        updatedAt = 5,
        updatedBy = "old-uid",
    )

    private fun change(type: SyncEntityType, id: String, fields: Map<String, Any?>) = SyncChange(
        key = SyncKey(type, id),
        updatedAt = (fields["updatedAt"] as? Number)?.toLong() ?: 0,
        fields = fields,
    )

    /** Все необязательные поля транзакции отсутствуют — только обязательные. */
    private fun minimalTxChange(): SyncChange = change(
        SyncEntityType.TRANSACTION,
        "t1",
        mapOf(
            "type" to "expense",
            "amountMinor" to 250,
            "accountId" to "a1",
            "categoryId" to "c1",
            "date" to 42,
        ),
    )

    private fun balanceFields(stamp: Any? = 5) = mapOf<String, Any?>(
        "name" to "Основной",
        "currencyCode" to "RUB",
        "iconKey" to "payments",
        "colorKey" to "blue",
        "deleted" to false,
        "updatedAt" to stamp,
    )

    private fun accountFields(opening: Any? = 1500, stamp: Any? = 5) = mapOf<String, Any?>(
        "name" to "Карта",
        "iconKey" to "credit_card",
        "colorKey" to "blue",
        "openingBalanceMinor" to opening,
        "balanceId" to "b1",
        "deleted" to false,
        "updatedAt" to stamp,
    )

    private fun categoryFields(stamp: Any? = 5) = mapOf<String, Any?>(
        "name" to "Еда",
        "iconKey" to "restaurant",
        "colorKey" to "orange",
        "type" to "expense",
        "deleted" to false,
        "updatedAt" to stamp,
    )

    private fun txFields(minor: Any? = 250, stamp: Any? = 5) = mapOf<String, Any?>(
        "type" to "expense",
        "amountMinor" to minor,
        "accountId" to "a1",
        "categoryId" to "c1",
        "date" to 42,
        "note" to "кофе",
        "deleted" to false,
        "updatedAt" to stamp,
        "updatedBy" to "old-uid",
    )
}
