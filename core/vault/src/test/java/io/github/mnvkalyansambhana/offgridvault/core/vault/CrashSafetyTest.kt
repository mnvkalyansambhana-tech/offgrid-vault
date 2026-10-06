package io.github.mnvkalyansambhana.offgridvault.core.vault

import io.github.mnvkalyansambhana.offgridvault.core.vault.VaultRepository.OpenResult
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File
import java.io.IOException

/**
 * M2 exit test "kill the process mid-save": the save is cut off before each file operation in
 * turn (and with a half-written temp file), then the vault is reopened with real I/O. It must
 * always open — as the old or the new version, never neither.
 */
class CrashSafetyTest {

    @get:Rule val tmp = TemporaryFolder()

    private class Crash : IOException("simulated power loss")

    /** Real file ops that "lose power" when operation number [crashAt] starts. */
    private class CrashingOps(private val crashAt: Int, private val tornWrite: Boolean) : VaultStore.FileOps {
        var count = 0
        override fun read(file: File) = VaultStore.FileOps.Real.read(file)
        override fun writeAndSync(file: File, bytes: ByteArray) {
            if (++count == crashAt) {
                if (tornWrite) file.writeBytes(bytes.copyOf(bytes.size / 2))
                throw Crash()
            }
            VaultStore.FileOps.Real.writeAndSync(file, bytes)
        }
        override fun moveAtomically(from: File, to: File) {
            if (++count == crashAt) throw Crash()
            VaultStore.FileOps.Real.moveAtomically(from, to)
        }
        override fun delete(file: File) = VaultStore.FileOps.Real.delete(file)
    }

    private fun scenario(crashAt: Int, tornWrite: Boolean, sensitive: Boolean, firstSave: Boolean) {
        val dir = tmp.newFolder()
        val key = TestKey()
        var oldTitles: List<String>? = null
        if (!firstSave) {
            val repo = VaultRepository(VaultStore(dir))
            repo.create(header(), vaultOf("old"), key.aead())
            repo.save(header(), vaultOf("old"), key.aead()) // so vault.prev exists too
            oldTitles = listOf("old")
        }
        val crashing = CrashingOps(crashAt, tornWrite)
        val repo = VaultRepository(VaultStore(dir, crashing))
        val crashed = runCatching {
            if (firstSave) repo.create(header(), vaultOf("new"), key.aead())
            else repo.save(header(generation = 2), vaultOf("new"), key.aead(), sensitive)
        }.exceptionOrNull()
        if (crashed != null && crashed !is Crash) throw crashed

        when (val result = VaultRepository(VaultStore(dir)).open(key.unlocker)) {
            is OpenResult.Opened -> {
                val titles = result.vault.titles()
                assertTrue("crashAt=$crashAt got $titles", titles == listOf("new") || titles == oldTitles)
            }
            OpenResult.NoVault -> assertTrue("only an unfinished first save may leave no vault", firstSave)
            else -> fail("crashAt=$crashAt torn=$tornWrite sensitive=$sensitive first=$firstSave → $result")
        }
    }

    @Test
    fun everyCrashPoint_leavesAnOpenableVault() {
        for (sensitive in listOf(false, true)) {
            for (crashAt in 1..6) {
                for (torn in listOf(false, true)) {
                    scenario(crashAt, torn, sensitive, firstSave = false)
                }
            }
        }
    }

    @Test
    fun crashDuringFirstSave_meansNoVaultYet() {
        for (crashAt in 1..3) for (torn in listOf(false, true)) scenario(crashAt, torn, sensitive = false, firstSave = true)
    }
}
