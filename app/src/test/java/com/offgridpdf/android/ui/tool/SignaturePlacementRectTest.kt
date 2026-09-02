package com.offgridpdf.android.ui.tool

import com.offgridpdf.android.pdf.PdfRect
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * The placement fields feed a rect drawn over the page preview. What matters
 * is that a field mid-edit produces no rect rather than a wrong one or a
 * crash — someone clearing a box to retype it is the normal case, not an
 * error.
 */
class SignaturePlacementRectTest {

    @Test
    fun `four valid numbers make a rect`() {
        assertEquals(
            PdfRect(36f, 48f, 150f, 50f),
            placementRectOf("36", "48", "150", "50"),
        )
    }

    @Test
    fun `decimals are kept, since typing beats a finger for precision`() {
        assertEquals(
            PdfRect(36.5f, 48.25f, 150f, 50f),
            placementRectOf("36.5", "48.25", "150", "50"),
        )
    }

    @Test
    fun `a negative origin is allowed — it just places the signature off-page`() {
        // Not this function's job to police: the page's own size is not known
        // here, and addSignature reports an off-page placement itself.
        assertEquals(
            PdfRect(-10f, -20f, 100f, 40f),
            placementRectOf("-10", "-20", "100", "40"),
        )
    }

    @Test
    fun `an empty field means no rect yet, not an error`() {
        assertNull(placementRectOf("", "48", "150", "50"))
        assertNull(placementRectOf("36", "", "150", "50"))
        assertNull(placementRectOf("36", "48", "", "50"))
        assertNull(placementRectOf("36", "48", "150", ""))
    }

    @Test
    fun `a non-numeric field means no rect`() {
        assertNull(placementRectOf("thirty-six", "48", "150", "50"))
        assertNull(placementRectOf("36", "48", "1 5 0", "50"))
    }

    @Test
    fun `a zero or negative size is not a rect`() {
        assertNull(placementRectOf("36", "48", "0", "50"))
        assertNull(placementRectOf("36", "48", "150", "0"))
        assertNull(placementRectOf("36", "48", "-150", "50"))
        assertNull(placementRectOf("36", "48", "150", "-50"))
    }
}
