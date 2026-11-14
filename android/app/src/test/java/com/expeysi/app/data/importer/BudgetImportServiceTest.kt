package com.expeysi.app.data.importer

import com.expeysi.app.data.db.AccountEntity
import com.expeysi.app.data.db.CategoryEntity
import com.expeysi.app.domain.TxType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Перекраска существующих категорий при импорте: файл экспорта Expeysi обязан
 * возвращать оформление, файлы внешнего «Бюджета» его не трогают.
 * Имена копий балансов: префикс «импорт » и нумерация повторов.
 */
class BudgetImportServiceTest {

    private val existing = CategoryEntity(
        id = "c1",
        name = "Еда",
        iconKey = "category",
        colorKey = "default",
        type = TxType.EXPENSE.wire,
        updatedAt = 1,
    )

    @Test
    fun `явные иконка и цвет перекрашивают существующую категорию`() {
        val updated = restyledCategory(
            existing,
            tx(categoryIconKey = "restaurant", categoryColorKey = "orange"),
            nowMillis = 77,
        )!!

        assertEquals("c1", updated.id)
        assertEquals("restaurant", updated.iconKey)
        assertEquals("orange", updated.colorKey)
        assertEquals(77, updated.updatedAt)
    }

    @Test
    fun `пустое оформление чужого файла категорию не меняет`() {
        assertNull(restyledCategory(existing, tx(categoryIconKey = null, categoryColorKey = null), nowMillis = 77))
        assertUnchanged(existing)
    }

    @Test
    fun `совпадающее оформление возвращает null`() {
        assertNull(
            restyledCategory(
                existing,
                tx(categoryIconKey = "category", categoryColorKey = "default"),
                nowMillis = 77,
            ),
        )
        assertUnchanged(existing)
    }

    @Test
    fun `частичное оформление обновляет только заданный атрибут`() {
        val onlyIcon = restyledCategory(existing, tx(categoryIconKey = "restaurant", categoryColorKey = null), nowMillis = 77)!!
        assertEquals("restaurant", onlyIcon.iconKey)
        assertEquals("default", onlyIcon.colorKey)
        assertEquals(77, onlyIcon.updatedAt)

        val onlyColor = restyledCategory(existing, tx(categoryIconKey = null, categoryColorKey = "orange"), nowMillis = 77)!!
        assertEquals("category", onlyColor.iconKey)
        assertEquals("orange", onlyColor.colorKey)
    }

    private fun assertUnchanged(category: CategoryEntity) {
        assertEquals(1, category.updatedAt)
    }

    @Test
    fun `номер копии идёт за максимальным занятым`() {
        assertEquals(
            "импорт Dong (3)",
            importedBalanceName("Dong", setOf("Dong", "импорт Dong", "импорт Dong (2)")),
        )
    }

    @Test
    fun `свободное имя берется без номера`() {
        // Свой баланс «Dong» не мешает: счёт только по копиям с префиксом;
        // мусор в скобках и имена с похожими префиксами нумерацию не сбивают.
        assertEquals("импорт Dong", importedBalanceName("Dong", setOf("Dong", "импорт Dong (x)", "импорт Dong2")))
        assertEquals("импорт Dong", importedBalanceName("Dong", setOf("Dong", "импорт Dong (2)")))
    }

    @Test
    fun `имя со скобками в основе номеруется корректно`() {
        assertEquals(
            "импорт Бюджет (RUB) (2)",
            importedBalanceName("Бюджет (RUB)", setOf("импорт Бюджет (RUB)")),
        )
    }

    @Test
    fun `парные строки файла восстанавливаются ногами перевода`() {
        val legs = transferLegsOf(
            listOf(
                item(accountName = "Карта", type = TxType.EXPENSE, amountMinor = 30_000, transferLinkId = "L1", note = "отпуск"),
                item(accountName = "Кошелёк", type = TxType.INCOME, amountMinor = 30_000, transferLinkId = "L1", note = "отпуск"),
            ),
            accounts,
            nowMillis = 77,
        )

        assertEquals(2, legs.size)
        val out = legs.first()
        val inLeg = legs.last()
        assertEquals(TxType.TRANSFER_OUT.wire, out.type)
        assertEquals("acc1", out.accountId)
        assertEquals(30_000L, out.amountMinor)
        assertEquals("отпуск", out.note)
        // У ног перевода категории нет: строка «Перевод» в файл служебная.
        assertEquals("", out.categoryId)
        assertEquals(TxType.TRANSFER_IN.wire, inLeg.type)
        assertEquals("acc2", inLeg.accountId)
        // Контрагенты ссылаются друг на друга, связка общая и свежая.
        assertEquals("acc2", out.counterpartyAccountId)
        assertEquals("acc1", inLeg.counterpartyAccountId)
        assertEquals(77L, out.updatedAt)
        assertEquals(out.linkedId, inLeg.linkedId)
        assertTrue(out.linkedId.isNotEmpty())
    }

    @Test
    fun `вырожденная связка без зачисления не реконструируется`() {
        val out = listOf(item(accountName = "Карта", type = TxType.EXPENSE, amountMinor = 30_000, transferLinkId = "L1"))
        assertTrue(transferLegsOf(out, accounts, nowMillis = 77).isEmpty())
    }

    @Test
    fun `вырожденная связка без списания не реконструируется`() {
        val inRow = listOf(item(accountName = "Кошелёк", type = TxType.INCOME, amountMinor = 30_000, transferLinkId = "L1"))
        assertTrue(transferLegsOf(inRow, accounts, nowMillis = 77).isEmpty())
    }

    @Test
    fun `повторная загрузка того же файла даёт независимую связку`() {
        val rows = listOf(
            item(accountName = "Карта", type = TxType.EXPENSE, amountMinor = 30_000, transferLinkId = "L1"),
            item(accountName = "Кошелёк", type = TxType.INCOME, amountMinor = 30_000, transferLinkId = "L1"),
        )
        val firstLink = transferLegsOf(rows, accounts, nowMillis = 77).first().linkedId
        val secondLink = transferLegsOf(rows, accounts, nowMillis = 77).first().linkedId

        assertNotEquals(firstLink, secondLink)
    }

    private val accounts = mapOf(
        AccountKey("Карта", "RUB", null) to account("acc1"),
        AccountKey("Кошелёк", "RUB", null) to account("acc2"),
    )

    private fun item(
        accountName: String,
        type: TxType,
        amountMinor: Long,
        transferLinkId: String? = null,
        note: String = "",
    ) = BudgetTransaction(
        accountName = accountName,
        categoryName = "Перевод",
        type = type,
        amountMinor = amountMinor,
        currencyCode = "RUB",
        date = 5,
        note = note,
        transferLinkId = transferLinkId,
    )

    private fun account(id: String) = AccountEntity(
        id = id,
        name = id,
        iconKey = "payments",
        colorKey = "blue",
        openingBalanceMinor = 0,
        currencyCode = "RUB",
        balanceId = "b",
        updatedAt = 0,
    )

    private fun tx(categoryIconKey: String?, categoryColorKey: String?) = BudgetTransaction(
        accountName = "Карта",
        categoryName = "Еда",
        type = TxType.EXPENSE,
        amountMinor = 100L,
        currencyCode = "RUB",
        date = 0,
        note = "",
        categoryIconKey = categoryIconKey,
        categoryColorKey = categoryColorKey,
    )
}
