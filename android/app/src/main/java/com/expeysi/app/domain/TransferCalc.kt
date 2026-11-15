package com.expeysi.app.domain

import java.math.BigDecimal
import java.math.MathContext
import java.math.RoundingMode

/**
 * Чистая арифметика перевода между счетами разных валют. Курс вводится вручную
 * и сети не загружается; пересчёт всегда идёт от поля, которое правил
 * пользователь, поэтому класс получает готовые тексты и отвечает только за
 * математику, округление и разворот пары — за состояние полей отвечает ViewModel.
 *
 * Суммы — тексты в формате ввода [Money] (группы разрядов, разделитель — точка
 * или запятая); курс хранится и считается в полной точности BigDecimal
 * ([MathContext.DECIMAL128]), округляется только показ ([formatRateDisplay],
 * до [RATE_DISPLAY_DIGITS] значащих цифр) и перевод в минимальные единицы.
 */
object TransferCalc {

    /** Знаков в дробной части курса для текстовых полей: "72,5", 1/3 — "0,333333". */
    const val RATE_SCALE = 6

    /** Значащих цифр в показе курса: 1 USD = 25 000 VND, 1 USD = 0,0000395 BTC-подобного. */
    const val RATE_DISPLAY_DIGITS = 6

    /** У всех валют MVP два знака в минимальных единицах. */
    private const val MINOR_SCALE = 2

    /**
     * Десятичное число из текста ввода: "72,5" и "72.5" → 72.5. Мусор, пустое
     * и отрицательное — null: поле ввода в этом случае не пересчитывается.
     */
    fun parse(text: String): BigDecimal? {
        val cleaned = text.filterNot { it.isWhitespace() || Character.isSpaceChar(it) }.replace(',', '.')
        if (!Regex("""^\d+(\.\d+)?$""").matches(cleaned)) return null
        return BigDecimal(cleaned)
    }

    /**
     * Зачисление = списание × курс, округлённое до минимальных единиц.
     * Возвращает текст поля или null, когда списание либо курс не годятся
     * (пустые, мусор или неположительные), — зачисление тогда не трогается.
     */
    fun receiveText(sendText: String, rateText: String): String? {
        val send = Money.parseToMinor(sendText) ?: return null
        if (send <= 0) return null
        val rate = parse(rateText) ?: return null
        if (rate.signum() <= 0) return null
        val receive = minorOf(BigDecimal.valueOf(send, MINOR_SCALE).multiply(rate)) ?: return null
        return Money.formatInput(Money.formatAuto(receive))
    }

    /**
     * Курс = зачисление / списание, шесть знаков, HALF_UP. Null, когда
     * списание неположительное или зачисление мусорное.
     */
    fun rateText(sendText: String, receiveText: String): String? {
        val send = Money.parseToMinor(sendText) ?: return null
        if (send <= 0) return null
        val receive = Money.parseToMinor(receiveText) ?: return null
        if (receive < 0) return null
        return formatRate(
            BigDecimal.valueOf(receive, MINOR_SCALE)
                .divide(BigDecimal.valueOf(send, MINOR_SCALE), RATE_SCALE, RoundingMode.HALF_UP),
        )
    }

    /**
     * Точный курс «куда/откуда» по суммам ног: число значащих цифр ограничено
     * только [MathContext.DECIMAL128], пересчёт туда-обратно сумму не меняет.
     * Null, когда списание или зачисление непригодны (нулевые или пустые).
     */
    fun exactRate(sendMinor: Long, receiveMinor: Long): BigDecimal? {
        if (sendMinor <= 0 || receiveMinor < 0) return null
        return BigDecimal.valueOf(receiveMinor, MINOR_SCALE)
            .divide(BigDecimal.valueOf(sendMinor, MINOR_SCALE), MathContext.DECIMAL128)
    }

    /**
     * Зачисление = списание × курс без промежуточных округлений курса:
     * округляется только перевод в минимальные единицы (HALF_UP).
     * Null, когда списание или курс непригодны, или переполнение Long.
     */
    fun receiveMinor(sendMinor: Long, rate: BigDecimal): Long? {
        if (sendMinor <= 0 || rate.signum() <= 0) return null
        return minorOf(BigDecimal.valueOf(sendMinor, MINOR_SCALE).multiply(rate))
    }

