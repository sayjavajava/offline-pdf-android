package com.offgridpdf.android.pdf

import com.tom_roush.pdfbox.pdmodel.PDDocument
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test
import org.kxml2.io.KXmlParser
import org.xmlpull.v1.XmlPullParser
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.InputStreamReader
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

/**
 * Web reference: `parseBlocks`/`layoutHtmlToPdf` (`docx-layout.ts`),
 * verified against `docx-convert.ts`. Ported behavior, not code — the web
 * version parses mammoth's HTML via `DOMParser`; this parses the DOCX's
 * own OOXML (`word/document.xml`) directly via a pull parser, per Spike B
 * (`ANDROID_IMPLEMENTATION_PLAN.md`, tool-docs repo).
 *
 * `convertDocxToPdf` and `parseDocxBlocks` both take the `XmlPullParser`
 * as a parameter, so — unlike A-12/A-15/A-22's genuine
 * `android.graphics.Bitmap` gaps — the *entire* pipeline is testable here
 * using `kxml2` (the same parser Android's own `Xml.newPullParser()`
 * wraps, already proven in Spike B's `DocxSpikeXmlPullTest.kt`).
 */
class PdfDocxToPdfTest {

    /** Builds a real, minimal, valid `.docx` (an OPC zip package) with [bodyXml] as `word/document.xml`'s `<w:body>`. */
    private fun buildDocx(bodyXml: String): ByteArray {
        val contentTypes = """
            <?xml version="1.0" encoding="UTF-8" standalone="yes"?>
            <Types xmlns="http://schemas.openxmlformats.org/package/2006/content-types">
              <Default Extension="rels" ContentType="application/vnd.openxmlformats-package.relationships+xml"/>
              <Default Extension="xml" ContentType="application/xml"/>
              <Override PartName="/word/document.xml" ContentType="application/vnd.openxmlformats-officedocument.wordprocessingml.document.main+xml"/>
            </Types>
        """.trimIndent()
        val rootRels = """
            <?xml version="1.0" encoding="UTF-8" standalone="yes"?>
            <Relationships xmlns="http://schemas.openxmlformats.org/package/2006/relationships">
              <Relationship Id="rId1" Type="http://schemas.openxmlformats.org/officeDocument/2006/relationships/officeDocument" Target="word/document.xml"/>
            </Relationships>
        """.trimIndent()
        // Built via concatenation, not a `.trimIndent()`'d triple-quoted
        // template: when `bodyXml` is itself an already-`.trimIndent()`'d
        // multi-line string, its zero-indent lines drag the *outer*
        // template's computed common indentation down to zero too, so the
        // `<?xml...?>` line's real leading whitespace survives untouched
        // and is no longer the document's literal first characters —
        // `XmlPullParserException: PI must not start with xml`. Plain
        // concatenation has no shared-indentation computation to corrupt.
        val documentXml = "<?xml version=\"1.0\" encoding=\"UTF-8\" standalone=\"yes\"?>\n" +
            "<w:document xmlns:w=\"http://schemas.openxmlformats.org/wordprocessingml/2006/main\">" +
            "<w:body>$bodyXml</w:body></w:document>"

        val out = ByteArrayOutputStream()
        ZipOutputStream(out).use { zip ->
            for ((name, content) in listOf(
                "[Content_Types].xml" to contentTypes,
                "_rels/.rels" to rootRels,
                "word/document.xml" to documentXml,
            )) {
                zip.putNextEntry(ZipEntry(name))
                zip.write(content.toByteArray(Charsets.UTF_8))
                zip.closeEntry()
            }
        }
        return out.toByteArray()
    }

    private fun blocksOf(bodyXml: String): DocxParseResult {
        val documentXml = extractDocxDocumentXml(buildDocx(bodyXml))
        val parser: XmlPullParser = KXmlParser()
        parser.setInput(InputStreamReader(ByteArrayInputStream(documentXml), Charsets.UTF_8))
        return parseDocxBlocks(parser)
    }

    // --- extractDocxDocumentXml ---

