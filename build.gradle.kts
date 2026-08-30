// Top-level build file: declares plugin versions once so every module
// applies them without repeating a version (Gradle's "apply false" plugin
// pattern). Actual application happens in app/build.gradle.kts.
plugins {
    alias(libs.plugins.android.application) apply false
    alias(libs.plugins.kotlin.android) apply false
    alias(libs.plugins.kotlin.compose) apply false
}
