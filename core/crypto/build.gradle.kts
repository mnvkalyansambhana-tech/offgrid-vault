plugins {
    alias(libs.plugins.android.library)
}

// Crypto primitives: Argon2id, AES-256-GCM, HKDF, BIP-39 (M1, T4).
android {
    namespace = "io.github.mnvkalyansambhana.offgridvault.core.crypto"
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

dependencies {
    testImplementation(libs.junit)
}
