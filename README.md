# OffGridPDF for Android

A native Android rewrite of [`offline-pdf-utility`](https://github.com/sayjavajava/offline-pdf-utility)
(Kotlin + Jetpack Compose, no wrapped web view). Same product promise: every
operation runs on-device, and the app declares no `INTERNET` permission at
all — enforced by CI, not just intended (see `.github/workflows/ci.yml`).

The implementation plan, architecture decisions, and the spike this project
is based on all live in the private `tool-docs` repository, not here — this
repo is code only. If you're implementing a feature from the plan, read it
there first.

## Requirements

- JDK 17
- Android SDK, `compileSdk`/`targetSdk` 37
- No NDK — this project deliberately has no native code

## Build

```
./gradlew assembleDebug lint testDebugUnitTest
```
