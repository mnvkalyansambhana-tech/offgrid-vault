package io.github.mnvkalyansambhana.offgridvault.build

import org.gradle.api.DefaultTask
import org.gradle.api.GradleException
import org.gradle.api.file.ConfigurableFileCollection
import org.gradle.api.file.RegularFileProperty
import org.gradle.api.tasks.CacheableTask
import org.gradle.api.tasks.InputFiles
import org.gradle.api.tasks.OutputFile
import org.gradle.api.tasks.PathSensitive
import org.gradle.api.tasks.PathSensitivity
import org.gradle.api.tasks.TaskAction

/**
 * Fails the build on source code that could leak secrets:
 * - writing to logcat or stdout (T14, CLAUDE.md hard constraint);
 * - using a raw window or text-field API instead of its secure wrapper (T2, S20). Those APIs
 *   are allowed only inside the wrapper file named in [Rule.allowedIn].
 *
 * A text scan is used instead of a linter so it works with any Kotlin version.
 */
@CacheableTask
abstract class CheckForbiddenApis : DefaultTask() {

    @get:InputFiles
    @get:PathSensitive(PathSensitivity.RELATIVE)
    abstract val sources: ConfigurableFileCollection

    @get:OutputFile
    abstract val report: RegularFileProperty

    @TaskAction
    fun check() {
        val violations = sources.files.sortedBy { it.path }.flatMap { file ->
            val rules = RULES.filter { it.allowedIn == null || file.name != it.allowedIn }
            file.readLines().mapIndexedNotNull { index, line ->
                rules.firstOrNull { it.pattern.containsMatchIn(line) }
                    ?.let { "${file.path}:${index + 1}: ${it.reason}" }
            }
        }
        report.get().asFile.writeText(violations.joinToString("\n", postfix = "\n"))
        if (violations.isNotEmpty()) {
            throw GradleException("Forbidden API usage:\n" + violations.joinToString("\n"))
        }
    }

    private class Rule(val pattern: Regex, val reason: String, val allowedIn: String? = null)

    private companion object {
        val RULES = listOf(
            Rule(Regex("""\bandroid\.util\.Log\b"""), "android.util.Log is forbidden"),
            Rule(Regex("""(^|[^\w.])Log\.(v|d|i|w|e|wtf)\("""), "Log calls are forbidden"),
            Rule(Regex("""(^|[^\w.])print(ln)?\("""), "print/println is forbidden"),
            Rule(Regex("""\bSystem\.(out|err)\b"""), "System.out/err is forbidden"),
            Rule(Regex("""\.printStackTrace\("""), "printStackTrace is forbidden"),
            Rule(
                Regex("""\bandroidx\.compose\.ui\.window\.(Dialog|Popup)\b"""),
                "use SecureDialog/SecurePopup (T2: FLAG_SECURE on every window)",
                allowedIn = "SecureDialog.kt",
            ),
            Rule(
                Regex("""\bandroid\.app\.(AlertDialog|Dialog)\b|\bandroidx\.appcompat\.app\.AlertDialog\b"""),
                "use SecureDialog (T2: FLAG_SECURE on every window)",
            ),
            Rule(
                Regex("""\bandroidx\.compose\.foundation\.text\.BasicTextField\b|\bandroidx\.compose\.material3?\.(Outlined)?TextField\b"""),
                "use SharpInput (S20: no keyboard learning)",
                allowedIn = "SharpInput.kt",
            ),
        )
    }
}
