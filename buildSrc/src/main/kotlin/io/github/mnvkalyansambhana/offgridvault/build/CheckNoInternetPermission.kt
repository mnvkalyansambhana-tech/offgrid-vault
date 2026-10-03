package io.github.mnvkalyansambhana.offgridvault.build

import org.gradle.api.DefaultTask
import org.gradle.api.GradleException
import org.gradle.api.file.RegularFileProperty
import org.gradle.api.tasks.CacheableTask
import org.gradle.api.tasks.InputFile
import org.gradle.api.tasks.OutputFile
import org.gradle.api.tasks.PathSensitive
import org.gradle.api.tasks.PathSensitivity
import org.gradle.api.tasks.TaskAction

/**
 * Fails the build if the **merged** manifest requests a network permission (hard constraint,
 * T13). Runs on the final manifest, so permissions added by any dependency are caught.
 */
@CacheableTask
abstract class CheckNoInternetPermission : DefaultTask() {

    @get:InputFile
    @get:PathSensitive(PathSensitivity.NONE)
    abstract val mergedManifest: RegularFileProperty

    @get:OutputFile
    abstract val report: RegularFileProperty

    @TaskAction
    fun check() {
        val manifest = mergedManifest.get().asFile.readText()
        val permissions = PERMISSION.findAll(manifest).map { it.groupValues[1] }.toList()
        val forbidden = permissions.filter { it in FORBIDDEN }
        report.get().asFile.writeText(permissions.joinToString("\n", postfix = "\n"))
        if (forbidden.isNotEmpty()) {
            throw GradleException(
                "Merged manifest requests forbidden permission(s): $forbidden. " +
                    "OffGrid Vault must never have network access.",
            )
        }
    }

    private companion object {
        val PERMISSION = Regex("""<uses-permission(?:-sdk-23)?\b[^>]*?android:name\s*=\s*"([^"]+)"""")
        val FORBIDDEN = setOf("android.permission.INTERNET")
    }
}
