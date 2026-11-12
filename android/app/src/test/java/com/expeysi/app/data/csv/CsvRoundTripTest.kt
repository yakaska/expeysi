package com.expeysi.app.data.csv

import com.expeysi.app.data.importer.AccountKey
import com.expeysi.app.data.importer.BudgetCsvParser
import com.expeysi.app.data.importer.BalanceKey
import com.expeysi.app.data.importer.resolveAccounts
import com.expeysi.app.data.importer.resolveBalances
import com.expeysi.app.data.importer.splitOpeningRows
import com.expeysi.app.data.csv.CsvFormat.EXTENDED_HEADER
import com.expeysi.app.data.db.AccountEntity
import com.expeysi.app.data.db.BalanceEntity
import com.expeysi.app.data.db.CategoryEntity
import com.expeysi.app.data.db.TransactionEntity
import com.expeysi.app.data.importer.BudgetTransaction
import com.expeysi.app.domain.BalanceCalculator
import com.expeysi.app.domain.TxSum
import com.expeysi.app.domain.TxType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.ByteArrayInputStream
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.UUID

private val UTC: ZoneId = ZoneId.of("UTC")

private fun millis(iso: String): Long = Instant.parse(iso).toEpochMilli()

private fun wallClock(millis: Long, zone: ZoneId): String =
    DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm").format(Instant.ofEpochMilli(millis).atZone(zone))

/** Экспорт обязан читаться импортом «Бюджета»: остатки, операции и оформление сходятся. */
class CsvRoundTripTest {

    private val parser = BudgetCsvParser()

    private val balances = listOf(
        BalanceEntity(
            id = "b1",
            name = "Основной",
            currencyCode = "RUB",
            iconKey = "payments",
            colorKey = "blue",
            updatedAt = 0,
        ),
        BalanceEntity(
            id = "b2",
            name = "Вьетнам",
            currencyCode = "USD",
            iconKey = "home",
            colorKey = "red",
            updatedAt = 0,
        ),
    )

