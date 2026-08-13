// Top-level build file. Plugins are declared here (without applying them) so
// that each module can opt in via the version catalog aliases.
plugins {
    alias(libs.plugins.android.application) apply false
    alias(libs.plugins.kotlin.android) apply false
}
