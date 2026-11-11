package com.expeysi.app.domain

/** Доли суммы по элементам списка; отрицательная или нулевая сумма даёт нулевые доли. */
object CategoryShares {

    /** Доля каждого элемента = amount / сумма списка; в сумме доли дают 1. */
    fun of(amountsMinor: List<Long>): List<Float> {
        val total = amountsMinor.sumOf { it }
        if (total <= 0L) return amountsMinor.map { 0f }
        return amountsMinor.map { it.toFloat() / total }
    }
}
