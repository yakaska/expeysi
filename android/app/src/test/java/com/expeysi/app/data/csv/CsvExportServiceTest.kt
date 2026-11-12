package com.expeysi.app.data.csv

import com.expeysi.app.data.csv.CsvFormat.EXTENDED_HEADER
import com.expeysi.app.data.db.AccountEntity
import com.expeysi.app.data.db.BalanceEntity
import com.expeysi.app.data.db.CategoryEntity
import com.expeysi.app.data.db.TransactionEntity
import com.expeysi.app.domain.TxType
import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.Instant
import java.time.ZoneId

private val UTC: ZoneId = ZoneId.of("UTC")

private fun millis(iso: String): Long = Instant.parse(iso).toEpochMilli()

class CsvExportServiceTest {

    private val balances = listOf(
        balance("b1", "Основной", "RUB", iconKey = "payments", colorKey = "blue"),
        balance("b2", "Вьетнам", "USD", iconKey = "home", colorKey = "red"),
    )

    @Test
    fun `собирается файл со служебными строками и операциями по датам`() {
        val now = millis("2026-02-01T00:00:00Z")
        val file = buildCsv(
            accounts = listOf(
                account("a1", "Карта", opening = 50_000, balanceId = "b1", iconKey = "credit_card"),
                account("a2", "Наличные", opening = -10_000, balanceId = "b1", colorKey = "green"),
                account("a3", "Пусто", opening = 0, balanceId = "b1"),
                // Валюта счёта в БД всегда резервный «RUB»: в файл идёт валюта баланса.
                account("a4", "Доллары", opening = 250_000, balanceId = "b2", iconKey = "savings", colorKey = "amber"),
                account("a5", "Удалено", opening = 99_00, balanceId = "b2", deleted = true),
            ),
            balances = balances,
            categories = listOf(
                category("c1", "Еда", TxType.EXPENSE, iconKey = "restaurant", colorKey = "orange"),
                category("c2", "Зарплата", TxType.INCOME, iconKey = "work", colorKey = "green"),
                category("c3", "Старое", TxType.EXPENSE, iconKey = "home", colorKey = "red", deleted = true),
            ),
            transactions = listOf(
                tx("t1", TxType.INCOME, 150_000, "a1", "c2", millis("2026-01-02T03:04:00Z")),
                tx("t2", TxType.EXPENSE, 25_050, "a1", "c1", millis("2026-01-05T22:30:00Z"), note = "Магазин; «ужин»"),
                tx("t3", TxType.EXPENSE, 5_000, "a2", "c1", millis("2025-12-31T10:00:00Z")),
                tx("t4", TxType.EXPENSE, 7_000, "a2", "c3", millis("2026-01-07T12:00:00Z")),
                tx("t5", TxType.EXPENSE, 1_000, "a1", "c1", millis("2026-01-08T00:00:00Z"), deleted = true),
                tx("t6", TxType.EXPENSE, 2_000, "a5", "c1", millis("2026-01-09T00:00:00Z")),
            ),
            nowMillis = now,
            zone = UTC,
            types = setOf(TxType.INCOME, TxType.EXPENSE),
        )

        // Служебные строки: по имени счёта; дата — ранняя выгруженная операция счёта,
        // у счёта без операций (Доллары) — время экспорта. Нулевой остаток строки не
        // создаёт, удалённые счёт и операция пропущены. Валюта — из баланса счёта.
        val expected = listOf(
            EXTENDED_HEADER,
            "2026-02-01;00:00;Доллары;Начальный остаток;2500.00;USD;;;;savings;amber;Вьетнам;home;red",
            "2026-01-02;00:00;Карта;Начальный остаток;500.00;RUB;;;;credit_card;blue;Основной;payments;blue",
            "2025-12-31;00:00;Наличные;Начальный остаток;-100.00;RUB;;;;payments;green;Основной;payments;blue",
            "2025-12-31;10:00;Наличные;Еда;-50.00;RUB;;restaurant;orange;payments;green;Основной;payments;blue",
            "2026-01-02;03:04;Карта;Зарплата;1500.00;RUB;;work;green;credit_card;blue;Основной;payments;blue",
            "2026-01-05;22:30;Карта;Еда;-250.50;RUB;\"Магазин; «ужин»\";restaurant;orange;credit_card;blue;Основной;payments;blue",
            "2026-01-07;12:00;Наличные;Старое;-70.00;RUB;;home;red;payments;green;Основной;payments;blue",
            "",
        ).joinToString("\r\n")
        assertEquals(expected, file.text)
        assertEquals(4, file.transactionCount)
    }

