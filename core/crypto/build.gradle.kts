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

    testOptions {
        // Argon2id runs at 64 MiB; the Bouncy Castle cross-check allocates it on the JVM heap.
        unitTests.all { it.maxHeapSize = "1g" }
    }
}

dependencies {
    implementation(libs.tink.android)
    // Lazysodium-android depends on JNA's *desktop* jar; Android needs JNA's AAR, which ships
    // libjnidispatch.so for Android ABIs. Swap one for the other.
    implementation(libs.lazysodium.android) { exclude(group = "net.java.dev.jna", module = "jna") }
    implementation(variantOf(libs.jna) { artifactType("aar") })

    // JVM unit tests run the same libsodium release through lazysodium-java (desktop natives),
    // with Bouncy Castle as an independent Argon2id oracle. Test-only, never shipped.
    testImplementation(libs.junit)
    testImplementation(libs.lazysodium.java)
    testImplementation(libs.jna)
    testImplementation(libs.bouncycastle.prov)

    androidTestImplementation(libs.androidx.test.runner)
    androidTestImplementation(libs.androidx.test.junit)
}

// lazysodium-android and lazysodium-java contain the same com.goterl.lazysodium classes; on the
// JVM test classpath only the desktop one can load natives.
configurations.matching { it.name.endsWith("UnitTestRuntimeClasspath") }.configureEach {
    exclude(group = "com.goterl", module = "lazysodium-android")
}
