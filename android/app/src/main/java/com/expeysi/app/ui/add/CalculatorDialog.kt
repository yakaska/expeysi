package com.expeysi.app.ui.add

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.DialogProperties
import com.expeysi.app.domain.Calculator

@Composable
fun CalculatorDialog(
    initialValue: String,
    onDismiss: () -> Unit,
    onApply: (String) -> Unit,
) {
    val calculator = remember { Calculator().apply { setValue(initialValue) } }
    var display by remember { mutableStateOf(calculator.display) }

    fun update(action: Calculator.() -> Unit) {
        calculator.action()
        display = calculator.display
    }

    AlertDialog(
        onDismissRequest = {},
        properties = DialogProperties(
            dismissOnBackPress = false,
            dismissOnClickOutside = false,
        ),
        title = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("Калькулятор", modifier = Modifier.weight(1f))
                IconButton(onClick = onDismiss) {
                    Icon(Icons.Filled.Close, contentDescription = "Закрыть")
                }
            }
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    Calculator.groupDigits(display),
                    style = MaterialTheme.typography.headlineMedium,
                    textAlign = TextAlign.End,
                    maxLines = 1,
                    color = if (calculator.isError) {
                        MaterialTheme.colorScheme.error
                    } else {
                        MaterialTheme.colorScheme.onSurface
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 8.dp),
                )
                val rows = listOf(
                    listOf("7", "8", "9", "÷"),
                    listOf("4", "5", "6", "×"),
                    listOf("1", "2", "3", "−"),
                    listOf(",", "0", "C", "+"),
                )
                rows.forEach { row ->
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        row.forEach { key ->
                            val isOp = key in listOf("÷", "×", "−", "+")
                            val content: @Composable () -> Unit = {
                                Text(key, style = MaterialTheme.typography.titleLarge)
                            }
                            val onClick: () -> Unit = {
                                when (key) {
                                    "," -> update { inputDot() }
                                    "C" -> update { clear() }
                                    "÷" -> update { inputOp('/') }
                                    "×" -> update { inputOp('*') }
                                    "−" -> update { inputOp('-') }
                                    "+" -> update { inputOp('+') }
                                    else -> update { inputDigit(key[0]) }
                                }
                            }
                            val modifier = Modifier.weight(1f)
                            if (isOp) {
                                FilledTonalButton(onClick = onClick, modifier = modifier) { content() }
                            } else {
                                OutlinedButton(onClick = onClick, modifier = modifier) { content() }
                            }
                        }
                    }
                }
                Button(
                    onClick = { update { equals() } },
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text("=", style = MaterialTheme.typography.titleLarge)
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = {
                    calculator.resultText()?.let(onApply)
                },
                enabled = !calculator.isError,
            ) { Text("Готово") }
        },
    )
}
