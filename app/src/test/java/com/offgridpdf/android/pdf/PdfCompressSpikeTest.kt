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
 * `CODE_AUDIT.md`'s Spike C write-up for the full context and real
 * measured result: recompressing this fixture's content stream through
 * `recompressPageContentStreams` measured **original=1119 bytes,
 * recompressed=1119 bytes — byte-for-byte identical**, on the only
 * environment this sandbox can actually run PdfBox-Android in (CI; no
 * local Gradle here). Not a bug: the content was already written through
 * the same default Flate filter this experiment re-runs, so re-encoding
 * the same bytes with the same encoder and no way to pick a different
 * level reproduces the same output exactly — the real, load-bearing
 * finding for this spike (see the write-up for what that means for A-22).
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
    fun `recompressing through the only available filter reproduces the same bytes and preserves the text`() {
        val document = buildRepetitiveFixture()
        val result = recompressPageContentStreams(document)

        assertEquals(
            "with no public API to choose a compression level, re-encoding through the same default filter should reproduce the same size",
            result.originalBytes,
            result.recompressedBytes,
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
