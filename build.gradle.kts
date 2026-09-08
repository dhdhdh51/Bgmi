// Root build file. Plugins are declared here with `apply false` so that the
// versions live in one place (gradle/libs.versions.toml) and are applied by the
// modules that need them.
//
// Note for AGP 9.x: Kotlin support is built into `com.android.application`, so
// the Kotlin Android plugin must NOT be applied — it conflicts with built-in
// Kotlin.
plugins {
    alias(libs.plugins.android.application) apply false
}