    @Test
    fun `экспорт читается обратно с остатками операциями и оформлением`() {
        val accounts = listOf(
            account("a1", "Карта", opening = 50_000, balanceId = "b1", iconKey = "credit_card"),
            account("a2", "Наличные", opening = -10_000, balanceId = "b1", colorKey = "green"),
            account("a3", "Сейф", opening = 2_000_000, balanceId = "b1"),
            account("a4", "Bucks", opening = 30_000, balanceId = "b2"),
        )
        val transactions = listOf(
            tx("t1", TxType.INCOME, 150_000, "a1", "c2", millis("2026-01-02T03:04:00Z")),
            tx("t2", TxType.EXPENSE, 25_050, "a1", "c1", millis("2026-01-05T22:30:00Z"), note = "Магазин; «ужин»"),
            tx("t3", TxType.EXPENSE, 5_000, "a2", "c1", millis("2025-12-31T10:00:00Z")),
            tx("t4", TxType.INCOME, 1_000_000, "a4", "c2", millis("2026-01-06T09:00:00Z")),
        )
        val file = buildCsv(
            accounts = accounts,
            balances = balances,
            categories = listOf(
                category("c1", "Еда", TxType.EXPENSE, iconKey = "restaurant", colorKey = "orange"),
                category("c2", "Зарплата", TxType.INCOME, iconKey = "work", colorKey = "green"),
            ),
            transactions = transactions,
            nowMillis = millis("2026-02-01T00:00:00Z"),
            zone = UTC,
            types = setOf(TxType.INCOME, TxType.EXPENSE),
        )

        val parsed = parser.parse(bytes(file.text))
        val (opening, operations) = splitOpeningRows(parsed)

        // Остатки: по (имя счёта, валюта, баланс) — одинаковые имена счетов
        // в разных балансах не сливаются.
        assertEquals(
            mapOf(
                AccountKey("Карта", "RUB", "Основной") to 50_000L,
                AccountKey("Наличные", "RUB", "Основной") to -10_000L,
                AccountKey("Сейф", "RUB", "Основной") to 2_000_000L,
                AccountKey("Bucks", "USD", "Вьетнам") to 30_000L,
            ),
            opening,
        )
        assertEquals(4, operations.size)
        // Валюта операции — валюта баланса счёта (в БД accounts.currencyCode резервная).
        val bucks = operations.first { it.accountName == "Bucks" }
        assertEquals("USD", bucks.currencyCode)
        assertEquals("Вьетнам", bucks.balanceName)
        assertEquals(
            setOf(
                Triple("Карта", "Зарплата", TxType.INCOME),
                Triple("Карта", "Еда", TxType.EXPENSE),
                Triple("Наличные", "Еда", TxType.EXPENSE),
                Triple("Bucks", "Зарплата", TxType.INCOME),
            ),
            operations.map { Triple(it.accountName, it.categoryName, it.type) }.toSet(),
        )
        assertEquals("Магазин; «ужин»", operations.first { it.type == TxType.EXPENSE && it.amountMinor == 25_050L }.note)
        // Оформление: счёт, категория и баланс из колонок экспорта.
        assertEquals("credit_card", operations.first { it.accountName == "Карта" }.accountIconKey)
        assertEquals("restaurant", operations.first { it.categoryName == "Еда" }.categoryIconKey)
        assertEquals("orange", operations.first { it.categoryName == "Еда" }.categoryColorKey)
        assertEquals("green", operations.first { it.categoryName == "Зарплата" }.categoryColorKey)
        assertEquals("home", bucks.balanceIconKey)
        assertEquals("red", bucks.balanceColorKey)
        // Формат хранит настенные дату и время: в той же зоне round-trip точен,
        // поэтому сверяем их, а не мгновения (зоны экспорта и теста различаются).
        assertEquals(
            listOf("2025-12-31T10:00", "2026-01-02T03:04", "2026-01-05T22:30", "2026-01-06T09:00").sorted(),
            operations.map { wallClock(it.date, ZoneId.systemDefault()) }.sorted(),
        )

        // Имена и оформление балансов восстанавливаются как в файле; порядок
        // balances — порядок встречи в файле, не смысловой, сверяем по имени.
        // Загруженные копии получают префикс «импорт ».
        val resolvedBalances = resolveBalances(parsed, takenNames = emptySet(), nowMillis = 0)
        val balancesByName = resolvedBalances.values.associateBy { it.name }
        assertEquals(setOf("импорт Основной", "импорт Вьетнам"), balancesByName.keys)
        assertEquals("RUB", balancesByName.getValue("импорт Основной").currencyCode)
        assertEquals("payments", balancesByName.getValue("импорт Основной").iconKey)
        assertEquals("blue", balancesByName.getValue("импорт Основной").colorKey)
        assertEquals("USD", balancesByName.getValue("импорт Вьетнам").currencyCode)
        assertEquals("home", balancesByName.getValue("импорт Вьетнам").iconKey)
        assertEquals("red", balancesByName.getValue("импорт Вьетнам").colorKey)

        // Счета: валюта из колонки, остаток и оформление из файла, привязка к балансу.
        val resolvedAccounts = resolveAccounts(parsed, resolvedBalances, opening, nowMillis = 0)
        assertEquals(4, resolvedAccounts.size)
        val mapped = resolvedAccounts.getValue(AccountKey("Карта", "RUB", "Основной"))
        assertEquals("Карта", mapped.name)
        assertEquals("credit_card", mapped.iconKey)
        assertEquals("blue", mapped.colorKey)
        assertEquals(50_000L, mapped.openingBalanceMinor)
        assertEquals("RUB", mapped.currencyCode)
        assertEquals(resolvedBalances.getValue(BalanceKey("Основной", "RUB")).id, mapped.balanceId)
        val bucksAccount = resolvedAccounts.getValue(AccountKey("Bucks", "USD", "Вьетнам"))
        assertEquals("USD", bucksAccount.currencyCode)
        assertEquals(30_000L, bucksAccount.openingBalanceMinor)
        assertEquals(resolvedBalances.getValue(BalanceKey("Вьетнам", "USD")).id, bucksAccount.balanceId)
        // Баланс каждого счёта после переимпорта сходится: остаток + доходы − расходы
        // считаем до и после, а не одну и ту же формулу дважды.
        accounts.forEach { source ->
            val resolved = resolvedAccounts.values.first { it.name == source.name }
            val sourceSums = transactions
                .filter { it.accountId == source.id }
                .map { TxSum(TxType.fromWire(it.type), it.amountMinor) }
            val importedSums = operations
                .filter { it.accountName == source.name }
                .map { TxSum(it.type, it.amountMinor) }
            assertEquals(
                BalanceCalculator.balanceMinor(source.openingBalanceMinor, sourceSums),
                BalanceCalculator.balanceMinor(resolved.openingBalanceMinor, importedSums),
            )
        }
    }

