import com.android.build.api.artifact.SingleArtifact
import io.github.mnvkalyansambhana.offgridvault.build.CheckNativeLibAlignment
import io.github.mnvkalyansambhana.offgridvault.build.CheckNoInternetPermission
import java.util.Properties

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
}

// Release signing (M9): the upload key and its passwords live OUTSIDE the repo. Create
// keystore.properties at the repo root (git-ignored), see docs/RELEASE.md. Without it the
// release build is simply unsigned (CI).
val uploadKey = rootProject.file("keystore.properties").takeIf { it.exists() }?.let { file ->
    Properties().apply { file.inputStream().use(::load) }
}

android {
    namespace = "io.github.mnvkalyansambhana.offgridvault"
    compileSdk = 37

    defaultConfig {
        // P16: permanent once published. Never change.
        applicationId = "io.github.mnvkalyansambhana.offgridvault"
        minSdk = 30
        targetSdk = 36
        versionCode = 1
        versionName = "0.1.0"
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        // ABIs that run Android 11+. Drops JNA's dead armeabi/mips/mips64 and 32-bit x86 libs.
        ndk { abiFilters += setOf("arm64-v8a", "armeabi-v7a", "x86_64") }
    }

    signingConfigs {
        if (uploadKey != null) {
            create("upload") {
                storeFile = file(uploadKey.getProperty("storeFile"))
                storePassword = uploadKey.getProperty("storePassword")
                keyAlias = uploadKey.getProperty("keyAlias")
                keyPassword = uploadKey.getProperty("keyPassword")
            }
        }
    }

    buildTypes {
        debug {
            // Debug builds install side by side with the Play build and get their own vault.
            applicationIdSuffix = ".debug"
            versionNameSuffix = "-debug"
        }
        release {
            signingConfig = signingConfigs.findByName("upload")
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    buildFeatures {
        compose = true
    }

    packaging {
        resources {
            excludes += setOf("/META-INF/{AL2.0,LGPL2.1}", "/META-INF/*.kotlin_module")
        }
    }

    lint {
        abortOnError = true
        checkReleaseBuilds = true
        // Security-relevant checks are errors, not warnings (T14).
        error += setOf(
            "AllowBackup",
            "DataExtractionRules",
            "ExportedContentProvider",
            "ExportedReceiver",
            "ExportedService",
            "GetInstance",
            "HardcodedDebugMode",
            "PackagedPrivateKey",
            "SecureRandom",
            "TrustAllX509TrustManager",
            "UnsafeDynamicallyLoadedCode",
            "UseCheckPermission",
            "WorldReadableFiles",
            "WorldWriteableFiles",
        )
    }
}

// Hard constraint + T13: fail the build if any merged manifest asks for network access,
// and (M1) if any 64-bit native library is not 16 KB aligned.
androidComponents {
    onVariants { variant ->
        val suffix = variant.name.replaceFirstChar { it.uppercase() }
        val noInternet = tasks.register<CheckNoInternetPermission>("check${suffix}NoInternet") {
            mergedManifest.set(variant.artifacts.get(SingleArtifact.MERGED_MANIFEST))
            report.set(layout.buildDirectory.file("reports/no-internet/${variant.name}.txt"))
        }
        val alignment = tasks.register<CheckNativeLibAlignment>("check${suffix}NativeLibAlignment") {
            apkDirectory.set(variant.artifacts.get(SingleArtifact.APK))
            report.set(layout.buildDirectory.file("reports/native-alignment/${variant.name}.txt"))
        }
        tasks.named("check") { dependsOn(noInternet, alignment) }
        tasks.matching { it.name == "assemble$suffix" }.configureEach { dependsOn(noInternet) }
    }
}

dependencies {
    implementation(project(":core:crypto"))
    implementation(project(":core:vault"))
    implementation(project(":autofill"))

    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.activity.compose)
    implementation(platform(libs.compose.bom))
    implementation(libs.compose.ui)
    implementation(libs.compose.foundation)
    implementation(libs.compose.ui.tooling.preview)
    implementation(libs.kotlinx.coroutines.android)
    implementation(libs.androidx.navigation.compose)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.lifecycle.runtime.compose)
    debugImplementation(libs.compose.ui.tooling)
}
