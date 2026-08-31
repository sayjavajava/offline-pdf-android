package com.offgridpdf.android.pdf

import com.tom_roush.pdfbox.pdmodel.PDDocument
import com.tom_roush.pdfbox.pdmodel.PDPage
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * Web reference: `comparePdfs`/`describe`/`buildReport` (`pdf-compare.ts`,
 * `CompareTool.tsx`). Real per-page visual comparison touches
 * `android.graphics.Bitmap` for every shared page (`renderImageWithDPI`
 * + `Bitmap.getPixels`), the same genuine gap as A-18/A-19 — not
 * verifiable under the JVM unit-test stub. What's tested here for real:
 * the presence-only (`onlyInA`/`onlyInB`) page accounting, which never
 * touches a shared page's rendering at all when one document has zero
 * pages in common with the other, plus the pure presentation logic
 * (`describeComparison`/`buildCompareReport`) against literal
 * `PageComparison` values.
 */
class PdfCompareTest {

    @Test
    fun `every page is onlyInB when document A has no pages`() {
        val documentA = PDDocument()
        val documentB = PDDocument()
        documentB.addPage(PDPage())
        documentB.addPage(PDPage())

        val result = comparePdfs(documentA, documentB)
        documentA.close()
        documentB.close()

        assertEquals(0, result.pageCountA)
        assertEquals(2, result.pageCountB)
        assertEquals(listOf(PageComparison.OnlyInB(1), PageComparison.OnlyInB(2)), result.pages)
    }

    @Test
    fun `every page is onlyInA when document B has no pages`() {
        val documentA = PDDocument()
        documentA.addPage(PDPage())
        val documentB = PDDocument()

        val result = comparePdfs(documentA, documentB)
        documentA.close()
        documentB.close()

        assertEquals(1, result.pageCountA)
        assertEquals(0, result.pageCountB)
        assertEquals(listOf(PageComparison.OnlyInA(1)), result.pages)
    }

    @Test
    fun `two empty documents compare as equal with no pages listed`() {
        val documentA = PDDocument()
        val documentB = PDDocument()

        val result = comparePdfs(documentA, documentB)
        documentA.close()
        documentB.close()

        assertEquals(0, result.pageCountA)
        assertEquals(0, result.pageCountB)
        assertEquals(emptyList<PageComparison>(), result.pages)
    }

    // --- describeComparison ---

    @Test
    fun `describeComparison labels each real outcome`() {
        assertEquals("Only in A (removed)", describeComparison(PageComparison.OnlyInA(1)))
        assertEquals("Only in B (added)", describeComparison(PageComparison.OnlyInB(1)))
        assertEquals(
            "Different page size",
            describeComparison(PageComparison.Both(1, textDiffers = null, visuallyDiffers = true, pixelDiffRatio = null)),
        )
        assertEquals(
            "Identical",
            describeComparison(PageComparison.Both(1, textDiffers = false, visuallyDiffers = false, pixelDiffRatio = 0f)),
        )
        assertEquals(
            "Text and visual differences",
            describeComparison(PageComparison.Both(1, textDiffers = true, visuallyDiffers = true, pixelDiffRatio = 0.5f)),
        )
        assertEquals(
            "Text differs",
            describeComparison(PageComparison.Both(1, textDiffers = true, visuallyDiffers = false, pixelDiffRatio = 0f)),
        )
        assertEquals(
            "Visual differences",
            describeComparison(PageComparison.Both(1, textDiffers = false, visuallyDiffers = true, pixelDiffRatio = 0.2f)),
        )
    }

    // --- buildCompareReport ---

    @Test
    fun `buildCompareReport lists every page with its label and pixel ratio`() {
        val result = CompareResult(
            pageCountA = 2,
            pageCountB = 3,
            pages = listOf(
                PageComparison.Both(1, textDiffers = false, visuallyDiffers = false, pixelDiffRatio = 0f),
                PageComparison.Both(2, textDiffers = true, visuallyDiffers = true, pixelDiffRatio = 0.256f),
                PageComparison.OnlyInB(3),
            ),
        )
        val report = buildCompareReport("A.pdf", "B.pdf", result)
        assertEquals(
            "Compared: A.pdf (2 pages) vs B.pdf (3 pages)\n" +
                "\n" +
                "Page 1: Identical (0.0% of pixels)\n" +
                "Page 2: Text and visual differences (25.6% of pixels)\n" +
                "Page 3: Only in B (added)",
            report,
        )
    }
}
