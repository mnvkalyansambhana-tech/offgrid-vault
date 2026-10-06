package io.github.mnvkalyansambhana.offgridvault.core.vault

import io.github.mnvkalyansambhana.offgridvault.core.crypto.AeadKey
import io.github.mnvkalyansambhana.offgridvault.core.crypto.AesGcm
import io.github.mnvkalyansambhana.offgridvault.core.crypto.Argon2id
import io.github.mnvkalyansambhana.offgridvault.core.crypto.Randomness
import io.github.mnvkalyansambhana.offgridvault.core.crypto.RecoveryKdf
import io.github.mnvkalyansambhana.offgridvault.core.vault.proto.Entry
import io.github.mnvkalyansambhana.offgridvault.core.vault.proto.LinkedApp
import io.github.mnvkalyansambhana.offgridvault.core.vault.proto.PasswordHistoryItem
import io.github.mnvkalyansambhana.offgridvault.core.vault.proto.Vault
import io.github.mnvkalyansambhana.offgridvault.core.vault.proto.VaultHeader
import okio.ByteString.Companion.encodeUtf8
import okio.ByteString.Companion.toByteString

/** A DEK plus an [VaultRepository.Unlocker] that hands out copies of it (or rejects). */
class TestKey {
    private val dek = AesGcm.newKey()
    var accept = true
    var unlockCalls = 0

    fun aead(): AeadKey = AeadKey.takeOwnership(dek.copyOf())

    val unlocker = VaultRepository.Unlocker {
        unlockCalls++
        if (accept) dek.copyOf() else null
    }
}

fun header(generation: Long = 1) = VaultHeader(
    generation = generation,
    argon2_iterations = 2,
    argon2_memory_kib = 64 * 1024,
    pin_salt = Argon2id.newSalt().toByteString(),
    recovery_salt = RecoveryKdf.newSalt().toByteString(),
    // Opaque in M2 (real wrapping arrives in M3).
    wrapped_key_pin = Randomness.bytes(60).toByteString(),
    wrapped_key_recovery = Randomness.bytes(60).toByteString(),
)

fun entry(title: String, password: String = "pw-$title") = Entry(
    id = Randomness.bytes(8).toByteString().hex(),
    title = title,
    username = "user@$title.example",
    urls = listOf("$title.example"),
    linked_apps = listOf(LinkedApp(package_name = "com.$title.app", cert_sha256 = Randomness.bytes(32).toByteString())),
    password = password.encodeUtf8(),
    notes = "note for $title".encodeUtf8(),
    history = listOf(PasswordHistoryItem(password = "old-$title".encodeUtf8(), replaced_at_millis = 1L)),
    created_at_millis = 1_000,
    updated_at_millis = 2_000,
)

fun vaultOf(vararg titles: String) = Vault(entries = titles.map { entry(it) })

fun Vault.titles() = entries.map { it.title }