    @Test
    fun `extracts word document xml from a real docx zip`() {
        val bytes = extractDocxDocumentXml(buildDocx("<w:p><w:r><w:t>Hello</w:t></w:r></w:p>"))
        assertTrue(String(bytes, Charsets.UTF_8).contains("Hello"))
    }

    @Test
    fun `rejects a zip with no word document xml`() {
        val out = ByteArrayOutputStream()
        ZipOutputStream(out).use { zip ->
            zip.putNextEntry(ZipEntry("not-a-docx.txt"))
            zip.write("nope".toByteArray())
            zip.closeEntry()
        }
        val error = assertThrows(IllegalArgumentException::class.java) { extractDocxDocumentXml(out.toByteArray()) }
        assertTrue(error.message!!.contains("valid DOCX"))
    }

    // --- parseDocxBlocks ---

    @Test
    fun `parses a plain paragraph`() {
        val result = blocksOf("<w:p><w:r><w:t>Hello world</w:t></w:r></w:p>")
        assertEquals(1, result.blocks.size)
        val paragraph = result.blocks[0] as DocxBlock.Paragraph
        assertEquals(listOf(DocxRun("Hello world")), paragraph.runs)
    }

    @Test
    fun `recognizes a heading by its paragraph style`() {
        val result = blocksOf(
            """<w:p><w:pPr><w:pStyle w:val="Heading2"/></w:pPr><w:r><w:t>Section Title</w:t></w:r></w:p>""",
        )
        val heading = result.blocks.single() as DocxBlock.Heading
        assertEquals(2, heading.level)
        assertEquals("Section Title", heading.runs.single().text)
    }

    @Test
    fun `treats an explicit w-b or w-i with no value as true`() {
        val result = blocksOf(
            """<w:p><w:r><w:rPr><w:b/><w:i/></w:rPr><w:t>Strong and slanted</w:t></w:r></w:p>""",
        )
        val run = (result.blocks.single() as DocxBlock.Paragraph).runs.single()
        assertTrue(run.bold)
        assertTrue(run.italic)
    }

    @Test
    fun `treats w-val=false as explicitly not bold`() {
        val result = blocksOf(
            """<w:p><w:r><w:rPr><w:b w:val="false"/></w:rPr><w:t>Not actually bold</w:t></w:r></w:p>""",
        )
        val run = (result.blocks.single() as DocxBlock.Paragraph).runs.single()
        assertEquals(false, run.bold)
    }

    @Test
    fun `a w-br becomes a newline within the run's text`() {
        val result = blocksOf("""<w:p><w:r><w:t>Line one</w:t><w:br/><w:t>Line two</w:t></w:r></w:p>""")
        val runs = (result.blocks.single() as DocxBlock.Paragraph).runs
        assertEquals(listOf(DocxRun("Line one\nLine two")), runs)
    }

    @Test
    fun `counts a table as skipped and does not parse its paragraphs as top-level blocks`() {
        val result = blocksOf(
            """
            <w:p><w:r><w:t>Before</w:t></w:r></w:p>
            <w:tbl><w:tr><w:tc><w:p><w:r><w:t>Cell text</w:t></w:r></w:p></w:tc></w:tr></w:tbl>
            <w:p><w:r><w:t>After</w:t></w:r></w:p>
            """.trimIndent(),
        )
        assertEquals(1, result.skippedTables)
        assertEquals(0, result.skippedImages)
        assertEquals(2, result.blocks.size)
        assertEquals("Before", (result.blocks[0] as DocxBlock.Paragraph).runs.single().text)
        assertEquals("After", (result.blocks[1] as DocxBlock.Paragraph).runs.single().text)
    }

    @Test
    fun `counts a drawing inside a run as a skipped image`() {
        val result = blocksOf(
            """<w:p><w:r><w:t>Caption: </w:t></w:r><w:r><w:drawing><w:inline><w:docPr/></w:inline></w:drawing></w:r></w:p>""",
        )
        assertEquals(1, result.skippedImages)
        assertEquals(1, result.blocks.size)
    }

