plugins {
    alias(libs.plugins.android.library)
}

// Autofill service and matching (M7+, T4).
android {
    namespace = "io.github.mnvkalyansambhana.offgridvault.autofill"
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