    @Test
    fun `фильтр типов оставляет выбранные операции и служебные строки`() {
        val file = buildCsv(
            accounts = listOf(
                account("a1", "Карта", opening = 50_000, balanceId = "b1", iconKey = "credit_card"),
                account("a2", "Наличные", opening = -10_000, balanceId = "b1"),
            ),
            balances = balances,
            categories = listOf(category("c2", "Зарплата", TxType.INCOME, iconKey = "work", colorKey = "green")),
            transactions = listOf(
                tx("t1", TxType.INCOME, 150_000, "a1", "c2", millis("2026-01-02T03:04:00Z")),
                tx("t2", TxType.EXPENSE, 25_050, "a1", "c1", millis("2026-01-05T22:30:00Z")),
            ),
            nowMillis = millis("2026-02-01T00:00:00Z"),
            zone = UTC,
            types = setOf(TxType.INCOME),
        )

        // Расходы не выгружаются; у счёта Наличные нет выгруженных операций, поэтому
        // дата его служебной строки падает на время экспорта. Остатки и оформление
        // выгружаются всегда.
        val expected = listOf(
            EXTENDED_HEADER,
            "2026-01-02;00:00;Карта;Начальный остаток;500.00;RUB;;;;credit_card;blue;Основной;payments;blue",
            "2026-02-01;00:00;Наличные;Начальный остаток;-100.00;RUB;;;;payments;blue;Основной;payments;blue",
            "2026-01-02;03:04;Карта;Зарплата;1500.00;RUB;;work;green;credit_card;blue;Основной;payments;blue",
            "",
        ).joinToString("\r\n")
        assertEquals(expected, file.text)
        assertEquals(1, file.transactionCount)
    }

    @Test
    fun `без выбранных типов остаются только служебные строки`() {
        val file = buildCsv(
            accounts = listOf(
                account("a1", "Карта", opening = 50_000, balanceId = "b1"),
                account("a2", "Наличные", opening = -10_000, balanceId = "b1"),
            ),
            balances = balances,
            categories = emptyList(),
            transactions = listOf(
                tx("t1", TxType.INCOME, 150_000, "a1", "c1", millis("2026-01-02T03:04:00Z")),
            ),
            nowMillis = millis("2026-02-01T00:00:00Z"),
            zone = UTC,
            types = emptySet(),
        )

        assertEquals(
            listOf(
                EXTENDED_HEADER,
                "2026-02-01;00:00;Карта;Начальный остаток;500.00;RUB;;;;payments;blue;Основной;payments;blue",
                "2026-02-01;00:00;Наличные;Начальный остаток;-100.00;RUB;;;;payments;blue;Основной;payments;blue",
                "",
            ).joinToString("\r\n"),
            file.text,
        )
        assertEquals(0, file.transactionCount)
    }

    @Test
    fun `без данных файл состоит из заголовка`() {
        val file = buildCsv(emptyList(), emptyList(), emptyList(), emptyList(), nowMillis = 0, zone = UTC, types = setOf(TxType.INCOME))
        assertEquals("$EXTENDED_HEADER\r\n", file.text)
        assertEquals(0, file.transactionCount)
    }

    @Test
    fun `сумма пишется с точкой и двумя знаками`() {
        assertEquals("0.00", formatAmount(0))
        assertEquals("0.05", formatAmount(5))
        assertEquals("1.00", formatAmount(100))
        assertEquals("1234.56", formatAmount(123_456))
        assertEquals("-100.10", formatAmount(-10_010))
    }

    @Test
    fun `экранируются разделитель кавычки и переводы строк`() {
        assertEquals("просто", escape("просто"))
        assertEquals("\"a;b\"", escape("a;b"))
        assertEquals("\"say \"\"hi\"\"\"", escape("say \"hi\""))
        assertEquals("\"строка\nс переносом\"", escape("строка\nс переносом"))
        assertEquals("\"a;\r\nb\"", escape("a;\r\nb"))
    }

    private fun account(
        id: String,
        name: String,
        opening: Long,
        balanceId: String,
        iconKey: String = "payments",
        colorKey: String = "blue",
        deleted: Boolean = false,
    ) = AccountEntity(
        id = id,
        name = name,
        iconKey = iconKey,
        colorKey = colorKey,
        openingBalanceMinor = opening,
        currencyCode = "RUB",
        balanceId = balanceId,
        deleted = deleted,
        updatedAt = 0,
    )

    private fun balance(
        id: String,
        name: String,
        currency: String,
        iconKey: String = "payments",
        colorKey: String = "blue",
    ) = BalanceEntity(
        id = id,
        name = name,
        currencyCode = currency,
        iconKey = iconKey,
        colorKey = colorKey,
        updatedAt = 0,
    )

    private fun category(
        id: String,
        name: String,
        type: TxType,
        iconKey: String = "restaurant",
        colorKey: String = "orange",
        deleted: Boolean = false,
    ) = CategoryEntity(
        id = id,
        name = name,
        iconKey = iconKey,
        colorKey = colorKey,
        type = type.wire,
        deleted = deleted,
        updatedAt = 0,
    )

    private fun tx(
        id: String,
        type: TxType,
        amountMinor: Long,
        accountId: String,
        categoryId: String,
        date: Long,
        note: String = "",
        deleted: Boolean = false,
    ) = TransactionEntity(
        id = id,
        type = type.wire,
        amountMinor = amountMinor,
        accountId = accountId,
        categoryId = categoryId,
        date = date,
        note = note,
        deleted = deleted,
        updatedAt = 0,
    )
}
