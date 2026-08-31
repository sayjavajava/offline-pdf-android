package com.offgridpdf.android.docx

import org.junit.Assert.assertEquals
import org.junit.Test
import org.kxml2.io.KXmlParser
import org.xmlpull.v1.XmlPullParser
import java.io.ByteArrayInputStream
import java.io.InputStreamReader
import java.util.zip.ZipInputStream

/**
 * Spike B, approach 1: parse `word/document.xml` (DOCX's own OOXML, a
 * real, documented, plain-XML format) directly with a pull parser,
 * skipping any HTML-intermediate / DOM step entirely — the web version's
 * `mammoth` + `DOMParser`-based `docx-layout.ts` has no direct Android
 * equivalent since Android has no DOM.
 *
 * Uses `kxml2` here rather than `android.util.Xml.newPullParser()`
 * because the latter is an Android-framework class unavailable under
 * plain JUnit (the same class of gap as `android.graphics.BitmapFactory`
 * in A-12/A-15) — but it's the exact same parser Android's own wrapper
 * uses underneath, so the parsing *logic* below (the event-loop walking
 * `w:p`/`w:t` start/end tags) is real evidence for what the shipped code
 * would do via `android.util.Xml.newPullParser()`, not a stand-in for
 * something structurally different. See `CODE_AUDIT.md`'s Spike B
 * write-up for the real, measured comparison against approach 2
 * (`DocxSpikePoiTest`) and the decision.
 */
class DocxSpikeXmlPullTest {

    private fun extractDocumentXmlBytes(docxBytes: ByteArray): ByteArray {
        ZipInputStream(ByteArrayInputStream(docxBytes)).use { zip ->
            var entry = zip.nextEntry
            while (entry != null) {
                if (entry.name == "word/document.xml") return zip.readBytes()
                entry = zip.nextEntry
            }
        }
        error("word/document.xml not found in fixture")
    }

    /**
     * Walks `w:p` (paragraph) / `w:t` (text run) start/end tags, collecting
     * each paragraph's text. Namespace processing is off by default (kxml2
     * and Android's own `android.util.Xml.newPullParser()` agree on this
     * default), so `parser.name` returns the raw qualified name including
     * its prefix ("w:p", not "p") -- matched on directly here rather than
     * enabling namespace-aware processing, since this spike only needs to
     * prove the parsing approach works, not handle every real-world DOCX's
     * possible alternate namespace prefix (a real A-25 implementation
     * should match by local name + namespace URI instead, for exactly that
     * reason).
     */
    private fun parseParagraphs(documentXmlBytes: ByteArray): List<String> {
        val parser: XmlPullParser = KXmlParser()
        parser.setInput(InputStreamReader(ByteArrayInputStream(documentXmlBytes), Charsets.UTF_8))

        val paragraphs = mutableListOf<String>()
        val currentParagraph = StringBuilder()
        var insideText = false

        var eventType = parser.eventType
        while (eventType != XmlPullParser.END_DOCUMENT) {
            when (eventType) {
                XmlPullParser.START_TAG -> when (parser.name) {
                    "w:p" -> currentParagraph.setLength(0)
                    "w:t" -> insideText = true
                }
                XmlPullParser.TEXT -> if (insideText) currentParagraph.append(parser.text)
                XmlPullParser.END_TAG -> when (parser.name) {
                    "w:t" -> insideText = false
                    "w:p" -> paragraphs.add(currentParagraph.toString())
                }
            }
            eventType = parser.next()
        }
        return paragraphs
    }

    @Test
    fun `reads paragraph text out of a real docx via direct OOXML pull-parsing`() {
        val documentXml = extractDocumentXmlBytes(DocxSpikeFixture.build())
        val extracted = parseParagraphs(documentXml)

        assertEquals(DocxSpikeFixture.paragraphs, extracted)
    }
}
