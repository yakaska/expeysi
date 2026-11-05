package com.expeysi.app.domain

data class TxDayAmount(val amountMinor: Long, val dayStartMillis: Long)

/** Суммы за скользящие окна: сегодня, последние 7 дней, последние 30 дней. */
data class WindowSums(val dayMinor: Long, val weekMinor: Long, val monthMinor: Long)

object Windows {

    private const val DAY_MILLIS = 86_400_000L

    fun sums(txs: List<TxDayAmount>, todayStartMillis: Long): WindowSums {
        val weekStart = todayStartMillis - 6 * DAY_MILLIS
        val monthStart = todayStartMillis - 29 * DAY_MILLIS
        return WindowSums(
            dayMinor = txs.filter { it.dayStartMillis == todayStartMillis }.sumOf { it.amountMinor },
            weekMinor = txs.filter { it.dayStartMillis >= weekStart }.sumOf { it.amountMinor },
            monthMinor = txs.filter { it.dayStartMillis >= monthStart }.sumOf { it.amountMinor },
        )
    }
}
