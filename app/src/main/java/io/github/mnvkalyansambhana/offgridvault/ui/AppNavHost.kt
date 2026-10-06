package io.github.mnvkalyansambhana.offgridvault.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
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
import io.github.mnvkalyansambhana.offgridvault.ui.vault.EntryDetailScreen
import io.github.mnvkalyansambhana.offgridvault.ui.vault.EntryDetailViewModel
import io.github.mnvkalyansambhana.offgridvault.ui.vault.EntryEditScreen
import io.github.mnvkalyansambhana.offgridvault.ui.vault.EntryEditViewModel
import io.github.mnvkalyansambhana.offgridvault.ui.vault.AboutScreen
import io.github.mnvkalyansambhana.offgridvault.ui.vault.SettingsScreen
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
    const val SETTINGS = "settings"
    const val ABOUT = "about"
    const val ENTRY = "entry/{id}"
    const val EDIT = "edit/{id}"
    const val NEW_ENTRY_ID = "new"

    fun entry(id: String) = "entry/$id"
    fun edit(id: String?) = "edit/${id ?: NEW_ENTRY_ID}"

    /** Screens that show or change vault content: leave them the moment the session locks. */
    val NEEDS_UNLOCKED = setOf(VAULT, RECOVERY_LATER, CHANGE_PIN, SETTINGS, ABOUT, ENTRY, EDIT)
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
                onOpenEntry = { nav.navigate(Routes.entry(it)) },
                onAdd = { nav.navigate(Routes.edit(null)) },
                onSettings = { nav.navigate(Routes.SETTINGS) },
                onLock = {
                    app.session.lock()
                    nav.replaceWith(Routes.UNLOCK)
                },
            )
        }
        composable(Routes.ENTRY) { backStack ->
            val id = backStack.arguments?.getString("id").orEmpty()
            val vm = viewModel(key = "entry-$id") { EntryDetailViewModel(app, id) }
            EntryDetailScreen(vm, app.session, onBack = { nav.popBackStack() }, onEdit = { nav.navigate(Routes.edit(id)) })
        }
        composable(Routes.EDIT) { backStack ->
            val id = backStack.arguments?.getString("id")?.takeIf { it != Routes.NEW_ENTRY_ID }
            val vm = viewModel(key = "edit-${id ?: "new"}") { EntryEditViewModel(app, id) }
            EntryEditScreen(vm, onDone = { nav.popBackStack() }, onCancel = { nav.popBackStack() })
        }
        composable(Routes.SETTINGS) {
            val state by app.session.state.collectAsState()
            val unlocked = state as? VaultSession.State.Unlocked
            SettingsScreen(
                hasRecoveryWords = unlocked?.let { app.keys.hasRecovery(it.header) } ?: true,
                onBack = { nav.popBackStack() },
                onChangePin = { nav.navigate(Routes.CHANGE_PIN) },
                onSetUpRecovery = { nav.navigate(Routes.RECOVERY_LATER) },
                onLockNow = {
                    app.session.lock()
                    nav.replaceWith(Routes.UNLOCK)
                },
                onAbout = { nav.navigate(Routes.ABOUT) },
            )
        }
        composable(Routes.ABOUT) { AboutScreen(onBack = { nav.popBackStack() }) }
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
