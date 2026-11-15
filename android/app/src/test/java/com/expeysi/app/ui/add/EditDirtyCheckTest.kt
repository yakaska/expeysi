package com.expeysi.app.ui.add

import com.expeysi.app.data.db.TransactionEntity
import com.expeysi.app.domain.Money
import java.time.LocalDate
import java.time.LocalTime
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Dirty-проверка экранов правки: неизменённая запись не меняет Save,
 * правка любого сохраняемого поля включает его, возврат поля к исходному
 * значению выключает снова.
 */
class EditDirtyCheckTest {

    private val date = LocalDate.of(2026, 2, 1)
    private val time = LocalTime.of(10, 30)

    @Test
    fun `расход не изменён, правится любым полем и возвращается обратно`() {
        val original = tx(
            id = "e-1",
            type = "expense",
            amountMinor = 63_000,
            accountId = "a-1",
            categoryId = "c-1",
            note = "",
        )
        val baseline = RegularDirtyInput(
            accountId = "a-1",
            categoryId = "c-1",
            amountText = "630",
            date = date,
            time = time,
            note = "",
        )
        // Загруженная запись без правок — Save выключен.
        assertFalse(regularDirty(baseline, original, date, time))
        // Правка каждого сохраняемого поля — Save включён.
        val changes = listOf(
            baseline.copy(accountId = "a-2"),
            baseline.copy(categoryId = "c-2"),
            baseline.copy(amountText = "700"),
            baseline.copy(date = date.plusDays(1)),
            baseline.copy(time = time.plusMinutes(5)),
            baseline.copy(note = "квартплата"),
            baseline.copy(amountText = "abc"),
        )
        changes.forEach { changed -> assertTrue(regularDirty(changed, original, date, time)) }
        // Возврат поля к исходному значению — Save снова выключен.
        val reverted = baseline.copy(note = "квартплата").copy(note = "")
        assertFalse(regularDirty(reverted, original, date, time))
        // Текст, равный по числу, и комментарий с пробелами — не изменения.
        assertFalse(regularDirty(baseline.copy(amountText = "630,0"), original, date, time))
        assertFalse(regularDirty(baseline.copy(note = "  "), original, date, time))
    }

    @Test
    fun `доход не изменён, правится суммой и возвращается обратно`() {
        val original = tx(
            id = "i-1",
            type = "income",
            amountMinor = 63_000,
            accountId = "a-1",
            categoryId = "c-2",
            note = "зарплата",
        )
        val baseline = RegularDirtyInput(
            accountId = "a-1",
            categoryId = "c-2",
            amountText = "630",
            date = date,
            time = time,
            note = "зарплата",
        )
        assertFalse(regularDirty(baseline, original, date, time))
        val changed = baseline.copy(amountText = "631")
        assertTrue(regularDirty(changed, original, date, time))
        val reverted = changed.copy(amountText = "630")
        assertFalse(regularDirty(reverted, original, date, time))
    }

    @Test
    fun `перевод не изменён, правится любым полем и возвращается обратно`() {
        // «Отправляете» хранится как итог ноги минус комиссия: 151 500 − 1 500 = 1 500.
        val outLeg = tx(
            id = "to-1",
            type = "transfer_out",
            amountMinor = 151_500,
            accountId = "a-1",
            categoryId = "",
            note = "обмен",
            linkedId = "pair-1",
            counterpartyAccountId = "a-2",
            feeMinor = 1_500,
        )
        val inLeg = tx(
            id = "ti-1",
            type = "transfer_in",
            amountMinor = 630,
            accountId = "a-2",
            categoryId = "",
            note = "обмен",
            linkedId = "pair-1",
            counterpartyAccountId = "a-1",
        )
        val baseline = TransferDirtyInput(
            fromAccountId = "a-1",
            toAccountId = "a-2",
            sendText = "1 500",
            feeText = "15",
            receiveText = "6,30",
            date = date,
            time = time,
            note = "обмен",
        )
        // Загруженный перевод без правок — Save выключен.
        assertFalse(transferDirty(baseline, outLeg, inLeg, outLeg, date, time))
        // Правка каждого поля пары — Save включён; пустая комиссия означает ноль.
        val changes = listOf(
            baseline.copy(fromAccountId = "a-3"),
            baseline.copy(toAccountId = "a-3"),
            baseline.copy(sendText = "999"),
            baseline.copy(feeText = "20"),
            baseline.copy(feeText = ""),
            baseline.copy(receiveText = "6,00"),
            baseline.copy(date = date.plusDays(1)),
            baseline.copy(time = time.plusMinutes(5)),
            baseline.copy(note = "другое"),
        )
        changes.forEach { changed -> assertTrue(transferDirty(changed, outLeg, inLeg, outLeg, date, time)) }
        // Возврат комиссии и зачисления к исходным значениям — Save снова выключен.
        assertFalse(transferDirty(baseline.copy(feeText = "20").copy(feeText = "15"), outLeg, inLeg, outLeg, date, time))
        assertFalse(transferDirty(baseline.copy(receiveText = "6,00").copy(receiveText = "6,30"), outLeg, inLeg, outLeg, date, time))
        // in-нога ещё не дошла по синку: зачисление не сравнивается, счёт берётся
        // из counterpartyAccountId; правка счёта-получателя остаётся изменением.
        assertFalse(transferDirty(baseline.copy(receiveText = "999"), outLeg, null, outLeg, date, time))
        assertTrue(transferDirty(baseline.copy(toAccountId = "a-9"), outLeg, null, outLeg, date, time))
        // out-нога не дошла: отправление и комиссия не сравниваются,
        // счёт-источник сверяется с counterpartyAccountId ноги-оригинала.
        assertFalse(transferDirty(baseline.copy(sendText = "999", feeText = ""), null, inLeg, inLeg, date, time))
        assertTrue(transferDirty(baseline.copy(fromAccountId = "a-9"), null, inLeg, inLeg, date, time))
    }
}

/** Фикстура записи: несущественные для dirty-сравнения поля — дефолтные. */
private fun tx(
    id: String,
    type: String,
    amountMinor: Long,
    accountId: String,
    categoryId: String,
    note: String = "",
    linkedId: String = "",
    counterpartyAccountId: String = "",
    feeMinor: Long = 0,
): TransactionEntity = TransactionEntity(
    id = id,
    type = type,
    amountMinor = amountMinor,
    accountId = accountId,
    categoryId = categoryId,
    date = 0L,
    note = note,
    linkedId = linkedId,
    counterpartyAccountId = counterpartyAccountId,
    feeMinor = feeMinor,
    updatedAt = 0L,
)
