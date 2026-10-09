package io.github.mnvkalyansambhana.offgridvault.ui.autofill

import android.os.Parcelable
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import io.github.mnvkalyansambhana.offgridvault.AppContainer
import io.github.mnvkalyansambhana.offgridvault.autofill.FillResponses
import io.github.mnvkalyansambhana.offgridvault.autofill.Matcher
import io.github.mnvkalyansambhana.offgridvault.autofill.ParsedRequest
import io.github.mnvkalyansambhana.offgridvault.autofill.Requester
import io.github.mnvkalyansambhana.offgridvault.core.vault.EntryView
import io.github.mnvkalyansambhana.offgridvault.core.vault.VaultSession
import io.github.mnvkalyansambhana.offgridvault.core.vault.proto.LinkedApp
import io.github.mnvkalyansambhana.offgridvault.ui.components.Body
import io.github.mnvkalyansambhana.offgridvault.ui.components.HeroBand
import io.github.mnvkalyansambhana.offgridvault.ui.components.IconButton
import io.github.mnvkalyansambhana.offgridvault.ui.components.Label
import io.github.mnvkalyansambhana.offgridvault.ui.components.OffGridIcon
import io.github.mnvkalyansambhana.offgridvault.ui.components.PopButton
import io.github.mnvkalyansambhana.offgridvault.ui.components.SharpInput
import io.github.mnvkalyansambhana.offgridvault.ui.components.TwoToneScreen
import io.github.mnvkalyansambhana.offgridvault.ui.theme.OffGridColors
import io.github.mnvkalyansambhana.offgridvault.ui.theme.OffGridType
import io.github.mnvkalyansambhana.offgridvault.ui.unlock.UnlockScreen
import io.github.mnvkalyansambhana.offgridvault.ui.unlock.UnlockViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okio.ByteString.Companion.decodeHex

/**
 * Autofill screens (M7, S15, S16): unlock first if needed (same PIN/fingerprint path and lockout
 * as the app), then either hand back the matching suggestions or let the user pick a login.
 */
@Composable
fun AutofillFlow(
    app: AppContainer,
    mode: String,
    request: ParsedRequest,
    requester: Requester?,
    requesterLabel: String,
    responses: FillResponses,
    matcher: Matcher,
    onResult: (Parcelable) -> Unit,
    onCancel: () -> Unit,
    onOpenApp: () -> Unit,
) {
    BackHandler(onBack = onCancel)
    RequireUnlocked(app, onCancel, onOpenApp) { unlocked ->
        if (mode == FillResponses.MODE_UNLOCK) {
            LaunchedEffect(Unit) { onResult(responses.unlocked(request, requester, unlocked.content, matcher)) }
        } else {
            Picker(app, unlocked, request, requester, requesterLabel, onCancel = onCancel) { entry ->
                // After "Remember for this app" the session holds the re-saved content.
                val content = (app.session.state.value as? VaultSession.State.Unlocked ?: unlocked).content
                onResult(responses.filled(request, entry, content))
            }
        }
    }
}

/** Same PIN/fingerprint path and S3 lockout as the app; [content] runs once the vault is open. */
@Composable
fun RequireUnlocked(
    app: AppContainer,
    onCancel: () -> Unit,
    onOpenApp: () -> Unit,
    content: @Composable (VaultSession.State.Unlocked) -> Unit,
) {
    val state by app.session.state.collectAsState()
    var lockedOut by remember { mutableStateOf(app.gate.isLockedOut()) }
    val unlocked = state as? VaultSession.State.Unlocked
    when {
        unlocked != null -> content(unlocked)
        lockedOut -> LockedOutNotice(onOpenApp, onCancel)
        else -> {
            val vm = viewModel { UnlockViewModel(app) }
            UnlockScreen(
                vm,
                nav = object : UnlockViewModel.Navigation {
                    override fun onUnlocked() = Unit // the session state change moves on
                    override fun onLockedOut() {
                        lockedOut = true
                    }
                    override fun onNoVault() = onCancel()
                },
                onForgotPin = onOpenApp,
            )
        }
    }
}

@Composable
private fun LockedOutNotice(onOpenApp: () -> Unit, onCancel: () -> Unit) {
    TwoToneScreen(
        scrollable = false,
        hero = { HeroBand(headline = "vault locked.", label = "Autofill", labelColor = OffGridColors.Coral) },
    ) {
        Body("After 3 wrong PINs, only your recovery words can unlock the vault. Open OffGrid Vault to use them.")
        Spacer(Modifier.weight(1f))
        PopButton("Open OffGrid Vault", onClick = onOpenApp, modifier = Modifier.fillMaxWidth())
        Box(Modifier.fillMaxWidth().heightIn(min = 44.dp).clickable(role = Role.Button, onClick = onCancel), contentAlignment = Alignment.Center) {
            Label("Cancel", color = OffGridColors.Ink)
        }
    }
}

