package com.offgridpdf.android.pdf

import com.tom_roush.pdfbox.pdmodel.PDDocument
import com.tom_roush.pdfbox.pdmodel.PDPage
import com.tom_roush.pdfbox.pdmodel.common.PDRectangle
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream

/**
 * Mirrors the web app's `parsePageRange.test.ts` case-for-case (same
 * inputs, same expected error text) plus `splitPdf`/`splitPdfToZip`'s own
 * ordering/identity tests — see that file for the source of truth these
 * were checked against, not reinvented independently.
 */
class PdfSplitTest {

    /** Each page gets a distinct height so split output can be identified by origin. */
    private fun buildMultiPagePdf(pageCount: Int): PDDocument {
        val document = PDDocument()
        repeat(pageCount) { i -> document.addPage(PDPage(PDRectangle(200f, 100f + i))) }
        return document
    }

    private fun pageHeights(bytes: ByteArray): List<Float> {
        ByteArrayInputStream(bytes).use { input ->
            PDDocument.load(input).use { doc ->
                return (0 until doc.numberOfPages).map { doc.getPage(it).mediaBox.height }
            }
        }
    }

    // --- parsePageRange -----------------------------------------------

    @Test
    fun `parses a single page`() {
        assertEquals(ParsePageRangeResult(listOf(2), emptyList()), parsePageRange("3", 5))
    }

    @Test
    fun `parses a comma list`() {
        assertEquals(ParsePageRangeResult(listOf(0, 2, 4), emptyList()), parsePageRange("1, 3, 5", 5))
    }

    @Test
    fun `parses a range`() {
        assertEquals(ParsePageRangeResult(listOf(1, 2, 3), emptyList()), parsePageRange("2-4", 5))
    }

    @Test
    fun `parses mixed ranges and singles`() {
        assertEquals(ParsePageRangeResult(listOf(0, 2, 3, 4, 7), emptyList()), parsePageRange("1, 3-5, 8", 10))
    }

    @Test
    fun `trims surrounding whitespace`() {
        assertEquals(ParsePageRangeResult(listOf(1, 3, 4), emptyList()), parsePageRange("  2 , 4-5  ", 5))
    }

    @Test
    fun `returns empty indices for an empty string`() {
        assertEquals(ParsePageRangeResult(emptyList(), emptyList()), parsePageRange("", 5))
    }

    @Test
    fun `rejects page 0 as out of range`() {
        val result = parsePageRange("0", 5)
        assertEquals(emptyList<Int>(), result.indices)
        assertEquals(listOf("Page 0 is outside this 5-page document."), result.errors)
    }

    @Test
    fun `reports a reversed range with a suggested fix`() {
        val result = parsePageRange("5-3", 5)
        assertEquals(emptyList<Int>(), result.indices)
        assertEquals(listOf("\"5-3\" is backwards — did you mean 3-5?"), result.errors)
    }

    @Test
    fun `reports pages beyond maxPages`() {
        val result = parsePageRange("99", 5)
        assertEquals(listOf("Page 99 is outside this 5-page document."), result.errors)
    }

    @Test
    fun `reports multiple out-of-range pages together`() {
        val result = parsePageRange("99, 104", 5)
        assertEquals(listOf("Pages 99, 104 are outside this 5-page document."), result.errors)
    }

    @Test
    fun `reports unparseable segments`() {
        val result = parsePageRange("abc", 5)
        assertEquals(listOf("Could not understand \"abc\" in the page range."), result.errors)
    }

    @Test
    fun `reports partial invalidity instead of silently dropping bad segments`() {
        val result = parsePageRange("1-3, 99", 5)
        assertEquals(listOf(0, 1, 2), result.indices)
        assertEquals(listOf("Page 99 is outside this 5-page document."), result.errors)
    }

    @Test
    fun `preserves input order`() {
        assertEquals(listOf(4, 0), parsePageRange("5,1", 5).indices)
    }

    @Test
    fun `keeps duplicates across segments`() {
        assertEquals(listOf(0, 0), parsePageRange("1,1", 5).indices)
        assertEquals(listOf(2, 0, 0), parsePageRange("3,1,1", 5).indices)
    }

