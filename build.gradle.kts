// Top-level build file: declares plugin versions once so every module
// applies them without repeating a version (Gradle's "apply false" plugin
// pattern). Actual application happens in app/build.gradle.kts.
//
// No org.jetbrains.kotlin.android here: AGP 9.0+ compiles Kotlin itself
// ("built-in Kotlin") and rejects that plugin as a conflict if applied
// alongside it — found by a real CI failure, not assumed from AGP 9's
// release notes. org.jetbrains.kotlin.plugin.compose (the Compose
// *compiler* plugin) is a separate, still-required plugin.
plugins {
    alias(libs.plugins.android.application) apply false
    alias(libs.plugins.kotlin.compose) apply false
}
