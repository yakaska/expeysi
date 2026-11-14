package com.expeysi.app.domain

import java.math.BigDecimal
import java.math.RoundingMode

/**
 * Простейший калькулятор: операции выполняются последовательно слева направо,
 * без приоритета умножения (как на карманном калькуляторе).
 * Результаты округляются до четырёх знаков после запятой — столько же, сколько
 * точность ввода сумм. Хвостовые нули результата срезаются на показе.
 * [expression] хранит ход текущего вычисления («100 − 40 + 10 =») для показа в интерфейсе.
 */
class Calculator {

    var display: String = "0"
        private set

    /** Ход текущего вычисления; пусто, пока вычисление не начато. */
    val expression: String
        get() = tokens.joinToString(" ")

    val isError: Boolean
        get() = display == ERROR

    private var accumulator: Double? = null
    private var pendingOp: Char? = null
    private var freshInput = true
    private val tokens = mutableListOf<String>()

    fun inputDigit(digit: Char) {
        require(digit in '0'..'9')
        if (isError) clear()
        if (freshInput) {
            resetIfFinished()
            display = digit.toString()
            freshInput = false
        } else if (display.replace(".", "").length < 15) {
            display = if (display == "0") digit.toString() else display + digit
        }
    }

    fun inputDot() {
        if (isError) clear()
        if (freshInput) {
            resetIfFinished()
            display = "0."
            freshInput = false
        } else if (!display.contains('.')) {
            display += "."
        }
    }

    fun inputOp(op: Char) {
        require(op in "+-*/")
        if (isError) return
        val glyph = opGlyph(op)
        when {
            accumulator == null -> {
                accumulator = display.toDoubleOrNull() ?: 0.0
                tokens.clear()
                tokens += numberToken()
                tokens += glyph
            }
            !freshInput -> {
                val operand = numberToken()
                if (!applyPending()) return
                tokens += operand
                tokens += glyph
            }
            tokens.lastOrNull() == RESULT -> {
                tokens.clear()
                tokens += numberToken()
                tokens += glyph
            }
            tokens.lastOrNull() in OP_GLYPHS -> tokens[tokens.lastIndex] = glyph
            else -> {
                tokens += numberToken()
                tokens += glyph
            }
        }
        pendingOp = op
        freshInput = true
    }

    fun equals() {
        if (isError || pendingOp == null) return
        val operand = numberToken()
        if (applyPending()) {
            tokens += operand
            tokens += RESULT
            pendingOp = null
            freshInput = true
        }
    }

    /** Удаляет последний символ вводимого числа; после «равно» и операции, а также при ошибке не действует. */
    fun backspace() {
        if (isError || freshInput) return
        display = display.dropLast(1)
        if (display.isEmpty() || display == "-") display = "0"
    }

    fun clear() {
        display = "0"
        accumulator = null
        pendingOp = null
        freshInput = true
        tokens.clear()
    }

    /** Подставляет готовое значение (например, текущую сумму из поля ввода). Мусор игнорируется. */
    fun setValue(text: String) {
        val cleaned = text.replace(" ", "").replace(',', '.')
        val value = cleaned.toDoubleOrNull() ?: return
        clear()
        display = format(value)
        // Предзаполненное значение — обычное число: его можно править (backspace, дописывание).
        freshInput = false
    }

    /** Текст для подстановки в поле суммы; null, если на экране ошибка. */
    fun resultText(): String? = if (isError) null else display.trimEnd('.')

    /** Новое число после «равно» начинает новую цепочку: сбрасывает накопитель и историю. */
    private fun resetIfFinished() {
        if (tokens.lastOrNull() == RESULT) {
            tokens.clear()
            accumulator = null
            pendingOp = null
        }
    }

    private fun numberToken(): String = groupDigits(display)

    private fun applyPending(): Boolean {
        val left = accumulator ?: return false
        val right = display.toDoubleOrNull() ?: return false
        val result = when (pendingOp) {
            '+' -> left + right
            '-' -> left - right
            '*' -> left * right
            '/' -> {
                if (right == 0.0) {
                    clear()
                    display = ERROR
                    return false
                }
                left / right
            }
            else -> return false
        }
        accumulator = result
        display = format(result)
        return true
    }

    private fun format(value: Double): String = BigDecimal(value).setScale(4, RoundingMode.HALF_UP).stripTrailingZeros().toPlainString()

    companion object {
        /** Технический маркер ошибки в display; локализованный текст рисует UI из ресурсов (calc_error). */
        const val ERROR = "error"
        private const val RESULT = "="
        private val OP_GLYPHS = setOf("+", "−", "×", "÷")

        fun groupDigits(raw: String): String = Money.formatInput(raw)

        private fun opGlyph(op: Char): String = when (op) {
            '+' -> "+"
            '-' -> "−"
            '*' -> "×"
            else -> "÷"
        }
    }
}
