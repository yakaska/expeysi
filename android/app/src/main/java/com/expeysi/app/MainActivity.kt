package com.expeysi.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
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
                        HomeScreen(onAddClick = { navController.navigate("add") })
                    }
                    composable("add") {
                        AddTransactionScreen(onClose = { navController.popBackStack() })
                    }
                }
            }
        }
    }
}
