package com.expeysi.app.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class CalculatorTest {

    private fun type(calc: Calculator, text: String) {
        text.forEach { ch ->
            when (ch) {
                '.' -> calc.inputDot()
                in '0'..'9' -> calc.inputDigit(ch)
                else -> calc.inputOp(ch)
            }
        }
    }

    @Test
    fun `сложение`() {
        val calc = Calculator()
        type(calc, "12+30")
        calc.equals()
        assertEquals("42", calc.display)
    }

    @Test
    fun `вычитание с отрицательным результатом`() {
        val calc = Calculator()
        type(calc, "5-8")
        calc.equals()
        assertEquals("-3", calc.display)
    }

    @Test
    fun `умножение с дробями`() {
        val calc = Calculator()
        type(calc, "2.5*4")
        calc.equals()
        assertEquals("10", calc.display)
    }

    @Test
    fun `деление округляется до четырёх знаков`() {
        val calc = Calculator()
        type(calc, "10/3")
        calc.equals()
        assertEquals("3.3333", calc.display)
    }

    @Test
    fun `операции идут слева направо без приоритета`() {
        val calc = Calculator()
        type(calc, "2+3*4")
        calc.equals()
        assertEquals("20", calc.display)
    }

    @Test
    fun `цепочка без равно показывает промежуточный итог`() {
        val calc = Calculator()
        type(calc, "100-40+")
        assertEquals("60", calc.display)
        type(calc, "10")
        calc.equals()
        assertEquals("70", calc.display)
    }

    @Test
    fun `точка не дублируется`() {
        val calc = Calculator()
        type(calc, "1.2.3")
        assertEquals("1.23", calc.display)
    }

    @Test
    fun `точка после операции начинает с нуля`() {
        val calc = Calculator()
        type(calc, "5+")
        calc.inputDot()
        assertEquals("0.", calc.display)
    }

    @Test
    fun `очистка сбрасывает всё`() {
        val calc = Calculator()
        type(calc, "12+5")
        calc.clear()
        assertEquals("0", calc.display)
        type(calc, "7")
        calc.equals()
        assertEquals("7", calc.display)
    }

    @Test
    fun `деление на ноль даёт ошибку и результат не отдаётся`() {
        val calc = Calculator()
        type(calc, "5/0")
        calc.equals()
        assertEquals(Calculator.ERROR, calc.display)
        assertNull(calc.resultText())
        calc.clear()
        assertEquals("0", calc.display)
        assertEquals("0", calc.resultText())
    }

    @Test
    fun `хвостовые нули результата срезаются`() {
        val calc = Calculator()
        type(calc, "0.5*2")
        calc.equals()
        assertEquals("1", calc.display)
        type(calc, "0.25*2")
        calc.equals()
        assertEquals("0.5", calc.display)
    }

    @Test
    fun `равно без операции ничего не меняет`() {
        val calc = Calculator()
        type(calc, "42")
        calc.equals()
        assertEquals("42", calc.display)
    }

    @Test
    fun `подставленное значение продолжает вычисления`() {
        val calc = Calculator()
        calc.setValue("1 234,56")
        assertEquals("1234.56", calc.display)
        calc.inputOp('+')
        calc.inputDigit('1')
        calc.equals()
        assertEquals("1235.56", calc.display)
    }

    @Test
    fun `подстановка мусора игнорируется`() {
        val calc = Calculator()
        calc.setValue("abc")
        assertEquals("0", calc.display)
        calc.setValue("")
        assertEquals("0", calc.display)
    }

    @Test
    fun `удаление последнего символа при вводе`() {
        val calc = Calculator()
        type(calc, "125")
        calc.backspace()
        assertEquals("12", calc.display)
    }

    @Test
    fun `удаление точки при вводе`() {
        val calc = Calculator()
        type(calc, "12.")
        calc.backspace()
        assertEquals("12", calc.display)
        calc.inputDot()
        assertEquals("12.", calc.display)
    }

    @Test
    fun `удаление до нуля и дальше`() {
        val calc = Calculator()
        type(calc, "5")
        calc.backspace()
        assertEquals("0", calc.display)
        calc.backspace()
        assertEquals("0", calc.display)
        type(calc, "7")
        assertEquals("7", calc.display)
    }

    @Test
    fun `удаление не действует после операции`() {
        val calc = Calculator()
        type(calc, "100+")
        assertEquals("100", calc.display)
        calc.backspace()
        assertEquals("100", calc.display)
        type(calc, "5")
        calc.backspace()
        assertEquals("0", calc.display)
    }

    @Test
    fun `удаление не действует после равно`() {
        val calc = Calculator()
        type(calc, "12+30")
        calc.equals()
        assertEquals("42", calc.display)
        calc.backspace()
        assertEquals("42", calc.display)
    }

    @Test
    fun `удаление не действует при ошибке`() {
        val calc = Calculator()
        type(calc, "5/0")
        calc.equals()
        assertEquals(Calculator.ERROR, calc.display)
        calc.backspace()
        assertEquals(Calculator.ERROR, calc.display)
    }

    @Test
    fun `предзаполненное значение можно править и считать дальше`() {
        val calc = Calculator()
        calc.setValue("125")
        calc.backspace()
        assertEquals("12", calc.display)
        calc.inputDigit('3')
        assertEquals("123", calc.display)
        calc.inputOp('+')
        calc.inputDigit('2')
        calc.equals()
        assertEquals("125", calc.display)
    }

    @Test
    fun `история накапливается по ходу вычисления`() {
        val calc = Calculator()
        type(calc, "100-40+")
        assertEquals("100 − 40 +", calc.expression)
        type(calc, "10")
        calc.equals()
        assertEquals("100 − 40 + 10 =", calc.expression)
        assertEquals("70", calc.display)
    }

    @Test
    fun `история пуста до первой операции`() {
        val calc = Calculator()
        type(calc, "125")
        assertEquals("", calc.expression)
    }

    @Test
    fun `история сбрасывается при вводе нового числа после равно`() {
        val calc = Calculator()
        type(calc, "12+30")
        calc.equals()
        assertEquals("12 + 30 =", calc.expression)
        type(calc, "5")
        assertEquals("", calc.expression)
    }

    @Test
    fun `повторная операция меняет знак в истории`() {
        val calc = Calculator()
        type(calc, "100+")
        assertEquals("100 +", calc.expression)
        calc.inputOp('*')
        assertEquals("100 ×", calc.expression)
    }

    @Test
    fun `операция после равно начинает новую строку`() {
        val calc = Calculator()
        type(calc, "12+30")
        calc.equals()
        assertEquals("12 + 30 =", calc.expression)
        calc.inputOp('-')
        assertEquals("42 −", calc.expression)
        type(calc, "8")
        calc.equals()
        assertEquals("42 − 8 =", calc.expression)
        assertEquals("34", calc.display)
    }

    @Test
    fun `история очищается сбросом и новой подстановкой`() {
        val calc = Calculator()
        type(calc, "5*")
        assertEquals("5 ×", calc.expression)
        calc.clear()
        assertEquals("", calc.expression)
        calc.setValue("7")
        calc.inputOp('+')
        assertEquals("7 +", calc.expression)
    }

    @Test
    fun `числа в истории группируются разрядами`() {
        val calc = Calculator()
        calc.setValue("1250.5")
        calc.inputOp('+')
        assertEquals("1 250,5 +", calc.expression)
    }

    @Test
    fun `история пуста при ошибке`() {
        val calc = Calculator()
        type(calc, "5/0")
        calc.equals()
        assertEquals(Calculator.ERROR, calc.display)
        assertEquals("", calc.expression)
    }

    @Test
    fun `группировка разрядов`() {
        assertEquals("1 234 567", Calculator.groupDigits("1234567"))
        assertEquals("1 234 567,89", Calculator.groupDigits("1234567.89"))
        assertEquals("10 000,", Calculator.groupDigits("10000."))
        assertEquals("999", Calculator.groupDigits("999"))
        assertEquals("-1 234", Calculator.groupDigits("-1234"))
        assertEquals("0", Calculator.groupDigits("0"))
        assertEquals(Calculator.ERROR, Calculator.groupDigits(Calculator.ERROR))
    }
}
