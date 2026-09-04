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

Two manual-only workflows share the same signing keystore and the same
`v<versionName>` GitHub Release, and produce the two different things
Android release builds come in:

- **Signed release APK** (`.github/workflows/release-apk.yml`) — an `.apk`,
  the format you hand a tester to sideload directly.
- **Signed release AAB** (`.github/workflows/release-aab.yml`) — an `.aab`
  (Android App Bundle), the format the Play Store requires for upload. An
  `.aab` is not directly installable; Play itself splits it into
  device-specific APKs at install time.

Run whichever one you need from the Actions tab — neither triggers
automatically on push, and running one does not run the other. Each needs
the same four repository secrets:

| Secret | Value |
|---|---|
| `OFFGRID_KEYSTORE_BASE64` | `base64 -w0 offgridpdf-release.jks` |
| `OFFGRID_KEYSTORE_PASSWORD` | the store password |
| `OFFGRID_KEY_ALIAS` | `offgridpdf` |
| `OFFGRID_KEY_PASSWORD` | the key password |

Each workflow verifies its own output is really signed (`apksigner verify`
for the APK, `jarsigner -verify` for the AAB — an AAB uses a jar-style
signature, not the APK v2/v3 scheme) before publishing it two ways:

- A **GitHub Release**, tagged `v<versionName>` (from `app/build.gradle.kts`)
  and named `OffGridPDF <versionName>` — a stable link under the repo's
  Releases tab. Both workflows publish to the *same* Release for a given
  version: whichever you run first creates it, the other adds its own asset
  alongside. Bump `versionName` before running either workflow again for a
  new build; each refuses to overwrite its own asset on an existing
  Release, on purpose, rather than silently replace someone's build.
- This run's own **Actions artifact** (`offgridpdf-release-apk` or
  `offgridpdf-release-aab`), kept for convenience — but it expires (90 days,
  this repo's default setting) and needs Actions-tab access to find, unlike
  the Release above.

### Publishing to the Play Store: versioning

Every build has two version fields in `app/build.gradle.kts`:

```kotlin
defaultConfig {
    versionCode = 1
    versionName = "0.1.0"
}
```

- **`versionName`** is the free-form string a user sees ("0.1.0"). It also
  names the GitHub Release tag above — bump it for every build you intend to
  hand out or upload, so each one gets its own Release instead of colliding
  with the last.
- **`versionCode`** is the integer Play actually orders releases by. It
  **must strictly increase with every upload, forever, across every track**
  — production, closed testing, internal testing, all of them share one
  counter. Once a `versionCode` has been uploaded to *any* track, even one
  you never publish, it can never be reused, even from a different track
  later. There is no way to undo an upload's `versionCode`.
- Bump both together before a Play upload. Neither release workflow reads
  or validates `versionCode` — they only read `versionName`, since that's
  what names the tag — so remembering to bump it is on you; nothing in CI
  currently catches a forgotten bump.

A typical release: bump `versionCode` and `versionName`, commit that on
`main`, then run the **Signed release AAB** workflow from the Actions tab
and upload the resulting `.aab` (from the Release or the Actions artifact)
to Play Console yourself — that upload step is manual and outside this
repo's CI, same as the actual Play Console listing and rollout decisions.

### What signing does and does not do

Signing proves an update came from whoever holds the key, and that the file
was not altered after it was signed. It does **not** hide anything: an APK is
a zip, and anyone holding one can unpack it, decompile the bytecode and read
the app's logic. Nothing here relies on that logic being secret -- there are
no keys, credentials or servers in this app, and its behaviour is meant to be
verifiable rather than hidden.

## License

GPL-3.0-or-later. See [`LICENSE`](LICENSE). Same license as the sibling
[`offline-pdf-utility`](https://github.com/sayjavajava/offline-pdf-utility)
and [`quiet-ocr`](https://github.com/sayjavajava/quiet-ocr) repositories.
