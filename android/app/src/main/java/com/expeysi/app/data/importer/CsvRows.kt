package com.expeysi.app.data.importer

/**
 * Строка CSV: значения ячеек и номер строки файла — для сообщений об ошибках.
 */
internal data class CsvRow(val values: List<String>, val line: Int)

/**
 * Разделитель опознаётся по заголовочной строке: где встречается чаще — тот и
 * разделитель. Запятые файлы пишут Excel; имена колонок не содержат ни запятых,
 * ни точек с запятой, поэтому подсчёт однозначен.
 */
internal fun detectCsvDelimiter(text: String): Char {
    val head = text.substringBefore('\n', "").ifEmpty { text }
    return if (head.count { it == ',' } > head.count { it == ';' }) ',' else ';'
}

/** Разбор CSV со стандартными кавычками: `""` — кавычка внутри поля. */
internal fun parseCsvRows(text: String, delimiter: Char): List<CsvRow> {
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
            !inQuotes && char == delimiter -> endField()
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
    if (inQuotes) throw BudgetImportException(BudgetImportError.UNCLOSED_QUOTE, line = rowLine)
    if (field.isNotEmpty() || row.isNotEmpty()) endRow()
    return rows
}