    @Test
    fun `копии балансов получают префикс импорт и нумерацию повторов`() {
        val items = listOf(
            txRow(
                accountName = "Карта",
                currencyCode = "RUB",
                balanceName = "Основной",
                balanceIconKey = "payments",
                balanceColorKey = "blue",
            ),
        )

        // Аддитивный импорт: каждая загрузка балансирует отдельной копией с
        // префиксом «импорт », оформление — из файла.
        val first = resolveBalances(items, takenNames = emptySet(), nowMillis = 0)
            .getValue(BalanceKey("Основной", "RUB"))
        assertEquals("импорт Основной", first.name)
        assertEquals("payments", first.iconKey)
        assertEquals("blue", first.colorKey)
        UUID.fromString(first.id)

        // Повторная загрузка: номер в скобках — за максимальным существующим
        // активным (удалённая копия с номером (2) не учитывается и не мешает).
        val second = resolveBalances(
            items,
            takenNames = setOf("Баланс пользователя", "импорт Основной", "импорт Основной (2)", "Основной"),
            nowMillis = 1,
        ).getValue(BalanceKey("Основной", "RUB"))
        assertEquals("импорт Основной (3)", second.name)
        UUID.fromString(second.id)
        assertNotEquals(first.id, second.id)

        // Одноимённые балансы разных валют в одной загрузке не сливаются:
        // нумерация начинается внутри самой загрузки.
        val both = listOf(
            txRow(accountName = "Касса", currencyCode = "RUB", balanceName = "Касса", balanceIconKey = null, balanceColorKey = null),
            txRow(accountName = "Касса", currencyCode = "USD", balanceName = "Касса", balanceIconKey = null, balanceColorKey = null),
        )
        val resolved = resolveBalances(both, takenNames = emptySet(), nowMillis = 0)
        assertEquals(
            listOf("импорт Касса", "импорт Касса (2)"),
            resolved.values.map { it.name },
        )
    }

    @Test
    fun `импорт всегда создает новые счета под новыми балансами`() {
        val key = AccountKey("Карта", "RUB", "Основной")
        val items = listOf(
            txRow(
                accountName = "Карта",
                currencyCode = "RUB",
                balanceName = "Основной",
                balanceIconKey = null,
                balanceColorKey = null,
                accountIconKey = "credit_card",
                accountColorKey = "teal",
            ),
        )
        val balanceByKey = resolveBalances(items, takenNames = emptySet(), nowMillis = 0)

        // Аддитивный импорт: счета не переиспользуются — каждая загрузка кладёт
        // свежую копию с оформлением и остатком из файла, привязанную к новому
        // балансу той же загрузки.
        val first = resolveAccounts(items, balanceByKey, openingByKey = mapOf(key to 50_000L), nowMillis = 0)
            .getValue(key)
        val second = resolveAccounts(items, balanceByKey, openingByKey = mapOf(key to 50_000L), nowMillis = 1)
            .getValue(key)
        assertEquals("credit_card", first.iconKey)
        assertEquals("teal", first.colorKey)
        assertEquals(50_000L, first.openingBalanceMinor)
        assertEquals(balanceByKey.getValue(BalanceKey("Основной", "RUB")).id, first.balanceId)
        UUID.fromString(first.id)
        UUID.fromString(second.id)
        assertNotEquals(first.id, second.id)
    }

