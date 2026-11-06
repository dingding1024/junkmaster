package io.github.junkmaster.app.ui.nav

import androidx.compose.runtime.Composable
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import io.github.junkmaster.app.ui.about.AboutScreen
import io.github.junkmaster.app.ui.confirm.DeepConfirmScreen
import io.github.junkmaster.app.ui.diagnostics.DiagnosticsScreen
import io.github.junkmaster.app.ui.home.HomeScreen
import io.github.junkmaster.app.ui.result.CleanResultScreen
import io.github.junkmaster.app.ui.scan.ScanProgressScreen
import io.github.junkmaster.app.ui.scan.ScanResultScreen
import io.github.junkmaster.app.ui.settings.SettingsScreen
import io.github.junkmaster.app.ui.shizuku.ShizukuSetupScreen

@Composable
fun AppNavHost() {
    val navController: NavHostController = rememberNavController()
    NavHost(navController = navController, startDestination = Routes.HOME) {
        composable(Routes.HOME) { HomeScreen(navController) }
        composable(Routes.SCAN_PROGRESS) { ScanProgressScreen(navController) }
        composable(Routes.SCAN_RESULT) { ScanResultScreen(navController) }
        composable(Routes.DEEP_CONFIRM) { DeepConfirmScreen(navController) }
        composable(Routes.SHIZUKU_SETUP) { ShizukuSetupScreen(navController) }
        composable(Routes.SETTINGS) { SettingsScreen(navController) }
        composable(Routes.CLEAN_RESULT) { CleanResultScreen(navController) }
        composable(Routes.ABOUT) { AboutScreen(navController) }
        composable(Routes.DIAGNOSTICS) { DiagnosticsScreen(navController) }
    }
}
