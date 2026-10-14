package com.offgridpdf.android.pdf

import com.tom_roush.pdfbox.pdmodel.PDPage
import com.tom_roush.pdfbox.pdmodel.common.PDRectangle
import org.junit.Assert.assertEquals
import org.junit.Test
import kotlin.math.cos
import kotlin.math.sin

/**
 * Web reference: `page-space.test.ts`, with the same independent oracle: the
 * display transform built the general way (a real rotation matrix about the
 * box origin, shifted back into the positive quadrant) rather than from the
 * per-angle cases `PageSpace.kt` uses. That is what `/Rotate` means: the page
 * is shown turned clockwise.
 */
class PageSpaceTest {

    private val offset = PageBox(100f, 50f, 612f, 792f)
    private val plain = PageBox(0f, 0f, 612f, 792f)

    private fun userToDisplayed(u: Float, v: Float, box: PageBox, rotation: Int): Pair<Double, Double> {
        val rad = Math.toRadians(rotation.toDouble())
        fun turn(x: Double, y: Double) = (x * cos(rad) + y * sin(rad)) to (-x * sin(rad) + y * cos(rad))
        val w = box.width.toDouble()
        val h = box.height.toDouble()
        val corners = listOf(turn(0.0, 0.0), turn(w, 0.0), turn(0.0, h), turn(w, h))
        val (x, y) = turn((u - box.x).toDouble(), (v - box.y).toDouble())
        return (x - corners.minOf { it.first }) to (y - corners.minOf { it.second })
    }

    private fun assertNear(expected: Pair<Double, Double>, actual: Pair<Double, Double>, what: String) {
        assertEquals("$what x", expected.first, actual.first, 1e-2)
        assertEquals("$what y", expected.second, actual.second, 1e-2)
    }

    @Test
    fun `toUserSpace maps a displayed point to the user-space point that displays there`() {
        for (box in listOf(plain, offset)) {
            for (rotation in listOf(0, 90, 180, 270, -90, 450)) {
                for ((dx, dy) in listOf(0f to 0f, 36f to 20f, 300f to 700f)) {
                    val (ux, uy) = toUserSpace(dx, dy, box, rotation)
                    assertNear(dx.toDouble() to dy.toDouble(), userToDisplayed(ux, uy, box, rotation), "/Rotate $rotation, $box")
                }
            }
        }
    }

    @Test
    fun `displayedSize swaps width and height for a quarter turn only`() {
        assertEquals(612f to 792f, displayedSize(offset, 0))
        assertEquals(792f to 612f, displayedSize(offset, 90))
        assertEquals(612f to 792f, displayedSize(offset, 180))
        assertEquals(792f to 612f, displayedSize(offset, -90))
    }

    @Test
    fun `a rotation that isn't a multiple of 90 counts as none, as PDFBox and pdf_js treat it`() {
        assertEquals(0, normalizeRotation(45))
        assertEquals(270, normalizeRotation(-90))
        assertEquals(90, normalizeRotation(450))
    }

    @Test
    fun `visibleBox is the CropBox clipped to the MediaBox`() {
        val page = PDPage(PDRectangle(0f, 0f, 612f, 792f))
        page.cropBox = PDRectangle(-20f, 100f, 380f, 1000f) // overhangs left and top
        assertEquals(PageBox(0f, 100f, 360f, 692f), visibleBox(page))
    }

    @Test
    fun `the signature image lands upright on the displayed placement`() {
        val placement = SignaturePlacement(page = 1, x = 40f, y = 500f, width = 120f, height = 45f)
        for (box in listOf(plain, offset)) {
            for (rotation in listOf(0, 90, 180, 270)) {
                val t = signatureImageTransform(placement, box, rotation)
                // Where the image's own unit square ends up, per the PDF `cm` rule.
                fun shown(lx: Float, ly: Float) =
                    userToDisplayed(t.a * lx + t.c * ly + t.e, t.b * lx + t.d * ly + t.f, box, rotation)
                // Its bottom-left, right edge and top edge pin position and orientation.
                assertNear(40.0 to 500.0, shown(0f, 0f), "origin, /Rotate $rotation, $box")
                assertNear(160.0 to 500.0, shown(1f, 0f), "right edge, /Rotate $rotation, $box")
                assertNear(40.0 to 545.0, shown(0f, 1f), "top edge, /Rotate $rotation, $box")
            }
        }
    }
}
