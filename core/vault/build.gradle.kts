plugins {
    alias(libs.plugins.android.library)
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

dependencies {
    testImplementation(libs.junit)
}
