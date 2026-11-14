package com.expeysi.app.domain

import com.expeysi.app.data.db.TransactionEntity

/**
 * Представление переводов в списках: пара ног одного перевода, обе попавшие в
 * один скоуп, показывается одной строкой за ногой-списанием. Ноги одного
 * перевода в разных скоупах (разные балансы, история одного счёта) остаются
 * каждая сама по себе.
 */
object Transfers {

    /**
     * Убирает из видимого списка ногу зачисления, если её нога-списание тоже
     * видна: строку рисуют по списанию, зачисление узнаётся по [TransactionEntity.linkedId].
     * Ноги без связки (legacy-данные) не сливаются.
     */
    fun collapse(transactions: List<TransactionEntity>): List<TransactionEntity> {
        val visibleOutLinkedIds = transactions
            .asSequence()
            .filter { TxType.fromWire(it.type) == TxType.TRANSFER_OUT }
            .map { it.linkedId }
            .filter { it.isNotEmpty() }
            .toSet()
        return transactions.filterNot { tx ->
            TxType.fromWire(tx.type) == TxType.TRANSFER_IN && tx.linkedId in visibleOutLinkedIds
        }
    }

    /**
     * Свёрнута ли строка в пару: нога списания, чья нога зачисления того же
     * перевода тоже была в списке [transactions] до свёртки. Такую пару рисуют
     * нейтрально, а одиночные ноги (партнёр в другом скоупе) — со знаком и
     * раскраской дохода или расхода.
     */
    fun isCollapsedPair(item: TransactionEntity, transactions: List<TransactionEntity>): Boolean {
        if (item.linkedId.isEmpty() || TxType.fromWire(item.type) != TxType.TRANSFER_OUT) return false
        return transactions.any {
            TxType.fromWire(it.type) == TxType.TRANSFER_IN && it.linkedId == item.linkedId
        }
    }

    /**
     * Подпись направления «откуда → куда»: для ноги-списания это сам счёт и
     * контрагент, для одинокой ноги зачисления — наоборот. Пустые имена
     * (контрагент ещё не доехал по синку) отбрасываются.
     */
    fun directionLabel(item: TransactionEntity, accountName: String, counterpartName: String): String {
        val (from, to) = when (TxType.fromWire(item.type)) {
            TxType.TRANSFER_OUT -> accountName to counterpartName
            else -> counterpartName to accountName
        }
        return listOf(from, to).filter { it.isNotBlank() }.joinToString(" → ")
    }
}
