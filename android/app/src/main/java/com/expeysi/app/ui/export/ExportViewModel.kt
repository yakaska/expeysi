package com.expeysi.app.ui.export

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.expeysi.app.R
import com.expeysi.app.data.csv.CsvExportService
import com.expeysi.app.domain.TxType
import com.expeysi.app.ui.common.UiText
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.OutputStream
import javax.inject.Inject

/**
 * Состояние экспорта: [includeIncome]/[includeExpense] — типы операций,
 * [message] — разовый текст для тоста, null — показан.
 */
data class ExportUiState(
    val includeIncome: Boolean = true,
    val includeExpense: Boolean = true,
    val inProgress: Boolean = false,
    val message: UiText? = null,
)

@HiltViewModel
class ExportViewModel @Inject constructor(
    private val csvExportService: CsvExportService,
) : ViewModel() {

    private val _state = MutableStateFlow(ExportUiState())
    val state: StateFlow<ExportUiState> = _state.asStateFlow()

    /** Включение и выключение доходов и расходов в выгрузке. */
    fun onIncomeChange(include: Boolean) = _state.update { it.copy(includeIncome = include) }

    fun onExpenseChange(include: Boolean) = _state.update { it.copy(includeExpense = include) }

    /** Экспорт в файл [output]; null — файл открыть не удалось. */
    fun exportTo(output: OutputStream?) {
        if (_state.value.inProgress) return
        if (output == null) {
            _state.update { it.copy(message = UiText.Res(R.string.import_error_open)) }
            return
        }
        val types = buildSet {
            if (_state.value.includeIncome) add(TxType.INCOME)
            if (_state.value.includeExpense) add(TxType.EXPENSE)
        }
        if (types.isEmpty()) return
        viewModelScope.launch {
            _state.update { it.copy(inProgress = true, message = null) }
            val message = try {
                val result = withContext(Dispatchers.IO) { csvExportService.write(output, types) }
                UiText.Res(R.string.export_done, result.transactionCount)
            } catch (e: Exception) {
                UiText.Res(R.string.export_error_generic)
            } finally {
                _state.update { it.copy(inProgress = false) }
            }
            _state.update { it.copy(message = message) }
        }
    }

    fun consumeMessage() {
        _state.update { it.copy(message = null) }
    }
}
