package com.expeysi.app.data.csv

import com.expeysi.app.data.db.AccountEntity
import com.expeysi.app.data.db.BalanceEntity
import com.expeysi.app.data.db.TransactionEntity
import com.expeysi.app.data.importer.BudgetCsvParser
import com.expeysi.app.data.importer.resolveAccounts
import com.expeysi.app.data.importer.resolveBalances
import com.expeysi.app.data.importer.splitOpeningRows
import com.expeysi.app.data.importer.transferLegsOf
import com.expeysi.app.domain.TxType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.ByteArrayInputStream
import java.time.Instant
import java.time.ZoneId

private val UTC: ZoneId = ZoneId.of("UTC")

private fun millis(iso: String): Long = Instant.parse(iso).toEpochMilli()

/**
 * Полный цикл файла перевода без Robolectric: экспорт строит CSV, парсер и
 * резолверы импорта читают его, реконструкция возвращает пару настоящих ног —
 * то есть round-trip сохраняет тип, связку, контрагента, суммы и оформление.
 */
class TransferRoundTripTest {

    @Test
    fun `файл экспорта восстанавливает пару ног с суммами и контрагентом`() {
        val accountById = mapOf(
            "a1" to account("a1", "Карта", opening = 50_000, balanceId = "b1"),
            "a2" to account("a2", "Кошелёк", opening = 0, balanceId = "b2"),
        )
        val balances = listOf(
            balance("b1", "Основной", "RUB"),
            balance("b2", "Донг", "USD"),
        )
        val file = buildCsv(
            accounts = accountById.values.toList(),
            balances = balances,
            categories = emptyList(),
            transactions = listOf(
                tx("t1", TxType.TRANSFER_OUT, 30_000, "a1", "", millis("2026-01-02T03:04:00Z"), note = "обмен валюты", linkedId = "L1", counterpartyAccountId = "a2"),
                tx("t2", TxType.TRANSFER_IN, 6_30, "a2", "", millis("2026-01-02T03:04:00Z"), note = "обмен валюты", linkedId = "L1", counterpartyAccountId = "a1"),
            ),
            nowMillis = millis("2026-02-01T00:00:00Z"),
            zone = UTC,
            types = setOf(TxType.INCOME, TxType.EXPENSE, TxType.TRANSFER_OUT, TxType.TRANSFER_IN),
        )

        val parsed = BudgetCsvParser().parse(ByteArrayInputStream(file.text.toByteArray(Charsets.UTF_8)))
        // Импорт отфильтровывает служебные строки остатков до реконструкции пар.
        val (_, operations) = splitOpeningRows(parsed)
        assertEquals("L1", operations.single { it.accountName == "Карта" }.transferLinkId)
        assertEquals(30_000L, operations.single { it.accountName == "Карта" }.amountMinor)

        val balanceByKey = resolveBalances(parsed, takenNames = emptySet(), nowMillis = 1)
        val accountsByName = resolveAccounts(parsed, balanceByKey, openingByKey = emptyMap(), nowMillis = 1)
        val legs = transferLegsOf(operations, accountsByName, nowMillis = 77)

        assertEquals(2, legs.size)
        val out = legs.first { TxType.fromWire(it.type) == TxType.TRANSFER_OUT }
        val inLeg = legs.first { TxType.fromWire(it.type) == TxType.TRANSFER_IN }
        // Имена счетов-копий восстанавливаются по ключу (имя, валюта, баланс).
        val nameById = accountsByName.entries.associate { it.value.id to it.key.name }
        assertEquals("Карта", nameById.getValue(out.accountId))
        assertEquals("Кошелёк", nameById.getValue(inLeg.accountId))
        // Суммы ног в своих валютах и заметка переживают файл без потерь.
        assertEquals(30_000L, out.amountMinor)
        assertEquals(6_30L, inLeg.amountMinor)
        assertEquals("обмен валюты", out.note)
        assertEquals("обмен валюты", inLeg.note)
        assertEquals("", out.categoryId)
        assertEquals(out.date, inLeg.date)
        // Связка свежая: копия независима от пар в живых данных и других загрузок,
        // контрагенты ссылаются друг на друга.
        assertTrue(out.linkedId.isNotEmpty())
        assertEquals(out.linkedId, inLeg.linkedId)
        assertNotEquals("L1", out.linkedId)
        assertEquals(inLeg.accountId, out.counterpartyAccountId)
        assertEquals(out.accountId, inLeg.counterpartyAccountId)
        assertEquals(77L, out.updatedAt)
    }

    @Test
    fun `одинокая нога без партнёра не реконструируется`() {
        val accountById = mapOf(
            "a1" to account("a1", "Карта", opening = 50_000, balanceId = "b1"),
        )
        val rows = listOf(
            BudgetCsvParser().parse(
                ByteArrayInputStream(
                    buildCsv(
                        accounts = accountById.values.toList(),
                        balances = listOf(balance("b1", "Основной", "RUB")),
                        categories = emptyList(),
                        transactions = listOf(
                            tx("t1", TxType.TRANSFER_OUT, 30_000, "a1", "", millis("2026-01-02T03:04:00Z"), linkedId = "L1", counterpartyAccountId = "a2"),
                        ),
                        nowMillis = millis("2026-02-01T00:00:00Z"),
                        zone = UTC,
                        types = setOf(TxType.TRANSFER_OUT),
                    ).text.toByteArray(Charsets.UTF_8),
                ),
            ),
        ).single()
        // Служебная строка остатка отфильтрована, остаётся одинокая нога.
        val (_, operations) = splitOpeningRows(rows)

        val balancesMap = resolveBalances(rows, takenNames = emptySet(), nowMillis = 1)
        val accountsByName = resolveAccounts(rows, balancesMap, openingByKey = emptyMap(), nowMillis = 1)

        assertEquals(emptyList<TransactionEntity>(), transferLegsOf(operations, accountsByName, nowMillis = 77))
    }

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

    private fun tx(
        id: String,
        type: TxType,
        amountMinor: Long,
        accountId: String,
        categoryId: String,
        date: Long,
        note: String = "",
        linkedId: String = "",
        counterpartyAccountId: String = "",
    ) = TransactionEntity(
        id = id,
        type = type.wire,
        amountMinor = amountMinor,
        accountId = accountId,
        categoryId = categoryId,
        date = date,
        note = note,
        linkedId = linkedId,
        counterpartyAccountId = counterpartyAccountId,
        updatedAt = 0,
    )
}
