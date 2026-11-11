package com.expeysi.app.ui.analysis

import android.content.res.Configuration
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.AccountBalanceWallet
import androidx.compose.material.icons.filled.MoreHoriz
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.expeysi.app.R
import com.expeysi.app.data.analysis.AnalysisAccount
import com.expeysi.app.data.analysis.CategoryStat
import com.expeysi.app.data.analysis.CategorySummary
import com.expeysi.app.domain.HistoryPeriod
import com.expeysi.app.domain.TxType
import com.expeysi.app.ui.common.AmountFormat
import com.expeysi.app.ui.common.categoriesLabel
import com.expeysi.app.ui.common.operationsLabel
import com.expeysi.app.ui.common.rublesLabel
import com.expeysi.app.ui.history.PeriodPickerSheet
import com.expeysi.app.ui.icons.CategoryIcon
import com.expeysi.app.ui.icons.CategoryPalette
import com.expeysi.app.ui.theme.ExpeysiTheme
import com.expeysi.app.ui.theme.TopCardShape
import java.time.LocalDate
import java.time.YearMonth
import java.time.ZoneId

private const val VISIBLE_CATEGORIES = 4

/** События экрана «Анализ финансов». */
data class AnalysisActions(
    val onBack: () -> Unit = {},
    val onMenuClick: () -> Unit = {},
    val onFiltersClick: () -> Unit = {},
    val onFiltersDismiss: () -> Unit = {},
    val onTypeSelect: (TxType) -> Unit = {},
    val onPreviousMonth: () -> Unit = {},
    val onNextMonth: () -> Unit = {},
    val onCenterClick: () -> Unit = {},
    val onPeriodSelected: (HistoryPeriod) -> Unit = {},
    val onRangeSelected: (LocalDate, LocalDate) -> Unit = { _, _ -> },
    val onPeriodPickerDismiss: () -> Unit = {},
    val onCategoryClick: (CategoryStat) -> Unit = {},
    val onExpandToggle: () -> Unit = {},
    val onDraftAccountToggle: (String) -> Unit = {},
    val onDraftAllAccounts: () -> Unit = {},
    val onFiltersReset: () -> Unit = {},
    val onFiltersApply: () -> Unit = {},
    val onRetry: () -> Unit = {},
)

