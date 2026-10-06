package io.github.mnvkalyansambhana.offgridvault.core.vault

import io.github.mnvkalyansambhana.offgridvault.core.crypto.AeadKey
import io.github.mnvkalyansambhana.offgridvault.core.vault.proto.Vault
import io.github.mnvkalyansambhana.offgridvault.core.vault.proto.VaultHeader
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * The single source of truth for "is the vault open?" (T3), shared by the UI and — later — the
 * autofill service. Locking closes the session key and drops every reference to vault content.
 * Auto-lock timers (S23) arrive in M4.
 */
class VaultSession {

    sealed interface State {
        data object Locked : State

        class Unlocked(
            val vault: Vault,
            val header: VaultHeader,
            val key: AeadKey,
            /** Opened from vault.prev — show "Restored from previous save" once (C18). */
            val restoredFromPrevious: Boolean,
        ) : State
    }

    private val mutableState = MutableStateFlow<State>(State.Locked)
    val state: StateFlow<State> = mutableState.asStateFlow()

    fun unlocked(opened: VaultRepository.OpenResult.Opened) {
        lock()
        mutableState.value = State.Unlocked(opened.vault, opened.header, opened.key, opened.restoredFromPrevious)
    }

    /** After a save: same key, new header and/or content. */
    fun updated(header: VaultHeader, vault: Vault) {
        val current = mutableState.value as? State.Unlocked ?: return
        mutableState.value = State.Unlocked(vault, header, current.key, restoredFromPrevious = false)
    }

    fun lock() {
        (mutableState.value as? State.Unlocked)?.key?.close()
        mutableState.value = State.Locked
    }
}
