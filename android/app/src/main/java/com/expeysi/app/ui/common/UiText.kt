package com.expeysi.app.ui.common

import android.content.Context
import androidx.annotation.StringRes
import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource

/**
 * Текст для показа пользователю без зависимости от Context в ViewModel:
 * [Res] — строка из ресурсов (аргументы могут быть в том числе UiText),
 * [Plain] — «сырые» данные (имена из БД, сообщения платформы).
 * ViewModel отдаёт UiText, UI резолвит строку.
 */
sealed interface UiText {
    data class Res(@param:StringRes val id: Int, val args: List<Any> = emptyList()) : UiText {
        constructor(id: Int, vararg args: Any) : this(id, args.toList())
    }

    data class Plain(val value: String) : UiText
}

fun UiText.asString(context: Context): String = when (this) {
    is UiText.Res -> context.getString(id, *args.map { if (it is UiText) it.asString(context) else it }.toTypedArray())
    is UiText.Plain -> value
}

@Composable
fun UiText.asString(): String = when (this) {
    is UiText.Res -> stringResource(id, *args.map { if (it is UiText) it.asString() else it }.toTypedArray())
    is UiText.Plain -> value
}