/** Готовое состояние + события; отрисовка без логики и доступа к данным. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun AnalysisContent(state: AnalysisUiState, actions: AnalysisActions) {
    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = { AnalysisTopBar(onBack = actions.onBack, onMenuClick = actions.onMenuClick) },
    ) { padding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState())
                    .padding(bottom = 32.dp),
            ) {
                if (state.isBalanceMissing) {
                    BalanceMissingState()
                } else {
                    ControlRow(
                        state = state,
                        onFiltersClick = actions.onFiltersClick,
                        onTypeSelect = actions.onTypeSelect,
                    )
                    TotalAmount(state = state)
                    when {
                        state.isError -> ErrorState(onRetry = actions.onRetry)
                        state.isFirstLoad -> AnalysisSkeleton()
                        else -> {
                            val summary = state.summary
                            if (summary != null) {
                                DonutBlock(
                                    state = state,
                                    summary = summary,
                                    onCenterClick = actions.onCenterClick,
                                    onPreviousMonth = actions.onPreviousMonth,
                                    onNextMonth = actions.onNextMonth,
                                    onSegmentClick = { index ->
                                        summary.categories.getOrNull(index)?.let(actions.onCategoryClick)
                                    },
                                )
                                val categories = summary.categories
                                if (categories.isEmpty()) {
                                    EmptyCategoriesText(state.type)
                                } else {
                                    CategoriesCard(
                                        categories = categories,
                                        currencySymbol = summary.currencySymbol,
                                        expanded = state.expanded,
                                        onCategoryClick = actions.onCategoryClick,
                                        onExpandToggle = actions.onExpandToggle,
                                    )
                                }
                            }
                        }
                    }
                }
            }
            if (state.isRefreshing) {
                RefreshScrim(modifier = Modifier.fillMaxSize())
            }
        }
        if (state.filtersOpen) {
            AnalysisFiltersSheet(
                accounts = state.accounts,
                draft = state.filterDraft,
                onAccountToggle = actions.onDraftAccountToggle,
                onAllAccounts = actions.onDraftAllAccounts,
                onReset = actions.onFiltersReset,
                onApply = actions.onFiltersApply,
                onDismiss = actions.onFiltersDismiss,
            )
        }
        if (state.periodPickerOpen) {
            PeriodPickerSheet(
                period = when (state.period.kind) {
                    AnalysisPeriodKind.MONTHS -> HistoryPeriod.MONTH
                    AnalysisPeriodKind.SLIDING -> state.period.sliding
                    AnalysisPeriodKind.RANGE -> HistoryPeriod.ALL
                },
                customRange = if (state.period.kind == AnalysisPeriodKind.RANGE) state.period.range else null,
                onPeriodSelected = actions.onPeriodSelected,
                onRangeSelected = actions.onRangeSelected,
                onDismiss = actions.onPeriodPickerDismiss,
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun AnalysisTopBar(onBack: () -> Unit, onMenuClick: () -> Unit) {
    TopAppBar(
        title = {
            Text(
                text = stringResource(R.string.analysis_title),
                style = MaterialTheme.typography.titleLarge,
            )
        },
        navigationIcon = {
            IconButton(onClick = onBack) {
                Icon(
                    Icons.AutoMirrored.Filled.ArrowBack,
                    contentDescription = stringResource(R.string.common_back),
                )
            }
        },
        actions = {
            IconButton(onClick = onMenuClick) {
                Icon(
                    Icons.Filled.MoreHoriz,
                    contentDescription = stringResource(R.string.analysis_menu_cd),
                )
            }
        },
    )
}

@Composable
private fun ControlRow(
    state: AnalysisUiState,
    onFiltersClick: () -> Unit,
    onTypeSelect: (TxType) -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        FilterButton(
            active = state.selectedAccountIds != null,
            onClick = onFiltersClick,
        )
        Spacer(modifier = Modifier.width(10.dp))
        Row(
            modifier = Modifier.weight(1f),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            TypePill(
                label = stringResource(R.string.home_expenses),
                selected = state.type == TxType.EXPENSE,
                modifier = Modifier.weight(1f),
                onClick = { onTypeSelect(TxType.EXPENSE) },
            )
            TypePill(
                label = stringResource(R.string.home_income),
                selected = state.type == TxType.INCOME,
                modifier = Modifier.weight(1f),
                onClick = { onTypeSelect(TxType.INCOME) },
            )
        }
    }
}

@Composable
private fun FilterButton(active: Boolean, onClick: () -> Unit) {
    Box(
        modifier = Modifier
            .size(44.dp)
            .background(MaterialTheme.colorScheme.surfaceContainer, CircleShape)
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            Icons.Filled.Tune,
            contentDescription = stringResource(R.string.analysis_filters),
            modifier = Modifier.size(20.dp),
        )
        if (active) {
            Box(
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(5.dp)
                    .size(8.dp)
                    .background(MaterialTheme.colorScheme.primary, CircleShape),
            )
        }
    }
}

@Composable
private fun TypePill(
    label: String,
    selected: Boolean,
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
) {
    Box(
        modifier = modifier
            .height(40.dp)
            .background(
                color = if (selected) {
                    MaterialTheme.colorScheme.inverseSurface
                } else {
                    MaterialTheme.colorScheme.surfaceContainer
                },
                shape = RoundedCornerShape(20.dp),
            )
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelLarge,
            color = if (selected) {
                MaterialTheme.colorScheme.inverseOnSurface
            } else {
                MaterialTheme.colorScheme.onSurfaceVariant
            },
        )
    }
}

@Composable
private fun TotalAmount(state: AnalysisUiState) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp)
            .heightIn(min = 40.dp),
        contentAlignment = Alignment.CenterStart,
    ) {
        val summary = state.summary
        if (summary == null) {
            SkeletonBar(width = 180.dp, height = 36.dp)
        } else {
            Text(
                text = AmountFormat.withCurrency(summary.totalMinor, summary.currencySymbol),
                style = MaterialTheme.typography.headlineMedium,
            )
        }
    }
}

@Composable
private fun DonutBlock(
    state: AnalysisUiState,
    summary: CategorySummary,
    onCenterClick: () -> Unit,
    onPreviousMonth: () -> Unit,
    onNextMonth: () -> Unit,
    onSegmentClick: (Int) -> Unit,
) {
    val zone = ZoneId.systemDefault()
    val total = summary.totalMinor
    val shares = if (total <= 0L) {
        emptyList()
    } else {
        summary.categories.map { it.amountMinor.toFloat() / total }
    }
    val typeLabel =
        stringResource(if (state.type == TxType.EXPENSE) R.string.home_expenses else R.string.home_income)
    val amountPart = "${AmountFormat.amount(total)} ${rublesLabel(total / 100)}"
    val description = if (state.period.kind == AnalysisPeriodKind.MONTHS) {
        stringResource(
            R.string.analysis_chart_cd,
            typeLabel,
            state.period.sinceLabel(),
            amountPart,
        )
    } else {
        stringResource(R.string.analysis_chart_cd_any, typeLabel, state.period.sinceLabel(), amountPart)
    }
    AnalysisDonut(
        shares = shares,
        colors = summary.categories.map { CategoryPalette.color(it.colorKey) },
        centerLabel = state.period.centerLabel(zone),
        description = description,
        previousVisible = state.period.kind == AnalysisPeriodKind.MONTHS &&
            hasOperationsInPreviousMonth(state.period.month, state.earliestMillis, zone),
        nextVisible = state.nextMonthVisible,
        swipeEnabled = state.period.monthsNavigable,
        onSegmentClick = onSegmentClick,
        onPrevious = onPreviousMonth,
        onNext = onNextMonth,
        onCenterClick = onCenterClick,
        modifier = Modifier.padding(start = 8.dp, end = 8.dp, bottom = 24.dp),
    )
}

@Composable
private fun CategoriesCard(
    categories: List<CategoryStat>,
    currencySymbol: String,
    expanded: Boolean,
    onCategoryClick: (CategoryStat) -> Unit,
    onExpandToggle: () -> Unit,
) {
    val visible = if (expanded) categories else categories.take(VISIBLE_CATEGORIES)
    val rest = categories.drop(VISIBLE_CATEGORIES)
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp),
        shape = TopCardShape,
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainer,
        ),
    ) {
        visible.forEachIndexed { index, category ->
            if (index > 0) {
                HorizontalDivider(
                    modifier = Modifier.padding(start = 72.dp),
                    color = MaterialTheme.colorScheme.outlineVariant,
                )
            }
            CategoryRow(
                category = category,
                currencySymbol = currencySymbol,
                onClick = { onCategoryClick(category) },
            )
        }
        if (rest.isNotEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = 48.dp)
                    .clickable(onClick = onExpandToggle),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = if (expanded) {
                        stringResource(R.string.analysis_collapse)
                    } else {
                        stringResource(
                            R.string.analysis_more_categories,
                            categoriesLabel(rest.size),
                            AmountFormat.withCurrency(rest.sumOf { it.amountMinor }, currencySymbol),
                        )
                    },
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.primary,
                )
            }
        }
    }
}

@Composable
private fun CategoryRow(
    category: CategoryStat,
    currencySymbol: String,
    onClick: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 72.dp)
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 16.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        CategoryIcon(iconKey = category.iconKey, colorKey = category.colorKey)
        Column(modifier = Modifier.weight(1f).padding(start = 16.dp)) {
            Text(
                text = category.name.ifBlank { stringResource(R.string.report_uncategorized) },
                style = MaterialTheme.typography.bodyLarge,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = operationsLabel(category.operationCount),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Text(
            text = AmountFormat.withCurrency(category.amountMinor, currencySymbol),
            style = MaterialTheme.typography.titleMedium,
            softWrap = false,
        )
    }
}

@Composable
private fun EmptyCategoriesText(type: TxType) {
    Text(
        text = stringResource(
            if (type == TxType.EXPENSE) R.string.analysis_empty_expense else R.string.analysis_empty_income,
        ),
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 48.dp, horizontal = 16.dp),
    )
}

/** Нет ни одного баланса: отчёт нечего показывать. */
@Composable
private fun BalanceMissingState() {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 64.dp, horizontal = 32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Box(
            modifier = Modifier
                .size(64.dp)
                .background(MaterialTheme.colorScheme.surfaceContainer, CircleShape),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                Icons.Filled.AccountBalanceWallet,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(28.dp),
            )
        }
        Spacer(modifier = Modifier.height(20.dp))
        Text(
            text = stringResource(R.string.analysis_no_balances),
            style = MaterialTheme.typography.titleMedium,
        )
        Text(
            text = stringResource(R.string.analysis_no_balances_hint),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(top = 8.dp),
        )
    }
}

