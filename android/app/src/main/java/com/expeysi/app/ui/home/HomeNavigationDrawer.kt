package com.expeysi.app.ui.home

import android.Manifest
import android.app.LocaleManager
import android.content.Context
import android.content.pm.PackageManager
import android.content.res.Resources
import android.os.Build
import android.provider.Settings
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
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
import androidx.compose.material.icons.filled.Alarm
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
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalDrawerSheet
import androidx.compose.material3.ModalNavigationDrawer
import androidx.compose.material3.NavigationDrawerItem
import androidx.compose.material3.NavigationDrawerItemDefaults
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TimePicker
import androidx.compose.material3.rememberTimePickerState
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
import androidx.core.content.ContextCompat
import androidx.core.os.LocaleListCompat
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.expeysi.app.R
import com.expeysi.app.data.ThemeMode
import com.expeysi.app.ui.common.rememberClipboardCopy
import com.expeysi.app.ui.theme.ThemeViewModel
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale
import java.text.DateFormat as JavaTextDateFormat

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
    var showReminderPicker by remember { mutableStateOf(false) }
    val context = LocalContext.current
    val copiedMessage = stringResource(R.string.home_family_code_copied)
    val copyLabel = stringResource(R.string.common_copy)
    val copyToClipboard = rememberClipboardCopy()
    val themeViewModel: ThemeViewModel = hiltViewModel()
    val themeMode by themeViewModel.mode.collectAsStateWithLifecycle()
    val reminderViewModel: ReminderViewModel = hiltViewModel()
    val reminderState by reminderViewModel.state.collectAsStateWithLifecycle()
    val permissionDeniedMessage = stringResource(R.string.reminder_permission_denied)
    // Разрешение на уведомления спрашиваем при включении тоглера на Android 13+;
    // отказ оставляет напоминание выключенным и объясняет это тостом.
    val requestNotificationPermission = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) { granted ->
        if (granted) {
            reminderViewModel.setEnabled(true)
        } else {
            Toast.makeText(context, permissionDeniedMessage, Toast.LENGTH_LONG).show()
        }
    }
    val onReminderToggle: (Boolean) -> Unit = { enabled ->
        when {
            !enabled -> reminderViewModel.setEnabled(false)
            reminderNeedsNotificationPermission(context) ->
                requestNotificationPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
            else -> reminderViewModel.setEnabled(true)
        }
    }
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
                    NavigationDrawerItem(
                        label = {
                            // Время выбранного напоминания второй строкой: supporting-текста
                            // у NavigationDrawerItem в этой версии material3 нет. Время
                            // показывается в формате устройства (12/24-hour).
                            Column {
                                Text(stringResource(R.string.drawer_reminder))
                                Text(
                                    formatDeviceTime(
                                        context,
                                        reminderState.hour,
                                        reminderState.minute,
                                    ),
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                        },
                        selected = false,
                        icon = { Icon(Icons.Filled.Alarm, contentDescription = null) },
                        onClick = { showReminderPicker = true },
                        badge = {
                            Switch(
                                checked = reminderState.enabled,
                                onCheckedChange = onReminderToggle,
                            )
                        },
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
    if (showReminderPicker) {
        ReminderTimePickerDialog(
            initialHour = reminderState.hour,
            initialMinute = reminderState.minute,
            onDismiss = { showReminderPicker = false },
            onSelect = { hour, minute ->
                showReminderPicker = false
                reminderViewModel.setTime(hour, minute)
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

/** Диалог выбора времени напоминания: время применяется сразу, включённость не трогается. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ReminderTimePickerDialog(
    initialHour: Int,
    initialMinute: Int,
    onDismiss: () -> Unit,
    onSelect: (hour: Int, minute: Int) -> Unit,
) {
    // Формат пикера должен совпадать с форматом часов устройства: 12-hour с AM/PM
    // или 24-hour — по настройке, а без неё по локали устройства.
    val pickerState = rememberTimePickerState(
        initialHour = initialHour,
        initialMinute = initialMinute,
        is24Hour = deviceUses24Hour(LocalContext.current),
    )
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.drawer_reminder)) },
        text = { TimePicker(state = pickerState) },
        confirmButton = {
            TextButton(
                onClick = { onSelect(pickerState.hour, pickerState.minute) },
            ) { Text(stringResource(R.string.common_ok)) }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.common_cancel)) }
        },
    )
}

/**
 * Время в формате устройства (как часы в системной панели): настройка «24-часовой
 * формат», а без неё — короткий локализованный шаблон. Локаль берём системную,
 * а не контекста: у приложения свой per-app язык, и его подмена не должна
 * менять формат часов.
 */
private fun formatDeviceTime(context: Context, hour: Int, minute: Int): String {
    val pattern = if (deviceUses24Hour(context)) "HH:mm" else "h:mm a"
    val formatter = SimpleDateFormat(pattern, deviceLocale(context))
    val moment = Calendar.getInstance().apply {
        set(Calendar.HOUR_OF_DAY, hour)
        set(Calendar.MINUTE, minute)
        set(Calendar.SECOND, 0)
        set(Calendar.MILLISECOND, 0)
    }.time
    return formatter.format(moment)
}

/** 24h или 12h: системная настройка TIME_12_24, если задана, иначе —
 *  правила локали устройства. Формат не зависит от языка приложения. */
private fun deviceUses24Hour(context: Context): Boolean {
    val setting = Settings.System.getString(context.contentResolver, Settings.System.TIME_12_24)
    if (setting != null) {
        return setting == "24"
    }
    return is24HourLocale(deviceLocale(context))
}

/**
 * Локаль устройства, а не приложения: per-app язык не должен влиять на
 * формат часов. LocaleManager.systemLocales даёт именно системный список;
 * Resources.getSystem() на новых API тоже подменяется per-app языком, поэтому
 * годится только как fallback до Android 13, где такого механизма не было.
 */
private fun deviceLocale(context: Context): Locale {
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
        val systemLocales = context.getSystemService(LocaleManager::class.java)?.systemLocales
        if (systemLocales != null && !systemLocales.isEmpty) {
            return systemLocales[0]
        }
    }
    return Resources.getSystem().configuration.locales[0]
}

/** Эквивалент скрытого DateFormat.is24HourLocale: H или k первым в коротком шаблоне — 24h. */
private fun is24HourLocale(locale: Locale): Boolean {
    val format = JavaTextDateFormat.getTimeInstance(JavaTextDateFormat.SHORT, locale)
    val pattern = (format as? SimpleDateFormat)?.toPattern().orEmpty()
    return pattern.firstOrNull() == 'H' || pattern.firstOrNull() == 'k'
}

/**
 * POST_NOTIFICATIONS — рантайм-разрешение Android 13+; до 13-го уведомления
 * включены по умолчанию и запрос не нужен.
 */
private fun reminderNeedsNotificationPermission(context: Context): Boolean {
    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) {
        // До Android 13 уведомления включены по умолчанию, запрос не нужен.
        return false
    }
    return ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) !=
        PackageManager.PERMISSION_GRANTED
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
