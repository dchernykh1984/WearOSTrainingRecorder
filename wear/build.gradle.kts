import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.ktlint)
    alias(libs.plugins.detekt)
    alias(libs.plugins.kover)
}

// Release signing is driven entirely by environment variables so the keystore
// never lives in the repository. When they are absent (local dev, PR CI) the
// release build simply stays unsigned; the GitHub Release workflow provides
// them from repository secrets.
val keystoreFile: String? = System.getenv("KEYSTORE_FILE")

// Google Play requires a distinct versionCode per APK published under one
// applicationId. The release workflow derives a monotonic base from the semantic
// version (major * 1000000 + minor * 1000 + patch) and each form factor adds a
// fixed offset, so the two APKs can never collide. The watch offset is the higher
// one: when an APK could serve both form factors, Play resolves the ambiguity in
// favour of the higher versionCode, and the watch build must win.
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
        versionName = System.getenv("VERSION_NAME") ?: rootProject.extra["releasedVersion"] as String

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
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
            // Shrink and obfuscate with R8, and strip unused resources. APK size
            // matters more on a watch than on a phone: watches have far less
            // storage, and the APK is pushed over Bluetooth when a paired phone
            // installs it.
            isMinifyEnabled = true
            isShrinkResources = true
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

    buildFeatures {
        compose = true
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

kover {
    reports {
        filters {
            excludes {
                // Generated code is not meaningful to cover.
                classes("*.BuildConfig", "*.R", "*.R$*")
            }
        }
        verify {
            rule {
                // No production logic exists yet, so the bound stays at 0 to keep
                // the scaffold green. Raise it as real code lands - the recording,
                // export and sync logic is plain Kotlin and should be well covered
                // by JVM tests; only the Compose screens need the exclusion list.
                minBound(0)
            }
        }
    }
}

// Pin transitive dependency versions for reproducible builds. Only the shipped
// and unit-test runtime classpaths are locked (Android's internal configurations
// are intentionally left out). Regenerate wear/gradle.lockfile with the
// "Update lockfiles" workflow or `./gradlew :wear:dependencies --write-locks`.
listOf(
    "debugRuntimeClasspath",
    "releaseRuntimeClasspath",
    "debugUnitTestRuntimeClasspath",
    "releaseUnitTestRuntimeClasspath",
).forEach { configurationName ->
    configurations.matching { it.name == configurationName }.configureEach {
        resolutionStrategy.activateDependencyLocking()
    }
}

dependencies {
    constraints {
        // Pulled in transitively by Health Services and Play Services Wearable.
        // Both carry advisories the OSV scan fails on; neither is a direct
        // dependency, so the only way to move them is a constraint.
        implementation("com.google.guava:guava:32.0.0-android") {
            because("GHSA-5mg8-w23w-74h3 and GHSA-7g45-4rm6-3mm3 are fixed in 32.0.0")
        }
        implementation("com.google.protobuf:protobuf-javalite:3.25.5") {
            because("GHSA-735f-pc8j-v9w8 (CVSS 8.7) is fixed in 3.25.5")
        }
    }

    implementation(project(":core"))
    implementation(project(":localization"))

    implementation(libs.androidx.core.ktx)

    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.lifecycle.runtime.compose)

    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.tooling.preview)
    implementation(libs.androidx.wear.compose.material3)
    implementation(libs.androidx.wear.compose.foundation)
    implementation(libs.androidx.wear.compose.navigation)
    implementation(libs.androidx.wear.tooling.preview)
    implementation(libs.androidx.wear.ongoing)

    implementation(libs.androidx.health.services.client)
    implementation(libs.kotlinx.coroutines.android)
    implementation(libs.kotlinx.coroutines.guava)
    implementation(libs.kotlinx.coroutines.play.services)
    implementation(libs.kotlinx.serialization.json)
    implementation(libs.androidx.datastore.preferences)
    implementation(libs.play.services.wearable)

    debugImplementation(libs.androidx.compose.ui.tooling)

    testImplementation(libs.junit)

    androidTestImplementation(libs.androidx.test.ext.junit)
    androidTestImplementation(libs.androidx.test.runner)
}
