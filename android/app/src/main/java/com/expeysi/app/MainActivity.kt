package com.expeysi.app

import android.net.Uri
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.core.tween
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.material3.DrawerValue
import androidx.compose.material3.rememberDrawerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.platform.LocalContext
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.expeysi.app.ui.accounts.AccountsScreen
import com.expeysi.app.ui.accounts.EditAccountScreen
import com.expeysi.app.ui.add.AddTransactionScreen
import com.expeysi.app.ui.analysis.AnalysisScreen
import com.expeysi.app.ui.auth.AuthScreen
import com.expeysi.app.ui.balances.EditBalanceScreen
import com.expeysi.app.ui.categories.CategoriesScreen
import com.expeysi.app.ui.categories.EditCategoryScreen
import com.expeysi.app.ui.categoryoperations.CategoryOperationsScreen
import com.expeysi.app.ui.categoryreport.CategoryReportScreen
import com.expeysi.app.ui.family.FamilyScreen
import com.expeysi.app.ui.history.HistoryScreen
import com.expeysi.app.ui.home.HomeNavigationDrawer
import com.expeysi.app.ui.home.HomeScreen
import com.expeysi.app.ui.home.HomeViewModel
import com.expeysi.app.ui.placeholder.PlaceholderScreen
import com.expeysi.app.ui.theme.ExpeysiTheme
import com.expeysi.app.ui.transactionreport.TransactionReportScreen
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.launch

@AndroidEntryPoint
class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            ExpeysiTheme {
                val sessionViewModel: SessionViewModel = hiltViewModel()
                val isSignedIn by sessionViewModel.isSignedIn.collectAsStateWithLifecycle()
                val familyId by sessionViewModel.familyId.collectAsStateWithLifecycle()
                when {
                    !isSignedIn -> AuthScreen()
                    familyId == null -> FamilyScreen()
                    else -> AppNavigation()
                }
            }
        }
    }
}