    @Test
    fun `does not duplicate pages inside a single expanded range`() {
        assertEquals(listOf(1, 2, 3), parsePageRange("2-4", 5).indices)
    }

    @Test
    fun `keeps a wildly out-of-range message short enough to read`() {
        val result = parsePageRange("1-1000", 5)
        assertEquals(emptyList<Int>(), result.indices)
        assertEquals(1, result.errors.size)
        assertTrue(result.errors[0].length < 120)
        assertTrue(result.errors[0].contains(Regex("and \\d+ more")))
    }

    // --- splitPdfToSingleFile -------------------------------------------

    @Test
    fun `splitPdfToSingleFile accepts all in any case and returns every page in order`() {
        buildMultiPagePdf(4).use { doc ->
            for (variant in listOf("all", "ALL", "All")) {
                val bytes = splitPdfToSingleFile(doc, variant)
                assertEquals(listOf(100f, 101f, 102f, 103f), pageHeights(bytes))
            }
        }
    }

    @Test
    fun `splitPdfToSingleFile preserves asked-for order`() {
        buildMultiPagePdf(5).use { doc ->
            val bytes = splitPdfToSingleFile(doc, "5,1")
            assertEquals(listOf(104f, 100f), pageHeights(bytes))
        }
    }

    @Test
    fun `splitPdfToSingleFile surfaces specific range errors`() {
        buildMultiPagePdf(5).use { doc ->
            val error = assertThrows(IllegalArgumentException::class.java) {
                splitPdfToSingleFile(doc, "1-3, 99")
            }
            assertTrue(error.message!!.contains("Page 99 is outside this 5-page document"))
        }
    }

    @Test
    fun `splitPdfToSingleFile surfaces a reversed-range suggestion`() {
        buildMultiPagePdf(5).use { doc ->
            val error = assertThrows(IllegalArgumentException::class.java) {
                splitPdfToSingleFile(doc, "5-3")
            }
            assertTrue(error.message!!.contains("is backwards"))
        }
    }

    // --- splitPdfToPages --------------------------------------------------

    @Test
    fun `splitPdfToPages returns one single-page PDF per requested page with the right identity`() {
        buildMultiPagePdf(5).use { doc ->
            val pages = splitPdfToPages(doc, "2,4")
            assertEquals(listOf(2, 4), pages.map { it.pageNumber })
            assertEquals(listOf(101f), pageHeights(pages[0].bytes))
            assertEquals(listOf(103f), pageHeights(pages[1].bytes))
        }
    }

    @Test
    fun `splitPdfToPages accepts all and returns every page in order`() {
        buildMultiPagePdf(4).use { doc ->
            val pages = splitPdfToPages(doc, "all")
            assertEquals(listOf(1, 2, 3, 4), pages.map { it.pageNumber })
            pages.forEachIndexed { i, page -> assertEquals(listOf(100f + i), pageHeights(page.bytes)) }
        }
    }

    @Test
    fun `splitPdfToPages preserves asked-for order and duplicates, each as its own entry`() {
        buildMultiPagePdf(5).use { doc ->
            val pages = splitPdfToPages(doc, "5,1,1")
            assertEquals(listOf(5, 1, 1), pages.map { it.pageNumber })
            assertEquals(listOf(104f), pageHeights(pages[0].bytes))
            assertEquals(listOf(100f), pageHeights(pages[1].bytes))
            assertEquals(listOf(100f), pageHeights(pages[2].bytes))
        }
    }

    @Test
    fun `splitPdfToPages surfaces the same range errors as splitPdfToSingleFile`() {
        buildMultiPagePdf(5).use { doc ->
            val error = assertThrows(IllegalArgumentException::class.java) {
                splitPdfToPages(doc, "1-3, 99")
            }
            assertTrue(error.message!!.contains("Page 99 is outside this 5-page document"))
        }
    }

    @Test
    fun `each page returned by splitPdfToPages stands alone as a valid single-page PDF`() {
        buildMultiPagePdf(3).use { doc ->
            val pages = splitPdfToPages(doc, "all")
            for (page in pages) {
                assertEquals(1, pageHeights(page.bytes).size)
            }
        }
    }
}
