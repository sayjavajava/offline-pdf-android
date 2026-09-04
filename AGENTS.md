# Working in this repo (for coding agents)

You are working on **OffGridPDF for Android** — a native Kotlin/Compose app with 20 PDF tools, all
running on-device. This file tells you how work is done here. It's aimed at coding agents (Claude
Code, Cursor, Codex, and anything else that reads `AGENTS.md`), but nothing in it is
agent-specific — a human contributor should read it too.

There is no `CONTRIBUTING.md` in this repo yet, so this file carries the setup and the pre-PR
checklist as well.

---

## 1. The two things you must not break

**a) Zero permissions.** This app declares **no `<uses-permission>` of any kind** — no `INTERNET`,
no camera, no storage. That is a stronger, externally checkable claim than any privacy policy, and
both the Play Store listing and the privacy policy lean on it. CI greps the *merged* manifest and
fails on `INTERNET`; the release workflows check the built artifact again. Files are read only via
the system document picker, one file at a time, chosen by the user.

**b) No native code.** No NDK, deliberately. Adding one changes the build, the review surface, and
the supply chain all at once.

Before adding a dependency or a permission, assume the answer is "vendor it or do without", and
only depart from that with a reason you can state.

## 2. Setup and the gate

```bash
./gradlew assembleDebug lint testDebugUnitTest
```

Requirements: JDK 17, Android SDK, `compileSdk`/`targetSdk` 37, `minSdk` 26. Run the command above
clean before you push — CI runs the same thing plus the manifest permission grep.

CI **skips the Gradle build for docs/workflow-only PRs**, via a step inside the job rather than a
job-level `if` (a skipped job reports neither success nor failure, so a required check would hang
forever). Read that comment in `.github/workflows/ci.yml` before changing it.

## 3. How work is done here

### The web app is the behaviour spec

The sibling repo [`offline-pdf-utility`](https://github.com/sayjavajava/offline-pdf-utility) ships
the same 20 tools in the browser and came first. **Before writing Kotlin for a tool, read its web
implementation — the `src/components/tools/*Tool.tsx` component and its `src/lib/*.ts` — and
actually use the tool in a browser.** The behaviour spec lives there, not in a one-line description
anywhere. Every screen here carries a comment naming its counterpart:

```kotlin
/** Web reference: `SplitTool.tsx` + `splitPdf`/`splitPdfToZip` (`pdf-ops.ts`). */
```

Keep that accurate when you touch a screen. Parity flows web → Android for tool behaviour; some
capabilities (batch mode, tool chaining, theme toggle, tablet layout) went Android-first here and
may get ported back the other way.

### Verify against reality, not plausibility

- **Don't assert layout — measure it.** A UI audit once claimed option rows overflowed based on
  adding up widths on paper. The fix only shipped once a real Compose UI test on a real emulator
  (`OptionRowLayoutTest.kt`) actually asserted it.
- **A green JVM test suite proves less than it looks.** See §6 — this has already bitten this
  project hard.
- **Don't quote a number you didn't measure.**

A spike that proves an idea *doesn't* work is a success. Write down the real blocker and stop.

### Report honestly, including what you didn't do

PR descriptions here state what was verified, what was assumed, and what was deliberately skipped
and why — including a **"Not touched, and why"** section when that's the honest answer. If you
couldn't run something (no device, no signing keystore), say so plainly rather than implying
coverage you don't have.

### One concern per PR, driven to green

Branch (`android/<topic>`, or `android/a-<n>-<slug>` for a numbered build item) → implement → run
the gate → push → open PR → **watch CI to green** → address review → merge. A PR you opened is
yours until it merges or closes; red CI on it is work now, whatever its review state. Never skip,
disable, or quarantine a test to get green, and never push an empty commit to kick CI.

Prefer several small complete PRs to one large one — the hardening work went out as seven separate
PRs (threading, input validation, a CVE bump, memory guards, state preservation, privacy items,
`FLAG_SECURE`), each reviewable on its own.

### Comment the *why*, not the *what*

Comments here explain decisions, rejected alternatives, and constraints — not what the line already
says. If you remove a safeguard, justify it in the same breath: `release-aab.yml` explicitly
documents why it skips the APK workflow's `aapt2` debuggable check (both artifacts come from the
same `release` buildType and merged manifest) rather than silently omitting it.

## 4. Repo conventions

- **Design system: "paper & ink".** `ui/theme/Color.kt` defines four category accents
  (organize/terracotta, security/forest, convert/teal, edit/plum) plus paper/ink neutrals.
  `ui/theme/Fonts.kt` uses Source Serif 4 / IBM Plex Sans / IBM Plex Mono, **vendored** in
  `res/font/`, never fetched.
- **Build screens from `ToolScaffold` plus the shared form primitives in `ui/common/`**
  (`ToolTextField`, `OptionChipRow`, `CheckboxRow`, `SectionLabel`). Don't hand-roll a text field.
- **Tool definitions** live in `ui/dashboard/PdfTool.kt` — that file is the list of 20 tools.
- **Passwords use plain `remember`, never `rememberSaveable`** — a document password is never
  written to saved instance state. See `ui/common/Savers.kt`.
- **Tool chaining**: a result can flow into the next tool. Chained output is named
  `<original-file-root>_<latest-operation-suffix>.pdf` and **must not accumulate suffixes across
  hops** — that's what `ChainOrigin` exists for.
