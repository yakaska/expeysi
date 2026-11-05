package com.expeysi.app.ui.family

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle

@Composable
fun FamilyScreen(viewModel: FamilyViewModel = hiltViewModel()) {
    val loading by viewModel.loading.collectAsStateWithLifecycle()
    val error by viewModel.error.collectAsStateWithLifecycle()
    var code by remember { mutableStateOf("") }

    Column(
        modifier = Modifier.fillMaxSize().padding(24.dp),
        verticalArrangement = Arrangement.Center,
    ) {
        Text("Общий учёт", style = MaterialTheme.typography.headlineMedium)
        Text(
            "Создайте семью на первом устройстве или введите код приглашения.",
            modifier = Modifier.padding(vertical = 16.dp),
        )
        Button(
            onClick = viewModel::createFamily,
            enabled = !loading,
            modifier = Modifier.fillMaxWidth(),
        ) {
            Text("Создать семью")
        }
        HorizontalDivider(modifier = Modifier.padding(vertical = 24.dp))
        OutlinedTextField(
            value = code,
            onValueChange = { code = it },
            label = { Text("Код семьи") },
            singleLine = true,
            enabled = !loading,
            modifier = Modifier.fillMaxWidth(),
        )
        OutlinedButton(
            onClick = { viewModel.joinFamily(code) },
            enabled = !loading && code.isNotBlank(),
            modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
        ) {
            Text("Присоединиться")
        }
        if (loading) CircularProgressIndicator(modifier = Modifier.padding(top = 16.dp))
        error?.let {
            Text(it, color = MaterialTheme.colorScheme.error, modifier = Modifier.padding(top = 16.dp))
        }
    }
}
