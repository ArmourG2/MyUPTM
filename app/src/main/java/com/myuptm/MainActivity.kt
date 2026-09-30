package com.myuptm

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.myuptm.navigation.MyUptmNavHost
import com.myuptm.navigation.MyUptmRoutes
import com.myuptm.ui.components.BottomNavBar
import com.myuptm.ui.theme.MyUPTMTheme
import com.myuptm.viewmodel.AuthViewModel
import com.myuptm.viewmodel.SettingsViewModel

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            val settingsViewModel: SettingsViewModel = viewModel(factory = SettingsViewModel.Factory)
            val settings by settingsViewModel.settings.collectAsState()

            MyUPTMTheme(
                themeMode = settings.themeMode,
                appFont = settings.appFont
            ) {
                MyUptmApp()
            }
        }
    }
}

@Composable
fun MyUptmApp() {
    val navController = rememberNavController()
    val authViewModel: AuthViewModel = viewModel()
    val userRole by authViewModel.userRole.collectAsState()
    val backStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = backStackEntry?.destination?.route
    val showBottomBar = currentRoute != null && currentRoute != MyUptmRoutes.SIGN_IN


    Scaffold(
        bottomBar = {
            if (showBottomBar && userRole != null) {
                BottomNavBar(
                    navController = navController,
                    userRole = userRole!!
                )
            }
        }
    ) { innerPadding ->
        MyUptmNavHost(
            navController = navController,
            modifier = Modifier.padding(innerPadding)
        )
    }
}