    @Test
    fun `чужой файл без названий балансов создает автосозданные Бюджеты`() {
        val items = parser.parse(
            bytes(
                "Дата;Время;Название счета;Категория;Сумма;Валюта;Комментарий\n" +
                    "2026-01-02;03:04;Карта;Еда;-50.00;RUB;Продукты\n" +
                    "2026-01-02;03:05;Наличные;Зарплата;1000;USD;\n",
            ),
        )
        val (opening, operations) = splitOpeningRows(items)
        assertTrue(opening.isEmpty())
        assertEquals(2, operations.size)

        val resolved = resolveBalances(items, takenNames = emptySet(), nowMillis = 0)
        assertEquals(
            listOf("импорт Бюджет (RUB)", "импорт Бюджет (USD)"),
            resolved.values.map { it.name },
        )
        // id всегда новый: импорт аддитивен, чужой файл никогда не сливается
        // с текущими балансами.
        resolved.values.forEach { UUID.fromString(it.id) }
    }

    @Test
    fun `повторная служебная строка замещает остаток`() {
        val (opening, operations) = splitOpeningRows(
            parser.parse(
                bytes(
                    "$EXTENDED_HEADER\n" +
                        "2026-01-02;03:04;Карта;Начальный остаток;100.00;RUB;;;;;;Основной;;\n" +
                        "2026-01-02;03:05;Карта;Начальный остаток;200.00;RUB;;;;;;Основной;;\n" +
                        "2026-01-03;10:00;Карта;Еда;-50.00;RUB;;;;;;Основной;;\n",
                ),
            ),
        )
        assertEquals(20_000L, opening[AccountKey("Карта", "RUB", "Основной")])
        assertEquals(1, operations.size)
    }

    private fun bytes(text: String) = ByteArrayInputStream(text.toByteArray(Charsets.UTF_8))

    private fun account(
        id: String,
        name: String,
        opening: Long,
        balanceId: String,
        iconKey: String = "payments",
        colorKey: String = "blue",
    ) = AccountEntity(
        id = id,
        name = name,
        iconKey = iconKey,
        colorKey = colorKey,
        openingBalanceMinor = opening,
        currencyCode = "RUB",
        balanceId = balanceId,
        updatedAt = 0,
    )

    private fun category(id: String, name: String, type: TxType, iconKey: String, colorKey: String) = CategoryEntity(
        id = id,
        name = name,
        iconKey = iconKey,
        colorKey = colorKey,
        type = type.wire,
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
    ) = TransactionEntity(
        id = id,
        type = type.wire,
        amountMinor = amountMinor,
        accountId = accountId,
        categoryId = categoryId,
        date = date,
        note = note,
        updatedAt = 0,
    )

    private fun txRow(
        accountName: String,
        currencyCode: String,
        balanceName: String?,
        balanceIconKey: String?,
        balanceColorKey: String?,
        accountIconKey: String? = null,
        accountColorKey: String? = null,
    ) = BudgetTransaction(
        accountName = accountName,
        categoryName = "Еда",
        type = TxType.EXPENSE,
        amountMinor = 100L,
        currencyCode = currencyCode,
        date = 0,
        note = "",
        accountIconKey = accountIconKey,
        accountColorKey = accountColorKey,
        balanceName = balanceName,
        balanceIconKey = balanceIconKey,
        balanceColorKey = balanceColorKey,
    )
}
