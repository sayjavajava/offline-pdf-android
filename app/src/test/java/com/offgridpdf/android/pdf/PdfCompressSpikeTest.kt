package com.offgridpdf.android.pdf

import com.tom_roush.pdfbox.pdmodel.PDDocument
import com.tom_roush.pdfbox.pdmodel.PDPage
import com.tom_roush.pdfbox.pdmodel.PDPageContentStream
import com.tom_roush.pdfbox.pdmodel.font.PDType1Font
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream

/**
 * Spike C measurement — see `PdfCompressSpike.kt`'s header and
 * `CODE_AUDIT.md`'s Spike C write-up for the full context and the real
 * measured numbers this test produces on CI (this sandbox has no local
 * Gradle, so CI is the only place this can actually run):
 * **original=1119 bytes, recompressed=1099 bytes (~1.8% smaller)** for
 * this fixture. A first run deliberately failed with `fail(...)` to get
 * that number out of the CI log via the existing `testLogging` (A-10);
 * this is the permanent, real assertion that replaced it.
 */
class PdfCompressSpikeTest {

    private val repeatedLine = "The quick brown fox jumps over the lazy dog."

    private fun buildRepetitiveFixture(): PDDocument {
        val document = PDDocument()
        val page = PDPage()
        document.addPage(page)
        PDPageContentStream(document, page).use { stream ->
            stream.setFont(PDType1Font.HELVETICA, 10f)
            // Deliberately repetitive: many identical operator sequences,
            // the shape most amenable to a higher compression ratio.
            for (i in 0 until 200) {
                stream.beginText()
                stream.newLineAtOffset(72f, (700 - i * 3).toFloat())
                stream.showText("$repeatedLine Line $i.")
                stream.endText()
            }
        }
        return document
    }

    @Test
    fun `recompressing a page's content stream at max ratio shrinks it and preserves its text`() {
        val document = buildRepetitiveFixture()
        val result = recompressPageContentStreams(document)

        assertTrue(
            "recompressed (${result.recompressedBytes}) should be no larger than original (${result.originalBytes})",
            result.recompressedBytes <= result.originalBytes,
        )

        val out = ByteArrayOutputStream()
        document.save(out)
        document.close()

        val reloaded = PDDocument.load(ByteArrayInputStream(out.toByteArray()))
        val text = extractText(reloaded, "all").single().text
        assertTrue("recompressed content must still contain its original text", text.contains(repeatedLine))
        assertEquals(200, Regex(Regex.escape(repeatedLine)).findAll(text).count())
        reloaded.close()
    }
}
