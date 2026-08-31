package com.offgridpdf.android.pdf

import com.tom_roush.pdfbox.pdmodel.PDDocument
import com.tom_roush.pdfbox.pdmodel.PDPage
import com.tom_roush.pdfbox.pdmodel.common.PDRectangle
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Web reference: `redactPdf`/`toPixelRect` (`pdf-redact.ts`),
 * `pixelToPdfRect`/`handleApplyToRange` (`RedactTool.tsx`). The
 * coordinate-flip math (`toPixelRect`/`pixelToPdfRect`) is pure and gets
 * direct tests against known coordinates per `ANDROID_IMPLEMENTATION_PLAN.md`'s
 * own explicit call for this — "get this right with a direct unit test
 * against known coordinates, not by eyeballing it."
 *
 * `redactPdf` itself only gets validation-path tests below (same shape as
 * A-18's/A-20's own real-render gap, see those files' header comments).
 * Every page carrying at least one box goes through PdfBox-Android's own
 * `PDFRenderer.renderImageWithDPI`, and `redactPdf` throws unless at least
 * one page has a box — so there is no way to reach a *successful* call
 * without touching that render path. Under this sandbox's JVM unit-test
 * stub (`isReturnDefaultValues = true`) that call returns a null rendered
 * image internally, which PdfBox-Android's own `PDFRenderer.renderImage`
 * (not this app's code) then dereferences, throwing an uncaught NPE from
 * inside the library before ever reaching application code — not
 * something this app's error handling can do anything about, and not
 * informative to assert on. The real, still-true claim — a redacted page
 * has zero extractable text and no annotations, because it is a freshly
 * built page whose content stream only ever contains a single flattened
 * image `drawImage` call and never a text operator — is a fact about
 * `redactPdf`'s construction, not something provable via a JVM-executed
 * round trip here; it needs a device/emulator, same as A-18's/A-20's own
 * render output.
 */
class PdfRedactTest {

    // --- toPixelRect / pixelToPdfRect ---

    @Test
    fun `toPixelRect converts a known PDF-point rect to pixel space`() {
        // Page 800pt tall, scale 2: a box at (10, 20, 30x40) in PDF space
        // (bottom-left origin) sits, in pixel space (top-left origin), at
        // x=20, y=(800-20-40)*2=1480, 60x80.
        val px = toPixelRect(RedactionRect(x = 10f, y = 20f, width = 30f, height = 40f), pageHeightPts = 800f, scale = 2f)
        assertEquals(20f, px.x)
        assertEquals(1480f, px.y)
        assertEquals(60f, px.width)
        assertEquals(80f, px.height)
    }

    @Test
    fun `toPixelRect places a box flush against the page top at pixel y zero`() {
        // A box whose top edge is the page's own top edge (y + height ==
        // pageHeightPts) must land at pixel y = 0 -- the one case an
        // off-by-a-flip bug would most obviously get wrong.
        val px = toPixelRect(RedactionRect(x = 0f, y = 700f, width = 100f, height = 100f), pageHeightPts = 800f, scale = 1f)
        assertEquals(0f, px.y)
    }

    @Test
    fun `pixelToPdfRect is the real inverse of toPixelRect`() {
        val original = RedactionRect(x = 15f, y = 250f, width = 120f, height = 45f)
        val scale = 2f
        val pageHeightPts = 792f
        val px = toPixelRect(original, pageHeightPts, scale)
        val roundTripped = pixelToPdfRect(
            PixelPoint(px.x, px.y),
            PixelPoint(px.x + px.width, px.y + px.height),
            pageHeightPts,
            scale,
        )
        assertEquals(original.x, roundTripped.x, 0.001f)
        assertEquals(original.y, roundTripped.y, 0.001f)
        assertEquals(original.width, roundTripped.width, 0.001f)
        assertEquals(original.height, roundTripped.height, 0.001f)
    }

    @Test
    fun `pixelToPdfRect normalizes corners drawn in any direction`() {
        // A drag can start bottom-right and end top-left just as easily as
        // the other way around -- the result must be identical either way.
        val pageHeightPts = 800f
        val a = pixelToPdfRect(PixelPoint(50f, 50f), PixelPoint(150f, 150f), pageHeightPts, 1f)
        val b = pixelToPdfRect(PixelPoint(150f, 150f), PixelPoint(50f, 50f), pageHeightPts, 1f)
        assertEquals(a, b)
    }

    // --- redactPdf ---

    @Test
    fun `redactPdf throws when no boxes are given`() {
        val document = PDDocument()
        document.addPage(PDPage())
        val e = assertThrows(IllegalArgumentException::class.java) {
            redactPdf(document, emptyMap())
        }
        document.close()
        assertEquals("Draw at least one redaction box before applying.", e.message)
    }

    @Test
    fun `redactPdf throws when every page maps to an empty box list`() {
        val document = PDDocument()
        document.addPage(PDPage())
        val e = assertThrows(IllegalArgumentException::class.java) {
            redactPdf(document, mapOf(1 to emptyList()))
        }
        document.close()
        assertEquals("Draw at least one redaction box before applying.", e.message)
    }

    @Test
    fun `redactPdf throws for a page number outside the document`() {
        val document = PDDocument()
        document.addPage(PDPage())
        val e = assertThrows(IllegalArgumentException::class.java) {
            redactPdf(document, mapOf(5 to listOf(RedactionRect(0f, 0f, 10f, 10f))))
        }
        document.close()
        assertEquals("Page 5 is outside this 1-page document.", e.message)
    }

    @Test
    fun `redactPdf throws for a zero-width or non-finite box`() {
        val document = PDDocument()
        document.addPage(PDPage())
        assertThrows(IllegalArgumentException::class.java) {
            redactPdf(document, mapOf(1 to listOf(RedactionRect(0f, 0f, 0f, 10f))))
        }
        assertThrows(IllegalArgumentException::class.java) {
            redactPdf(document, mapOf(1 to listOf(RedactionRect(0f, 0f, Float.NaN, 10f))))
        }
        document.close()
    }

    // --- applyBoxesToRange ---

    @Test
    fun `applyBoxesToRange copies boxes only to same-sized pages`() {
        val document = PDDocument()
        document.addPage(PDPage(PDRectangle.LETTER)) // page 1: source
        document.addPage(PDPage(PDRectangle.LETTER)) // page 2: same size
        document.addPage(PDPage(PDRectangle.A4)) // page 3: different size

        val box = RedactionRect(10f, 10f, 50f, 20f)
        val result = applyBoxesToRange(
            document = document,
            redactions = mapOf(1 to listOf(box)),
            sourcePageNumber = 1,
            targetPageNumbers = listOf(2, 3),
        )
        document.close()

        assertEquals(listOf(2), result.applied)
        assertEquals(listOf(3), result.skipped)
        assertEquals(listOf(box), result.redactions[2])
        assertTrue(result.redactions[3].isNullOrEmpty())
    }

    @Test
    fun `applyBoxesToRange never copies a page onto itself even if listed as a target`() {
        val document = PDDocument()
        document.addPage(PDPage(PDRectangle.LETTER))
        document.addPage(PDPage(PDRectangle.LETTER))

        val box = RedactionRect(10f, 10f, 50f, 20f)
        val result = applyBoxesToRange(
            document = document,
            redactions = mapOf(1 to listOf(box)),
            sourcePageNumber = 1,
            targetPageNumbers = listOf(1, 2),
        )
        document.close()

        assertEquals(listOf(2), result.applied)
        assertEquals(emptyList<Int>(), result.skipped)
        assertEquals(listOf(box), result.redactions[1]) // unchanged, not duplicated
    }

    @Test
    fun `applyBoxesToRange is a no-op when the source page has no boxes`() {
        val document = PDDocument()
        document.addPage(PDPage(PDRectangle.LETTER))
        document.addPage(PDPage(PDRectangle.LETTER))

        val result = applyBoxesToRange(
            document = document,
            redactions = emptyMap(),
            sourcePageNumber = 1,
            targetPageNumbers = listOf(2),
        )
        document.close()

        assertEquals(emptyMap<Int, List<RedactionRect>>(), result.redactions)
        assertEquals(emptyList<Int>(), result.applied)
        assertEquals(emptyList<Int>(), result.skipped)
    }
}
