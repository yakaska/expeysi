package com.expeysi.app.ui.home

import android.widget.Toast
import androidx.appcompat.app.AppCompatDelegate
import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Logout
import androidx.compose.material.icons.filled.AccountBalanceWallet
import androidx.compose.material.icons.filled.Category
import androidx.compose.material.icons.filled.CompareArrows
import androidx.compose.material.icons.filled.DarkMode
import androidx.compose.material.icons.filled.FileDownload
import androidx.compose.material.icons.filled.FileUpload
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Payments
import androidx.compose.material.icons.filled.PieChart
import androidx.compose.material.icons.filled.ReceiptLong
import androidx.compose.material.icons.filled.SwapHoriz
import androidx.compose.material.icons.filled.Translate
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DrawerState
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalDrawerSheet
import androidx.compose.material3.ModalNavigationDrawer
import androidx.compose.material3.NavigationDrawerItem
import androidx.compose.material3.NavigationDrawerItemDefaults
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.core.os.LocaleListCompat
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.expeysi.app.R
import com.expeysi.app.data.ThemeMode
import com.expeysi.app.ui.common.rememberClipboardCopy
import com.expeysi.app.ui.theme.ThemeViewModel

/** Дисплейный шрифт со стилизацией «под иврит» для имени приложения в шапке drawer. */
private val SholomFontFamily = FontFamily(Font(R.font.sholom))

