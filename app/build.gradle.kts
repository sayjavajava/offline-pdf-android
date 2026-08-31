plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
}

android {
    namespace = "com.offgridpdf.android"
    // 37, not 36: Compose BOM 2026.08.00's artifacts (1.12.0) declare a
    // minCompileSdk of 37 in their AAR metadata — found by a real CI
    // failure ("CheckAarMetadataWorkAction"), not assumed from release notes.
    compileSdk = 37

    defaultConfig {
        applicationId = "com.offgridpdf.android"
        minSdk = 26
        targetSdk = 37
        versionCode = 1
        versionName = "0.1.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    buildTypes {
        release {
            isMinifyEnabled = false
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    // No android.kotlinOptions{} block: that was the removed kotlin-android
    // plugin's DSL. Built-in Kotlin's jvmTarget defaults to
    // compileOptions.targetCompatibility above (17) — nothing else to set.

    buildFeatures {
        compose = true
    }

    testOptions {
        unitTests {
            // PdfBox-Android's own code calls android.util.Log internally
            // (confirmed by direct bytecode inspection in
            // NATIVE_ANDROID_SPIKE.md, tool-docs repo) — three logging
            // call sites, nothing that affects correctness. Without this,
            // AGP's default stubbed android.jar throws on any android.*
            // call from a plain (non-Robolectric) unit test; this makes
            // it a harmless no-op instead, avoiding a Robolectric
            // dependency for tests that don't otherwise need one.
            isReturnDefaultValues = true

            all { test ->
                // Gradle's default test-failure summary prints only the
                // exception class and a line number, not its message or
                // full cause chain -- not enough to diagnose a real CI
                // failure (see PR #9's history: two rounds of guessing
                // before this was added). Full detail on every run, not
                // just a one-off debugging flag, so the next failure is
                // diagnosable from the first log without a round-trip.
                test.testLogging {
                    events("failed")
                    exceptionFormat = org.gradle.api.tasks.testing.logging.TestExceptionFormat.FULL
                    showCauses = true
                    showExceptions = true
                    showStackTraces = true
                }
            }
        }
    }
}

dependencies {
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.activity.compose)
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.ui)
    implementation(libs.androidx.ui.graphics)
    implementation(libs.androidx.ui.tooling.preview)
    implementation(libs.androidx.material3)
    implementation(libs.androidx.material.icons.extended)
    implementation(libs.androidx.navigation.compose)
    implementation(libs.kotlinx.coroutines.android)

    // PDF manipulation — pure-JVM, no NDK. Verified against Maven Central's
    // real published .aar in NATIVE_ANDROID_SPIKE.md before this dependency
    // was chosen; see that doc (tool-docs repo) for why.
    implementation(libs.pdfbox.android)
    implementation(libs.bouncycastle.bcprov)
    implementation(libs.bouncycastle.bcpkix)
    implementation(libs.bouncycastle.bcutil)

    debugImplementation(libs.androidx.ui.tooling)

    testImplementation(libs.junit)
}
