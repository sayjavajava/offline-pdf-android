package com.offgridpdf.android.ui.tool

import com.offgridpdf.android.pdf.PdfRect
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * The crop preview's outline. The coordinate flip is the part worth pinning
 * down: margins are entered per edge, but the rect is in PDF point-space
 * with a bottom-left origin, so the *bottom* margin becomes y. Getting that
 * backwards would draw an outline that looks plausible on a symmetric crop
 * and is upside down on every other one.
 */
class CropKeptRegionTest {

    private val a4Width = 595f
    private val a4Height = 842f

    @Test
    fun `no margins keeps the whole page`() {
        assertEquals(
            PdfRect(0f, 0f, a4Width, a4Height),
            keptRegionOf("0", "0", "0", "0", a4Width, a4Height),
        )
    }

    @Test
    fun `an even margin insets the page on all four sides`() {
        assertEquals(
            PdfRect(36f, 36f, a4Width - 72f, a4Height - 72f),
            keptRegionOf("36", "36", "36", "36", a4Width, a4Height),
        )
    }

    @Test
    fun `the bottom margin is the origin, not the top`() {
        // top=100, bottom=10: the kept region starts 10pt up from the bottom
        // and its height loses both margins. If y came from the top margin
        // this would be PdfRect(0, 100, ...) instead.
        val kept = keptRegionOf("100", "10", "0", "0", a4Width, a4Height)
        assertEquals(PdfRect(0f, 10f, a4Width, a4Height - 110f), kept)
    }

    @Test
    fun `left and right both shrink the width but only left moves x`() {
        val kept = keptRegionOf("0", "0", "20", "50", a4Width, a4Height)
        assertEquals(PdfRect(20f, 0f, a4Width - 70f, a4Height), kept)
    }

    @Test
    fun `margins that consume the page keep nothing`() {
        assertNull(keptRegionOf("500", "500", "0", "0", a4Width, a4Height))
        assertNull(keptRegionOf("0", "0", "400", "300", a4Width, a4Height))
        // Exactly consuming it is still nothing, not a zero-size rect.
        assertNull(keptRegionOf("0", "842", "0", "0", a4Width, a4Height))
    }

    @Test
    fun `a negative margin is not a crop`() {
        assertNull(keptRegionOf("-10", "0", "0", "0", a4Width, a4Height))
    }

    @Test
    fun `a half-typed margin means no outline yet, not an error`() {
        assertNull(keptRegionOf("", "0", "0", "0", a4Width, a4Height))
        assertNull(keptRegionOf("0", "abc", "0", "0", a4Width, a4Height))
    }
}
