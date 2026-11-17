package com.expeysi.app.domain

/** Тип контрагента по долгам: физлицо или компания; хранится wire-строкой в БД. */
enum class CounterpartyType(val wire: String) {
    PERSON("person"),
    COMPANY("company"),
    ;

    companion object {
        fun fromWire(value: String): CounterpartyType = entries.firstOrNull { it.wire == value } ?: PERSON
    }
}
