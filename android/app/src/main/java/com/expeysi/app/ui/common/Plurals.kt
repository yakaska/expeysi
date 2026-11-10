package com.expeysi.app.ui.common

import androidx.compose.runtime.Composable
import androidx.compose.ui.res.pluralStringResource
import com.expeysi.app.R

/** Склонения подписей отчётов: «1 операция», «2 операции», «5 операций». */
@Composable
fun operationsLabel(count: Int): String = pluralStringResource(R.plurals.common_operations, count, count)

/** «1 категории», «2 категории», «5 категорий» — для ссылки раскрытия списка. */
@Composable
fun categoriesLabel(count: Int): String = pluralStringResource(R.plurals.common_categories, count, count)

/** «0 рублей», «1 рубль», «178 449 рублей» — для описания доступности. */
@Composable
fun rublesLabel(count: Long): String = pluralStringResource(R.plurals.common_rubles, count.toInt(), count)
