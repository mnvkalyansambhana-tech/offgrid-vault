package io.github.mnvkalyansambhana.offgridvault

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.SystemClock
import androidx.core.content.ContextCompat
import io.github.mnvkalyansambhana.offgridvault.core.vault.SessionTimeout
import io.github.mnvkalyansambhana.offgridvault.core.vault.VaultSession
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * Auto-lock (S7, S23), process-wide so it also covers the app being in the background:
 * - screen off → lock immediately;
 * - 5 minutes without interaction → lock (monotonic clock, checked every few seconds).
 * Switching to another app does **not** lock (copy-paste / autofill workflows).
 */
class SessionGuard(context: Context, private val session: VaultSession) {

    private val timeout = SessionTimeout { SystemClock.elapsedRealtime() }
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)

    init {
        ContextCompat.registerReceiver(
            context.applicationContext,
            object : BroadcastReceiver() {
                override fun onReceive(context: Context, intent: Intent) = session.lock()
            },
            IntentFilter(Intent.ACTION_SCREEN_OFF),
            ContextCompat.RECEIVER_NOT_EXPORTED,
        )
        scope.launch {
            session.state.collect { if (it is VaultSession.State.Unlocked) timeout.touch() }
        }
        scope.launch {
            while (true) {
                delay(CHECK_EVERY_MILLIS)
                if (session.state.value is VaultSession.State.Unlocked && timeout.isExpired()) session.lock()
            }
        }
    }

    fun touch() = timeout.touch()

    private companion object {
        const val CHECK_EVERY_MILLIS = 5_000L
    }
}
