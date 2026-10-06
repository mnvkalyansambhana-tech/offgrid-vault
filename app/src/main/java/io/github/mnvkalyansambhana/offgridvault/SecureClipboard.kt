package io.github.mnvkalyansambhana.offgridvault

import android.content.ClipData
import android.content.ClipDescription
import android.content.ClipboardManager
import android.content.Context
import android.os.Build
import android.os.PersistableBundle
import io.github.mnvkalyansambhana.offgridvault.core.vault.VaultSession
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.io.File

/**
 * Copying secrets (S11, S18): the clip is marked sensitive (no preview in Android 13+'s
 * "copied" pop-up, kept out of keyboard clipboard history where honoured), and cleared after
 * [CLEAR_AFTER_MILLIS] or when the vault locks, whichever comes first. Clearing doesn't read the
 * clipboard first (background apps can't), so a newer clip copied within those seconds is cleared
 * too — accepted in S18. A marker file survives process death: if the app was killed before the
 * timer fired, the clipboard is cleared on the next start.
 */
class SecureClipboard(context: Context, session: VaultSession) {

    private val clipboard = context.getSystemService(ClipboardManager::class.java)
    private val pendingMarker = File(context.noBackupFilesDir, "clip.pending")
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private var timer: Job? = null

    init {
        if (pendingMarker.exists()) clear()
        scope.launch {
            session.state.collect { if (it is VaultSession.State.Locked && pendingMarker.exists()) clear() }
        }
    }

    /** [secret] becomes a String inside ClipData — unavoidable for the clipboard (S25). */
    fun copySecret(label: String, secret: CharSequence) {
        val clip = ClipData.newPlainText(label, secret)
        clip.description.extras = PersistableBundle().apply {
            putBoolean(
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) ClipDescription.EXTRA_IS_SENSITIVE else LEGACY_SENSITIVE_KEY,
                true,
            )
        }
        pendingMarker.writeText("1")
        clipboard.setPrimaryClip(clip)
        timer?.cancel()
        timer = scope.launch {
            delay(CLEAR_AFTER_MILLIS)
            clear()
        }
    }

    /** Copies non-secret text (a username) — not marked sensitive, but cleared the same way. */
    fun copyPlain(label: String, text: CharSequence) {
        pendingMarker.writeText("1")
        clipboard.setPrimaryClip(ClipData.newPlainText(label, text))
        timer?.cancel()
        timer = scope.launch {
            delay(CLEAR_AFTER_MILLIS)
            clear()
        }
    }

    fun clear() {
        timer?.cancel()
        runCatching { clipboard.clearPrimaryClip() }
        pendingMarker.delete()
    }

    companion object {
        /** S11. */
        const val CLEAR_AFTER_MILLIS = 30_000L
        const val CLEAR_AFTER_SECONDS = 30
        private const val LEGACY_SENSITIVE_KEY = "android.content.extra.IS_SENSITIVE"
    }
}
