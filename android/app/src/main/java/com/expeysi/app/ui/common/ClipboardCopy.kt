package com.expeysi.app.ui.common

import android.content.ClipData
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.platform.ClipEntry
import androidx.compose.ui.platform.LocalClipboard
import kotlinx.coroutines.launch

/** Копирование в буфер обмена: (метка, текст). Метку видно в менеджерах буфера. */
@Composable
fun rememberClipboardCopy(): (String, String) -> Unit {
    val clipboard = LocalClipboard.current
    val scope = rememberCoroutineScope()
    return remember(clipboard, scope) {
        { label: String, text: String ->
            scope.launch { clipboard.setClipEntry(ClipEntry(ClipData.newPlainText(label, text))) }
        }
    }
}
