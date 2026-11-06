package com.expeysi.app.data.importer

import com.expeysi.app.domain.TxType
import java.io.InputStream
import java.math.BigDecimal
import java.math.RoundingMode
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.ZoneId
import java.time.format.DateTimeParseException
import javax.inject.Inject

data class BudgetTransaction(
    val accountName: String,
    val categoryName: String,
    val type: TxType,
    val amountMinor: Long,
    val currencyCode: String,
    val date: Long,
    val note: String,
)

class BudgetCsvParser @Inject constructor() {
    private val zone: ZoneId = ZoneId.systemDefault()

    fun parse(input: InputStream): List<BudgetTransaction> {
        val text = input.bufferedReader(Charsets.UTF_8).use { it.readText() }.removePrefix("\uFEFF")
        val rows = parseRows(text).filter { row -> row.values.any { it.isNotBlank() } }
        if (rows.isEmpty()) throw BudgetImportException("Файл пуст")

        val header = rows.first().values.mapIndexed { index, name -> name.trim() to index }.toMap()
        val columns = REQUIRED_COLUMNS.mapValues { (_, name) ->
            header[name] ?: throw BudgetImportException("В файле нет колонки «$name»")
        }
        val lastColumn = columns.values.max()

        return rows.drop(1).map { row ->
            if (row.values.size <= lastColumn) {
                throw BudgetImportException("Строка ${row.line}: недостаточно колонок")
            }
            parseTransaction(row.values, columns, row.line)
        }.also {
            if (it.isEmpty()) throw BudgetImportException("В файле нет операций")
        }
    }

    private fun parseTransaction(
        values: List<String>,
        columns: Map<String, Int>,
        line: Int,
    ): BudgetTransaction {
        fun value(column: String) = values[columns.getValue(column)].trim()

        val accountName = value("account")
        val categoryName = value("category")
        val currencyCode = value("currency").uppercase()
        if (accountName.isEmpty()) throw BudgetImportException("Строка $line: пустое название счёта")
        if (categoryName.isEmpty()) throw BudgetImportException("Строка $line: пустая категория")
        if (!CURRENCY_REGEX.matches(currencyCode)) {
            throw BudgetImportException("Строка $line: некорректная валюта")
        }

        val amount = try {
            BigDecimal(value("amount"))
        } catch (_: NumberFormatException) {
            throw BudgetImportException("Строка $line: некорректная сумма")
        }
        if (amount.signum() == 0) throw BudgetImportException("Строка $line: нулевая сумма")
        val amountMinor = try {
            amount.abs().movePointRight(2).setScale(0, RoundingMode.UNNECESSARY).longValueExact()
        } catch (_: ArithmeticException) {
            throw BudgetImportException("Строка $line: сумма слишком большая или имеет больше двух знаков")
        }

        val date = try {
            LocalDateTime.of(
                LocalDate.parse(value("date")),
                LocalTime.parse(value("time")),
            ).atZone(zone).toInstant().toEpochMilli()
        } catch (_: DateTimeParseException) {
            throw BudgetImportException("Строка $line: некорректные дата или время")
        }

        return BudgetTransaction(
            accountName = accountName,
            categoryName = categoryName,
            type = if (amount.signum() < 0) TxType.EXPENSE else TxType.INCOME,
            amountMinor = amountMinor,
            currencyCode = currencyCode,
            date = date,
            note = value("note"),
        )
    }

    private fun parseRows(text: String): List<CsvRow> {
        val rows = mutableListOf<CsvRow>()
        var row = mutableListOf<String>()
        var field = StringBuilder()
        var line = 1
        var rowLine = 1
        var inQuotes = false
        var index = 0

        fun endField() {
            row.add(field.toString())
            field = StringBuilder()
        }

        fun endRow() {
            endField()
            rows.add(CsvRow(row, rowLine))
            row = mutableListOf()
            rowLine = line + 1
        }

        while (index < text.length) {
            val char = text[index]
            when {
                inQuotes && char == '"' && text.getOrNull(index + 1) == '"' -> {
                    field.append('"')
                    index++
                }
                char == '"' -> inQuotes = !inQuotes
                !inQuotes && char == ';' -> endField()
                !inQuotes && (char == '\n' || char == '\r') -> {
                    endRow()
                    if (char == '\r' && text.getOrNull(index + 1) == '\n') index++
                    line++
                    rowLine = line
                }
                else -> field.append(char)
            }
            index++
        }
        if (inQuotes) throw BudgetImportException("Строка $rowLine: незакрытая кавычка")
        if (field.isNotEmpty() || row.isNotEmpty()) endRow()
        return rows
    }

    private data class CsvRow(val values: List<String>, val line: Int)

    private companion object {
        val CURRENCY_REGEX = Regex("[A-Z]{3}")
        val REQUIRED_COLUMNS = mapOf(
            "date" to "Дата",
            "time" to "Время",
            "account" to "Название счета",
            "category" to "Категория",
            "amount" to "Сумма",
            "currency" to "Валюта",
            "note" to "Комментарий",
        )
    }
}

class BudgetImportException(message: String) : Exception(message)
