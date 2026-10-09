package io.github.mnvkalyansambhana.offgridvault.autofill

import io.github.mnvkalyansambhana.offgridvault.core.crypto.Randomness
import io.github.mnvkalyansambhana.offgridvault.core.crypto.wipe
import java.util.concurrent.ConcurrentHashMap

/** A login the user asked to save (P11). Holds the typed password until the save screen takes it. */
class PendingSave(
    val requester: Requester,
    /** App name, or the website host. */
    val label: String,
    val username: String,
    password: ByteArray,
) {
    private var secret: ByteArray? = password

    /** The caller owns and must wipe the result. */
    fun password(): ByteArray? = secret?.copyOf()

    fun wipe() {
        secret?.wipe()
        secret = null
    }

    override fun toString() = "PendingSave(██)"
}

/**
 * Hands a [PendingSave] from the autofill service to `AutofillActivity` **inside this process**:
 * the intent carries only a random token, never the password (it would pass through the system).
 * One-shot, and anything not taken within [TTL_MILLIS] is wiped.
 */
object PendingSaves {
    private const val TTL_MILLIS = 5 * 60 * 1000L
    private class Slot(val save: PendingSave, val at: Long)
    private val slots = ConcurrentHashMap<String, Slot>()

    fun put(save: PendingSave, nowMillis: Long = System.currentTimeMillis()): String {
        purge(nowMillis)
        val token = Randomness.bytes(16).joinToString("") { "%02x".format(it) }
        slots[token] = Slot(save, nowMillis)
        return token
    }

    fun take(token: String, nowMillis: Long = System.currentTimeMillis()): PendingSave? {
        purge(nowMillis)
        return slots.remove(token)?.save
    }

    private fun purge(nowMillis: Long) {
        slots.entries.removeIf { (_, slot) -> (nowMillis - slot.at > TTL_MILLIS).also { if (it) slot.save.wipe() } }
    }
}
