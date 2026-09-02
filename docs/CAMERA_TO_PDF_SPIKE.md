# Spike: camera capture to PDF

Status: **spike only.** Nothing here is implemented. The dependencies used
to gather the numbers below were added, measured, and removed again.

## The question

"Scan a document with the camera and get a PDF", of the kind Adobe Scan
does. That product is really five things:

1. Camera capture, framed for a page.
2. Edge detection -- find the document's outline in the frame.
3. Perspective correction -- de-skew that quadrilateral into a rectangle.
4. Enhancement -- contrast, shadow removal, sometimes greyscale.
5. Multi-page capture, reorder, export as one PDF.

Step 5 already exists here. `imagesToPdf` (`pdf/PdfImagesToPdf.kt`) turns a
list of `ImageFile(name, bytes)` into a PDF, one page per image at that
image's own dimensions, and the Convert Images to PDF screen already drives
it with a multi-select picker. A capture feature does not need a new export
path -- it needs to produce JPEG bytes and hand them to the code that is
already there.

## What makes this app different

Two constraints decide the whole design, and neither is negotiable:

- **No `INTERNET` permission, ever.** Not "we don't use the network" -- the
  permission is absent from the manifest, and CI fails the build if it ever
  appears in the *merged* manifest, precisely so a dependency cannot add it
  quietly.
- **No NDK.** `README.md` states the project deliberately has no native
  code.

The second rules out OpenCV, which is where most document-scanner tutorials
go for steps 2 and 3.

## Measured, not assumed

Both candidate libraries were added to a real build and the merged manifest
and APK inspected. Baseline debug APK: **26,796,950 bytes**.

| Option | APK delta | Permissions added to the merged manifest | Offline gate |
|---|---|---|---|
| CameraX 1.5.0 (`core`, `camera2`, `lifecycle`, `view`) | **+2.07 MB** | none | passes |
| ML Kit Document Scanner 16.0.0-beta1 | **+2.77 MB** | `INTERNET`, `ACCESS_NETWORK_STATE` | **fails** |

The ML Kit result is the important one, and it is worse than it looks. The
permissions do not come from the scanner. The manifest-merger blame report
names the source:

```
uses-permission android:name="android.permission.INTERNET"
--> [com.google.android.datatransport:transport-backend-cct:2.3.3]
```

That is Google's telemetry upload backend, arriving transitively. So the
turnkey "Adobe Scan in a box" option would add a network permission to this
app in order to ship analytics, and this repo's own CI gate catches it:

```
CI OFFLINE GATE: would FAIL (INTERNET found in merged manifest)
```

ML Kit is therefore not a close call or a trade-off to weigh. It is
incompatible with the thing this app is for, and the check that exists to
notice that did notice it.

## The options that remain

### A. `ACTION_IMAGE_CAPTURE` -- hand off to the camera app

No library, no APK cost, and **no `CAMERA` permission**: an app that does
not declare `CAMERA` can still start the capture intent. (Declaring it and
not holding it is what breaks; not declaring it at all is fine.)

The catch is what it means rather than what it costs. The document is
photographed by *another app* -- whichever camera app the user has -- which
may back the image up to a cloud service before this app ever sees it. For
a tool whose entire pitch is "nothing you open ever leaves this device",
routing new documents through an uncontrolled third app is a real weakening
of the promise, even though this app itself still sends nothing.

Steps 2-4 do not exist at all in this option. The user gets whatever
photograph they took: a page at an angle, with shadows.

### B. CameraX in-app capture

+2.07 MB, no permissions from the library, and the first real permission
this app has ever declared: `CAMERA`.

That last point deserves to be a product decision rather than a technical
one. The app currently requests *nothing*, and the store listing says so.
Adding Camera is visible to every user and is the sort of thing a
privacy-focused audience notices. It is defensible -- capture genuinely
needs the camera, and in-process capture is *better* for the privacy story
than option A -- but it should be a deliberate choice, not a side effect of
picking a library.

In exchange, the image never leaves this process between the sensor and the
PDF.

### C. Edge detection and perspective correction without native code

Feasible, and more work than the capture itself. On a downscaled bitmap
(say 640px on the long edge, which is plenty for finding a page outline):
greyscale, blur, Sobel gradients, threshold, then either a Hough transform
for the four dominant lines or contour tracing for the largest convex quad.
Perspective correction is then a homography, which is straightforward
matrix work, applied with `Canvas.drawBitmapMesh` or a per-pixel inverse
map.

None of that needs the NDK. All of it needs tuning against real
photographs -- shadows, patterned tablecloths, a page that does not contrast
with the desk -- and that tuning is the actual cost, not the algorithm.

The honest read: this is its own project, comparable in size to a whole
tool screen, and it cannot be evaluated without a device and a pile of real
photos.

## Recommendation

Ship it in two stages, and make the permission question explicit before
either.

**Stage 1 -- capture to PDF, no correction.** CameraX capture into the
existing `imagesToPdf` path, with multi-page capture (shoot, review,
retake, add another) and manual crop reusing the crop preview from PR #50,
which already draws a rectangle over a rendered page and reports it in
point space. That gives a working scanner with an honest description --
"photograph pages into a PDF, and crop them yourself" -- rather than
implying automatic correction that is not there.

**Stage 2 -- automatic edge detection**, if stage 1 gets used. Prototyped
against real photographs first, behind a "detect edges" button that falls
back to the manual crop when it is not confident. Never silently: a wrong
automatic crop that removes part of a document is worse than no crop.

**Do not** take the ML Kit route to shortcut stage 2. The measurement above
is the reason.

## What still needs a device

Everything about image quality. Capture resolution against PDF page size,
JPEG quality against file size, whether a phone-camera page at default
settings is legible enough to be worth shipping, and how long
`imagesToPdf` takes for a 20-page capture. The existing spike workflows
(`.github/workflows/spike-a-page-rendering.yml`) are the pattern for
answering that on a real emulator.

## Open question for the product

Adding `CAMERA` is the first permission this app would request. It is the
right call if capture is a feature worth having, and it is worth deciding on
purpose rather than discovering in a store listing diff.