    // --- convertDocxToPdf (full pipeline: parse + layout) ---

    private fun freshParser(): XmlPullParser = KXmlParser()

    @Test
    fun `converts a real docx to a PDF whose text can be extracted back`() {
        val docx = buildDocx(
            """
            <w:p><w:pPr><w:pStyle w:val="Heading1"/></w:pPr><w:r><w:t>Report Title</w:t></w:r></w:p>
            <w:p><w:r><w:t>This is the body paragraph.</w:t></w:r></w:p>
            """.trimIndent(),
        )
        val result = convertDocxToPdf(docx, freshParser())
        assertTrue(result.warnings.isEmpty())

        val reloaded = PDDocument.load(ByteArrayInputStream(result.bytes))
        val text = extractText(reloaded, "all").joinToString("\n") { it.text }
        assertTrue(text.contains("Report Title"))
        assertTrue(text.contains("This is the body paragraph."))
        reloaded.close()
    }

    @Test
    fun `overflowing content spills onto additional pages`() {
        val paragraphs = (1..120).joinToString("") { i ->
            "<w:p><w:r><w:t>Paragraph number $i with enough words to take up real space on the page.</w:t></w:r></w:p>"
        }
        val result = convertDocxToPdf(buildDocx(paragraphs), freshParser())

        val reloaded = PDDocument.load(ByteArrayInputStream(result.bytes))
        assertTrue("expected more than one page, got ${reloaded.numberOfPages}", reloaded.numberOfPages > 1)
        reloaded.close()
    }

    @Test
    fun `reports a warning when the document contains a table`() {
        val docx = buildDocx(
            """
            <w:p><w:r><w:t>Text before the table.</w:t></w:r></w:p>
            <w:tbl><w:tr><w:tc><w:p><w:r><w:t>Cell</w:t></w:r></w:p></w:tc></w:tr></w:tbl>
            """.trimIndent(),
        )
        val result = convertDocxToPdf(docx, freshParser())
        assertTrue(result.warnings.any { it.contains("table") })
    }

    /**
     * Real regression test for a real production bug (see this file's own
     * header comment, and `convertDocxToPdf`'s): `android.util.Xml.
     * newPullParser()` enables `FEATURE_PROCESS_NAMESPACES` by default,
     * unlike `kxml2`'s own default (confirmed for real on a device, not
     * assumed) -- with that feature on, `getName()` returns unprefixed
     * local names ("p", not "w:p"), silently matching none of
     * `parseDocxBlocks`'s prefixed checks and producing a blank PDF for
     * every real DOCX conversion, while every JVM test here (which never
     * enabled that feature) stayed green throughout. `KXmlParser` is a
     * real, spec-compliant `XmlPullParser` implementation and supports
     * being switched into that same namespace-aware mode explicitly, so
     * this reproduces the exact real bug under the JVM stub rather than
     * needing another on-device run -- and proves `convertDocxToPdf`'s own
     * fix (explicitly disabling the feature before parsing) actually
     * works regardless of what feature state the caller's parser arrives
     * in.
     */
    @Test
    fun `still parses real content when the caller's parser starts in namespace-aware mode, matching Android's real default`() {
        val parser: XmlPullParser = KXmlParser()
        parser.setFeature(XmlPullParser.FEATURE_PROCESS_NAMESPACES, true)
        val docx = buildDocx(
            """
            <w:p><w:pPr><w:pStyle w:val="Heading1"/></w:pPr><w:r><w:t>Report Title</w:t></w:r></w:p>
            <w:p><w:r><w:t>This is the body paragraph.</w:t></w:r></w:p>
            """.trimIndent(),
        )

        val result = convertDocxToPdf(docx, parser)

        val reloaded = PDDocument.load(ByteArrayInputStream(result.bytes))
        val text = extractText(reloaded, "all").joinToString("\n") { it.text }
        assertTrue("expected real parsed content, got blank text: \"$text\"", text.contains("Report Title"))
        assertTrue(text.contains("This is the body paragraph."))
        reloaded.close()
    }
}
