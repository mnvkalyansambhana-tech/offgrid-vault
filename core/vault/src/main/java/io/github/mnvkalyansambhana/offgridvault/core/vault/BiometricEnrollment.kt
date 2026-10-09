package io.github.mnvkalyansambhana.offgridvault.core.vault

import io.github.mnvkalyansambhana.offgridvault.core.crypto.AeadKey
import io.github.mnvkalyansambhana.offgridvault.core.vault.proto.Vault
import io.github.mnvkalyansambhana.offgridvault.core.vault.proto.VaultHeader
import okio.ByteString
import okio.ByteString.Companion.toByteString

/**
 * Turns fingerprint unlock on and off (S2, M6). Turning it on needs the PIN re-entered first (the
 * caller passes the K_bio-sealed DEK it produced); turning it off needs nothing — it only removes
 * a way in. Both are header changes, so both are full saves (C16); removal is a sensitive save so
 * `vault.prev` keeps no biometric copy either (C18).
 */
class BiometricEnrollment(private val repository: VaultRepository) {

    /** @param sealedDek `K_bio.seal(DEK, ad = VaultKeys.BIO_LABEL)`. */
    fun enable(header: VaultHeader, vault: Vault, key: AeadKey, sealedDek: ByteArray): VaultHeader {
        require(sealedDek.isNotEmpty()) { "empty biometric copy" }
        return repository.save(header.copy(wrapped_key_bio = sealedDek.toByteString()), vault, key)
    }

    fun disable(header: VaultHeader, vault: Vault, key: AeadKey): VaultHeader =
        repository.save(header.copy(wrapped_key_bio = ByteString.EMPTY), vault, key, sensitive = true)
}
