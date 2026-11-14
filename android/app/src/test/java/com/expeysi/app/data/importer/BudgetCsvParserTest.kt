package com.expeysi.app.data.importer

import com.expeysi.app.domain.TxType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test
import java.io.ByteArrayInputStream

class BudgetCsvParserTest {

    private val parser = BudgetCsvParser()

    @Test
    fun `экспорт Бюджета читается с суммами и типами операций`() {
        val transactions = parse(
            HEADER +
                "2026-01-02;03:04;Карта;Жилье;-8000000;VND;Квартира\n" +
                "2026-01-02;03:05;Карта;Зарплата;10000000;VND;\n" +
                "2026-01-02;03:06;Наличные;Еда;-33333.30;VND;\n",
        )

        assertEquals(3, transactions.size)
        assertEquals(80_000_000_000L, transactions.first().amountMinor)
        assertEquals(TxType.EXPENSE, transactions.first().type)
        assertEquals("Жилье", transactions.first().categoryName)
        assertEquals("Квартира", transactions.first().note)
        assertEquals("VND", transactions.first().currencyCode)
        assertEquals(100_000_000_000L, transactions.first { it.type == TxType.INCOME }.amountMinor)
        assertEquals(333_333_000L, transactions.last().amountMinor)
    }

    @Test
    fun `кавычки сохраняют разделители и кавычки в комментарии`() {
        val transactions = parse(
            "Дата;Время;Название счета;Категория;Сумма;Валюта;Комментарий\n" +
                "2026-01-02;03:04;Наличные;Еда;-1.2e+03;VND;\"Магазин; \"\"ужин\"\"\"\n",
        )

        assertEquals(12_000_000L, transactions.single().amountMinor)
        assertEquals("Магазин; \"ужин\"", transactions.single().note)
    }

    @Test
    fun `несколько счетов и валют сохраняются в операциях`() {
        val transactions = parse(
            HEADER +
                "2026-01-02;03:04;Наличные;Еда;-100;VND;\n" +
                "2026-01-02;03:05;Карта;Зарплата;200;RUB;\n",
        )

        assertEquals(listOf("Наличные", "Карта"), transactions.map { it.accountName })
        assertEquals(listOf("VND", "RUB"), transactions.map { it.currencyCode })
    }

    @Test
    fun `колонки оформления читаются если они в заголовке`() {
        val transactions = parse(
            "${HEADER.trimEnd()};Иконка категории;Цвет категории;Иконка счета;Цвет счета;" +
                "Название баланса;Иконка баланса;Цвет баланса\n" +
                "2026-01-02;03:04;Карта;Еда;-800.50;RUB;Квартира;restaurant;orange;credit_card;blue;Основной;payments;blue\n",
        )

        val tx = transactions.single()
        assertEquals("restaurant", tx.categoryIconKey)
        assertEquals("orange", tx.categoryColorKey)
        assertEquals("credit_card", tx.accountIconKey)
        assertEquals("blue", tx.accountColorKey)
        assertEquals("Основной", tx.balanceName)
        assertEquals("payments", tx.balanceIconKey)
        assertEquals("blue", tx.balanceColorKey)
    }

    @Test
    fun `связка перевода читается из колонки файла`() {
        val transactions = parse(
            "${HEADER.trimEnd()};Иконка категории;Цвет категории;Иконка счета;Цвет счета;" +
                "Название баланса;Иконка баланса;Цвет баланса;Связка перевода\n" +
                "2026-01-02;03:04;Карта;Перевод;-300.50;RUB;;swap_horiz;default;credit_card;blue;Основной;payments;blue;L1\n" +
                "2026-01-02;03:04;Кошелёк;Перевод;300.50;RUB;;swap_horiz;default;payments;blue;Основной;payments;blue;\n",
        )

        assertEquals("L1", transactions.first().transferLinkId)
        // Пустое поле связки — операция вне перевода.
        assertEquals(null, transactions.last().transferLinkId)
    }

    @Test
    fun `без колонок оформления атрибуты не заполнены`() {
        val transactions = parse(HEADER + "2026-01-02;03:04;Наличные;Еда;-100;VND;\n")
        val tx = transactions.single()
        assertEquals(null, tx.categoryIconKey)
        assertEquals(null, tx.accountColorKey)
        assertEquals(null, tx.balanceName)
        assertEquals(null, tx.balanceColorKey)
    }

    @Test
    fun `строки короче колонок оформления дают пустые атрибуты`() {
        val transactions = parse(
            "${HEADER.trimEnd()};Иконка категории;Цвет категории;Иконка счета;Цвет счета;" +
                "Название баланса;Иконка баланса;Цвет баланса\n" +
                "2026-01-02;03:04;Карта;Еда;-800.50;RUB;\n",
        )

        val tx = transactions.single()
        assertEquals(null, tx.categoryIconKey)
        assertEquals(null, tx.accountColorKey)
        assertEquals(null, tx.balanceName)
    }

    @Test
    fun `жилье с е и ё сопоставляется одинаково`() {
        assertEquals(normalizeBudgetCategoryName("Жилье"), normalizeBudgetCategoryName(" Жильё "))
    }

    @Test
    fun `файл с запятой как разделителем тоже читается`() {
        // Разделитель опознаётся по заголовку; комментарий с запятой — в кавычках.
        val transactions = parse(
            "Дата,Время,Название счета,Категория,Сумма,Валюта,Комментарий\n" +
                "2026-01-02,03:04,Наличные,Еда,-100,VND,\n" +
                "2026-01-02,03:05,\"Карта, запасная\",Зарплата,200,RUB,\"Магазин, «ужин»\"\n",
        )

        assertEquals(2, transactions.size)
        assertEquals("Наличные", transactions.first().accountName)
        assertEquals(1_000_000L, transactions.first().amountMinor)
        assertEquals(TxType.EXPENSE, transactions.first().type)
        assertEquals("Карта, запасная", transactions.last().accountName)
        assertEquals(2_000_000L, transactions.last().amountMinor)
        assertEquals(TxType.INCOME, transactions.last().type)
        assertEquals("Магазин, «ужин»", transactions.last().note)
    }

    @Test
    fun `некорректная сумма сообщает номер строки`() {
        val error = assertThrows(BudgetImportException::class.java) {
            parse(HEADER + "2026-01-02;03:04;Наличные;Еда;abc;VND;\n")
        }
        assertEquals(BudgetImportError.BAD_AMOUNT, error.reason)
        assertEquals(2, error.line)
    }

    @Test
    fun `незакрытая кавычка отклоняет файл`() {
        val error = assertThrows(BudgetImportException::class.java) {
            parse(HEADER + "2026-01-02;03:04;Наличные;Еда;-100;VND;\"комментарий\n")
        }
        assertEquals(BudgetImportError.UNCLOSED_QUOTE, error.reason)
        assertEquals(2, error.line)
    }

    private fun parse(text: String) = parser.parse(ByteArrayInputStream(text.toByteArray()))

    private companion object {
        const val HEADER = "Дата;Время;Название счета;Категория;Сумма;Валюта;Комментарий\n"
    }
}
