package com.expeysi.app.domain

import kotlin.math.abs

object Money {

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

    /** "123,45" или "123.45" → 12345. Мусор → null. */
    fun parseToMinor(text: String): Long? {
        val cleaned = compact(text).replace(',', '.')
        if (!Regex("""^-?\d+(\.\d{1,2})?$""").matches(cleaned)) return null
        val negative = cleaned.startsWith("-")
        val absText = cleaned.removePrefix("-")
        val dot = absText.indexOf('.')
        val rubles = if (dot == -1) absText else absText.substring(0, dot)
        val kopecks = if (dot == -1) "0" else absText.substring(dot + 1)
        val minor = rubles.toLong() * 100 + kopecks.padEnd(2, '0').toLong()
        return if (negative) -minor else minor
    }

    /** Группирует разряды и заменяет десятичную точку запятой, не завершая ввод за пользователя. */
    fun formatInput(text: String): String {
        val normalized = compact(text).replace('.', ',')
        val separator = normalized.indexOf(',')
        if (separator != normalized.lastIndexOf(',')) return text

        val integer = if (separator == -1) normalized else normalized.substring(0, separator)
        val fraction = if (separator == -1) "" else normalized.substring(separator)
        val digits = integer.removePrefix("-")
        if (digits.any { !it.isDigit() }) return text

        val grouped = digits.reversed().chunked(3).joinToString(" ").reversed()
        val sign = if (integer.startsWith("-")) "-" else ""
        return sign + grouped + fraction
    }

    /** 123456 → "1 234,56". */
    fun format(minor: Long): String {
        val negative = minor < 0
        val absolute = abs(minor)
        val rubles = grouped(absolute / 100)
        val kopecks = (absolute % 100).toString().padStart(2, '0')
        val sign = if (negative) "-" else ""
        return "$sign$rubles,$kopecks"
    }

    /** 123456 → "1 234": целые основные единицы без копеек, группы разрядов — неразрывный пробел. */
    fun formatWhole(minor: Long): String {
        val major = minor / 100
        val sign = if (major < 0) "-" else ""
        return sign + grouped(abs(major), NON_BREAKING_SPACE)
    }

    /** Целая сумма — без копеек ("58 000"), с дробной частью — как в format() ("1 234,56"). */
    fun formatAuto(minor: Long): String = if (minor % 100 == 0L) formatWhole(minor) else format(minor)

    /**
     * Компактный вид одной строкой: до миллиона основных единиц — целое число либо
     * дробная часть, если она есть ("1 234,56"); от миллиона — один знак после
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
     * после запятой ("6,7" + Scale.MILLION), ниже порога — обычное число без нулевой
     * дробной части ("1 234"), с дробью — как в format().
     */
    fun compact(minor: Long): CompactAmount {
        val sign = if (minor < 0) "-" else ""
        val absolute = abs(minor)
        val major = absolute / 100
        val scale = when {
            major >= Scale.BILLION.majorUnits -> Scale.BILLION
            major >= Scale.MILLION.majorUnits -> Scale.MILLION
            else -> Scale.NONE
        }
        return when (scale) {
            Scale.NONE -> {
                val digits = if (minor % 100 == 0L) grouped(major, NON_BREAKING_SPACE) else format(absolute)
                CompactAmount(sign + digits, Scale.NONE)
            }
            else -> compactScaled(sign, absolute, scale)
        }
    }

    /** Округлённый до одного знака minor / масштаб; при переходе через порядок масштаб повышается. */
    private tailrec fun compactScaled(sign: String, minor: Long, scale: Scale): CompactAmount {
        val unitMinor = scale.majorUnits * 100
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
