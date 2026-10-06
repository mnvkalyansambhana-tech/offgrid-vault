package io.github.mnvkalyansambhana.offgridvault.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import io.github.mnvkalyansambhana.offgridvault.AppContainer
import io.github.mnvkalyansambhana.offgridvault.ui.recovery.RecoveryLaterScreen
import io.github.mnvkalyansambhana.offgridvault.ui.recovery.RecoveryLaterViewModel
import io.github.mnvkalyansambhana.offgridvault.ui.setup.SetupScreen
import io.github.mnvkalyansambhana.offgridvault.ui.setup.SetupViewModel
import io.github.mnvkalyansambhana.offgridvault.ui.unlock.UnlockScreen
import io.github.mnvkalyansambhana.offgridvault.ui.unlock.UnlockViewModel
import io.github.mnvkalyansambhana.offgridvault.ui.vault.VaultHomeScreen

private object Routes {
    const val SETUP = "setup"
    const val UNLOCK = "unlock"
    const val VAULT = "vault"
    const val RECOVERY_LATER = "recovery-later"
}

/** Single-activity navigation (T3). Each destination replaces the back stack: no going "back" into setup or past a lock. */
@Composable
fun AppNavHost(app: AppContainer) {
    val nav = rememberNavController()
    val start = remember { if (app.repository.hasVault()) Routes.UNLOCK else Routes.SETUP }
    NavHost(navController = nav, startDestination = start) {
        composable(Routes.SETUP) {
            val vm = viewModel { SetupViewModel(app) }
            SetupScreen(vm, onCreated = { nav.replaceWith(Routes.VAULT) })
        }
        composable(Routes.UNLOCK) {
            val vm = viewModel { UnlockViewModel(app) }
            UnlockScreen(
                vm,
                onUnlocked = { nav.replaceWith(Routes.VAULT) },
                onNoVault = { nav.replaceWith(Routes.SETUP) },
            )
        }
        composable(Routes.VAULT) {
            VaultHomeScreen(
                session = app.session,
                hasRecovery = app.keys::hasRecovery,
                onSetUpRecovery = { nav.navigate(Routes.RECOVERY_LATER) },
                onLock = { nav.replaceWith(Routes.UNLOCK) },
            )
        }
        composable(Routes.RECOVERY_LATER) {
            val vm = viewModel { RecoveryLaterViewModel(app) }
            RecoveryLaterScreen(vm, onDone = { nav.popBackStack() }, onCancel = { nav.popBackStack() })
        }
    }
}

private fun NavHostController.replaceWith(route: String) = navigate(route) {
    popUpTo(graph.id) { inclusive = true }
    launchSingleTop = true
}
