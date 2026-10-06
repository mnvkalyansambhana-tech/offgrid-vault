package io.github.mnvkalyansambhana.offgridvault.core.vault

import io.github.mnvkalyansambhana.offgridvault.core.crypto.DecryptionFailedException
import okio.ByteString.Companion.toByteString
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test
import java.nio.ByteBuffer

class VaultFormatTest {

    private val key = TestKey()
    private val header = header(generation = 7)
    private val vault = vaultOf("github", "bank")
    private val file = VaultFormat.seal(header, vault, key.aead())

    @Test
    fun roundTrip_preservesHeaderAndEntries() {
        val sealed = VaultFormat.parse(file)
        assertEquals(VaultFormat.CURRENT_VERSION, sealed.formatVersion)
        assertEquals(header, sealed.header)
        assertEquals(2, sealed.argon2Params.iterations)
        assertEquals(vault, VaultFormat.open(sealed, key.aead()))
    }

    @Test
    fun layout_startsWithMagicAndVersion() {
        assertEquals("PVLT", String(file.copyOfRange(0, 4), Charsets.US_ASCII))
        assertEquals(1, ByteBuffer.wrap(file, 4, 2).short.toInt())
    }

    @Test
    fun flippingAnyByte_isDetected() {
        // Every byte: magic, version, header length, header, nonce, ciphertext, tag.
        for (i in file.indices) {
            val tampered = file.copyOf().also { it[i] = (it[i].toInt() xor 0x01).toByte() }
            val failed = runCatching { VaultFormat.open(VaultFormat.parse(tampered), key.aead()) }.exceptionOrNull()
            assertTrue(
                "byte $i: unexpected ${failed?.javaClass}",
                failed is CorruptVaultException ||
                    failed is DecryptionFailedException ||
                    failed is UnsupportedVaultVersionException,
            )
        }
    }

    @Test
    fun wrongKey_isRejected() {
        assertThrows(DecryptionFailedException::class.java) {
            VaultFormat.open(VaultFormat.parse(file), TestKey().aead())
        }
    }

    @Test
    fun truncatedOrGarbage_isCorrupt() {
        listOf(ByteArray(0), file.copyOf(9), file.copyOf(file.size - 1), "hello".toByteArray()).forEach { bad ->
            val failed = runCatching { VaultFormat.open(VaultFormat.parse(bad), key.aead()) }.exceptionOrNull()
            assertTrue(failed is CorruptVaultException || failed is DecryptionFailedException)
        }
    }

    @Test
    fun hugeHeaderLength_isRejectedWithoutAllocating() {
        val bad = file.copyOf().also { ByteBuffer.wrap(it).putInt(6, Int.MAX_VALUE) }
        assertThrows(CorruptVaultException::class.java) { VaultFormat.parse(bad) }
    }

    @Test
    fun newerFormat_isReportedAsUnsupported() {
        val newer = file.copyOf().also { ByteBuffer.wrap(it).putShort(4, 2) }
        val e = assertThrows(UnsupportedVaultVersionException::class.java) { VaultFormat.parse(newer) }
        assertEquals(2, e.version)
    }

    @Test
    fun weakOrMalformedHeaders_areRefused() {
        val bad = listOf(
            header.copy(argon2_iterations = 1),
            header.copy(argon2_memory_kib = 1024),
            header.copy(pin_salt = ByteArray(8).toByteString()),
            header.copy(recovery_salt = ByteArray(0).toByteString()),
            header.copy(wrapped_key_pin = ByteArray(0).toByteString()),
            header.copy(generation = 0),
        )
        bad.forEach { h -> assertThrows(CorruptVaultException::class.java) { VaultFormat.seal(h, vault, key.aead()) } }
    }

    @Test
    fun emptyVault_roundTrips() {
        val empty = vaultOf()
        assertEquals(empty, VaultFormat.open(VaultFormat.parse(VaultFormat.seal(header, empty, key.aead())), key.aead()))
    }
}
