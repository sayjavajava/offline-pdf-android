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

## Building a signed release APK

The debug APK CI produces on every run is fine for a quick look, but it is
signed with a throwaway key the runner regenerates each time, so every build
has a different signature and Android refuses to install one over another --
testers have to uninstall first, every time. A release build signed with a
key you keep fixes that: updates install straight over the top.

### One-time: create the key

```
keytool -genkeypair -v \
  -keystore offgridpdf-release.jks \
  -alias offgridpdf \
  -keyalg RSA -keysize 4096 -validity 10000 \
  -storetype PKCS12
```

Keep the resulting `.jks` and its passwords somewhere you will still have
them years from now, and somewhere private. This key *is* the app's identity
to Android:

- Lose it, and no further update can ever be installed over an existing copy.
  Every user has to uninstall and lose their settings.
- Leak it, and someone else can build an APK that every installed copy will
  accept as a genuine update of this app.

`.gitignore` refuses `*.jks`, `*.keystore` and `keystore.properties` so a key
cannot be committed by accident, but that only guards this one mistake.

### Local builds

Put the four values in `~/.gradle/gradle.properties` -- your home directory,
not this repository:

```
offgrid.keystore.path=/absolute/path/to/offgridpdf-release.jks
offgrid.keystore.password=...
offgrid.key.alias=offgridpdf
offgrid.key.password=...
```

Then `./gradlew assembleRelease` produces a signed APK at
`app/build/outputs/apk/release/app-release.apk`. The same four values are
read from `OFFGRID_KEYSTORE_PATH`, `OFFGRID_KEYSTORE_PASSWORD`,
`OFFGRID_KEY_ALIAS` and `OFFGRID_KEY_PASSWORD` if you prefer environment
variables.

With none of them set the build still works -- it just produces an *unsigned*
release APK, which will not install. That is deliberate: an unsigned artifact
fails loudly at install time, where falling back to the debug key would look
fine and then block every future update.

### CI builds

The **Signed release APK** workflow (`.github/workflows/release-apk.yml`)
does the same thing on demand: run it from the Actions tab. It needs four
repository secrets:

| Secret | Value |
|---|---|
| `OFFGRID_KEYSTORE_BASE64` | `base64 -w0 offgridpdf-release.jks` |
| `OFFGRID_KEYSTORE_PASSWORD` | the store password |
| `OFFGRID_KEY_ALIAS` | `offgridpdf` |
| `OFFGRID_KEY_PASSWORD` | the key password |

The workflow checks the APK it built is really signed, is not debuggable, and
still declares no `INTERNET` permission, before publishing it two ways:

- A **GitHub Release**, tagged `v<versionName>` (from `app/build.gradle.kts`)
  and named `OffGridPDF <versionName>` — a stable link under the repo's
  Releases tab, which is what you'd hand a tester or link from a device.
  Bump `versionName` (and `versionCode`) before running the workflow again;
  it refuses to overwrite a Release that already exists for the current
  version, on purpose, rather than silently replace someone's build.
- This run's own **Actions artifact** (`offgridpdf-release-apk`), kept for
  convenience — but it expires (90 days, this repo's default setting) and
  needs Actions-tab access to find, unlike the Release above.

### What signing does and does not do

Signing proves an update came from whoever holds the key, and that the file
was not altered after it was signed. It does **not** hide anything: an APK is
a zip, and anyone holding one can unpack it, decompile the bytecode and read
the app's logic. Nothing here relies on that logic being secret -- there are
no keys, credentials or servers in this app, and its behaviour is meant to be
verifiable rather than hidden.
