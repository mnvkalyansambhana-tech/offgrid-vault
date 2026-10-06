plugins {
    alias(libs.plugins.android.library)
    alias(libs.plugins.wire)
}

// Vault file format, storage and session (M2+, T4).
android {
    namespace = "io.github.mnvkalyansambhana.offgridvault.core.vault"
    compileSdk = 37

    defaultConfig {
        minSdk = 30
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        consumerProguardFiles("consumer-rules.pro")
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
}

// T7: Kotlin classes generated from src/main/proto.
wire {
    kotlin {}
}

dependencies {
    // Exposed in the public API (AeadKey, Argon2Params, generated Vault types).
    api(project(":core:crypto"))
    api(libs.wire.runtime)
    api(libs.kotlinx.coroutines.core)

    testImplementation(libs.junit)
}