private val previewAccounts = listOf(
    AnalysisAccount(id = "cash", name = "Наличные", iconKey = "payments", colorKey = "green"),
    AnalysisAccount(id = "card", name = "Карта", iconKey = "credit_card", colorKey = "blue"),
)

private val previewExpenseCategories = listOf(
    CategoryStat(
        categoryId = "1",
        name = "Еда",
        iconKey = "restaurant",
        colorKey = "orange",
        amountMinor = 7_844_900,
        operationCount = 23,
    ),
    CategoryStat(
        categoryId = "2",
        name = "Транспорт",
        iconKey = "directions_bus",
        colorKey = "blue",
        amountMinor = 4_156_000,
        operationCount = 14,
    ),
    CategoryStat(
        categoryId = "3",
        name = "Жильё",
        iconKey = "home",
        colorKey = "brown",
        amountMinor = 3_500_000,
        operationCount = 1,
    ),
    CategoryStat(
        categoryId = "4",
        name = "Одежда",
        iconKey = "checkroom",
        colorKey = "purple",
        amountMinor = 1_248_300,
        operationCount = 4,
    ),
    CategoryStat(
        categoryId = "5",
        name = "Здоровье",
        iconKey = "medical_services",
        colorKey = "red",
        amountMinor = 630_400,
        operationCount = 3,
    ),
    CategoryStat(
        categoryId = "6",
        name = "Без категории",
        iconKey = "category",
        colorKey = "default",
        amountMinor = 490_000,
        operationCount = 2,
    ),
)

