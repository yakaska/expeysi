package com.expeysi.app.ui.debts

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.expeysi.app.R
import com.expeysi.app.domain.CounterpartyType
import com.expeysi.app.ui.common.SaveButton

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EditCounterpartyScreen(
    onClose: () -> Unit,
    viewModel: EditCounterpartyViewModel = hiltViewModel(),
) {
    val name by viewModel.name.collectAsStateWithLifecycle()
    val type by viewModel.type.collectAsStateWithLifecycle()
    val note by viewModel.note.collectAsStateWithLifecycle()
    val saved by viewModel.saved.collectAsStateWithLifecycle()

    LaunchedEffect(saved) {
        if (saved) onClose()
    }

    Scaffold(
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        stringResource(
                            if (viewModel.isEdit) R.string.counterparty_edit_title else R.string.counterparty_add_title,
                        ),
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onClose) {
                        Icon(
                            Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = stringResource(R.string.common_back),
                        )
                    }
                },
            )
        },
        bottomBar = {
            // Непрозрачная подложка: прокручиваемое содержимое не просвечивает под панелью.
            Surface(color = MaterialTheme.colorScheme.background) {
                SaveButton(
                    enabled = name.isNotBlank(),
                    onClick = { viewModel.save {} },
                    onDisabledClick = {},
                    modifier = Modifier
                        .navigationBarsPadding()
                        .imePadding()
                        .padding(horizontal = 16.dp, vertical = 12.dp),
                )
            }
        },
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            OutlinedTextField(
                value = name,
                onValueChange = viewModel::onNameChange,
                label = { Text(stringResource(R.string.common_name)) },
                supportingText = if (name.isBlank()) {
                    { Text(stringResource(R.string.common_name_error)) }
                } else {
                    null
                },
                colors = debtFieldColors(),
                modifier = Modifier.fillMaxWidth(),
            )
            SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
                CounterpartyType.entries.forEachIndexed { index, typeItem ->
                    SegmentedButton(
                        selected = type == typeItem,
                        onClick = { viewModel.onTypeChange(typeItem) },
                        shape = SegmentedButtonDefaults.itemShape(index = index, count = CounterpartyType.entries.size),
                    ) {
                        Text(
                            stringResource(
                                if (typeItem == CounterpartyType.PERSON) {
                                    R.string.counterparty_type_person
                                } else {
                                    R.string.counterparty_type_company
                                },
                            ),
                        )
                    }
                }
            }
            OutlinedTextField(
                value = note,
                onValueChange = viewModel::onNoteChange,
                label = { Text(stringResource(R.string.add_note_label)) },
                colors = debtFieldColors(),
                modifier = Modifier.fillMaxWidth(),
            )
        }
    }
}
