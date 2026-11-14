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
     * Достраивает страницу списка: нога, чей партнёр остался за границей страницы
     * (лимит обрезал пару посередине), запрашивает партнёра по связке через
     * [linkedLegs] — иначе на стыке страниц пара развалилась бы на две строки
     * со знаком. Партнёр берётся живым и в скоупе [scopeIds]: нога с партнёром
     * в другом скоупе остаётся одиночной со знаком. Страница пересортируется
     * порядком запроса (дата и версия по убыванию) — вставленный партнёр
     * занимает своё место, остальной порядок не меняется.
     */
    suspend fun supplementSplitLegs(
        page: List<TransactionEntity>,
        scopeIds: Set<String>,
        linkedLegs: suspend (String) -> List<TransactionEntity>,
    ): List<TransactionEntity> {
        if (page.isEmpty()) return page
        val partnerType = mapOf(
            TxType.TRANSFER_OUT to TxType.TRANSFER_IN,
            TxType.TRANSFER_IN to TxType.TRANSFER_OUT,
        )
        val onPageIds = page.mapTo(mutableSetOf()) { it.id }
        val linkedIds = page.asSequence()
            .map { it to TxType.fromWire(it.type) }
            .filter { (leg, type) -> leg.linkedId.isNotEmpty() && partnerType.containsKey(type) }
            .filter { (leg, type) ->
                page.none { peer ->
                    peer.id != leg.id &&
                        peer.linkedId == leg.linkedId &&
                        TxType.fromWire(peer.type) == partnerType[type]
                }
            }
            .map { (leg, _) -> leg.linkedId }
            .toSet()
        if (linkedIds.isEmpty()) return page
        val partners = linkedIds
            .flatMap { linkedLegs(it) }
            .filter { !it.deleted && it.id !in onPageIds && it.accountId in scopeIds }
            .filter { leg ->
                page.any { peer ->
                    peer.linkedId == leg.linkedId &&
                        TxType.fromWire(peer.type) == partnerType[TxType.fromWire(leg.type)]
                }
            }
            .distinctBy { it.id }
        if (partners.isEmpty()) return page
        return (page + partners).sortedWith(
            compareByDescending<TransactionEntity> { it.date }.thenByDescending { it.updatedAt },
        )
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
