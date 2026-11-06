package com.expeysi.app.domain

import java.math.BigDecimal
import java.math.RoundingMode

/**
 * Простейший калькулятор: операции выполняются последовательно слева направо,
 * без приоритета умножения (как на карманном калькуляторе).
 * Результаты округляются до 2 знаков — сумма всё равно хранится в копейках.
 */
class Calculator {

    var display: String = "0"
        private set

    val isError: Boolean
        get() = display == ERROR

    private var accumulator: Double? = null
    private var pendingOp: Char? = null
    private var freshInput = true

    fun inputDigit(digit: Char) {
        require(digit in '0'..'9')
        if (isError) clear()
        if (freshInput) {
            display = digit.toString()
            freshInput = false
        } else if (display.replace(".", "").length < 15) {
            display = if (display == "0") digit.toString() else display + digit
        }
    }

    fun inputDot() {
        if (isError) clear()
        if (freshInput) {
            display = "0."
            freshInput = false
        } else if (!display.contains('.')) {
            display += "."
        }
    }

    fun inputOp(op: Char) {
        require(op in "+-*/")
        if (isError) return
        if (accumulator == null) {
            accumulator = display.toDoubleOrNull() ?: 0.0
        } else if (!freshInput) {
            if (!applyPending()) return
        }
        pendingOp = op
        freshInput = true
    }

    fun equals() {
        if (isError || pendingOp == null) return
        if (applyPending()) {
            pendingOp = null
            freshInput = true
        }
    }

    fun clear() {
        display = "0"
        accumulator = null
        pendingOp = null
        freshInput = true
    }

    /** Подставляет готовое значение (например, текущую сумму из поля ввода). Мусор игнорируется. */
    fun setValue(text: String) {
        val cleaned = text.replace(" ", "").replace(',', '.')
        val value = cleaned.toDoubleOrNull() ?: return
        clear()
        display = format(value)
    }

    /** Текст для подстановки в поле суммы; null, если на экране ошибка. */
    fun resultText(): String? = if (isError) null else display.trimEnd('.')

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

    private fun format(value: Double): String = BigDecimal(value).setScale(2, RoundingMode.HALF_UP).stripTrailingZeros().toPlainString()

    companion object {
        private const val ERROR = "Ошибка"

        fun groupDigits(raw: String): String = Money.formatInput(raw)
    }
}
