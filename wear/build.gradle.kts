import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
}

// Release signing is driven entirely by environment variables so the keystore
// never lives in the repository. When they are absent (local dev, PR CI) the
// release build simply stays unsigned; the GitHub Release workflow provides
// them from repository secrets.
val keystoreFile: String? = System.getenv("KEYSTORE_FILE")

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

        // versionCode must increase monotonically for over-the-top installs; CI
        // feeds github.run_number. versionName is the human-readable release tag.
        versionCode = System.getenv("VERSION_CODE")?.toIntOrNull() ?: 1
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
}

kotlin {
    compilerOptions {
        jvmTarget = JvmTarget.JVM_17
    }
}

dependencies {
    testImplementation(libs.junit)
}
