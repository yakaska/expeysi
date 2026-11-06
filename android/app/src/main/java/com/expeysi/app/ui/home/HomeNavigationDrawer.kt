package com.expeysi.app.ui.home

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccountBalanceWallet
import androidx.compose.material.icons.filled.Category
import androidx.compose.material.icons.filled.FileUpload
import androidx.compose.material.icons.filled.Home
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DrawerState
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalDrawerSheet
import androidx.compose.material3.ModalNavigationDrawer
import androidx.compose.material3.NavigationDrawerItem
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.expeysi.app.R

@Composable
fun HomeNavigationDrawer(
    drawerState: DrawerState,
    importing: Boolean,
    onHomeClick: () -> Unit,
    onImportClick: () -> Unit,
    onCategoriesClick: () -> Unit,
    onAccountsClick: () -> Unit,
    content: @Composable () -> Unit,
) {
    ModalNavigationDrawer(
        drawerState = drawerState,
        drawerContent = {
            ModalDrawerSheet(modifier = Modifier.fillMaxWidth(2f / 3f)) {
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
                            painter = painterResource(R.drawable.expeysi_portrait),
                            contentDescription = null,
                            modifier = Modifier
                                .size(72.dp)
                                .clip(CircleShape),
                        )
                        Spacer(Modifier.size(12.dp))
                        Text(stringResource(R.string.app_name), style = MaterialTheme.typography.headlineSmall)
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
                DrawerSection("Данные")
                NavigationDrawerItem(
                    label = { Text("Импорт из Бюджета") },
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
            }
        },
        content = content,
    )
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
