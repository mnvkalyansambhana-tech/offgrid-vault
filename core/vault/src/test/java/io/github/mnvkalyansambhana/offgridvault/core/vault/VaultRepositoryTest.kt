package io.github.mnvkalyansambhana.offgridvault.core.vault

import io.github.mnvkalyansambhana.offgridvault.core.vault.VaultRepository.OpenResult
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File
import java.nio.ByteBuffer

class VaultRepositoryTest {

    @get:Rule val tmp = TemporaryFolder()

    private val key = TestKey()
    private val dir: File get() = tmp.root
    private val repo get() = VaultRepository(VaultStore(dir))
    private val bin get() = File(dir, "vault.bin")
    private val prev get() = File(dir, "vault.prev")

    private fun openOk(): OpenResult.Opened = repo.open(key.unlocker) as OpenResult.Opened

    private fun decrypt(file: File) = VaultFormat.open(VaultFormat.parse(file.readBytes()), key.aead())

    @Test
    fun noFiles_isNoVault() {
        assertEquals(OpenResult.NoVault, repo.open(key.unlocker))
    }

    @Test
    fun createThenOpen() {
        repo.create(header(), vaultOf("github"), key.aead())
        val opened = openOk()
        assertEquals(listOf("github"), opened.vault.titles())
        assertEquals(1L, opened.header.generation)
        assertFalse(opened.restoredFromPrevious)
        assertFalse(prev.exists())
    }

    @Test
    fun save_incrementsGeneration_andKeepsPrevious() {
        repo.create(header(), vaultOf("a"), key.aead())
        val next = repo.save(openOk().header, vaultOf("a", "b"), key.aead())
        assertEquals(2L, next.generation)
        assertEquals(listOf("a", "b"), decrypt(bin).titles())
        assertEquals(listOf("a"), decrypt(prev).titles())
    }

    @Test
    fun sensitiveSave_leavesNoStaleDataInPrevious() {
        repo.create(header(), vaultOf("keep", "secret-to-delete"), key.aead())
        repo.save(openOk().header, vaultOf("keep"), key.aead(), sensitive = true)
        assertArrayEquals(bin.readBytes(), prev.readBytes())
        assertEquals(listOf("keep"), decrypt(prev).titles())
    }

    @Test
    fun corruptCurrent_fallsBackToPrevious_andRepairsBoth() {
        repo.create(header(), vaultOf("v1"), key.aead())
        repo.save(openOk().header, vaultOf("v1", "v2"), key.aead())
        bin.writeBytes(bin.readBytes().also { it[it.size - 5] = (it[it.size - 5].toInt() xor 1).toByte() })

        val opened = openOk()
        assertTrue(opened.restoredFromPrevious)
        assertEquals(listOf("v1"), opened.vault.titles())
        // Re-saved immediately: both files now valid and identical.
        assertEquals(listOf("v1"), decrypt(bin).titles())
        assertArrayEquals(bin.readBytes(), prev.readBytes())
        assertFalse(openOk().restoredFromPrevious)
    }

    @Test
    fun missingCurrent_fallsBackToPrevious() {
        repo.create(header(), vaultOf("v1"), key.aead())
        repo.save(openOk().header, vaultOf("v2"), key.aead())
        bin.delete()
        val opened = openOk()
        assertTrue(opened.restoredFromPrevious)
        assertEquals(listOf("v1"), opened.vault.titles())
    }

    @Test
    fun wrongCredential_isRejected_withoutSecondArgon2RunOnIdenticalHeader() {
        repo.create(header(), vaultOf("a"), key.aead())
        repo.save(openOk().header, vaultOf("a"), key.aead(), sensitive = true)
        key.accept = false
        key.unlockCalls = 0
        assertEquals(OpenResult.Rejected, repo.open(key.unlocker))
        assertEquals(1, key.unlockCalls)
    }

    @Test
    fun bothCorrupt_isUnreadable() {
        repo.create(header(), vaultOf("a"), key.aead())
        repo.save(openOk().header, vaultOf("b"), key.aead())
        bin.writeBytes(byteArrayOf(1, 2, 3))
        prev.writeBytes(byteArrayOf(4, 5, 6))
        assertEquals(OpenResult.Unreadable(), repo.open(key.unlocker))
    }

    @Test
    fun newerFormat_isUnreadableWithVersion() {
        repo.create(header(), vaultOf("a"), key.aead())
        bin.writeBytes(bin.readBytes().also { ByteBuffer.wrap(it).putShort(4, 3) })
        assertEquals(OpenResult.Unreadable(newerFormatVersion = 3), repo.open(key.unlocker))
    }

    @Test
    fun keyIsUsableUntilClosed() {
        repo.create(header(), vaultOf("a"), key.aead())
        val opened = openOk()
        repo.save(opened.header, opened.vault, opened.key)
        opened.key.close()
        assertTrue(opened.key.isClosed)
    }
}