- **Testing ladder, cheapest first.** Plain JUnit wherever possible (PdfBox-Android runs under
  plain `java`/`javac` with a ~20-line `android.util.Log` stub, so most PDF-logic tests need
  neither Robolectric nor an emulator) → Robolectric only for logic genuinely touching framework
  classes (`Uri`, `ContentResolver`) → a real instrumented test only for rendering quality and
  drag-based UI. **But read §6 first on what JVM-only tests cannot catch.**
- `res/drawable/ic_brand_mark.xml` is the real brand glyph. `ic_launcher_foreground.xml` is
  explicitly a **placeholder** and still needs real art.
- Build items are numbered `A-<n>` — a strictly separate namespace from the web repo's `F-<n>`.

- **`CHANGELOG.md` records user-facing changes only** — a new tool, a behaviour change, a bug
  someone would notice — under `## [Unreleased]` until there's a release to name. Internal-only
  work (CI, tooling, refactors, spikes, release plumbing) gets no entry. It went stale once
  already: it covered the 20 tools and then nothing for the next thirty-odd PRs, and fixing it
  meant reconstructing the whole period from the merge history. Keep it current as you go.
- **Licensed GPL-3.0-or-later**, same as both sibling repos. See `LICENSE`.

## 5. Releasing

Two **manual-only** (`workflow_dispatch`) workflows, both reading `versionName` from
`app/build.gradle.kts` to name a shared `v<versionName>` GitHub Release:

- `release-apk.yml` → `app-release.apk`, for handing a tester a sideloadable build.
- `release-aab.yml` → `app-release.aab`, the format the Play Store requires. An `.aab` is **not**
  directly installable.

Whichever runs first creates the Release; the other adds its asset alongside. Each refuses to
overwrite **its own** asset — an asset-aware check, not a tag-exists check, because a tag-only
check wrongly blocks the second workflow. See `README.md` for the four required secrets.

**Signing is the maintainer's alone.** An agent builds and modifies the CI plumbing that *reads*
`OFFGRID_KEYSTORE_BASE64` and friends; an agent never generates, holds, requests, or transmits the
keystore or its passwords. Lose that key and no update can ever install over an existing copy.

**`versionCode` must strictly increase forever, across every Play track** — once uploaded to any
track, even one never published, it can never be reused. **Neither workflow reads or validates
it**; bumping it is a human step nothing in CI catches.

## 6. Landmines — each of these cost a real investigation

**JVM-stubbed unit tests hide runtime-only failures, and this already nearly shipped a disaster.**
The first attempt to draw text via PdfBox-Android on a *real* Android runtime crashed with an NPE
inside `PDFBoxResourceLoader.getStream()`. The app had never had an `Application` subclass, so
`PDFBoxResourceLoader.init(context)` — required before any standard-14-font metrics lookup — had
never been called anywhere, ever. Because CI had only run JVM-stubbed unit tests, this was
invisible: **every already-merged tool that draws text (Watermark, Page Numbers, DOCX→PDF,
Signature's typed mode) would have crashed the first time it ran on a real device.** Fixed by
`OffGridPdfApplication : Application()` calling `PDFBoxResourceLoader.init()` in `onCreate()`,
wired via `android:name` in the manifest. When a library touches the Android runtime, a green JVM
suite is not evidence.

**You can get a real emulator from CI even with no local device.** GitHub Actions' hosted Ubuntu
runners support KVM-accelerated Android emulation. `.github/workflows/spike-a-page-rendering.yml`
boots a real API-26 AVD (this app's own `minSdk`) via `reactivecircus/android-emulator-runner` and
runs an instrumented test against it, path-filtered so a one-time spike isn't a permanent CI cost.
Reach for that before declaring device-class work impossible. Its actual finding, worth knowing:
the platform `PdfRenderer` **cannot open this app's own PdfBox-Android output**.

**Compose test assertions.** `assertIsDisplayed()` fails for anything below the fold — an
off-screen node reads the same as a clipped one, so call `performScrollTo()` first. A text matcher
that hits more than one node needs `onAllNodesWithText` + `assertCountEquals`, not
`onNodeWithText`.

**Scope your test runners.** A helper script that ran *every* `androidTest` class turned an
unrelated spike workflow red. Filter to the class you mean.

**Never bulk-edit imports with a regex.** A word-boundary matcher stripped still-used Kotlin
delegate imports (`getValue`/`setValue`) across several files and broke the build. If you must do
it mechanically, diff against `git show HEAD:<file>`, restore anything whose simple name still
appears in the body, then compile.

**AAB ≠ APK for verification.** An `.aab` carries a jar-style signature: verify with
`jarsigner -verify`, not `apksigner`. `aapt2 dump badging` cannot read an `.aab`'s binary manifest
(that needs `bundletool`).

**PDF permission restrictions are an honour system.** Compliant readers respect them; the content
is still decryptable with the open password, and the UI says so plainly. Keep it honest.

## 7. Where the deeper context lives

Implementation plans, architecture decisions, and the full build log live in a **separate private
`tool-docs` repository**, not here — this repo is code only. If you have access, read
`tool-docs/AGENTS.md` first, then `tool-docs/offline-pdf-android/CODE_AUDIT.md` (the build log:
what was built, what was verified, and how) and
`tool-docs/offline-pdf-utility/ANDROID_IMPLEMENTATION_PLAN.md` (the A-1..A-25 build order; its §4
is the fullest statement of these conventions).

Note that the plan and the build log are deliberately two documents: the plan says what to build,
the log says what actually happened, and they are *expected* to diverge when a real blocker turns
up. The divergence is the valuable part.

**If you don't have access**, this file plus `README.md` is enough to work correctly here. You'll
be missing the backlog and the audit trail, so: don't invent an `A-` number, and ask before
starting anything large.
