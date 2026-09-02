package com.offgridpdf.android.pdf

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The budget arithmetic is deliberately pure so it can be tested for real
 * here — unlike the rendering it protects, which needs `Bitmap` and so only
 * no-ops under this project's plain-JUnit setup (see `PdfCompress.kt`'s note
 * on the same limitation). These are the numbers the guard actually decides
 * on, checked against page sizes that occur in practice.
 */
class PdfRenderBudgetTest {

    // US Letter and A4 in PDF points, the two sizes nearly every real
    // document uses.
    private val letterWidth = 612f
    private val letterHeight = 792f
    private val a4Width = 595f
    private val a4Height = 842f

    @Test
    fun `estimates a Letter page at scale 1 as roughly 1_9 MB`() {
        // 612 x 792 px x 4 bytes = 1,938,816
        assertEquals(1_938_816L, estimateBitmapBytes(letterWidth, letterHeight, 1f))
    }

    @Test
    fun `scaling by four multiplies the estimate by sixteen`() {
        val atOne = estimateBitmapBytes(letterWidth, letterHeight, 1f)
        val atFour = estimateBitmapBytes(letterWidth, letterHeight, 4f)
        assertEquals(atOne * 16, atFour)
    }

    @Test
    fun `a Letter page at the maximum scale is over 100 MB`() {
        // The case that motivated the guard: scale 8 is 576dpi, and one page
        // of it does not fit in a modest device's whole heap.
        val bytes = estimateBitmapBytes(letterWidth, letterHeight, 8f)
        assertTrue("expected >100MB, got ${bytes / (1024 * 1024)}MB", bytes > 100L * 1024 * 1024)
    }

    @Test
    fun `does not overflow on an absurd page and scale`() {
        // 4000pt square at scale 8 is 32000 x 32000 px; as Int arithmetic
        // this product wraps negative, which would make the guard pass the
        // very request it exists to reject.
        val bytes = estimateBitmapBytes(4000f, 4000f, 8f)
        assertTrue("expected a positive estimate, got $bytes", bytes > 0)
        assertEquals(32_000L * 32_000L * 4L, bytes)
    }

    @Test
    fun `allows a page that fits the budget`() {
        // A4 at scale 2 is about 8MB; 64MB of headroom is plenty.
        requireRenderableAtScale(a4Width, a4Height, 2f, pageNumber = 1, budgetBytes = 64L * 1024 * 1024)
    }

    @Test
    fun `rejects a page that exceeds the budget, naming the page and the fix`() {
        val error = assertThrows(IllegalArgumentException::class.java) {
            requireRenderableAtScale(letterWidth, letterHeight, 8f, pageNumber = 7, budgetBytes = 64L * 1024 * 1024)
        }
        val message = error.message!!
        assertTrue("should name the page: $message", message.contains("Page 7"))
        assertTrue("should suggest the one knob the user controls: $message", message.contains("smaller scale"))
    }

    @Test
    fun `custom advice replaces the default, for a screen with no scale to lower`() {
        val error = assertThrows(IllegalArgumentException::class.java) {
            requireRenderableAtScale(
                letterWidth,
                letterHeight,
                8f,
                pageNumber = 3,
                budgetBytes = 64L * 1024 * 1024,
                advice = "This page cannot be previewed on this device.",
            )
        }
        val message = error.message!!
        assertTrue("should still name the page: $message", message.contains("Page 3"))
        assertTrue("should carry the caller's advice: $message", message.contains("cannot be previewed"))
        assertFalse(
            "should not tell a preview's user to change a scale they cannot reach: $message",
            message.contains("smaller scale"),
        )
    }

    @Test
    fun `the boundary is inclusive so an exactly-fitting page is allowed`() {
        val exact = estimateBitmapBytes(letterWidth, letterHeight, 1f)
        requireRenderableAtScale(letterWidth, letterHeight, 1f, pageNumber = 1, budgetBytes = exact)
        assertThrows(IllegalArgumentException::class.java) {
            requireRenderableAtScale(letterWidth, letterHeight, 1f, pageNumber = 1, budgetBytes = exact - 1)
        }
    }

    @Test
    fun `the device budget is a positive fraction of the heap`() {
        val budget = maxSafeBitmapBytes()
        assertTrue("budget should be positive, was $budget", budget > 0)
        assertTrue("budget should be below the whole heap", budget < Runtime.getRuntime().maxMemory())
    }
}