private val previewIncomeCategories = listOf(
    CategoryStat(
        categoryId = "11",
        name = "Зарплата",
        iconKey = "work",
        colorKey = "green",
        amountMinor = 12_000_000,
        operationCount = 1,
    ),
    CategoryStat(
        categoryId = "12",
        name = "Премия",
        iconKey = "star",
        colorKey = "amber",
        amountMinor = 3_500_000,
        operationCount = 2,
    ),
)

private fun previewSummary(type: TxType): CategorySummary {
    val categories = if (type == TxType.EXPENSE) previewExpenseCategories else previewIncomeCategories
    return CategorySummary(
        totalMinor = categories.sumOf { it.amountMinor },
        currencySymbol = "₽",
        categories = categories,
    )
}

private val previewMonth = YearMonth.of(2026, 4)

private fun analysisPreviewState(
    type: TxType,
    summary: CategorySummary? = previewSummary(type),
): AnalysisUiState = AnalysisUiState(
    balanceId = "main",
    type = type,
    period = AnalysisPeriod(month = previewMonth),
    currentMonth = previewMonth,
    accounts = previewAccounts,
    summary = summary,
)

@Composable
private fun AnalysisThemePreview(state: AnalysisUiState, dark: Boolean) {
    ExpeysiTheme(darkTheme = dark) {
        AnalysisContent(
            state = state,
            actions = AnalysisActions(),
        )
    }
}

