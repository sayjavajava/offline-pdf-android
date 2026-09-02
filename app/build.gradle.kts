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

    // Release signing, supplied from outside the repository: a keystore path
    // plus three secrets, read from environment variables (what CI sets) or
    // Gradle properties in ~/.gradle/gradle.properties (what a local build
    // uses). Nothing secret is committed, and nothing here is required to
    // build the app -- see the null case below.
    val releaseStorePath = System.getenv("OFFGRID_KEYSTORE_PATH")
        ?: providers.gradleProperty("offgrid.keystore.path").orNull
    val releaseStorePassword = System.getenv("OFFGRID_KEYSTORE_PASSWORD")
        ?: providers.gradleProperty("offgrid.keystore.password").orNull
    val releaseKeyAlias = System.getenv("OFFGRID_KEY_ALIAS")
        ?: providers.gradleProperty("offgrid.key.alias").orNull
    val releaseKeyPassword = System.getenv("OFFGRID_KEY_PASSWORD")
        ?: providers.gradleProperty("offgrid.key.password").orNull

    val releaseSigningReady = releaseStorePath != null &&
        releaseStorePassword != null &&
        releaseKeyAlias != null &&
        releaseKeyPassword != null &&
        file(releaseStorePath).exists()

    signingConfigs {
        // Created only when all four values are present. Registering it
        // unconditionally would fail the build for every contributor and for
        // CI's ordinary PR checks, none of which have the keystore -- and
        // this project's normal build has to keep working without it.
        if (releaseSigningReady) {
            create("release") {
                storeFile = file(releaseStorePath!!)
                storePassword = releaseStorePassword
                keyAlias = releaseKeyAlias
                keyPassword = releaseKeyPassword

                // v1 off: it is the old JAR-signing scheme, and minSdk 26 is
                // well above the API 24 where v2 became available, so nothing
                // that can install this app needs it. v3 on: it is what makes
                // signing-key *rotation* possible later. Without it, the key
                // generated today is the only key this app can ever be signed
                // with -- lose it and every installed copy is stranded, with
                // an uninstall the only way forward.
                enableV1Signing = false
                enableV2Signing = true
                enableV3Signing = true
            }
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = false

            // Without credentials the release APK comes out unsigned and
            // cannot be installed. That is deliberate: an unsigned artifact
            // that fails at install time is safer than one silently signed
            // with the debug key, which would look installable and then
            // block every future update with a signature mismatch.
            signingConfig = if (releaseSigningReady) {
                signingConfigs.getByName("release")
            } else {
                null
            }
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
    // Spike B (ANDROID_IMPLEMENTATION_PLAN.md, tool-docs repo) already
    // proved poi-ooxml dexes cleanly on `implementation` -- see
    // CODE_AUDIT.md's Spike B write-up and PR #18's CI run. That question
    // is settled, and the spike's own decision favors the direct OOXML/
    // kxml2 approach (zero new runtime dependency) for the real A-25, so
    // this moves to `testImplementation`: DocxSpikePoiTest.kt keeps
    // running as a real, JVM-verified record, without every future build
    // paying poi-ooxml's ~36.8 MB APK / dex-time cost for code no shipped
    // tool actually uses.
    testImplementation(libs.apache.poi.ooxml)
    // Spike B comparison approach only -- see the note on apache.poi.ooxml
    // above.
    testImplementation(libs.kxml2)

    // Spike A only (ANDROID_IMPLEMENTATION_PLAN.md, tool-docs repo): real
    // android.graphics.pdf.PdfRenderer usage needs a live Android runtime,
    // not the JVM-only android.jar stub testOptions.unitTests relies on
    // above -- so this lives under androidTest, run via a real emulator in
    // CI (.github/workflows/spike-a-page-rendering.yml), not
    // testDebugUnitTest. pdfbox-android is re-declared here even though
    // it's already `implementation` above: androidTestImplementation does
    // not inherit the app's own `implementation` classpath at compile
    // time (only androidTest's own declared deps and the app module's own
    // main-source-set classes are visible there), so PDFRenderer/PDDocument
    // types need their own explicit entry to resolve in this source set.
    androidTestImplementation(libs.pdfbox.android)
    androidTestImplementation(libs.junit)
    androidTestImplementation(libs.androidx.test.core)
    androidTestImplementation(libs.androidx.test.runner)
    androidTestImplementation(libs.androidx.test.ext.junit)
}
