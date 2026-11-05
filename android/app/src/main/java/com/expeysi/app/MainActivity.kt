package com.expeysi.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
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
import com.expeysi.app.ui.auth.AuthScreen
import com.expeysi.app.ui.balances.EditBalanceScreen
import com.expeysi.app.ui.categories.CategoriesScreen
import com.expeysi.app.ui.categories.EditCategoryScreen
import com.expeysi.app.ui.family.FamilyScreen
import com.expeysi.app.ui.history.HistoryScreen
import com.expeysi.app.ui.home.HomeScreen
import com.expeysi.app.ui.theme.ExpeysiTheme
import dagger.hilt.android.AndroidEntryPoint

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
    NavHost(navController = navController, startDestination = "home") {
        composable("home") {
            HomeScreen(
                onAddClick = { navController.navigate("add") },
                onItemClick = { id -> navController.navigate("edit/$id") },
                onAccountsClick = { navController.navigate("accounts") },
                onCategoriesClick = { navController.navigate("categories") },
                onAddBalanceClick = { navController.navigate("balances/add") },
                onBalanceHistoryClick = { id -> navController.navigate("history/balance/$id") },
                onAccountHistoryClick = { id -> navController.navigate("history/account/$id") },
                onSummaryClick = { id, type, days ->
                    navController.navigate("history/balance/$id?type=${type.wire}&days=$days")
                },
            )
        }
        composable("add") {
            AddTransactionScreen(onClose = { navController.popBackStack() })
        }
        composable(
            route = "edit/{transactionId}",
            arguments = listOf(navArgument("transactionId") { type = NavType.StringType }),
        ) {
            AddTransactionScreen(onClose = { navController.popBackStack() })
        }
        composable("accounts") {
            AccountsScreen(
                onBack = { navController.popBackStack() },
                onAddClick = { navController.navigate("accounts/add") },
                onItemClick = { id -> navController.navigate("accounts/edit/$id") },
            )
        }
        composable("accounts/add") {
            EditAccountScreen(onClose = { navController.popBackStack() })
        }
        composable(
            route = "accounts/edit/{accountId}",
            arguments = listOf(navArgument("accountId") { type = NavType.StringType }),
        ) {
            EditAccountScreen(onClose = { navController.popBackStack() })
        }
        composable("categories") {
            CategoriesScreen(
                onBack = { navController.popBackStack() },
                onAddClick = { navController.navigate("categories/add") },
                onItemClick = { id -> navController.navigate("categories/edit/$id") },
            )
        }
        composable("categories/add") {
            EditCategoryScreen(onClose = { navController.popBackStack() })
        }
        composable(
            route = "categories/edit/{categoryId}",
            arguments = listOf(navArgument("categoryId") { type = NavType.StringType }),
        ) {
            EditCategoryScreen(onClose = { navController.popBackStack() })
        }
        composable("balances/add") {
            EditBalanceScreen(onClose = { navController.popBackStack() })
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
                onBack = { navController.popBackStack() },
                onItemClick = { id -> navController.navigate("edit/$id") },
            )
        }
        composable(
            route = "history/account/{accountId}",
            arguments = listOf(navArgument("accountId") { type = NavType.StringType }),
        ) {
            HistoryScreen(
                onBack = { navController.popBackStack() },
                onItemClick = { id -> navController.navigate("edit/$id") },
            )
        }
        composable(
            route = "balances/edit/{balanceId}",
            arguments = listOf(navArgument("balanceId") { type = NavType.StringType }),
        ) {
            EditBalanceScreen(onClose = { navController.popBackStack() })
        }
    }
}
