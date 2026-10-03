import io.github.mnvkalyansambhana.offgridvault.build.CheckForbiddenApis

plugins {
    base
    alias(libs.plugins.android.application) apply false
    alias(libs.plugins.android.library) apply false
    alias(libs.plugins.kotlin.android) apply false
    alias(libs.plugins.kotlin.compose) apply false
}

// T13: every resolved configuration is pinned in gradle.lockfile; refresh with
//   ./gradlew resolveAllDependencies --write-locks
allprojects {
    dependencyLocking {
        lockAllConfigurations()
    }
}

subprojects {
    tasks.register("resolveAllDependencies") {
        description = "Resolves every resolvable configuration (used with --write-locks)."
        val resolvable = configurations.filter { it.isCanBeResolved }
        notCompatibleWithConfigurationCache("Resolves configurations at execution time")
        // Graph resolution is enough to record lock state; avoids artifact/variant selection.
        doLast { resolvable.forEach { it.incoming.resolutionResult.rootComponent.get() } }
    }
}

// T14: no logging/printing of anything, anywhere (secrets must never reach logcat).
val checkForbiddenApis = tasks.register<CheckForbiddenApis>("checkForbiddenApis") {
    sources.from(
        fileTree(rootDir) {
            include("**/src/**/*.kt", "**/src/**/*.java")
            exclude("**/build/**", "buildSrc/**")
        },
    )
    report.set(layout.buildDirectory.file("reports/forbidden-apis.txt"))
}

tasks.named("check") { dependsOn(checkForbiddenApis) }
