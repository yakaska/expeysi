package com.expeysi.app.data.importer

import com.expeysi.app.data.db.CategoryEntity
import com.expeysi.app.domain.TxType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
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
