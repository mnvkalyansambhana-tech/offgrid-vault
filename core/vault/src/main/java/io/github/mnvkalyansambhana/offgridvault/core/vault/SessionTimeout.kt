package io.github.mnvkalyansambhana.offgridvault.core.vault

/**
 * S23 inactivity rule: the vault locks after [timeoutMillis] without user interaction. Uses a
 * monotonic clock (elapsed time since boot on Android), so changing the phone's clock can't
 * extend a session.
 */
class SessionTimeout(
    private val timeoutMillis: Long = DEFAULT_TIMEOUT_MILLIS,
    private val clock: () -> Long,
) {
    @Volatile private var lastActivity = clock()

    fun touch() {
        lastActivity = clock()
    }

    fun isExpired(): Boolean = clock() - lastActivity >= timeoutMillis

    companion object {
        /** 5 minutes (S7, S23). */
        const val DEFAULT_TIMEOUT_MILLIS = 5 * 60 * 1000L
    }
}
