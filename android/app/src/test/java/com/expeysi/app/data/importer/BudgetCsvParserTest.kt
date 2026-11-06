package com.expeysi.app.data.importer

import com.expeysi.app.domain.TxType
import java.io.ByteArrayInputStream
import java.nio.file.Files
import java.nio.file.Path
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test

class BudgetCsvParserTest {

    private val parser = BudgetCsvParser()

    @Test
    fun `реальный экспорт Бюджета читается полностью`() {
        val path = Path.of("..", "..", "docs", "Export-01.01.2026-31.12.2026.csv")
        val transactions = Files.newInputStream(path).use(parser::parse)

        assertEquals(80, transactions.size)
        assertEquals(800_000_000L, transactions.first().amountMinor)
        assertEquals(TxType.EXPENSE, transactions.first().type)
        assertEquals("Жилье", transactions.first().categoryName)
        assertEquals("Квартира", transactions.first().note)
        assertEquals("VND", transactions.first().currencyCode)
        assertEquals(1_000_000_000L, transactions.first { it.type == TxType.INCOME }.amountMinor)
        assertEquals(3_333_330L, transactions.last { it.amountMinor == 3_333_330L }.amountMinor)
    }

    @Test
    fun `кавычки сохраняют разделители и кавычки в комментарии`() {
        val transactions = parse(
            "Дата;Время;Название счета;Категория;Сумма;Валюта;Комментарий\n" +
                "2026-01-02;03:04;Наличные;Еда;-1.2e+03;VND;\"Магазин; \"\"ужин\"\"\"\n",
        )

        assertEquals(120_000L, transactions.single().amountMinor)
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
    fun `жилье с е и ё сопоставляется одинаково`() {
        assertEquals(normalizeBudgetCategoryName("Жилье"), normalizeBudgetCategoryName(" Жильё "))
    }

    @Test
    fun `некорректная сумма сообщает номер строки`() {
        val error = assertThrows(BudgetImportException::class.java) {
            parse(HEADER + "2026-01-02;03:04;Наличные;Еда;abc;VND;\n")
        }
        assertEquals("Строка 2: некорректная сумма", error.message)
    }

    @Test
    fun `незакрытая кавычка отклоняет файл`() {
        assertThrows(BudgetImportException::class.java) {
            parse(HEADER + "2026-01-02;03:04;Наличные;Еда;-100;VND;\"комментарий\n")
        }
    }

    private fun parse(text: String) = parser.parse(ByteArrayInputStream(text.toByteArray()))

    private companion object {
        const val HEADER = "Дата;Время;Название счета;Категория;Сумма;Валюта;Комментарий\n"
    }
}