/** "Pop · Autofill no match": search the whole vault, pick one, optionally remember it for this app (S15). */
@Composable
private fun Picker(
    app: AppContainer,
    unlocked: VaultSession.State.Unlocked,
    request: ParsedRequest,
    requester: Requester?,
    requesterLabel: String,
    onCancel: () -> Unit,
    onDone: (EntryView) -> Unit,
) {
    var query by rememberSaveable { mutableStateOf("") }
    var selectedId by rememberSaveable { mutableStateOf<String?>(null) }
    var rememberForApp by rememberSaveable { mutableStateOf(false) }
    var busy by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    val entries = unlocked.content.search(query)
    // S15 links apps only; S32: never link when the cert couldn't be read (requester == null).
    val appRequester = requester as? Requester.App

    Column(Modifier.fillMaxSize()) {
        HeroBand(
            headline = "fill ${requesterLabel.lowercase()}.",
            label = "Autofill",
            supporting = when (requester) {
                is Requester.Web -> requester.host
                is Requester.App -> "${requester.packageName} · no linked login yet"
                null -> "${request.packageName} · can't verify this app"
            },
            navigation = { IconButton(OffGridIcon.Close, "Cancel", onCancel) },
        )
        LazyColumn(Modifier.weight(1f).fillMaxWidth(), contentPadding = PaddingValues(horizontal = 24.dp, vertical = 12.dp)) {
            item { SharpInput(value = query, onValueChange = { query = it }, label = "Search vault", modifier = Modifier.fillMaxWidth()) }
            if (entries.isEmpty()) {
                item { Body("nothing matches \"$query\".", color = OffGridColors.TextOnLight2, modifier = Modifier.padding(vertical = 24.dp)) }
            }
            items(entries, key = { it.id }) { entry ->
                val selected = entry.id == selectedId
                Row(
                    Modifier
                        .fillMaxWidth()
                        .heightIn(min = 60.dp)
                        .then(if (selected) Modifier.background(OffGridColors.Card).border(1.5.dp, OffGridColors.Ink) else Modifier)
                        .clickable(role = Role.RadioButton) { selectedId = entry.id }
                        .padding(horizontal = 12.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column(Modifier.weight(1f)) {
                        Body(entry.title.ifEmpty { "(untitled)" }, style = OffGridType.BodyStrong)
                        val sub = listOfNotNull(entry.username.ifEmpty { null }, entry.urls.firstOrNull()).joinToString(" · ")
                        if (sub.isNotEmpty()) Body(sub, color = OffGridColors.TextOnLight2)
                    }
                    if (selected) Label("✓", color = OffGridColors.Green)
                }
            }
        }
        Column(
            Modifier.fillMaxWidth().windowInsetsPadding(WindowInsets.navigationBars).padding(horizontal = 24.dp, vertical = 16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            if (appRequester != null) {
                Row(
                    Modifier.fillMaxWidth().heightIn(min = 44.dp).clickable(role = Role.Checkbox) { rememberForApp = !rememberForApp },
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Box(
                        Modifier.size(22.dp).border(1.5.dp, OffGridColors.Ink).then(if (rememberForApp) Modifier.background(OffGridColors.Ink) else Modifier),
                        contentAlignment = Alignment.Center,
                    ) { if (rememberForApp) BasicText("✓", style = OffGridType.BodyStrong.copy(color = OffGridColors.Mint)) }
                    Spacer(Modifier.width(12.dp))
                    Column {
                        Label("Remember for this app", color = OffGridColors.Ink)
                        Body("links this login to the app's name and signature, so look-alike apps won't match.", color = OffGridColors.TextOnLight2)
                    }
                }
            }
            PopButton(
                "Fill",
                enabled = selectedId != null && !busy,
                modifier = Modifier.fillMaxWidth(),
                onClick = {
                    val entry = selectedId?.let(unlocked.content::entry) ?: return@PopButton
                    if (appRequester == null || !rememberForApp) {
                        onDone(entry)
                        return@PopButton
                    }
                    busy = true
                    scope.launch {
                        withContext(Dispatchers.IO) {
                            val current = app.session.state.value as? VaultSession.State.Unlocked ?: return@withContext
                            // S13: pin the cert this app is signed with right now.
                            val link = LinkedApp(appRequester.packageName, appRequester.certs.min().decodeHex())
                            app.session.save(app.repository, current.content.linkApp(entry.id, link, System.currentTimeMillis()))
                        }
                        onDone(entry)
                    }
                },
            )
        }
    }
}
