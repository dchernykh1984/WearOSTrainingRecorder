plugins {
    alias(libs.plugins.kotlin.jvm)
    alias(libs.plugins.ktlint)
    alias(libs.plugins.detekt)
    alias(libs.plugins.kover)
}

// Deliberately a plain Kotlin/JVM module, not an Android one. Everything the
// watch and the phone must agree on - the field catalogue, the sport taxonomy,
// the screen configuration model and its inheritance, the layout planner, the
// race-stats contract and the storage connector interface - lives here, where it
// is exercised by fast JVM tests instead of an emulator.
kotlin {
    jvmToolchain(17)
}

ktlint {
    android.set(false)
}

detekt {
    buildUponDefaultConfig = true
    config.setFrom(rootProject.file("config/detekt/detekt.yml"))
}

kover {
    reports {
        verify {
            rule {
                // Pure logic with no UI or platform glue, so it is held to a far
                // higher bar than the Android modules.
                minBound(80)
            }
        }
    }
}

listOf(
    "runtimeClasspath",
    "testRuntimeClasspath",
).forEach { configurationName ->
    configurations.matching { it.name == configurationName }.configureEach {
        resolutionStrategy.activateDependencyLocking()
    }
}

dependencies {
    testImplementation(libs.junit)
    implementation(libs.kotlinx.serialization.json)

    testImplementation(libs.kotlin.test)
}
