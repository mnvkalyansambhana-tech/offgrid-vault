package io.github.mnvkalyansambhana.offgridvault.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import io.github.mnvkalyansambhana.offgridvault.AppContainer
import io.github.mnvkalyansambhana.offgridvault.core.vault.VaultSession
import io.github.mnvkalyansambhana.offgridvault.ui.erase.EraseScreen
import io.github.mnvkalyansambhana.offgridvault.ui.erase.EraseViewModel
import io.github.mnvkalyansambhana.offgridvault.ui.recovery.RecoverScreen
import io.github.mnvkalyansambhana.offgridvault.ui.recovery.RecoverViewModel
import io.github.mnvkalyansambhana.offgridvault.ui.recovery.RecoveryLaterScreen
import io.github.mnvkalyansambhana.offgridvault.ui.recovery.RecoveryLaterViewModel
import io.github.mnvkalyansambhana.offgridvault.ui.setup.SetupScreen
import io.github.mnvkalyansambhana.offgridvault.ui.setup.SetupViewModel
import io.github.mnvkalyansambhana.offgridvault.ui.unlock.LockedOutScreen
import io.github.mnvkalyansambhana.offgridvault.ui.unlock.UnlockScreen
import io.github.mnvkalyansambhana.offgridvault.ui.unlock.UnlockViewModel
import io.github.mnvkalyansambhana.offgridvault.ui.vault.ChangePinScreen
import io.github.mnvkalyansambhana.offgridvault.ui.vault.ChangePinViewModel
import io.github.mnvkalyansambhana.offgridvault.ui.vault.VaultHomeScreen

private object Routes {
    const val SETUP = "setup"
    const val UNLOCK = "unlock"
    const val LOCKED_OUT = "locked-out"
    const val RECOVER = "recover"
    const val VAULT = "vault"
    const val RECOVERY_LATER = "recovery-later"
    const val CHANGE_PIN = "change-pin"
    const val ERASE = "erase"

    /** Screens that show or change vault content: leave them the moment the session locks. */
    val NEEDS_UNLOCKED = setOf(VAULT, RECOVERY_LATER, CHANGE_PIN)
}

/** Single-activity navigation (T3). Destinations replace the back stack: no going "back" past a lock. */
@Composable
fun AppNavHost(app: AppContainer) {
    val nav = rememberNavController()
    val lockedRoute = { if (app.gate.isLockedOut()) Routes.LOCKED_OUT else Routes.UNLOCK }
    val start = remember { if (!app.repository.hasVault()) Routes.SETUP else lockedRoute() }

    // S23 / S3: when the session locks (screen off, timeout, Lock now, third wrong PIN), go to
    // the unlock (or lockout) screen.
    LaunchedEffect(Unit) {
        app.session.state.collect { state ->
            if (state is VaultSession.State.Locked && nav.currentDestination?.route in Routes.NEEDS_UNLOCKED) {
                nav.replaceWith(lockedRoute())
            }
        }
    }

    NavHost(navController = nav, startDestination = start) {
        composable(Routes.SETUP) {
            val vm = viewModel { SetupViewModel(app) }
            SetupScreen(vm, onCreated = { nav.replaceWith(Routes.VAULT) })
        }
        composable(Routes.UNLOCK) {
            val vm = viewModel { UnlockViewModel(app) }
            UnlockScreen(
                vm,
                nav = object : UnlockViewModel.Navigation {
                    override fun onUnlocked() = nav.replaceWith(Routes.VAULT)
                    override fun onLockedOut() = nav.replaceWith(Routes.LOCKED_OUT)
                    override fun onNoVault() = nav.replaceWith(Routes.SETUP)
                },
                onForgotPin = { nav.navigate(Routes.RECOVER) },
            )
        }
        composable(Routes.LOCKED_OUT) {
            val hasWords = remember { app.pinReset.hasRecoveryWords() }
            LockedOutScreen(
                hasWords,
                onUseRecoveryWords = { nav.navigate(Routes.RECOVER) },
                onErase = { nav.navigate(Routes.ERASE) },
            )
        }
        composable(Routes.RECOVER) {
            val vm = viewModel { RecoverViewModel(app) }
            RecoverScreen(
                vm,
                onDone = { nav.replaceWith(Routes.VAULT) },
                onCancel = { nav.replaceWith(lockedRoute()) },
                onErase = { nav.navigate(Routes.ERASE) },
            )
        }
        composable(Routes.VAULT) {
            VaultHomeScreen(
                session = app.session,
                hasRecovery = app.keys::hasRecovery,
                onSetUpRecovery = { nav.navigate(Routes.RECOVERY_LATER) },
                onChangePin = { nav.navigate(Routes.CHANGE_PIN) },
                onLock = { nav.replaceWith(Routes.UNLOCK) },
            )
        }
        composable(Routes.RECOVERY_LATER) {
            val vm = viewModel { RecoveryLaterViewModel(app) }
            RecoveryLaterScreen(vm, onDone = { nav.popBackStack() }, onCancel = { nav.popBackStack() })
        }
        composable(Routes.ERASE) {
            val vm = viewModel { EraseViewModel(app) }
            EraseScreen(vm, onErased = { nav.replaceWith(Routes.SETUP) }, onCancel = { nav.popBackStack() })
        }
        composable(Routes.CHANGE_PIN) {
            val vm = viewModel { ChangePinViewModel(app) }
            ChangePinScreen(vm, onDone = { nav.popBackStack() }, onCancel = { nav.popBackStack() })
        }
    }
}

private fun NavHostController.replaceWith(route: String) = navigate(route) {
    popUpTo(graph.id) { inclusive = true }
    launchSingleTop = true
}
