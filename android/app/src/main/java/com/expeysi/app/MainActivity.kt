package com.expeysi.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.expeysi.app.ui.accounts.AccountsScreen
import com.expeysi.app.ui.accounts.EditAccountScreen
import com.expeysi.app.ui.add.AddTransactionScreen
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
                val navController = rememberNavController()
                NavHost(navController = navController, startDestination = "home") {
                    composable("home") {
                        HomeScreen(
                            onAddClick = { navController.navigate("add") },
                            onItemClick = { id -> navController.navigate("edit/$id") },
                            onAccountsClick = { navController.navigate("accounts") },
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
                }
            }
        }
    }
}
