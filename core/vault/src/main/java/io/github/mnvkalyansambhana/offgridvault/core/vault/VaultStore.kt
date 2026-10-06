package io.github.mnvkalyansambhana.offgridvault.core.vault

import java.io.File
import java.io.FileOutputStream
import java.nio.file.Files
import java.nio.file.StandardCopyOption

/**
 * Crash-safe vault files in app-private storage (C5, C18):
 * - `vault.bin`  — current vault
 * - `vault.prev` — the previous good save (fallback when `vault.bin` is unreadable)
 * - `vault.tmp`  — a save in progress
 *
 * A save is: write + fsync `vault.tmp` → rename `vault.bin` → `vault.prev` → rename
 * `vault.tmp` → `vault.bin`. Renames are atomic, so after a crash at any point either the old
 * or the new vault is intact. Same guarantees as `androidx.core.util.AtomicFile`, but plain
 * JVM I/O so every crash point can be tested.
 */
class VaultStore(private val directory: File, private val files: FileOps = FileOps.Real) {

    private val current = File(directory, "vault.bin")
    private val previous = File(directory, "vault.prev")
    private val pending = File(directory, "vault.tmp")
    private val pendingPrevious = File(directory, "vault.prev.tmp")

    /**
     * Finishes or discards a save interrupted by a crash. Call before reading.
     * - `vault.tmp` + `vault.prev`, no `vault.bin`: the crash hit between the two renames; the
     *   temp file was fully written and synced before the first rename, so promote it.
     * - any other leftover temp file is incomplete and is deleted.
     */
    fun recoverInterruptedSave() {
        if (!current.exists() && previous.exists() && pending.exists()) {
            files.moveAtomically(pending, current)
        }
        files.delete(pending)
        files.delete(pendingPrevious)
    }

    fun hasAnyVault(): Boolean = current.exists() || previous.exists()

    fun readCurrent(): ByteArray? = files.read(current)

    fun readPrevious(): ByteArray? = files.read(previous)

    /** Saves [bytes] as the new `vault.bin`; the old one becomes `vault.prev`. */
    fun write(bytes: ByteArray) {
        directory.mkdirs()
        files.writeAndSync(pending, bytes)
        if (current.exists()) files.moveAtomically(current, previous)
        files.moveAtomically(pending, current)
    }

    /**
     * C18 "write twice": makes `vault.prev` an exact copy of the new `vault.bin`, so the
     * previous save cannot keep a deleted entry, cleared history or an old-PIN-wrapped key.
     */
    fun replacePreviousWithCurrent() {
        val bytes = checkNotNull(files.read(current)) { "no current vault" }
        files.writeAndSync(pendingPrevious, bytes)
        files.moveAtomically(pendingPrevious, previous)
    }

    /** S30: deletes every vault file (current, previous, any save in progress). */
    fun eraseAll() {
        listOf(pending, pendingPrevious, current, previous).forEach(files::delete)
    }

    /** File operations, injectable so tests can simulate a crash at any step. */
    interface FileOps {
        fun read(file: File): ByteArray?
        fun writeAndSync(file: File, bytes: ByteArray)
        fun moveAtomically(from: File, to: File)
        fun delete(file: File)

        object Real : FileOps {
            override fun read(file: File): ByteArray? = if (file.exists()) file.readBytes() else null

            override fun writeAndSync(file: File, bytes: ByteArray) {
                FileOutputStream(file).use { out ->
                    out.write(bytes)
                    out.flush()
                    out.fd.sync()
                }
            }

            override fun moveAtomically(from: File, to: File) {
                Files.move(
                    from.toPath(),
                    to.toPath(),
                    StandardCopyOption.ATOMIC_MOVE,
                    StandardCopyOption.REPLACE_EXISTING,
                )
            }

            override fun delete(file: File) {
                Files.deleteIfExists(file.toPath())
            }
        }
    }
}
