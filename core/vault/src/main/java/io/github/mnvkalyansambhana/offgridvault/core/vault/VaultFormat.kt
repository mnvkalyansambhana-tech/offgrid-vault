package io.github.mnvkalyansambhana.offgridvault.core.vault

import io.github.mnvkalyansambhana.offgridvault.core.crypto.AeadKey
import io.github.mnvkalyansambhana.offgridvault.core.crypto.Argon2Params
import io.github.mnvkalyansambhana.offgridvault.core.crypto.Argon2id
import io.github.mnvkalyansambhana.offgridvault.core.crypto.RecoveryKdf
import io.github.mnvkalyansambhana.offgridvault.core.vault.proto.Vault
import io.github.mnvkalyansambhana.offgridvault.core.vault.proto.VaultHeader
import java.io.IOException
import java.nio.ByteBuffer

/**
 * Vault file format v1 (SECURITY_DESIGN.md §4):
 *
 * ```
 * "PVLT" | format_version u16 | header_len u32 | header (Wire VaultHeader) | AES-256-GCM payload
 * \______________________ associated data (C6) ______________________/
 * ```
 * The payload is `nonce ‖ ciphertext ‖ tag` of a Wire `Vault`, under the DEK, with every byte
 * before it as associated data — so any change to magic, version or header fails decryption.
 */
object VaultFormat {
    const val CURRENT_VERSION = 1
    const val MAX_HEADER_BYTES = 16 * 1024
    const val MAX_FILE_BYTES = 64 * 1024 * 1024
    const val MAX_WRAPPED_KEY_BYTES = 1024

    private val MAGIC = byteArrayOf('P'.code.toByte(), 'V'.code.toByte(), 'L'.code.toByte(), 'T'.code.toByte())
    private const val PREFIX_BYTES = 4 + 2 + 4

    /**
     * A parsed file whose header has been range-checked but is **not yet authenticated**:
     * it becomes trustworthy only once [open] succeeds. Use it to pick unlock parameters.
     */
    class Sealed internal constructor(
        val formatVersion: Int,
        val header: VaultHeader,
        internal val associatedData: ByteArray,
        internal val payload: ByteArray,
    ) {
        val argon2Params: Argon2Params
            get() = Argon2Params(header.argon2_iterations, header.argon2_memory_kib)
    }

    fun seal(header: VaultHeader, vault: Vault, key: AeadKey): ByteArray {
        validateHeader(header)
        val headerBytes = VaultHeader.ADAPTER.encode(header)
        val associatedData = ByteBuffer.allocate(PREFIX_BYTES + headerBytes.size)
            .put(MAGIC)
            .putShort(CURRENT_VERSION.toShort())
            .putInt(headerBytes.size)
            .put(headerBytes)
            .array()
        val payload = key.encrypt(Vault.ADAPTER.encode(vault), associatedData)
        return associatedData + payload
    }

    /** @throws CorruptVaultException or [UnsupportedVaultVersionException]; never decrypts. */
    fun parse(file: ByteArray): Sealed {
        if (file.size > MAX_FILE_BYTES) throw CorruptVaultException("file too large")
        if (file.size < PREFIX_BYTES) throw CorruptVaultException("file too short")
        val buffer = ByteBuffer.wrap(file)
        val magic = ByteArray(MAGIC.size).also(buffer::get)
        if (!magic.contentEquals(MAGIC)) throw CorruptVaultException("not a vault file")
        val version = buffer.short.toInt() and 0xFFFF
        if (version > CURRENT_VERSION) throw UnsupportedVaultVersionException(version)
        if (version < 1) throw CorruptVaultException("bad format version")
        val headerLength = buffer.int
        if (headerLength !in 1..MAX_HEADER_BYTES || headerLength > buffer.remaining()) {
            throw CorruptVaultException("bad header length")
        }
        val headerBytes = ByteArray(headerLength).also(buffer::get)
        val header = try {
            VaultHeader.ADAPTER.decode(headerBytes)
        } catch (_: IOException) {
            throw CorruptVaultException("unreadable header")
        }
        validateHeader(header)
        // Migration hook: when format v2 exists, convert older headers here and let the caller
        // re-save (Sealed.formatVersion < CURRENT_VERSION).
        val associatedData = file.copyOfRange(0, PREFIX_BYTES + headerLength)
        val payload = file.copyOfRange(PREFIX_BYTES + headerLength, file.size)
        return Sealed(version, header, associatedData, payload)
    }

    /**
     * Decrypts and decodes the payload, authenticating the header at the same time.
     * @throws io.github.mnvkalyansambhana.offgridvault.core.crypto.DecryptionFailedException
     *   if the key is wrong or any byte of the file was changed.
     */
    fun open(sealed: Sealed, key: AeadKey): Vault {
        val plaintext = key.decrypt(sealed.payload, sealed.associatedData)
        return try {
            Vault.ADAPTER.decode(plaintext)
        } catch (_: IOException) {
            throw CorruptVaultException("unreadable payload")
        } finally {
            plaintext.fill(0)
        }
    }

    private fun validateHeader(header: VaultHeader) {
        fun check(condition: Boolean, what: String) {
            if (!condition) throw CorruptVaultException("invalid header: $what")
        }
        check(header.generation >= 1, "generation")
        check(
            runCatching { Argon2Params(header.argon2_iterations, header.argon2_memory_kib) }.isSuccess,
            "argon2 parameters",
        )
        check(header.pin_salt.size == Argon2id.SALT_BYTES, "pin salt")
        check(header.recovery_salt.size == RecoveryKdf.SALT_BYTES, "recovery salt")
        check(header.wrapped_key_pin.size in 1..MAX_WRAPPED_KEY_BYTES, "pin key")
        // Empty = recovery words not set up yet (S28, P19).
        check(header.wrapped_key_recovery.size <= MAX_WRAPPED_KEY_BYTES, "recovery key")
        check(header.wrapped_key_bio.size <= MAX_WRAPPED_KEY_BYTES, "biometric key")
    }
}

/** The file is damaged or not a vault. Messages describe *where*, never vault content. */
class CorruptVaultException(message: String) : Exception(message)

/** Written by a newer app version (e.g. after a downgrade). */
class UnsupportedVaultVersionException(val version: Int) : Exception("unsupported vault format $version")
