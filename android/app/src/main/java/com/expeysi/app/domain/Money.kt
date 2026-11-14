package com.expeysi.app.domain

import kotlin.math.abs

object Money {

    /** Масштаб хранения: столько минимальных единиц в одной основной единице валюты. */
    const val SCALE = 10_000L

    /** Знаков дробной части после запятой в основной единице: 1/10 000. */
    const val SCALE_DIGITS = 4

    /** Прежний масштаб хранения (сотые доли валюты) до перехода на 1/10 000. */
    const val LEGACY_SCALE = 100L

    /** Неразрывный пробел: группировка разрядов и связь числа с единицей порядка. */
    private const val NON_BREAKING_SPACE = "\u00A0"

    /** Порядок компактной записи: NONE — обычное число, MILLION/BILLION — сокращение. */
    enum class Scale(val majorUnits: Long) {
        NONE(1L),
        MILLION(1_000_000L),
        BILLION(1_000_000_000L),
    }

    /** Числовая часть и порядок компактной записи; суффикс порядка резолвится в UI-слое. */
    data class CompactAmount(val digits: String, val scale: Scale)

    /** "12,3456" или "12.34" → 123456. Мусор или больше четырёх знаков дробной части → null. */
    fun parseToMinor(text: String): Long? {
        val cleaned = compact(text).replace(',', '.')
        if (!Regex("""^-?\d+(\.\d{1,4})?$""").matches(cleaned)) return null
        val negative = cleaned.startsWith("-")
        val absText = cleaned.removePrefix("-")
        val dot = absText.indexOf('.')
        val major = if (dot == -1) absText else absText.substring(0, dot)
        val fraction = if (dot == -1) "0" else absText.substring(dot + 1)
        val minor = major.toLong() * SCALE + fraction.padEnd(SCALE_DIGITS, '0').toLong()
        return if (negative) -minor else minor
    }

    /**
     * Группирует разряды и заменяет десятичную точку запятой, не завершая ввод
     * за пользователя. Ведущие нули целой части срезаются: "05" → "5", поэтому
     * ввод поверх префила "0" заменяет его, а не слипается с ним.
     */
    fun formatInput(text: String): String {
        val normalized = compact(text).replace('.', ',')
        val separator = normalized.indexOf(',')
        if (separator != normalized.lastIndexOf(',')) return text

        val integer = if (separator == -1) normalized else normalized.substring(0, separator)
        val fraction = if (separator == -1) "" else normalized.substring(separator)
        val rawDigits = integer.removePrefix("-")
        if (rawDigits.any { !it.isDigit() }) return text

        val digits = if (rawDigits.isEmpty()) "" else rawDigits.trimStart('0').ifEmpty { "0" }
        val grouped = digits.reversed().chunked(3).joinToString(" ").reversed()
        val sign = if (integer.startsWith("-")) "-" else ""
        return sign + grouped + fraction
    }

    /**
     * Только значащие знаки: "12,34", не "12,3400"; "12,311", не "12,3110";
     * целая сумма — без запятой ("12"). Нули дробной части срезаны, поэтому
     * format(minor) == formatAuto(minor); оба оставлены для ясности вызовов.
     */
    fun format(minor: Long): String {
        val negative = minor < 0
        val absolute = abs(minor)
        val whole = grouped(absolute / SCALE)
        val fraction = (absolute % SCALE).toString().padStart(SCALE_DIGITS, '0').trimEnd('0')
        val sign = if (negative) "-" else ""
        return if (fraction.isEmpty()) "$sign$whole" else "$sign$whole,$fraction"
    }

    /** 123456 → "12": целые основные единицы без дробной части, группы разрядов — неразрывный пробел. */
    fun formatWhole(minor: Long): String {
        val major = minor / SCALE
        val sign = if (major < 0) "-" else ""
        return sign + grouped(abs(major), NON_BREAKING_SPACE)
    }

    /** Целая сумма — без дробной части ("58 000"), с дробной — только значащие знаки ("12,34"). */
    fun formatAuto(minor: Long): String = if (minor % SCALE == 0L) formatWhole(minor) else format(minor)

    /**
     * Компактный вид одной строкой: до миллиона основных единиц — целое число либо
     * дробная часть, если она есть ("1 234,5678"); от миллиона — один знак после
     * запятой и единица из ресурсов UI-слоя ("6,7 млн"; 999,95 млн → "1 млрд"
     * вместо "1000,0 млн"). Знак минус сохраняется.
     */
    fun formatCompact(minor: Long, millionSuffix: String, billionSuffix: String): String {
        val compact = compact(minor)
        return when (compact.scale) {
            Scale.NONE -> compact.digits
            Scale.MILLION -> compactScaledText(compact.digits, millionSuffix)
            Scale.BILLION -> compactScaledText(compact.digits, billionSuffix)
        }
    }

    private fun compactScaledText(digits: String, suffix: String): String {
        val normalized = digits.replace(" ", NON_BREAKING_SPACE)
        return normalized + NON_BREAKING_SPACE + suffix
    }

    /**
     * Компактный вид для сводок. Суммы от миллиона основных единиц — с одним знаком
     * после запятой ("6,7" + Scale.MILLION), ниже порога — целые числа без нулевой
     * дробной части ("1 234"), с дробью — только значащие знаки ("1 234,56").
     */
    fun compact(minor: Long): CompactAmount {
        val sign = if (minor < 0) "-" else ""
        val absolute = abs(minor)
        val major = absolute / SCALE
        val scale = when {
            major >= Scale.BILLION.majorUnits -> Scale.BILLION
            major >= Scale.MILLION.majorUnits -> Scale.MILLION
            else -> Scale.NONE
        }
        return when (scale) {
            Scale.NONE -> {
                val digits = if (minor % SCALE == 0L) grouped(major, NON_BREAKING_SPACE) else format(absolute)
                CompactAmount(sign + digits, Scale.NONE)
            }
            else -> compactScaled(sign, absolute, scale)
        }
    }

    /** Округлённый до одного знака minor / масштаб; при переходе через порядок масштаб повышается. */
    private tailrec fun compactScaled(sign: String, minor: Long, scale: Scale): CompactAmount {
        val unitMinor = scale.majorUnits * SCALE
        /*
         * (minor * 10 + unitMinor / 2) / unitMinor переполняет long на больших minor,
         * поэтому деление выполнено до умножения; minor >= 0, кроме Long.MIN_VALUE.
         */
        val tenths = (minor / unitMinor) * 10 + ((minor % unitMinor) * 10 + unitMinor / 2) / unitMinor
        return when {
            /* Округление дотянуло до следующего порядка: 999 999 990 → "1" (млрд). */
            scale == Scale.MILLION && tenths >= 10_000L -> compactScaled(sign, minor, Scale.BILLION)
            scale == Scale.BILLION && tenths >= 10_000L -> {
                CompactAmount(sign + format(minor), Scale.NONE)
            }
            else -> {
                val digits = grouped(tenths / 10)
                CompactAmount(sign + if (tenths % 10 == 0L) digits else "$digits,${tenths % 10}", scale)
            }
        }
    }

    private fun grouped(value: Long, separator: String = " "): String = value.toString().reversed().chunked(3).joinToString(separator).reversed()

    private fun compact(text: String): String = text.filterNot {
        it.isWhitespace() || Character.isSpaceChar(it)
    }
}
