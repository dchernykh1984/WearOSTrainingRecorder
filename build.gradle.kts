// Top-level build file. Plugins are declared here (without applying them) so
// that each module can opt in via the version catalog aliases.
plugins {
    alias(libs.plugins.android.application) apply false
    alias(libs.plugins.kotlin.android) apply false
    alias(libs.plugins.kotlin.jvm) apply false
    alias(libs.plugins.ktlint) apply false
    alias(libs.plugins.detekt) apply false
    alias(libs.plugins.kover) apply false
}

// Single source of truth for the version outside CI. Release builds get
// VERSION_NAME from the release tag; every local, debug and PR build would
// otherwise report a hardcoded literal that silently goes stale the moment the
// first release ships. release-please owns this manifest, so reading it keeps the
// two in step automatically.
val releasedVersion: String =
    Regex("\"\\.\"\\s*:\\s*\"([^\"]+)\"")
        .find(file(".release-please-manifest.json").readText())
        ?.groupValues
        ?.get(1)
        ?: error("No root package version found in .release-please-manifest.json")

extra["releasedVersion"] = releasedVersion
