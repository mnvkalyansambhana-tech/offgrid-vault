package io.github.mnvkalyansambhana.offgridvault.core.vault

import io.github.mnvkalyansambhana.offgridvault.core.crypto.AeadKey
import io.github.mnvkalyansambhana.offgridvault.core.crypto.Randomness
import io.github.mnvkalyansambhana.offgridvault.core.crypto.wipe
import io.github.mnvkalyansambhana.offgridvault.core.vault.proto.Entry
import io.github.mnvkalyansambhana.offgridvault.core.vault.proto.LinkedApp
import io.github.mnvkalyansambhana.offgridvault.core.vault.proto.PasswordHistoryItem
import io.github.mnvkalyansambhana.offgridvault.core.vault.proto.Vault
import okio.ByteString
import okio.ByteString.Companion.toByteString
import java.security.MessageDigest

/**
 * A secret kept encrypted in memory under the session key (S9). Opaque on purpose: it can only be
 * turned back into bytes by [VaultContent.reveal], and it never prints its content.
 */
class SealedSecret internal constructor(internal val blob: ByteArray) {
    val isEmpty: Boolean get() = blob.isEmpty()
    override fun toString() = "██"
}

/** An entry as held while the vault is open: metadata in clear, secrets sealed (S9). */
class EntryView internal constructor(
    val id: String,
    val title: String,
    val username: String,
    val urls: List<String>,
    val linkedApps: List<LinkedApp>,
    val password: SealedSecret,
    val notes: SealedSecret,
    val history: List<HistoryView>,
    val createdAtMillis: Long,
    val updatedAtMillis: Long,
) {
    override fun toString() = "EntryView(id=$id, ██)"
}

class HistoryView internal constructor(val password: SealedSecret, val replacedAtMillis: Long)

/** What the user typed in the add/edit form. The caller wipes [password] and [notes] afterwards. */
class EntryDraft(
    val id: String?,
    val title: String,
    val username: String,
    val urls: List<String>,
    val password: ByteArray,
    val notes: ByteArray,
)

/**
 * The unlocked vault's contents with **every secret re-encrypted under an ephemeral session key**
 * (S9): passwords, notes and history are decrypted only when revealed, copied, edited or saved.
 * Immutable: each edit returns a new [VaultContent] to save. The session key is closed on lock.
 */
class VaultContent private constructor(
    private val sessionKey: AeadKey,
    val entries: List<EntryView>,
) {
    /** Plaintext of [secret]; the caller must wipe it as soon as it's shown/copied. */
    fun reveal(secret: SealedSecret): ByteArray =
        if (secret.isEmpty) ByteArray(0) else sessionKey.decrypt(secret.blob, AD)

    fun entry(id: String): EntryView? = entries.firstOrNull { it.id == id }

    /** Case-insensitive match on title, username and websites; sorted by title. */
    fun search(query: String): List<EntryView> {
        val q = query.trim().lowercase()
        val matches = if (q.isEmpty()) entries else entries.filter { e ->
            e.title.lowercase().contains(q) || e.username.lowercase().contains(q) || e.urls.any { it.lowercase().contains(q) }
        }
        return matches.sortedWith(compareBy(String.CASE_INSENSITIVE_ORDER) { it.title })
    }

    /**
     * Adds a new entry, or updates [EntryDraft.id]. A changed password pushes the old one onto the
     * history (newest first, at most [MAX_HISTORY], C9/C15).
     */
    fun upsert(draft: EntryDraft, nowMillis: Long): VaultContent {
        val existing = draft.id?.let(::entry)
        val password = seal(draft.password)
        val history = if (existing != null && passwordChanged(existing, draft.password) && !existing.password.isEmpty) {
            (listOf(HistoryView(existing.password, nowMillis)) + existing.history).take(MAX_HISTORY)
        } else {
            existing?.history ?: emptyList()
        }
        val updated = EntryView(
            id = existing?.id ?: newId(),
            title = draft.title.trim(),
            username = draft.username.trim(),
            urls = draft.urls.map { it.trim() }.filter { it.isNotEmpty() },
            linkedApps = existing?.linkedApps ?: emptyList(),
            password = password,
            notes = seal(draft.notes),
            history = history,
            createdAtMillis = existing?.createdAtMillis ?: nowMillis,
            updatedAtMillis = nowMillis,
        )
        val others = entries.filter { it.id != updated.id }
        return VaultContent(sessionKey, others + updated)
    }

    /** P12: gone for good (save it as a sensitive save, C18). */
    fun delete(id: String): VaultContent = VaultContent(sessionKey, entries.filter { it.id != id })

    /** C15 "Clear history" (save it as a sensitive save, C18). */
    fun clearHistory(id: String): VaultContent = VaultContent(
        sessionKey,
        entries.map { e ->
            if (e.id != id) e else EntryView(
                e.id, e.title, e.username, e.urls, e.linkedApps, e.password, e.notes, emptyList(), e.createdAtMillis, e.updatedAtMillis,
            )
        },
    )

    /** The plaintext Wire message for [VaultRepository.save]. Secrets exist in clear only during the call. */
    fun toVault(): Vault = Vault(
        entries = entries.map { e ->
            Entry(
                id = e.id,
                title = e.title,
                username = e.username,
                urls = e.urls,
                linked_apps = e.linkedApps,
                password = revealToByteString(e.password),
                notes = revealToByteString(e.notes),
                history = e.history.map { PasswordHistoryItem(password = revealToByteString(it.password), replaced_at_millis = it.replacedAtMillis) },
                created_at_millis = e.createdAtMillis,
                updated_at_millis = e.updatedAtMillis,
            )
        },
    )

    private fun revealToByteString(secret: SealedSecret): ByteString {
        val plain = reveal(secret)
        return try {
            plain.toByteString()
        } finally {
            plain.wipe()
        }
    }

    private fun seal(plain: ByteArray): SealedSecret =
        SealedSecret(if (plain.isEmpty()) ByteArray(0) else sessionKey.encrypt(plain, AD))

    private fun passwordChanged(existing: EntryView, newPassword: ByteArray): Boolean {
        val old = reveal(existing.password)
        return try {
            !MessageDigest.isEqual(old, newPassword)
        } finally {
            old.wipe()
        }
    }

    companion object {
        const val MAX_HISTORY = 5
        private val AD = "offgrid-vault/v1/session".toByteArray(Charsets.US_ASCII)

        /**
         * Converts the freshly decrypted vault into session-sealed form. The caller drops [vault]
         * right after (its ByteStrings can't be wiped; they become unreachable).
         */
        fun from(vault: Vault, sessionKey: AeadKey): VaultContent {
            val content = VaultContent(sessionKey, emptyList())
            return VaultContent(
                sessionKey,
                vault.entries.map { e ->
                    EntryView(
                        id = e.id,
                        title = e.title,
                        username = e.username,
                        urls = e.urls,
                        linkedApps = e.linked_apps,
                        password = content.sealByteString(e.password),
                        notes = content.sealByteString(e.notes),
                        history = e.history.map { HistoryView(content.sealByteString(it.password), it.replaced_at_millis) },
                        createdAtMillis = e.created_at_millis,
                        updatedAtMillis = e.updated_at_millis,
                    )
                },
            )
        }

        private fun newId(): String = Randomness.bytes(16).joinToString("") { "%02x".format(it) }
    }

    private fun sealByteString(value: ByteString): SealedSecret {
        val plain = value.toByteArray()
        return try {
            seal(plain)
        } finally {
            plain.wipe()
        }
    }
}