@Composable
private fun AppNavigation() {
    val navController = rememberNavController()
    val homeViewModel: HomeViewModel = hiltViewModel()
    val homeUiState by homeViewModel.uiState.collectAsStateWithLifecycle()
    val importState by homeViewModel.importState.collectAsStateWithLifecycle()
    val leaveState by homeViewModel.leaveState.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val drawerState = rememberDrawerState(DrawerValue.Closed)
    val scope = rememberCoroutineScope()
    val importLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        homeViewModel.importBudget(uri?.let(context.contentResolver::openInputStream))
    }

    fun navigateBack() {
        if (navController.previousBackStackEntry != null) navController.popBackStack()
    }

    LaunchedEffect(importState.message) {
        importState.message?.let {
            Toast.makeText(context, it, Toast.LENGTH_LONG).show()
            homeViewModel.consumeImportMessage()
        }
    }
    LaunchedEffect(leaveState.failed) {
        if (leaveState.failed) {
            Toast.makeText(context, R.string.family_leave_error, Toast.LENGTH_LONG).show()
            homeViewModel.consumeLeaveFamilyError()
        }
    }

    HomeNavigationDrawer(
        drawerState = drawerState,
        importing = importState.inProgress,
        leavingFamily = leaveState.inProgress,
        onHomeClick = {
            scope.launch {
                drawerState.close()
                navController.popBackStack("home", inclusive = false)
            }
        },
        onAnalysisClick = {
            scope.launch {
                drawerState.close()
                navController.navigate("analysis")
            }
        },
        onTransactionComparisonClick = {
            scope.launch {
                drawerState.close()
                navController.navigate("category-report")
            }
        },
        onExpensesReportClick = {
            scope.launch {
                drawerState.close()
                navController.navigate("transaction-report/expense")
            }
        },
        onIncomesReportClick = {
            scope.launch {
                drawerState.close()
                navController.navigate("transaction-report/income")
            }
        },
        onImportClick = {
            scope.launch {
                drawerState.close()
                importLauncher.launch(
                    arrayOf("text/*", "text/comma-separated-values", "application/octet-stream"),
                )
            }
        },
        onExportClick = {
            scope.launch {
                drawerState.close()
                navController.navigate("data-export")
            }
        },
        onDataImportClick = {
            scope.launch {
                drawerState.close()
                navController.navigate("data-import")
            }
        },
        onCategoriesClick = {
            scope.launch {
                drawerState.close()
                navController.navigate("categories")
            }
        },
        onAccountsClick = {
            scope.launch {
                drawerState.close()
                navController.navigate("accounts")
            }
        },
        onLeaveFamilyClick = homeViewModel::leaveFamily,
        familyCode = homeUiState.familyId,
    ) {
        NavHost(
            navController = navController,
            startDestination = "home",
            enterTransition = { slideInHorizontally(animationSpec = tween(300)) { it } },
            exitTransition = { slideOutHorizontally(animationSpec = tween(300)) { -it } },
            popEnterTransition = { slideInHorizontally(animationSpec = tween(300)) { -it } },
            popExitTransition = { slideOutHorizontally(animationSpec = tween(300)) { it } },
        ) {
            composable("home") {
                HomeScreen(
                    onMenuClick = { scope.launch { drawerState.open() } },
                    onAddClick = { navController.navigate("add") },
                    onItemClick = { id -> navController.navigate("edit/$id") },
                    onAddBalanceClick = { navController.navigate("balances/add") },
                    onEditBalanceClick = { id -> navController.navigate("balances/edit/$id") },
                    onBalanceHistoryClick = { id -> navController.navigate("history/balance/$id") },
                    onAccountHistoryClick = { id -> navController.navigate("history/account/$id") },
                    onSummaryClick = { id, type, days ->
                        navController.navigate("history/balance/$id?type=${type.wire}&days=$days")
                    },
                    onAnalysisClick = { navController.navigate("analysis") },
                    viewModel = homeViewModel,
                )
            }
            composable("category-report") {
                CategoryReportScreen(
                    onBack = { navigateBack() },
                    onItemClick = { id -> navController.navigate("edit/$id") },
                )
            }
            composable("analysis") {
                AnalysisScreen(
                    onBack = { navigateBack() },
                    onOpenCategory = { request ->
                        val accounts = request.accountIds
                            ?.joinToString(",")
                            ?.takeIf { it.isNotEmpty() }
                            ?.let(Uri::encode)
                            .orEmpty()
                        val start = request.startMillis?.toString().orEmpty()
                        val end = request.endMillisExclusive?.toString().orEmpty()
                        navController.navigate(
                            "analysis/category/${Uri.encode(request.categoryId)}" +
                                "?type=${request.type.wire}&start=$start&end=$end" +
                                "&label=${Uri.encode(request.periodLabel)}" +
                                "&balance=${Uri.encode(request.balanceId.orEmpty())}" +
                                "&accounts=${Uri.encode(accounts)}",
                        )
                    },
                )
            }
            composable(
                route = "analysis/category/{categoryId}?type={type}&start={start}&end={end}&label={label}&balance={balance}&accounts={accounts}",
                arguments = listOf(
                    navArgument("categoryId") { type = NavType.StringType },
                    navArgument("type") {
                        type = NavType.StringType
                        defaultValue = "expense"
                    },
                    navArgument("start") {
                        type = NavType.StringType
                        defaultValue = ""
                    },
                    navArgument("end") {
                        type = NavType.StringType
                        defaultValue = ""
                    },
                    navArgument("label") {
                        type = NavType.StringType
                        defaultValue = ""
                    },
                    navArgument("balance") {
                        type = NavType.StringType
                        defaultValue = ""
                    },
                    navArgument("accounts") {
                        type = NavType.StringType
                        defaultValue = ""
                    },
                ),
            ) {
                CategoryOperationsScreen(
                    onBack = { navigateBack() },
                    onItemClick = { id -> navController.navigate("edit/$id") },
                )
            }
            composable(
                route = "transaction-report/{type}",
                arguments = listOf(navArgument("type") { type = NavType.StringType }),
            ) {
                TransactionReportScreen(
                    onBack = { navigateBack() },
                    onItemClick = { id -> navController.navigate("edit/$id") },
                )
            }
            composable("data-export") {
                PlaceholderScreen(title = R.string.drawer_data_export, onBack = { navigateBack() })
            }
            composable("data-import") {
                PlaceholderScreen(title = R.string.drawer_data_import, onBack = { navigateBack() })
            }
            composable("add") {
                AddTransactionScreen(
                    onClose = { navigateBack() },
                    onAddCategoryClick = { type -> navController.navigate("categories/add?type=${type.wire}") },
                )
            }
            composable(
                route = "edit/{transactionId}",
                arguments = listOf(navArgument("transactionId") { type = NavType.StringType }),
            ) {
                AddTransactionScreen(
                    onClose = { navigateBack() },
                    onAddCategoryClick = { type -> navController.navigate("categories/add?type=${type.wire}") },
                )
            }
            composable("accounts") {
                AccountsScreen(
                    onBack = { navigateBack() },
                    onAddClick = { navController.navigate("accounts/add") },
                    onItemClick = { id -> navController.navigate("accounts/edit/$id") },
                )
            }
            composable("accounts/add") {
                EditAccountScreen(onClose = { navigateBack() })
            }
            composable(
                route = "accounts/edit/{accountId}",
                arguments = listOf(navArgument("accountId") { type = NavType.StringType }),
            ) {
                EditAccountScreen(onClose = { navigateBack() })
            }
            composable("categories") {
                CategoriesScreen(
                    onBack = { navigateBack() },
                    onAddClick = { navController.navigate("categories/add") },
                    onItemClick = { id -> navController.navigate("categories/edit/$id") },
                )
            }
            composable(
                route = "categories/add?type={type}",
                arguments = listOf(
                    navArgument("type") {
                        type = NavType.StringType
                        defaultValue = "expense"
                    },
                ),
            ) {
                EditCategoryScreen(onClose = { navigateBack() })
            }
            composable(
                route = "categories/edit/{categoryId}",
                arguments = listOf(navArgument("categoryId") { type = NavType.StringType }),
            ) {
                EditCategoryScreen(onClose = { navigateBack() })
            }
            composable("balances/add") {
                EditBalanceScreen(onClose = { navigateBack() })
            }
            composable(
                route = "history/balance/{balanceId}?type={type}&days={days}",
                arguments = listOf(
                    navArgument("balanceId") { type = NavType.StringType },
                    navArgument("type") {
                        type = NavType.StringType
                        nullable = true
                    },
                    navArgument("days") {
                        type = NavType.IntType
                        defaultValue = 0
                    },
                ),
            ) {
                HistoryScreen(
                    onBack = { navigateBack() },
                    onAddClick = { navController.navigate("add") },
                    onItemClick = { id -> navController.navigate("edit/$id") },
                )
            }
            composable(
                route = "history/account/{accountId}",
                arguments = listOf(navArgument("accountId") { type = NavType.StringType }),
            ) {
                HistoryScreen(
                    onBack = { navigateBack() },
                    onAddClick = { navController.navigate("add") },
                    onItemClick = { id -> navController.navigate("edit/$id") },
                )
            }
            composable(
                route = "balances/edit/{balanceId}",
                arguments = listOf(navArgument("balanceId") { type = NavType.StringType }),
            ) {
                EditBalanceScreen(onClose = { navigateBack() })
            }
        }
    }
}
