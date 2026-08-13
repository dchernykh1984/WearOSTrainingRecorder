plugins {
    alias(libs.plugins.android.library)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.ktlint)
    alias(libs.plugins.detekt)
}

// Resources only. Both the watch and the phone name the same sports and the same
// data fields, so their translations live here once instead of drifting apart in
// two copies of fifteen locales.
android {
    namespace = "com.dchernykh.trainingrecorder.localization"
    compileSdk = 36

    defaultConfig {
        minSdk = 26
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    lint {
        abortOnError = true
        warningsAsErrors = false
        checkReleaseBuilds = false
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
    // Exposed rather than implementation: both apps switch the locale through
    // AppCompatDelegate, and the per-app locale service appcompat contributes to
    // the manifest has to reach their merged manifests too.
    api(libs.androidx.appcompat)

    testImplementation(project(":core"))
    testImplementation(libs.junit)
    testImplementation(libs.kotlin.test)
}