@Composable
fun HomeNavigationDrawer(
    drawerState: DrawerState,
    importing: Boolean,
    leavingFamily: Boolean,
    onHomeClick: () -> Unit,
    onAnalysisClick: () -> Unit,
    onCategoryComparisonClick: () -> Unit,
    onExpensesReportClick: () -> Unit,
    onIncomesReportClick: () -> Unit,
    onTransfersReportClick: () -> Unit,
    onImportClick: () -> Unit,
    onExportClick: () -> Unit,
    onCategoriesClick: () -> Unit,
    onAccountsClick: () -> Unit,
    onLeaveFamilyClick: () -> Unit,
    familyCode: String? = null,
    content: @Composable () -> Unit,
) {
    var showLeaveConfirmation by remember { mutableStateOf(false) }
    var showLanguagePicker by remember { mutableStateOf(false) }
    var showThemePicker by remember { mutableStateOf(false) }
    val context = LocalContext.current
    val copiedMessage = stringResource(R.string.home_family_code_copied)
    val copyLabel = stringResource(R.string.common_copy)
    val copyToClipboard = rememberClipboardCopy()
    val themeViewModel: ThemeViewModel = hiltViewModel()
    val themeMode by themeViewModel.mode.collectAsStateWithLifecycle()
    ModalNavigationDrawer(
        drawerState = drawerState,
        drawerContent = {
            ModalDrawerSheet(modifier = Modifier.fillMaxWidth(2f / 3f)) {
                Column(modifier = Modifier.verticalScroll(rememberScrollState())) {
                    Surface(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RectangleShape,
                        color = MaterialTheme.colorScheme.primaryContainer,
                    ) {
                        Row(
                            modifier = Modifier.padding(12.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Image(
                                painter = painterResource(R.drawable.expeysi_icon),
                                contentDescription = null,
                                modifier = Modifier
                                    .size(72.dp)
                                    .clip(CircleShape),
                            )
                            Spacer(Modifier.size(12.dp))
                            Column {
                                Text(
                                    stringResource(R.string.app_name),
                                    style = MaterialTheme.typography.headlineSmall
                                        .copy(fontFamily = SholomFontFamily),
                                )
                                familyCode?.let { code ->
                                    Text(
                                        stringResource(R.string.home_family_code, code),
                                        style = MaterialTheme.typography.bodySmall,
                                        modifier = Modifier.clickable(
                                            onClickLabel = copyLabel,
                                        ) {
                                            copyToClipboard("family code", code)
                                            Toast.makeText(context, copiedMessage, Toast.LENGTH_SHORT).show()
                                        },
                                    )
                                }
                            }
                        }
                    }
                    DrawerSection(stringResource(R.string.drawer_section_reports))
                    NavigationDrawerItem(
                        label = { Text(stringResource(R.string.drawer_home)) },
                        selected = true,
                        icon = { Icon(Icons.Filled.Home, contentDescription = null) },
                        onClick = onHomeClick,
                        modifier = Modifier.padding(horizontal = 12.dp),
                    )
                    NavigationDrawerItem(
                        label = { Text(stringResource(R.string.drawer_expenses_report)) },
                        selected = false,
                        icon = { Icon(Icons.Filled.ReceiptLong, contentDescription = null) },
                        onClick = onExpensesReportClick,
                        modifier = Modifier.padding(horizontal = 12.dp),
                    )
                    NavigationDrawerItem(
                        label = { Text(stringResource(R.string.drawer_incomes_report)) },
                        selected = false,
                        icon = { Icon(Icons.Filled.Payments, contentDescription = null) },
                        onClick = onIncomesReportClick,
                        modifier = Modifier.padding(horizontal = 12.dp),
                    )
                    NavigationDrawerItem(
                        label = { Text(stringResource(R.string.drawer_transfers_report)) },
                        selected = false,
                        icon = { Icon(Icons.Filled.SwapHoriz, contentDescription = null) },
                        onClick = onTransfersReportClick,
                        modifier = Modifier.padding(horizontal = 12.dp),
                    )
                    DrawerSection(stringResource(R.string.drawer_section_analytics))
                    NavigationDrawerItem(
                        label = { Text(stringResource(R.string.analysis_title)) },
                        selected = false,
                        icon = { Icon(Icons.Filled.PieChart, contentDescription = null) },
                        onClick = onAnalysisClick,
                        modifier = Modifier.padding(horizontal = 12.dp),
                    )
                    NavigationDrawerItem(
                        label = { Text(stringResource(R.string.drawer_category_comparison)) },
                        selected = false,
                        icon = { Icon(Icons.Filled.CompareArrows, contentDescription = null) },
                        onClick = onCategoryComparisonClick,
                        modifier = Modifier.padding(horizontal = 12.dp),
                    )
                    DrawerSection(stringResource(R.string.drawer_section_data))
                    NavigationDrawerItem(
                        label = { Text(stringResource(R.string.drawer_data_export)) },
                        selected = false,
                        icon = { Icon(Icons.Filled.FileDownload, contentDescription = null) },
                        onClick = onExportClick,
                        modifier = Modifier.padding(horizontal = 12.dp),
                    )
                    NavigationDrawerItem(
                        label = { Text(stringResource(R.string.drawer_data_import)) },
                        selected = false,
                        icon = {
                            if (importing) {
                                CircularProgressIndicator(modifier = Modifier.size(24.dp))
                            } else {
                                Icon(Icons.Filled.FileUpload, contentDescription = null)
                            }
                        },
                        onClick = { if (!importing) onImportClick() },
                        modifier = Modifier.padding(horizontal = 12.dp),
                    )
                    DrawerSection(stringResource(R.string.drawer_section_interface))
                    NavigationDrawerItem(
                        label = { Text(stringResource(R.string.drawer_language)) },
                        selected = false,
                        icon = { Icon(Icons.Filled.Translate, contentDescription = null) },
                        onClick = { showLanguagePicker = true },
                        modifier = Modifier.padding(horizontal = 12.dp),
                    )
                    NavigationDrawerItem(
                        label = { Text(stringResource(R.string.drawer_theme)) },
                        selected = false,
                        icon = { Icon(Icons.Filled.DarkMode, contentDescription = null) },
                        onClick = { showThemePicker = true },
                        modifier = Modifier.padding(horizontal = 12.dp),
                    )
                    DrawerSection(stringResource(R.string.drawer_section_misc))
                    NavigationDrawerItem(
                        label = { Text(stringResource(R.string.history_mode_categories)) },
                        selected = false,
                        icon = { Icon(Icons.Filled.Category, contentDescription = null) },
                        onClick = onCategoriesClick,
                        modifier = Modifier.padding(horizontal = 12.dp),
                    )
                    NavigationDrawerItem(
                        label = { Text(stringResource(R.string.home_accounts)) },
                        selected = false,
                        icon = { Icon(Icons.Filled.AccountBalanceWallet, contentDescription = null) },
                        onClick = onAccountsClick,
                        modifier = Modifier.padding(horizontal = 12.dp),
                    )
                    NavigationDrawerItem(
                        label = { Text(stringResource(R.string.family_leave_action)) },
                        selected = false,
                        icon = {
                            if (leavingFamily) {
                                CircularProgressIndicator(modifier = Modifier.size(24.dp))
                            } else {
                                Icon(Icons.AutoMirrored.Filled.Logout, contentDescription = null)
                            }
                        },
                        onClick = { if (!leavingFamily) showLeaveConfirmation = true },
                        colors = NavigationDrawerItemDefaults.colors(
                            unselectedTextColor = MaterialTheme.colorScheme.error,
                            unselectedIconColor = MaterialTheme.colorScheme.error,
                        ),
                        modifier = Modifier.padding(horizontal = 12.dp),
                    )
                }
            }
        },
        content = content,
    )
    if (showLeaveConfirmation) {
        AlertDialog(
            onDismissRequest = { if (!leavingFamily) showLeaveConfirmation = false },
            title = { Text(stringResource(R.string.family_leave_action)) },
            text = { Text(stringResource(R.string.family_leave_confirmation)) },
            confirmButton = {
                TextButton(
                    onClick = {
                        showLeaveConfirmation = false
                        onLeaveFamilyClick()
                    },
                    enabled = !leavingFamily,
                ) {
                    Text(stringResource(R.string.family_leave_action))
                }
            },
            dismissButton = {
                TextButton(
                    onClick = { showLeaveConfirmation = false },
                    enabled = !leavingFamily,
                ) {
                    Text(stringResource(R.string.common_cancel))
                }
            },
        )
    }
    if (showLanguagePicker) {
        LanguagePickerDialog(onDismiss = { showLanguagePicker = false })
    }
    if (showThemePicker) {
        ThemePickerDialog(
            selectedMode = themeMode,
            onDismiss = { showThemePicker = false },
            onSelect = { selected ->
                showThemePicker = false
                themeViewModel.setMode(selected)
            },
        )
    }
}

/** Диалог переключения языка: выбор применяется сразу, активити пересоздаётся. */
@Composable
private fun LanguagePickerDialog(onDismiss: () -> Unit) {
    // Пустой список локалей — «язык системы», иначе используем код языка.
    val activeLanguageTag = AppCompatDelegate.getApplicationLocales()
        .takeIf { !it.isEmpty }
        ?.get(0)
        ?.language
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.drawer_language)) },
        text = {
            Column {
                RadioOption(
                    label = stringResource(R.string.language_system),
                    selected = activeLanguageTag == null,
                    onClick = {
                        onDismiss()
                        applyLanguage(null)
                    },
                )
                RadioOption(
                    label = stringResource(R.string.language_english),
                    selected = activeLanguageTag == "en",
                    onClick = {
                        onDismiss()
                        applyLanguage("en")
                    },
                )
                RadioOption(
                    label = stringResource(R.string.language_russian),
                    selected = activeLanguageTag == "ru",
                    onClick = {
                        onDismiss()
                        applyLanguage("ru")
                    },
                )
            }
        },
        confirmButton = {},
    )
}

