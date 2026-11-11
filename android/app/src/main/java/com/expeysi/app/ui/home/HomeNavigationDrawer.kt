package com.expeysi.app.ui.home

import android.widget.Toast
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
import androidx.compose.material.icons.filled.AttachMoney
import androidx.compose.material.icons.filled.Category
import androidx.compose.material.icons.filled.CompareArrows
import androidx.compose.material.icons.filled.FileDownload
import androidx.compose.material.icons.filled.FileUpload
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Payments
import androidx.compose.material.icons.filled.PieChart
import androidx.compose.material.icons.filled.ReceiptLong
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DrawerState
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalDrawerSheet
import androidx.compose.material3.ModalNavigationDrawer
import androidx.compose.material3.NavigationDrawerItem
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
import androidx.compose.ui.unit.dp
import com.expeysi.app.R
import com.expeysi.app.ui.common.rememberClipboardCopy

@Composable
fun HomeNavigationDrawer(
    drawerState: DrawerState,
    importing: Boolean,
    leavingFamily: Boolean,
    onHomeClick: () -> Unit,
    onAnalysisClick: () -> Unit,
    onTransactionComparisonClick: () -> Unit,
    onExpensesReportClick: () -> Unit,
    onIncomesReportClick: () -> Unit,
    onImportClick: () -> Unit,
    onExportClick: () -> Unit,
    onDataImportClick: () -> Unit,
    onCategoriesClick: () -> Unit,
    onAccountsClick: () -> Unit,
    onLeaveFamilyClick: () -> Unit,
    familyCode: String? = null,
    content: @Composable () -> Unit,
) {
    var showLeaveConfirmation by remember { mutableStateOf(false) }
    val context = LocalContext.current
    val copiedMessage = stringResource(R.string.home_family_code_copied)
    val copyLabel = stringResource(R.string.common_copy)
    val copyToClipboard = rememberClipboardCopy()
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
                                    style = MaterialTheme.typography.headlineSmall,
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
                    DrawerSection("Отчёты")
                    NavigationDrawerItem(
                        label = { Text("Главная") },
                        selected = true,
                        icon = { Icon(Icons.Filled.Home, contentDescription = null) },
                        onClick = onHomeClick,
                        modifier = Modifier.padding(horizontal = 12.dp),
                    )
                    NavigationDrawerItem(
                        label = { Text(stringResource(R.string.analysis_title)) },
                        selected = false,
                        icon = { Icon(Icons.Filled.PieChart, contentDescription = null) },
                        onClick = onAnalysisClick,
                        modifier = Modifier.padding(horizontal = 12.dp),
                    )
                    NavigationDrawerItem(
                        label = { Text(stringResource(R.string.drawer_transaction_comparison)) },
                        selected = false,
                        icon = { Icon(Icons.Filled.CompareArrows, contentDescription = null) },
                        onClick = onTransactionComparisonClick,
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
                    DrawerSection("Данные")
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
                        icon = { Icon(Icons.Filled.FileUpload, contentDescription = null) },
                        onClick = onDataImportClick,
                        modifier = Modifier.padding(horizontal = 12.dp),
                    )
                    NavigationDrawerItem(
                        label = { Text("Импорт из Бюджета") },
                        selected = false,
                        icon = {
                            if (importing) {
                                CircularProgressIndicator(modifier = Modifier.size(24.dp))
                            } else {
                                Icon(Icons.Filled.AttachMoney, contentDescription = null)
                            }
                        },
                        onClick = { if (!importing) onImportClick() },
                        modifier = Modifier.padding(horizontal = 12.dp),
                    )
                    DrawerSection("Разное")
                    NavigationDrawerItem(
                        label = { Text("Категории") },
                        selected = false,
                        icon = { Icon(Icons.Filled.Category, contentDescription = null) },
                        onClick = onCategoriesClick,
                        modifier = Modifier.padding(horizontal = 12.dp),
                    )
                    NavigationDrawerItem(
                        label = { Text("Счета") },
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
                    Text(stringResource(R.string.action_cancel))
                }
            },
        )
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
