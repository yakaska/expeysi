package com.expeysi.app.ui.export

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.expeysi.app.R
import com.expeysi.app.data.csv.CsvExportService
import com.expeysi.app.data.db.AccountDao
import com.expeysi.app.data.db.AccountEntity
import com.expeysi.app.data.db.TransactionDao
import com.expeysi.app.domain.TxType
import com.expeysi.app.ui.common.UiText
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.OutputStream
import java.time.LocalDate
import java.time.ZoneId
import javax.inject.Inject

/** Счёт для фильтра «Дополнительно»: отображается по имени. */
data class AccountOption(
    val id: String,
    val name: String,
)

/**
 * Состояние экспорта: [includeIncome]/[includeExpense]/[includeTransfers] — типы
 * операций, [incomeCount]/[expenseCount]/[transferCount] — числа операций с учётом
 * фильтров [dateFrom]/[dateTo] и [selectedAccountIds] (список [accounts] показывается
 * в «Дополнительно»), [delimiter] и [writeBom] — оформление CSV-файла,
 * [message] — разовый текст для снекбара (null — показан), [exportSucceeded] —
 * успех последнего экспорта.
 */
data class ExportUiState(
    val includeIncome: Boolean = true,
    val includeExpense: Boolean = true,
    val includeTransfers: Boolean = true,
    val inProgress: Boolean = false,
    val message: UiText? = null,
    val exportSucceeded: Boolean = false,
    val incomeCount: Int = 0,
    val expenseCount: Int = 0,
    val transferCount: Int = 0,
    val accounts: List<AccountOption> = emptyList(),
    val selectedAccountIds: Set<String> = emptySet(),
    val accountSelectionInitialized: Boolean = false,
    val dateFrom: LocalDate? = null,
    val dateTo: LocalDate? = null,
    val delimiter: Char = ';',
    val writeBom: Boolean = true,
)

@HiltViewModel
class ExportViewModel @Inject constructor(
    private val csvExportService: CsvExportService,
    private val accountDao: AccountDao,
    private val transactionDao: TransactionDao,
) : ViewModel() {

    private val _state = MutableStateFlow(ExportUiState())
    val state: StateFlow<ExportUiState> = _state.asStateFlow()

    // Фильтры дат задаются локальной датой телефона, операции хранят epoch millis.
    private val zone: ZoneId = ZoneId.systemDefault()

    /** Фильтр выгрузки, влияющий и на числа в подписях, и на содержимое файла. */
    private data class ExportFilter(
        val fromMillis: Long?,
        val toExclusiveMillis: Long?,
        /** null — фильтр счетов не задан, выгружаются все. */
        val accountIds: Set<String>?,
    )

    private val filters = _state
        .map {
            ExportFilter(
                fromMillis = fromMillis(it.dateFrom),
                toExclusiveMillis = toExclusiveMillis(it.dateTo),
                accountIds = it.selectedAccountIds.takeIf { _ -> it.accountSelectionInitialized },
            )
        }
        .distinctUntilChanged()

    init {
        // Список счетов для фильтра: первый непустой список выделяется целиком.
        // Счета, добавленные после инициализации, остаются невыбранными — явный
        // выбор пользователя не переписывается.
        viewModelScope.launch {
            accountDao.observeActive().collect { accounts ->
                _state.update { current ->
                    current.copy(
                        accounts = accounts.map { AccountOption(id = it.id, name = it.name) },
                        selectedAccountIds = if (current.accountSelectionInitialized) {
                            current.selectedAccountIds
                        } else {
                            accounts.map(AccountEntity::id).toSet()
                        },
                        accountSelectionInitialized = current.accountSelectionInitialized || accounts.isNotEmpty(),
                    )
                }
            }
        }
        // Числа операций по типам с учётом фильтров дат и счетов.
        viewModelScope.launch {
            combine(transactionDao.observeActive(), filters) { transactions, filter ->
                filter to transactions
            }.collect { (filter, transactions) ->
                val visible = transactions.filter { tx ->
                    (filter.accountIds == null || tx.accountId in filter.accountIds) &&
                        (filter.fromMillis == null || tx.date >= filter.fromMillis) &&
                        (filter.toExclusiveMillis == null || tx.date < filter.toExclusiveMillis)
                }
                _state.update {
                    it.copy(
                        incomeCount = visible.count { tx -> TxType.fromWire(tx.type) == TxType.INCOME },
                        expenseCount = visible.count { tx -> TxType.fromWire(tx.type) == TxType.EXPENSE },
                        transferCount = visible.count { tx -> TxType.fromWire(tx.type).isTransfer },
                    )
                }
            }
        }
    }

    /** Включение и выключение доходов и расходов в выгрузке. */
    fun onIncomeChange(include: Boolean) = _state.update { it.copy(includeIncome = include) }

    fun onExpenseChange(include: Boolean) = _state.update { it.copy(includeExpense = include) }

    fun onTransfersChange(include: Boolean) = _state.update { it.copy(includeTransfers = include) }

    /** Выбор счетов в «Дополнительно». */
    fun onAccountToggle(accountId: String, selected: Boolean) = _state.update {
        it.copy(
            selectedAccountIds = if (selected) {
                it.selectedAccountIds + accountId
            } else {
                it.selectedAccountIds - accountId
            },
        )
    }

    /** Диапазон дат из «Дополнительно»: начало и конец задаются вместе. */
    fun onDateRangeChange(from: LocalDate, to: LocalDate) = _state.update { it.copy(dateFrom = from, dateTo = to) }

    fun onDateRangeClear() = _state.update { it.copy(dateFrom = null, dateTo = null) }

    fun onDelimiterChange(delimiter: Char) = _state.update { it.copy(delimiter = delimiter) }

    fun onBomChange(enabled: Boolean) = _state.update { it.copy(writeBom = enabled) }

    /** Экспорт в файл [output]; null — файл открыть не удалось. */
    fun exportTo(output: OutputStream?) {
        if (_state.value.inProgress) return
        if (output == null) {
            _state.update { it.copy(message = UiText.Res(R.string.import_error_open)) }
            return
        }
        val state = _state.value
        val types = buildSet {
            if (state.includeIncome) add(TxType.INCOME)
            if (state.includeExpense) add(TxType.EXPENSE)
            if (state.includeTransfers) {
                add(TxType.TRANSFER_OUT)
                add(TxType.TRANSFER_IN)
            }
        }
        if (types.isEmpty()) return
        viewModelScope.launch {
            _state.update { it.copy(inProgress = true, message = null, exportSucceeded = false) }
            val (message, succeeded) = try {
                withContext(Dispatchers.IO) {
                    csvExportService.write(
                        output = output,
                        types = types,
                        fromMillis = fromMillis(state.dateFrom),
                        toMillisExclusive = toExclusiveMillis(state.dateTo),
                        accountIds = state.selectedAccountIds.takeIf { state.accountSelectionInitialized },
                        delimiter = state.delimiter,
                        bom = state.writeBom,
                    )
                }
                UiText.Res(R.string.export_saved) to true
            } catch (e: Exception) {
                UiText.Res(R.string.export_error_generic) to false
            }
            _state.update { it.copy(inProgress = false, message = message, exportSucceeded = succeeded) }
        }
    }

    fun consumeMessage() {
        _state.update { it.copy(message = null, exportSucceeded = false) }
    }

    private fun fromMillis(date: LocalDate?): Long? = date?.atStartOfDay(zone)?.toInstant()?.toEpochMilli()

    private fun toExclusiveMillis(date: LocalDate?): Long? = date?.plusDays(1)?.atStartOfDay(zone)?.toInstant()?.toEpochMilli()
}
