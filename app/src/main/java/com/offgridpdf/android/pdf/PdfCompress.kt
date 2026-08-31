package com.offgridpdf.android.pdf

import android.graphics.Bitmap
import com.tom_roush.pdfbox.pdmodel.PDDocument
import com.tom_roush.pdfbox.pdmodel.graphics.image.PDImageXObject
import java.io.ByteArrayOutputStream

private const val JPEG_QUALITY = 80

/**
 * Recompresses [document]'s embedded raster images as JPEG at a fixed
 * quality — the one real compression lever available here, per Spike C
 * (`ANDROID_IMPLEMENTATION_PLAN.md`, tool-docs repo): stream-level
 * recompression measured zero gain on PdfBox-Android (no public API to
 * choose a Flate compression level), so unlike the web version's qpdf-
 * based `compressPdf` (`--optimize-images` *and* `--recompress-flate`
 * *and* `--object-streams=generate`), this only has the image lever to
 * pull.
 *
 * Matches qpdf's own `--optimize-images` behavior in the one way that
 * matters most: an image is only replaced when the recompressed bytes
 * actually come out smaller, so a document with no images — or one
 * that's already efficiently compressed — is safely returned unchanged
 * rather than made bigger. Lossy for any image that *is* replaced (a
 * JPEG re-save loses some quality), same disclosed trade-off
 * `CompressTool.tsx`'s own copy states.
 *
 * **Real, honestly-documented gap, same as A-12/A-15**: decoding an
 * embedded image to a `Bitmap` (`PDImageXObject.getImage()`) and
 * re-encoding it (`Bitmap.compress`) both go through
 * `android.graphics.Bitmap`/`BitmapFactory` — real Android framework
 * classes that no-op under this project's plain-JUnit setup (no
 * Robolectric). `PdfCompressTest.kt` can verify the walk-every-page,
 * only-replace-if-smaller, and no-images-is-a-safe-no-op *structure*,
 * but not real compressed image quality or size — that needs manual
 * verification on a device/emulator, same as A-12/A-15 and Spike A.
 *
 * Unlike `createFromByteArray` (A-12/A-15's silent no-op under the stub),
 * `getImage()` actually validates the *decoded* bitmap's dimensions and
 * throws a real `IOException` when they come back degenerate — caught
 * here, per image, so one image that fails to decode (a real corrupt
 * embedded image, or — under this sandbox's `BitmapFactory` stub — any
 * image at all) is skipped rather than aborting the whole compress pass,
 * same fail-safe spirit as "only replace if smaller."
 */
fun compressPdf(document: PDDocument): ByteArray {
    for (page in document.pages) {
        val resources = page.resources ?: continue
        for (name in resources.xObjectNames.toList()) {
            val xObject = resources.getXObject(name)
            if (xObject !is PDImageXObject) continue

            try {
                val originalBytes = xObject.cosObject.createRawInputStream().use { it.readBytes() }
                val bitmap = xObject.image ?: continue
                val recompressed = ByteArrayOutputStream().also {
                    bitmap.compress(Bitmap.CompressFormat.JPEG, JPEG_QUALITY, it)
                }.toByteArray()

                if (recompressed.isNotEmpty() && recompressed.size < originalBytes.size) {
                    resources.put(name, PDImageXObject.createFromByteArray(document, recompressed, name.name))
                }
            } catch (e: Exception) {
                // Leave this image untouched rather than failing the
                // whole document over one image that couldn't be decoded.
            }
        }
    }

    val out = ByteArrayOutputStream()
    document.save(out)
    return out.toByteArray()
}