private fun applyLanguage(tag: String?) {
    AppCompatDelegate.setApplicationLocales(
        tag?.let(LocaleListCompat::forLanguageTags) ?: LocaleListCompat.getEmptyLocaleList(),
    )
}

/** Диалог выбора темы: «как в системе», светлая, тёмная; применяется сразу. */
@Composable
private fun ThemePickerDialog(
    selectedMode: ThemeMode,
    onDismiss: () -> Unit,
    onSelect: (ThemeMode) -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.drawer_theme)) },
        text = {
            Column {
                RadioOption(
                    label = stringResource(R.string.theme_system),
                    selected = selectedMode == ThemeMode.SYSTEM,
                    onClick = { onSelect(ThemeMode.SYSTEM) },
                )
                RadioOption(
                    label = stringResource(R.string.theme_light),
                    selected = selectedMode == ThemeMode.LIGHT,
                    onClick = { onSelect(ThemeMode.LIGHT) },
                )
                RadioOption(
                    label = stringResource(R.string.theme_dark),
                    selected = selectedMode == ThemeMode.DARK,
                    onClick = { onSelect(ThemeMode.DARK) },
                )
            }
        },
        confirmButton = {},
    )
}

@Composable
private fun RadioOption(
    label: String,
    selected: Boolean,
    onClick: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        RadioButton(selected = selected, onClick = null)
        Spacer(Modifier.size(12.dp))
        Text(label, style = MaterialTheme.typography.bodyLarge)
    }
}

@Composable
private fun DrawerSection(title: String) {
    Text(
        title,
        style = MaterialTheme.typography.titleSmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(horizontal = 28.dp, vertical = 12.dp),
    )
}
