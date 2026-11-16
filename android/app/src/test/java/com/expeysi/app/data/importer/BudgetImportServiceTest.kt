package com.expeysi.app.data.importer

import com.expeysi.app.data.csv.CsvFormat
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
    fun `комиссия файла возвращается ноге списания отдельным полем`() {
        val legs = transferLegsOf(
            listOf(
                // 30 000 отправлено, 1 500 комиссия — как их пишет экспорт.
                item(accountName = "Карта", type = TxType.EXPENSE, amountMinor = 30_000, transferLinkId = "L1", feeMinor = 1_500),
                item(accountName = "Кошелёк", type = TxType.INCOME, amountMinor = 30_000, transferLinkId = "L1"),
            ),
            accounts,
            nowMillis = 77,
        )

        assertEquals(2, legs.size)
        val out = legs.first()
        val inLeg = legs.last()
        // Ноге списания пишется «всего списано» и комиссия рядом — как в базе.
        assertEquals(31_500L, out.amountMinor)
        assertEquals(1_500L, out.feeMinor)
        assertEquals(30_000L, inLeg.amountMinor)
        assertEquals(0L, inLeg.feeMinor)
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
    fun `три строки связки целиком остаются операциями`() {
        val group = listOf(
            item(accountName = "Карта", type = TxType.EXPENSE, amountMinor = 10_000, transferLinkId = "L1"),
            item(accountName = "Карта", type = TxType.EXPENSE, amountMinor = 20_000, transferLinkId = "L1"),
            item(accountName = "Кошелёк", type = TxType.INCOME, amountMinor = 30_000, transferLinkId = "L1"),
        )

        assertTrue(transferLegsOf(group, accounts, nowMillis = 77).isEmpty())
    }

    @Test
    fun `два списания в одной связке целиком остаются операциями`() {
        val group = listOf(
            item(accountName = "Карта", type = TxType.EXPENSE, amountMinor = 10_000, transferLinkId = "L1"),
            item(accountName = "Карта", type = TxType.EXPENSE, amountMinor = 20_000, transferLinkId = "L1"),
        )

        assertTrue(transferLegsOf(group, accounts, nowMillis = 77).isEmpty())
    }

    @Test
    fun `списание и зачисление на одном счете не реконструируются`() {
        val group = listOf(
            item(accountName = "Карта", type = TxType.EXPENSE, amountMinor = 30_000, transferLinkId = "L1"),
            item(accountName = "Карта", type = TxType.INCOME, amountMinor = 30_000, transferLinkId = "L1"),
        )

        assertTrue(transferLegsOf(group, accounts, nowMillis = 77).isEmpty())
    }

    @Test
    fun `вырожденная связка импортируется строками один к одному`() {
        // Как в BudgetImportService.import: группа без валидной пары идёт
        // обычными операциями — каждая строка файла становится операцией
        // своего знака, число операций равно числу строк, а остатки счетов
        // складываются с арифметикой CSV без потерь и без надстроек.
        val rows = listOf(
            item(accountName = "Карта", type = TxType.EXPENSE, amountMinor = 10_000, transferLinkId = "L1"),
            item(accountName = "Карта", type = TxType.EXPENSE, amountMinor = 20_000, transferLinkId = "L1"),
            item(accountName = "Кошелёк", type = TxType.INCOME, amountMinor = 30_000, transferLinkId = "L1"),
        )
        assertTrue(transferLegsOf(rows, accounts, nowMillis = 77).isEmpty())

        assertEquals(rows.size, 3)
        val signed = { name: String ->
            rows.filter { it.accountName == name }
                .sumOf { if (it.type == TxType.EXPENSE) -it.amountMinor else it.amountMinor }
        }
        assertEquals(-30_000L, signed("Карта"))
        assertEquals(30_000L, signed("Кошелёк"))
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

    @Test
    fun `нетто по счетам считается со знаком без служебных строк остатков`() {
        // Экран остатков показывает, что даст импорт без правки: служебные
        // строки «Начальный остаток» уходят отдельно и в нетто не входят.
        val nets = accountNets(
            listOf(
                item(accountName = "Карта", type = TxType.EXPENSE, amountMinor = 10_000),
                item(accountName = "Карта", type = TxType.INCOME, amountMinor = 2_500),
                item(accountName = "Кошелёк", type = TxType.INCOME, amountMinor = 30_000, transferLinkId = "L1"),
                item(accountName = "Карта", type = TxType.EXPENSE, amountMinor = 5_000, transferLinkId = "L2"),
                openingBalance(150_000),
            ),
        )

        assertEquals(2, nets.size)
        assertEquals(-12_500L, nets[AccountKey("Карта", "RUB", null)])
        assertEquals(30_000L, nets[AccountKey("Кошелёк", "RUB", null)])
    }

    @Test
    fun `введенный остаток превращается в opening разницей с нетто`() {
        // «Сейчас на счете 35 000», нетто операций файла −10 794,51:
        // счет должен начаться с 45 794,51, чтобы импорт показал 35 000.
        assertEquals(45_794_51L, openingFromCurrent(currentMinor = 35_000_00L, netMinor = -10_794_51L))
        // Счет пуст или закрыт: 0 − net = −net.
        assertEquals(99_999_73L, openingFromCurrent(currentMinor = 0L, netMinor = -99_999_73L))
        // Нетто совпадает с реальностью — остаток импорта нулевой.
        assertEquals(0L, openingFromCurrent(currentMinor = 100_000_00L, netMinor = 100_000_00L))
    }

    private fun openingBalance(amountMinor: Long) = BudgetTransaction(
        accountName = "Карта",
        categoryName = CsvFormat.OPENING_ROW_CATEGORY,
        type = TxType.INCOME,
        amountMinor = amountMinor,
        currencyCode = "RUB",
        date = 1,
        note = "Открытие",
    )

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
        feeMinor: Long = 0L,
    ) = BudgetTransaction(
        accountName = accountName,
        categoryName = "Перевод",
        type = type,
        amountMinor = amountMinor,
        currencyCode = "RUB",
        date = 5,
        note = note,
        transferLinkId = transferLinkId,
        feeMinor = feeMinor,
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
