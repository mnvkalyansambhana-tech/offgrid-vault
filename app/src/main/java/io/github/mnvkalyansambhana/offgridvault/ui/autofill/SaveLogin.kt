package io.github.mnvkalyansambhana.offgridvault.ui.autofill

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import io.github.mnvkalyansambhana.offgridvault.AppContainer
import io.github.mnvkalyansambhana.offgridvault.autofill.Matcher
import io.github.mnvkalyansambhana.offgridvault.autofill.PendingSaves
import io.github.mnvkalyansambhana.offgridvault.autofill.SavePlan
import io.github.mnvkalyansambhana.offgridvault.core.crypto.wipe
import io.github.mnvkalyansambhana.offgridvault.core.vault.EntryView
import io.github.mnvkalyansambhana.offgridvault.core.vault.VaultSession
import io.github.mnvkalyansambhana.offgridvault.ui.components.Body
import io.github.mnvkalyansambhana.offgridvault.ui.components.HeroBand
import io.github.mnvkalyansambhana.offgridvault.ui.components.Label
import io.github.mnvkalyansambhana.offgridvault.ui.components.PopButton
import io.github.mnvkalyansambhana.offgridvault.ui.components.SharpCard
import io.github.mnvkalyansambhana.offgridvault.ui.components.TwoToneScreen
import io.github.mnvkalyansambhana.offgridvault.ui.theme.OffGridColors
import io.github.mnvkalyansambhana.offgridvault.ui.theme.OffGridType
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * P11 save (M8). Takes the in-process [io.github.mnvkalyansambhana.offgridvault.autofill.PendingSave]
 * once (a ViewModel, so a rotation doesn't lose it) and wipes it when done or abandoned.
 */
class SaveLoginViewModel(private val app: AppContainer, token: String) : ViewModel() {

    val pending = PendingSaves.take(token)
    var saving by mutableStateOf(false)
        private set
    private val matcher = Matcher()

    fun plan(unlocked: VaultSession.State.Unlocked): SavePlan? {
        val save = pending ?: return null
        val password = save.password() ?: return null
        return try {
            SavePlan.of(unlocked.content, matcher, save, password)
        } finally {
            password.wipe()
        }
    }

    /** P23: the user picked an existing account to update instead of saving a new one. */
    fun planFor(unlocked: VaultSession.State.Unlocked, entry: EntryView): SavePlan? {
        val password = pending?.password() ?: return null
        return try {
            SavePlan.forEntry(unlocked.content, entry, password)
        } finally {
            password.wipe()
        }
    }

    fun save(plan: SavePlan, onDone: () -> Unit) {
        val save = pending ?: return
        saving = true
        viewModelScope.launch {
            withContext(Dispatchers.IO) {
                val unlocked = app.session.state.value as? VaultSession.State.Unlocked ?: return@withContext
                val password = save.password() ?: return@withContext
                try {
                    val next = SavePlan.apply(unlocked.content, plan, save, password, System.currentTimeMillis())
                    if (next !== unlocked.content) app.session.save(app.repository, next)
                } finally {
                    password.wipe()
                }
            }
            save.wipe()
            onDone()
        }
    }

    override fun onCleared() {
        pending?.wipe()
    }
}

/** "Pop · Autofill save". */
@Composable
fun SaveLoginFlow(app: AppContainer, token: String, onDone: () -> Unit, onOpenApp: () -> Unit) {
    val vm = viewModel { SaveLoginViewModel(app, token) }
    BackHandler(onBack = onDone)
    val save = vm.pending
    if (save == null) {
        LaunchedEffect(Unit) { onDone() } // expired or already handled
        return
    }
    RequireUnlocked(app, onCancel = onDone, onOpenApp = onOpenApp) { unlocked ->
        val suggested = remember(unlocked) { vm.plan(unlocked) } ?: return@RequireUnlocked
        // P23: a different (or unknown) username is a new account; updating one is opt-in.
        val savedAccounts = (suggested as? SavePlan.New)?.savedAccounts.orEmpty()
        var chosen by remember(suggested) { mutableStateOf<EntryView?>(null) }
        val plan = chosen?.let { vm.planFor(unlocked, it) } ?: suggested
        TwoToneScreen(
            hero = {
                HeroBand(
                    headline = when (plan) {
                        is SavePlan.Update -> "update password?"
                        is SavePlan.Unchanged -> "already saved."
                        is SavePlan.New -> "save this login?"
                    },
                    label = "OffGrid Vault · Autofill",
                )
            },
        ) {
            SharpCard(Modifier.fillMaxWidth()) {
                Label(save.label, style = OffGridType.LabelSmall)
                Body(save.username.ifEmpty { "(no username)" }, style = OffGridType.BodyStrong)
                Body("••••••••••", style = OffGridType.Secret)
            }
            Body(
                when (plan) {
                    is SavePlan.Update -> "Updates \"${plan.entry.title}\" (${plan.entry.username.ifEmpty { "no username" }}). " +
                        "The old password moves to its history."
                    is SavePlan.Unchanged -> "\"${plan.entry.title}\" already has this password."
                    is SavePlan.New -> if (savedAccounts.isEmpty()) "Saved as a new login." else
                        "Saved as a new login next to your other accounts here."
                },
                color = OffGridColors.TextOnLight2,
            )
            if (savedAccounts.isNotEmpty()) {
                Label("Or update a saved account", style = OffGridType.LabelSmall)
                savedAccounts.forEach { entry ->
                    val selected = chosen?.id == entry.id
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .heightIn(min = 52.dp)
                            .then(if (selected) Modifier.background(OffGridColors.Card).border(1.5.dp, OffGridColors.Ink) else Modifier.border(1.dp, OffGridColors.PaperHairline))
                            .clickable(role = Role.RadioButton) { chosen = if (selected) null else entry }
                            .padding(horizontal = 12.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Column(Modifier.weight(1f)) {
                            Body(entry.username.ifEmpty { "(no username)" }, style = OffGridType.BodyStrong)
                            Body(entry.title, color = OffGridColors.TextOnLight2)
                        }
                        if (selected) Label("✓", color = OffGridColors.Green)
                    }
                }
            }
            Spacer(Modifier.height(8.dp))
            if (plan is SavePlan.Unchanged) {
                PopButton("Done", onClick = onDone, modifier = Modifier.fillMaxWidth())
            } else {
                Row(horizontalArrangement = Arrangement.spacedBy(14.dp), verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        Modifier.weight(1f).heightIn(min = 52.dp).clickable(role = Role.Button, enabled = !vm.saving, onClick = onDone),
                        contentAlignment = Alignment.Center,
                    ) { Label("Not now", color = OffGridColors.Ink) }
                    PopButton(
                        if (plan is SavePlan.Update) "Update" else "Save",
                        onClick = { vm.save(plan, onDone) },
                        enabled = !vm.saving,
                        modifier = Modifier.weight(1f),
                    )
                }
            }
        }
    }
}
