package com.expeysi.app.data.csv

/**
 * Контракт CSV-формата: файл экспорта приложения и файлы импорта из «Бюджета»
 * делят один заголовок и служебную категорию начального остатка.
 */
object CsvFormat {

    /** Заголовок файла: тот же, что требует [com.expeysi.app.data.importer.BudgetCsvParser]. */
    const val HEADER = "Дата;Время;Название счета;Категория;Сумма;Валюта;Комментарий"

    /**
     * Имена опциональных колонок после «Комментарий»: вид экспорта и импорта
     * приложения. В файлах внешнего «Бюджета» их нет, парсер читает их,
     * только если они заявлены в заголовке.
     */
    const val CATEGORY_ICON_COLUMN = "Иконка категории"
    const val CATEGORY_COLOR_COLUMN = "Цвет категории"
    const val ACCOUNT_ICON_COLUMN = "Иконка счета"
    const val ACCOUNT_COLOR_COLUMN = "Цвет счета"
    const val BALANCE_NAME_COLUMN = "Название баланса"
    const val BALANCE_ICON_COLUMN = "Иконка баланса"
    const val BALANCE_COLOR_COLUMN = "Цвет баланса"

    /** Полный заголовок файла экспорта Expeysi: обязательные колонки плюс оформление. */
    const val EXTENDED_HEADER =
        "$HEADER;$CATEGORY_ICON_COLUMN;$CATEGORY_COLOR_COLUMN;$ACCOUNT_ICON_COLUMN;" +
            "$ACCOUNT_COLOR_COLUMN;$BALANCE_NAME_COLUMN;$BALANCE_ICON_COLUMN;$BALANCE_COLOR_COLUMN"

    /**
     * Зарезервированное имя категории служебной строки начального остатка.
     * Экспорт пишет такими строками начальные остатки счетов, импорт
     * распознаёт их и остаток ставит счёту вместо создания операции.
     * Имя запрещено для пользовательских категорий, иначе их операции
     * при переимпорте превратились бы в остатки.
     */
    const val OPENING_ROW_CATEGORY = "Начальный остаток"

    /** Имя категории для операции со «сгоревшим» id категории (в норме не случается). */
    const val FALLBACK_CATEGORY = "Без категории"

    /** Служебная ли это строка: точное совпадение с зарезервированным именем. */
    fun isOpeningRowCategory(name: String): Boolean = name.trim() == OPENING_ROW_CATEGORY
}
