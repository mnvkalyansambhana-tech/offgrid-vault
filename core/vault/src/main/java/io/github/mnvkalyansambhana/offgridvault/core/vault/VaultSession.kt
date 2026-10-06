package io.github.mnvkalyansambhana.offgridvault.core.vault

import io.github.mnvkalyansambhana.offgridvault.core.crypto.AeadKey
import io.github.mnvkalyansambhana.offgridvault.core.crypto.AesGcm
import io.github.mnvkalyansambhana.offgridvault.core.vault.proto.VaultHeader
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * The single source of truth for "is the vault open?" (T3), shared by the UI and — later — the
 * autofill service. While unlocked it holds the DEK ([State.Unlocked.key]) and the contents with
 * every secret sealed under a separate ephemeral session key (S9). Locking closes both keys and
 * drops every reference to vault content. Auto-lock (S23) is driven by the app.
 */
class VaultSession {

    sealed interface State {
        data object Locked : State

        class Unlocked internal constructor(
            val header: VaultHeader,
            /** The DEK, for saving. */
            val key: AeadKey,
            val content: VaultContent,
            internal val sessionKey: AeadKey,
            /** Opened from vault.prev — show "Restored from previous save" once (C18). */
            val restoredFromPrevious: Boolean,
            /** Wrong PINs since the last unlock — show "N wrong PIN attempts…" once (S12). */
            val failedAttemptsBefore: Int,
        ) : State
    }

    private val mutableState = MutableStateFlow<State>(State.Locked)
    val state: StateFlow<State> = mutableState.asStateFlow()

    /** Takes over [opened]: its plaintext vault is re-sealed under a new session key and dropped. */
    fun unlocked(opened: VaultRepository.OpenResult.Opened, failedAttemptsBefore: Int = 0) {
        lock()
        val sessionKey = AeadKey.takeOwnership(AesGcm.newKey())
        mutableState.value = State.Unlocked(
            header = opened.header,
            key = opened.key,
            content = VaultContent.from(opened.vault, sessionKey),
            sessionKey = sessionKey,
            restoredFromPrevious = opened.restoredFromPrevious,
            failedAttemptsBefore = failedAttemptsBefore,
        )
    }

    /** After a header-only save (PIN change, recovery words added). */
    fun updated(header: VaultHeader) {
        val current = current() ?: return
        updated(header, current.content)
    }

    /** After a save: same keys, new header and contents. */
    fun updated(header: VaultHeader, content: VaultContent) {
        val current = current() ?: return
        mutableState.value = State.Unlocked(
            header, current.key, content, current.sessionKey,
            restoredFromPrevious = false,
            failedAttemptsBefore = current.failedAttemptsBefore,
        )
    }

    /** Saves [content] (sensitive = delete / clear history, C18) and publishes the result. */
    fun save(repository: VaultRepository, content: VaultContent, sensitive: Boolean = false) {
        val current = current() ?: throw IllegalStateException("vault is locked")
        val header = repository.save(current.header, content.toVault(), current.key, sensitive)
        updated(header, content)
    }

    fun lock() {
        current()?.let {
            it.key.close()
            it.sessionKey.close()
        }
        mutableState.value = State.Locked
    }

    private fun current(): State.Unlocked? = mutableState.value as? State.Unlocked
}
