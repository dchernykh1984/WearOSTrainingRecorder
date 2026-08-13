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

// See the comment in wear/build.gradle.kts: one applicationId, two APKs, so each
// form factor offsets the shared monotonic base to keep its versionCode unique.
val versionCodeBase = System.getenv("VERSION_CODE")?.toIntOrNull() ?: 1
val mobileVersionCodeOffset = 1

android {
    namespace = "com.dchernykh.trainingrecorder.mobile"
    compileSdk = 36

    defaultConfig {
        // Same applicationId as :wear - see the comment in wear/build.gradle.kts.
        applicationId = "com.dchernykh.trainingrecorder"
        minSdk = 26
        targetSdk = 36

        versionCode = versionCodeBase * 10 + mobileVersionCodeOffset
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
            // Shrink and obfuscate with R8, and strip unused resources.
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
        // be promoted to errors once the codebase stabilises.
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
                // the scaffold green. Raise it as real code lands.
                minBound(0)
            }
        }
    }
}

// See the comment in wear/build.gradle.kts. Regenerate mobile/gradle.lockfile
// with the "Update lockfiles" workflow or
// `./gradlew :mobile:dependencies --write-locks`.
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
        // The same transitive advisories the watch module pins out; they arrive
        // here through Play Services Wearable.
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

    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.tooling.preview)
    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.datastore.preferences)
    implementation(libs.play.services.wearable)
    implementation(libs.kotlinx.coroutines.play.services)

    debugImplementation(libs.androidx.compose.ui.tooling)

    testImplementation(libs.junit)

    androidTestImplementation(libs.androidx.test.ext.junit)
    androidTestImplementation(libs.androidx.test.runner)
}
