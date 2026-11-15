package com.expeysi.app.ui.add

import com.expeysi.app.data.db.TransactionEntity
import com.expeysi.app.domain.Money
import java.time.LocalDate
import java.time.LocalTime

/**
 * Состояние полей редактирования для сравнения с загруженной записью.
 * Суммы — тексты полей: они сводятся к минимальным единицам тем же разбором,
 * что и сохранение, поэтому «выглядит иначе, но равно по числу» и «сломанный
 * ввод» различаются корректно.
 */
internal data class RegularDirtyInput(
    val accountId: String?,
    val categoryId: String?,
    val amountText: String,
    val date: LocalDate,
    val time: LocalTime,
    val note: String,
)

internal data class TransferDirtyInput(
    val fromAccountId: String?,
    val toAccountId: String?,
    val sendText: String,
    val feeText: String,
    val receiveText: String,
    val date: LocalDate,
    val time: LocalTime,
    val note: String,
)

/**
 * Запись изменена? Сравнивается всё, что сохраняется: счёт, категория,
 * сумма, дата и время (как показаны — без секунд исходной записи) и
 * комментарий, обрезанный так же, как при сохранении. Невалидный ввод
 * считается изменением: сохранение в этом состоянии выключено по canSave.
 * Незначащие различия не считаются: пустая комиссия = ноль.
 */
internal fun regularDirty(
    input: RegularDirtyInput,
    original: TransactionEntity,
    originalDate: LocalDate,
    originalTime: LocalTime,
): Boolean = input.accountId != original.accountId ||
    input.categoryId != original.categoryId ||
    Money.parseToMinor(input.amountText) != original.amountMinor ||
    input.date != originalDate ||
    input.time != originalTime ||
    input.note.trim() != original.note

/**
 * Пара ног перевода изменена? Счета, суммы обеих ног, комиссия, дата и
 * время, комментарий. Курс в записи не хранится и на dirty не влияет:
 * он выводится из сумм ног. Недостающая нога (ещё не дошедшая по синку)
 * не сравнивается — правятся только её поля.
 */
internal fun transferDirty(
    input: TransferDirtyInput,
    outLeg: TransactionEntity?,
    inLeg: TransactionEntity?,
    original: TransactionEntity,
    originalDate: LocalDate,
    originalTime: LocalTime,
): Boolean {
    if (input.fromAccountId != (outLeg?.accountId ?: original.counterpartyAccountId.ifEmpty { null })) return true
    if (input.toAccountId != (inLeg?.accountId ?: original.counterpartyAccountId.ifEmpty { null })) return true
    if (input.date != originalDate || input.time != originalTime) return true
    if (input.note.trim() != original.note) return true
    if (outLeg != null) {
        // «Отправляете» хранится без комиссии, итог ноги — с ней.
        val sendExpected = outLeg.amountMinor - outLeg.feeMinor
        if (Money.parseToMinor(input.sendText) != sendExpected) return true
        val feeInput = if (input.feeText.isEmpty()) 0L else Money.parseToMinor(input.feeText)
        if (feeInput != outLeg.feeMinor) return true
    }
    if (inLeg != null && Money.parseToMinor(input.receiveText) != inLeg.amountMinor) return true
    return false
}