@Preview(name = "Расходы · Light", showBackground = true)
@Composable
private fun AnalysisContentExpensesLightPreview() {
    AnalysisThemePreview(analysisPreviewState(TxType.EXPENSE), dark = false)
}

@Preview(
    name = "Расходы · Dark",
    showBackground = true,
    uiMode = Configuration.UI_MODE_NIGHT_YES,
)
@Composable
private fun AnalysisContentExpensesDarkPreview() {
    AnalysisThemePreview(analysisPreviewState(TxType.EXPENSE), dark = true)
}

@Preview(name = "Доходы · Light", showBackground = true)
@Composable
private fun AnalysisContentIncomesLightPreview() {
    AnalysisThemePreview(analysisPreviewState(TxType.INCOME), dark = false)
}

@Preview(
    name = "Доходы · Dark",
    showBackground = true,
    uiMode = Configuration.UI_MODE_NIGHT_YES,
)
@Composable
private fun AnalysisContentIncomesDarkPreview() {
    AnalysisThemePreview(analysisPreviewState(TxType.INCOME), dark = true)
}

@Preview(name = "Пусто · Light", showBackground = true)
@Composable
private fun AnalysisContentEmptyLightPreview() {
    AnalysisThemePreview(
        analysisPreviewState(
            TxType.EXPENSE,
            summary = CategorySummary(0L, "₽", emptyList()),
        ),
        dark = false,
    )
}

@Preview(
    name = "Пусто · Dark",
    showBackground = true,
    uiMode = Configuration.UI_MODE_NIGHT_YES,
)
@Composable
private fun AnalysisContentEmptyDarkPreview() {
    AnalysisThemePreview(
        analysisPreviewState(
            TxType.EXPENSE,
            summary = CategorySummary(0L, "₽", emptyList()),
        ),
        dark = true,
    )
}

@Preview(name = "Загрузка · Light", showBackground = true)
@Composable
private fun AnalysisContentLoadingLightPreview() {
    AnalysisThemePreview(analysisPreviewState(TxType.EXPENSE, summary = null), dark = false)
}

@Preview(
    name = "Загрузка · Dark",
    showBackground = true,
    uiMode = Configuration.UI_MODE_NIGHT_YES,
)
@Composable
private fun AnalysisContentLoadingDarkPreview() {
    AnalysisThemePreview(analysisPreviewState(TxType.EXPENSE, summary = null), dark = true)
}

@Preview(name = "Нет балансов · Light", showBackground = true)
@Composable
private fun AnalysisContentNoBalanceLightPreview() {
    AnalysisThemePreview(
        AnalysisUiState(
            balanceId = null,
            type = TxType.EXPENSE,
        ),
        dark = false,
    )
}

@Preview(
    name = "Нет балансов · Dark",
    showBackground = true,
    uiMode = Configuration.UI_MODE_NIGHT_YES,
)
@Composable
private fun AnalysisContentNoBalanceDarkPreview() {
    AnalysisThemePreview(
        AnalysisUiState(
            balanceId = null,
            type = TxType.EXPENSE,
        ),
        dark = true,
    )
}

@Preview(name = "Ошибка · Light", showBackground = true)
@Composable
private fun AnalysisContentErrorLightPreview() {
    AnalysisThemePreview(
        analysisPreviewState(TxType.EXPENSE, summary = null).copy(isError = true),
        dark = false,
    )
}

@Preview(
    name = "Ошибка · Dark",
    showBackground = true,
    uiMode = Configuration.UI_MODE_NIGHT_YES,
)
@Composable
private fun AnalysisContentErrorDarkPreview() {
    AnalysisThemePreview(
        analysisPreviewState(TxType.EXPENSE, summary = null).copy(isError = true),
        dark = true,
    )
}
