package io.github.mnvkalyansambhana.offgridvault.build

import org.gradle.api.DefaultTask
import org.gradle.api.GradleException
import org.gradle.api.file.DirectoryProperty
import org.gradle.api.file.RegularFileProperty
import org.gradle.api.tasks.InputDirectory
import org.gradle.api.tasks.OutputFile
import org.gradle.api.tasks.PathSensitive
import org.gradle.api.tasks.PathSensitivity
import org.gradle.api.tasks.TaskAction
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.util.zip.ZipFile

/**
 * Checks that every 64-bit native library in the built APK has ELF LOAD segments aligned to
 * at least 16 KB (Google Play requirement for Android 15+ devices; M1 exit test for
 * libsodium.so and JNA's libjnidispatch.so).
 */
abstract class CheckNativeLibAlignment : DefaultTask() {

    @get:InputDirectory
    @get:PathSensitive(PathSensitivity.RELATIVE)
    abstract val apkDirectory: DirectoryProperty

    @get:OutputFile
    abstract val report: RegularFileProperty

    @TaskAction
    fun check() {
        val apks = apkDirectory.get().asFile.walkTopDown().filter { it.extension == "apk" }.toList()
        if (apks.isEmpty()) throw GradleException("No APK found in ${apkDirectory.get()}")
        val lines = mutableListOf<String>()
        val failures = mutableListOf<String>()
        apks.forEach { apk ->
            ZipFile(apk).use { zip ->
                zip.entries().asSequence()
                    .filter { it.name.startsWith("lib/") && it.name.endsWith(".so") }
                    .filter { entry -> ABIS_64.any { entry.name.startsWith("lib/$it/") } }
                    .forEach { entry ->
                        val align = minLoadAlignment(zip.getInputStream(entry).readBytes())
                        val line = "${apk.name}!${entry.name}: min LOAD p_align=$align"
                        lines += line
                        if (align < MIN_ALIGNMENT) failures += line
                    }
            }
        }
        report.get().asFile.writeText(lines.joinToString("\n", postfix = "\n"))
        if (failures.isNotEmpty()) {
            throw GradleException("Native libraries not 16 KB aligned:\n" + failures.joinToString("\n"))
        }
    }

    /** Smallest p_align over all PT_LOAD program headers of a 64-bit little-endian ELF file. */
    private fun minLoadAlignment(elf: ByteArray): Long {
        val buf = ByteBuffer.wrap(elf).order(ByteOrder.LITTLE_ENDIAN)
        require(buf.getInt(0) == ELF_MAGIC) { "not an ELF file" }
        require(elf[4].toInt() == ELFCLASS64) { "not a 64-bit ELF file" }
        val phOff = buf.getLong(0x20)
        val phEntSize = buf.getShort(0x36).toInt() and 0xFFFF
        val phNum = buf.getShort(0x38).toInt() and 0xFFFF
        return (0 until phNum)
            .map { (phOff + it.toLong() * phEntSize).toInt() }
            .filter { buf.getInt(it) == PT_LOAD }
            .minOf { buf.getLong(it + 0x30) }
    }

    private companion object {
        const val MIN_ALIGNMENT = 16_384L
        const val ELF_MAGIC = 0x464C457F
        const val ELFCLASS64 = 2
        const val PT_LOAD = 1
        val ABIS_64 = listOf("arm64-v8a", "x86_64")
    }
}