    /** Обратный курс 1/rate в полной точности. Null, когда курс неположительный. */
    fun invertRate(rate: BigDecimal): BigDecimal? {
        if (rate.signum() <= 0) return null
        return BigDecimal.ONE.divide(rate, MathContext.DECIMAL128)
    }

    /**
     * Курс для показа рядом с полями («1 USD = 25 000 VND»): до
     * [RATE_DISPLAY_DIGITS] значащих цифр, HALF_UP, группы разрядов, запятая.
     * Расчёты этот формат не используют.
     */
    fun formatRateDisplay(rate: BigDecimal): String {
        if (rate.signum() <= 0) return "0"
        val rounded = rate.round(MathContext(RATE_DISPLAY_DIGITS, RoundingMode.HALF_UP)).stripTrailingZeros()
        return formattedPlain(rounded.toPlainString())
    }

    /**
     * Обратный курс для разворота перевода: 1 / rate. Null, когда курс
     * неположительный. Обратный курс с округлением может отличаться от
     * исходного в последнем знаке — это ожидаемо.
     */
    fun invertedRateText(rateText: String): String? {
        val rate = parse(rateText) ?: return null
        if (rate.signum() <= 0) return null
        return formatRate(BigDecimal.ONE.divide(rate, RATE_SCALE, RoundingMode.HALF_UP))
    }

    /** Разворот перевода: списание и зачисление меняются местами, курс инвертируется. */
    data class Swapped(val sendText: String, val receiveText: String, val rateText: String?)

    fun swap(sendText: String, receiveText: String, rateText: String): Swapped = Swapped(
        sendText = receiveText,
        receiveText = sendText,
        rateText = invertedRateText(rateText),
    )

    /**
     * Готовность перевода к сохранению: счета выбраны и различаются, списание
     * положительное, комиссия пустая или неотрицательная; для разных валют
     * сверх того курс и зачисление положительные. В одной валюте курс не
     * показывается, и зачисление на готовность не влияет.
     */
    fun canSave(
        fromId: String?,
        toId: String?,
        sameCurrency: Boolean,
        sendText: String,
        receiveText: String,
        rateText: String,
        feeText: String,
    ): Boolean {
        if (fromId == null || toId == null || fromId == toId) return false
        val send = Money.parseToMinor(sendText) ?: return false
        if (send <= 0) return false
        // Комиссия необязательна: пустая строка — ноль, остальное — сумма >= 0.
        if (feeText.isNotEmpty() && (Money.parseToMinor(feeText) ?: return false) < 0) return false
        if (sameCurrency) return true
        val rate = parse(rateText) ?: return false
        if (rate.signum() <= 0) return false
        val receive = Money.parseToMinor(receiveText) ?: return false
        return receive > 0
    }

    /** Курс для поля ввода: шесть знаков, нулевые срезаны, группы разрядов, запятая. */
    private fun formatRate(rate: BigDecimal): String {
        val plain = rate.setScale(RATE_SCALE, RoundingMode.HALF_UP).stripTrailingZeros().toPlainString()
        return formattedPlain(plain)
    }

    /** "1234.5" → "1 234,5": группы разрядов целой части, десятичная запятая. */
    private fun formattedPlain(plain: String): String {
        val dot = plain.indexOf('.')
        val integer = if (dot == -1) plain else plain.substring(0, dot)
        val fraction = if (dot == -1) "" else plain.substring(dot + 1)
        return integer.reversed().chunked(3).joinToString(" ").reversed() + if (fraction.isEmpty()) "" else ",$fraction"
    }

    /** BigDecimal → минимальные единицы (сотые), HALF_UP; переполнение Long — null. */
    private fun minorOf(value: BigDecimal): Long? = runCatching {
        value.movePointRight(MINOR_SCALE).setScale(0, RoundingMode.HALF_UP).longValueExact()
    }.getOrNull()
}
