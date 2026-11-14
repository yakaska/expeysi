package com.expeysi.app.ui

import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.OutlinedTextField
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.TextFieldValue
import com.expeysi.app.domain.Money

@Composable
fun MoneyTextField(
    value: String,
    onValueChange: (String) -> Unit,
    label: @Composable () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    trailingIcon: @Composable (() -> Unit)? = null,
) {
    var fieldValue by remember {
        mutableStateOf(TextFieldValue(value, selection = TextRange(value.length)))
    }

    LaunchedEffect(value) {
        if (fieldValue.text != value) {
            fieldValue = TextFieldValue(value, selection = TextRange(value.length))
        }
    }

    OutlinedTextField(
        value = fieldValue,
        onValueChange = { newValue ->
            val formatted = Money.formatInput(newValue.text)

            fun mapPosition(position: Int): Int {
                var charactersLeft = newValue.text.take(position).count {
                    !it.isWhitespace() && !Character.isSpaceChar(it)
                }
                if (charactersLeft == 0) return 0
                formatted.forEachIndexed { index, character ->
                    if (character != ' ') {
                        charactersLeft--
                        if (charactersLeft == 0) return index + 1
                    }
                }
                return formatted.length
            }

            fieldValue = newValue.copy(
                text = formatted,
                selection = TextRange(
                    mapPosition(newValue.selection.start),
                    mapPosition(newValue.selection.end),
                ),
                composition = null,
            )
            onValueChange(formatted)
        },
        modifier = modifier,
        enabled = enabled,
        label = label,
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
        singleLine = true,
        trailingIcon = trailingIcon,
    )
}
