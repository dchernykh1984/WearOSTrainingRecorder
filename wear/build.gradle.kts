import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.ktlint)
    alias(libs.plugins.detekt)
}

// Release signing is driven entirely by environment variables so the keystore
// never lives in the repository. When they are absent (local dev, PR CI) the
// release build simply stays unsigned; the GitHub Release workflow provides
// them from repository secrets.
val keystoreFile: String? = System.getenv("KEYSTORE_FILE")

// Google Play requires a distinct versionCode per APK published under one
// applicationId. CI feeds a monotonic base (github.run_number) and each form
// factor adds a fixed offset, so the two APKs can never collide. The watch offset
// is the higher one: when an APK could serve both form factors, Play resolves the
// ambiguity in favour of the higher versionCode, and the watch build must win.
val versionCodeBase = System.getenv("VERSION_CODE")?.toIntOrNull() ?: 1
val wearVersionCodeOffset = 2

android {
    namespace = "com.dchernykh.trainingrecorder.wear"
    compileSdk = 36

    defaultConfig {
        // The watch and phone APKs must share one applicationId (and one signing
        // key) for Google Play to deliver them as a single app per form factor.
        applicationId = "com.dchernykh.trainingrecorder"
        // Wear OS 3 (API 30) is the oldest platform Google still supports; older
        // watches run the pre-3 RPC-based platform, which this app does not target.
        minSdk = 30
        targetSdk = 36

        // versionCode must increase monotonically for over-the-top installs.
        // versionName is the human-readable release tag.
        versionCode = versionCodeBase * 10 + wearVersionCodeOffset
        versionName = System.getenv("VERSION_NAME") ?: "0.1.0"
    }

    signingConfigs {
        if (keystoreFile != null) {
            create("release") {
                storeFile = file(keystoreFile)
                storePassword = System.getenv("KEYSTORE_PASSWORD")
                keyAlias = System.getenv("KEY_ALIAS")
                keyPassword = System.getenv("KEY_PASSWORD")
            }
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro",
            )
            if (keystoreFile != null) {
                signingConfig = signingConfigs.getByName("release")
            }
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    lint {
        // Fail the build on lint errors; warnings stay non-fatal for now and can
        // be promoted to errors once the codebase stabilises. Android Lint ships
        // the Wear OS checks (standalone flag, unsupported APIs, tile and
        // complication misuse), so this is the gate that catches watch-specific
        // manifest and API mistakes.
        abortOnError = true
        warningsAsErrors = false
        // lintDebug in CI covers analysis; skip the duplicate release lint pass.
        checkReleaseBuilds = false
    }
}

kotlin {
    compilerOptions {
        jvmTarget = JvmTarget.JVM_17
    }
}

ktlint {
    android.set(true)
}

detekt {
    buildUponDefaultConfig = true
    config.setFrom(rootProject.file("config/detekt/detekt.yml"))
}

dependencies {
    testImplementation(libs.junit)
}
