package com.expeysi.app.ui.common

import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import com.expeysi.app.R
import com.expeysi.app.domain.TransferFees

/**
 * Отображаемое имя категории в разрезках (итоги, донаты, топ категорий):
 * псевдо-категория комиссий переводов подписывается своей строкой,
 * неименованное (удалённая категория, пустое имя) — «Без категории»,
 * остальные — своим именем.
 */
@Composable
fun categoryDisplayName(categoryId: String, name: String): String = when (categoryId) {
    TransferFees.CATEGORY_ID -> stringResource(R.string.transfer_fees_category)
    else -> name.ifBlank { stringResource(R.string.report_uncategorized) }
}
