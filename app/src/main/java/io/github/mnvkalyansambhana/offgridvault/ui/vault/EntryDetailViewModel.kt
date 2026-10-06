package io.github.mnvkalyansambhana.offgridvault.ui.vault

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import io.github.mnvkalyansambhana.offgridvault.AppContainer
import io.github.mnvkalyansambhana.offgridvault.core.crypto.wipe
import io.github.mnvkalyansambhana.offgridvault.core.vault.EntryView
import io.github.mnvkalyansambhana.offgridvault.core.vault.SealedSecret
import io.github.mnvkalyansambhana.offgridvault.core.vault.VaultSession
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Entry detail (S8, S10, S11, C15, P12). Revealed text is held only while visible: 20 s, the
 * timer restarts on each tap of the eye, and leaving the screen/app re-masks immediately.
 */
class EntryDetailViewModel(private val app: AppContainer, private val entryId: String) : ViewModel() {

    enum class Field { Password, Notes }

    var revealedPassword by mutableStateOf<String?>(null)
        private set
    var revealedNotes by mutableStateOf<String?>(null)
        private set
    /** C9: one previous password may be revealed at a time (index into the history list). */
    var revealedHistoryIndex by mutableStateOf<Int?>(null)
        private set
    var revealedHistory by mutableStateOf<String?>(null)
        private set
    var secondsLeft by mutableIntStateOf(0)
        private set
    var copiedMessage by mutableStateOf<String?>(null)
        private set
    var busy by mutableStateOf(false)
        private set

    private var revealTimer: Job? = null
    private var toastTimer: Job? = null

    fun entry(): EntryView? = unlocked()?.content?.entry(entryId)

    /** S10: reveal for 20 s; tapping again while visible hides it, tapping the other field restarts. */
    fun toggleReveal(field: Field) {
        val entry = entry() ?: return
        val visible = when (field) {
            Field.Password -> revealedPassword != null
            Field.Notes -> revealedNotes != null
        }
        if (visible) {
            when (field) {
                Field.Password -> revealedPassword = null
                Field.Notes -> revealedNotes = null
            }
            if (revealedPassword == null && revealedNotes == null && revealedHistory == null) maskAll()
            return
        }
        val text = revealText(if (field == Field.Password) entry.password else entry.notes) ?: return
        when (field) {
            Field.Password -> revealedPassword = text
            Field.Notes -> revealedNotes = text
        }
        startRevealTimer()
    }

    /** C9: previous passwords reveal like the current one (same 20 s timer, S10). */
    fun toggleHistory(index: Int) {
        val item = entry()?.history?.getOrNull(index) ?: return
        if (revealedHistoryIndex == index) {
            revealedHistoryIndex = null
            revealedHistory = null
            if (revealedPassword == null && revealedNotes == null) maskAll()
            return
        }
        val text = revealText(item.password) ?: return
        revealedHistoryIndex = index
        revealedHistory = text
        startRevealTimer()
    }

    fun copyHistory(index: Int) {
        val item = entry()?.history?.getOrNull(index) ?: return
        val text = revealText(item.password) ?: return
        app.clipboard.copySecret("Previous password", text)
        showToast("Copied · clipboard clears in 30s")
    }

    fun maskAll() {
        revealTimer?.cancel()
        revealedPassword = null
        revealedNotes = null
        revealedHistoryIndex = null
        revealedHistory = null
        secondsLeft = 0
    }

    fun copyPassword() {
        val entry = entry() ?: return
        val text = revealText(entry.password) ?: return
        app.clipboard.copySecret("Password", text)
        showToast("Copied · clipboard clears in 30s")
    }

    fun copyUsername() {
        val entry = entry() ?: return
        app.clipboard.copyPlain("Username", entry.username)
        showToast("Username copied · clears in 30s")
    }

    fun delete(onDone: () -> Unit) = mutate(sensitive = true, onDone) { it.delete(entryId) }

    fun clearHistory() = mutate(sensitive = true, onDone = {}) { it.clearHistory(entryId) }

    private fun mutate(
        sensitive: Boolean,
        onDone: () -> Unit,
        change: (io.github.mnvkalyansambhana.offgridvault.core.vault.VaultContent) -> io.github.mnvkalyansambhana.offgridvault.core.vault.VaultContent,
    ) {
        val unlocked = unlocked() ?: return
        busy = true
        maskAll()
        viewModelScope.launch {
            withContext(Dispatchers.IO) { app.session.save(app.repository, change(unlocked.content), sensitive) }
            busy = false
            onDone()
        }
    }

    private fun revealText(secret: SealedSecret): String? {
        if (unlocked() == null) return null
        val bytes = unlocked()!!.content.reveal(secret)
        return try {
            String(bytes, Charsets.UTF_8)
        } finally {
            bytes.wipe()
        }
    }

    private fun startRevealTimer() {
        revealTimer?.cancel()
        secondsLeft = REVEAL_SECONDS
        revealTimer = viewModelScope.launch {
            while (secondsLeft > 0) {
                delay(1_000)
                secondsLeft--
            }
            revealedPassword = null
            revealedNotes = null
            revealedHistoryIndex = null
            revealedHistory = null
        }
    }

    private fun showToast(text: String) {
        copiedMessage = text
        toastTimer?.cancel()
        toastTimer = viewModelScope.launch {
            delay(3_000)
            copiedMessage = null
        }
    }

    private fun unlocked() = app.session.state.value as? VaultSession.State.Unlocked

    override fun onCleared() = maskAll()

    companion object {
        /** S10. */
        const val REVEAL_SECONDS = 20
    }
}
