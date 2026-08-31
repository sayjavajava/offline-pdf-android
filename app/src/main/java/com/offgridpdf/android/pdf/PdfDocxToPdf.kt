package com.offgridpdf.android.pdf

import com.tom_roush.pdfbox.pdmodel.PDDocument
import com.tom_roush.pdfbox.pdmodel.PDPage
import com.tom_roush.pdfbox.pdmodel.PDPageContentStream
import com.tom_roush.pdfbox.pdmodel.common.PDRectangle
import com.tom_roush.pdfbox.pdmodel.font.PDFont
import com.tom_roush.pdfbox.pdmodel.font.PDType1Font
import org.xmlpull.v1.XmlPullParser
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.InputStreamReader
import java.util.zip.ZipInputStream

/**
 * A-25 — Convert DOCX to PDF (`ANDROID_IMPLEMENTATION_PLAN.md`, tool-docs
 * repo), the plan's own "hardest item," using the approach Spike B's real
 * measurements recommended: parse the DOCX's own OOXML (`word/document.xml`,
 * a real, documented, plain-XML format) directly with a pull parser, skip
 * `mammoth` and the web version's `DOMParser`-based `docx-layout.ts`
 * entirely — Android has no DOM at all — and skip Apache POI too (Spike B
 * proved it dexes cleanly, but at a real ~36.8 MB APK cost for what a
 * zero-new-dependency alternative already does).
 *
 * The parser (`parseDocxBlocks`) and the whole pipeline
 * (`convertDocxToPdf`) both take an [XmlPullParser] as a parameter rather
 * than constructing `android.util.Xml.newPullParser()` internally — unlike
 * A-12/A-15/A-22's genuine `android.graphics.Bitmap`-shaped gaps (no
 * testable equivalent exists for those), this makes the *entire* pipeline
 * testable under plain JUnit: tests pass a real `kxml2` parser (same
 * technique Spike B's own `DocxSpikeXmlPullTest.kt` used), production code
 * passes Android's own `Xml.newPullParser()` — same interface, same
 * parsing logic, no gap.
 *
 * Deliberately bounded scope, matching the web version's own "rough
 * parity, not pixel-identical" bar (`docx-layout.ts`'s header comment) but
 * narrower still: headings, paragraphs, and bold/italic runs are laid out
 * properly. Tables and images are not supported in this pass — skipped
 * and counted as warnings, never silently dropped, same honesty standard
 * as the web version's own unencodable-character/skipped-image warnings.
 * List item text is not lost either, but its bullet/number marker and
 * indentation are not preserved yet — a `w:numPr`-marked paragraph is
 * parsed and drawn as an ordinary paragraph, real content, imperfect
 * formatting, rather than dropped.
 */

data class DocxRun(val text: String, val bold: Boolean = false, val italic: Boolean = false)

sealed class DocxBlock {
    data class Heading(val level: Int, val runs: List<DocxRun>) : DocxBlock()
    data class Paragraph(val runs: List<DocxRun>) : DocxBlock()
}

data class DocxParseResult(val blocks: List<DocxBlock>, val skippedTables: Int, val skippedImages: Int)

data class DocxConversionResult(val bytes: ByteArray, val warnings: List<String>)

private const val PAGE_WIDTH = 595f
private const val PAGE_HEIGHT = 842f
private const val MARGIN = 50f
private const val CONTENT_WIDTH = PAGE_WIDTH - MARGIN * 2
private const val LINE_HEIGHT_FACTOR = 1.35f
private const val BODY_SIZE = 11f
private val HEADING_SIZES = mapOf(1 to 22f, 2 to 18f, 3 to 15f, 4 to 13f, 5 to 12f, 6 to 11f)
private val HEADING_STYLE = Regex("^[Hh]eading\\s*([1-6])$")

/** Extracts `word/document.xml`'s raw bytes from a real `.docx` (an OPC zip package). */
fun extractDocxDocumentXml(docxBytes: ByteArray): ByteArray {
    ZipInputStream(ByteArrayInputStream(docxBytes)).use { zip ->
        var entry = zip.nextEntry
        while (entry != null) {
            if (entry.name == "word/document.xml") return zip.readBytes()
            entry = zip.nextEntry
        }
    }
    throw IllegalArgumentException("This does not look like a valid DOCX file (no word/document.xml found).")
}

private fun isFalseVal(value: String?): Boolean = value == "false" || value == "0" || value == "off"

private fun headingLevelFromStyle(styleId: String?): Int? {
    val match = styleId?.let { HEADING_STYLE.find(it) } ?: return null
    return match.groupValues[1].toIntOrNull()
}

/**
 * Walks `word/document.xml`'s own `w:p`/`w:r`/`w:t`/`w:b`/`w:i`/`w:pStyle`
 * structure directly. Namespace processing is off (kxml2 and Android's own
 * `Xml.newPullParser()` agree on this default, confirmed in Spike B), so
 * tag/attribute names are matched by their raw qualified form (`"w:p"`,
 * `"w:val"`), not a bare local name.
 */
fun parseDocxBlocks(parser: XmlPullParser): DocxParseResult {
    val blocks = mutableListOf<DocxBlock>()
    var skippedTables = 0
    var skippedImages = 0

    fun attributeValue(name: String): String? {
        for (i in 0 until parser.attributeCount) {
            if (parser.getAttributeName(i) == name) return parser.getAttributeValue(i)
        }
        return null
    }

    fun skipElement(tagName: String) {
        var depth = 1
        while (depth > 0) {
            when (parser.next()) {
                XmlPullParser.START_TAG -> if (parser.name == tagName) depth++
                XmlPullParser.END_TAG -> if (parser.name == tagName) depth--
            }
        }
    }

    fun readText(tagName: String): String {
        val sb = StringBuilder()
        var eventType = parser.next()
        while (!(eventType == XmlPullParser.END_TAG && parser.name == tagName)) {
            if (eventType == XmlPullParser.TEXT) sb.append(parser.text)
            eventType = parser.next()
        }
        return sb.toString()
    }

    fun parseRun(): DocxRun {
        var bold = false
        var italic = false
        val text = StringBuilder()
        var eventType = parser.next()
        while (!(eventType == XmlPullParser.END_TAG && parser.name == "w:r")) {
            if (eventType == XmlPullParser.START_TAG) {
                when (parser.name) {
                    "w:b" -> bold = !isFalseVal(attributeValue("w:val"))
                    "w:i" -> italic = !isFalseVal(attributeValue("w:val"))
                    "w:t" -> text.append(readText("w:t"))
                    "w:br", "w:cr" -> text.append("\n")
                    "w:tab" -> text.append("\t")
                    "w:drawing" -> {
                        skipElement("w:drawing")
                        skippedImages++
                    }
                }
            }
            eventType = parser.next()
        }
        return DocxRun(text.toString(), bold, italic)
    }

    fun parseParagraph(): DocxBlock {
        var headingLevel: Int? = null
        val runs = mutableListOf<DocxRun>()
        var eventType = parser.next()
        while (!(eventType == XmlPullParser.END_TAG && parser.name == "w:p")) {
            if (eventType == XmlPullParser.START_TAG) {
                when (parser.name) {
                    "w:pStyle" -> headingLevel = headingLevelFromStyle(attributeValue("w:val"))
                    "w:r" -> runs.add(parseRun())
                }
            }
            eventType = parser.next()
        }
        val level = headingLevel
        return if (level != null) DocxBlock.Heading(level, runs) else DocxBlock.Paragraph(runs)
    }

    var eventType = parser.eventType
    while (eventType != XmlPullParser.END_DOCUMENT) {
        if (eventType == XmlPullParser.START_TAG) {
            when (parser.name) {
                "w:p" -> blocks.add(parseParagraph())
                "w:tbl" -> {
                    skipElement("w:tbl")
                    skippedTables++
                }
            }
        }
        eventType = parser.next()
    }

    return DocxParseResult(blocks, skippedTables, skippedImages)
}

private data class Atom(val text: String, val run: DocxRun, val hardBreak: Boolean = false)

private fun wordsOf(part: String, run: DocxRun): List<Atom> =
    Regex("\\s+|\\S+").findAll(part).map { Atom(it.value, run) }.toList()

private fun tokenize(runs: List<DocxRun>): List<Atom> {
    val atoms = mutableListOf<Atom>()
    for (run in runs) {
        var start = 0
        val text = run.text
        for (i in text.indices) {
            if (text[i] == '\n') {
                if (i > start) atoms.addAll(wordsOf(text.substring(start, i), run))
                atoms.add(Atom("", run, hardBreak = true))
                start = i + 1
            }
        }
        if (start < text.length) atoms.addAll(wordsOf(text.substring(start), run))
    }
    return atoms
}

/** Lays [DocxBlock]s out onto real PDF pages, one `PDPage`/content stream at a time. */
private class DocxPdfWriter(private val document: PDDocument) {
    private var page: PDPage = newPage()
    private var stream: PDPageContentStream = PDPageContentStream(document, page)
    private var cursorY = PAGE_HEIGHT - MARGIN
    var unencodableCount = 0
        private set

    private fun newPage(): PDPage {
        val p = PDPage(PDRectangle(PAGE_WIDTH, PAGE_HEIGHT))
        document.addPage(p)
        return p
    }

    private fun ensureSpace(height: Float) {
        if (cursorY - height < MARGIN) {
            stream.close()
            page = newPage()
            stream = PDPageContentStream(document, page)
            cursorY = PAGE_HEIGHT - MARGIN
        }
    }

    private fun fontFor(bold: Boolean, italic: Boolean): PDFont = when {
        bold && italic -> PDType1Font.HELVETICA_BOLD_OBLIQUE
        bold -> PDType1Font.HELVETICA_BOLD
        italic -> PDType1Font.HELVETICA_OBLIQUE
        else -> PDType1Font.HELVETICA
    }

    private fun widthOf(text: String, font: PDFont, size: Float): Float =
        if (text.isEmpty()) 0f else font.getStringWidth(text) / 1000f * size

    private fun sanitize(text: String, font: PDFont): String {
        val out = StringBuilder()
        for (ch in text) {
            try {
                font.encode(ch.toString())
                out.append(ch)
            } catch (e: Exception) {
                out.append('?')
                unencodableCount++
            }
        }
        return out.toString()
    }

    private fun wrapIntoLines(runs: List<DocxRun>, maxWidth: Float, size: Float): List<List<Pair<String, DocxRun>>> {
        val atoms = tokenize(runs)
        val lines = mutableListOf<MutableList<Pair<String, DocxRun>>>()
        var current = mutableListOf<Pair<String, DocxRun>>()
        var width = 0f

        for (atom in atoms) {
            if (atom.hardBreak) {
                lines.add(current)
                current = mutableListOf()
                width = 0f
                continue
            }
            val isSpace = atom.text.isBlank()
            if (isSpace && current.isEmpty()) continue

            val w = widthOf(atom.text, fontFor(atom.run.bold, atom.run.italic), size)
            if (!isSpace && current.isNotEmpty() && width + w > maxWidth) {
                lines.add(current)
                current = mutableListOf()
                width = 0f
            }
            current.add(atom.text to atom.run)
            width += w
        }
        lines.add(current)
        return lines
    }

    private fun mergeSegments(line: List<Pair<String, DocxRun>>): List<Pair<String, DocxRun>> {
        val merged = mutableListOf<Pair<String, DocxRun>>()
        for ((text, run) in line) {
            val last = merged.lastOrNull()
            if (last != null && last.second.bold == run.bold && last.second.italic == run.italic) {
                merged[merged.size - 1] = (last.first + text) to last.second
            } else {
                merged.add(text to run)
            }
        }
        return merged
    }

    private fun drawWrapped(runs: List<DocxRun>, x: Float, maxWidth: Float, size: Float, lineHeight: Float) {
        for (line in wrapIntoLines(runs, maxWidth, size)) {
            ensureSpace(lineHeight)
            var lx = x
            val baseline = cursorY - size
            for ((text, run) in mergeSegments(line)) {
                val font = fontFor(run.bold, run.italic)
                val sanitized = sanitize(text, font)
                val w = widthOf(sanitized, font, size)
                if (sanitized.isNotBlank()) {
                    stream.beginText()
                    stream.setFont(font, size)
                    stream.newLineAtOffset(lx, baseline)
                    stream.showText(sanitized)
                    stream.endText()
                }
                lx += w
            }
            cursorY -= lineHeight
        }
    }

    fun drawHeading(level: Int, runs: List<DocxRun>) {
        val size = HEADING_SIZES[level] ?: BODY_SIZE
        val lineHeight = size * LINE_HEIGHT_FACTOR
        ensureSpace(lineHeight + size * 0.3f)
        cursorY -= size * 0.3f
        drawWrapped(runs.map { it.copy(bold = true) }, MARGIN, CONTENT_WIDTH, size, lineHeight)
        cursorY -= size * 0.25f
    }

    fun drawParagraph(runs: List<DocxRun>) {
        if (runs.isEmpty() || runs.all { it.text.isBlank() }) {
            cursorY -= BODY_SIZE * LINE_HEIGHT_FACTOR * 0.5f
            return
        }
        drawWrapped(runs, MARGIN, CONTENT_WIDTH, BODY_SIZE, BODY_SIZE * LINE_HEIGHT_FACTOR)
        cursorY -= BODY_SIZE * LINE_HEIGHT_FACTOR * 0.35f
    }

    fun finish() {
        stream.close()
    }
}

/**
 * Converts [docxBytes] to a real, text-based PDF (selectable/searchable
 * text, not a rasterized image) using [parser] to walk the DOCX's own
 * OOXML. Pass a freshly constructed, not-yet-`setInput` parser — this
 * function calls `setInput` itself once `word/document.xml` is extracted.
 */
fun convertDocxToPdf(docxBytes: ByteArray, parser: XmlPullParser): DocxConversionResult {
    val documentXmlBytes = extractDocxDocumentXml(docxBytes)
    parser.setInput(InputStreamReader(ByteArrayInputStream(documentXmlBytes), Charsets.UTF_8))
    val parseResult = parseDocxBlocks(parser)

    PDDocument().use { document ->
        val writer = DocxPdfWriter(document)
        for (block in parseResult.blocks) {
            when (block) {
                is DocxBlock.Heading -> writer.drawHeading(block.level, block.runs)
                is DocxBlock.Paragraph -> writer.drawParagraph(block.runs)
            }
        }
        writer.finish()

        val warnings = mutableListOf<String>()
        if (writer.unencodableCount > 0) {
            val count = writer.unencodableCount
            warnings.add(
                "$count character${if (count == 1) "" else "s"} could not be rendered in the standard font and " +
                    "${if (count == 1) "was" else "were"} replaced with \"?\".",
            )
        }
        if (parseResult.skippedTables > 0) {
            val count = parseResult.skippedTables
            warnings.add("$count table${if (count == 1) "" else "s"} not yet supported and ${if (count == 1) "was" else "were"} skipped.")
        }
        if (parseResult.skippedImages > 0) {
            val count = parseResult.skippedImages
            warnings.add("$count image${if (count == 1) "" else "s"} not yet supported and ${if (count == 1) "was" else "were"} skipped.")
        }

        val out = ByteArrayOutputStream()
        document.save(out)
        return DocxConversionResult(out.toByteArray(), warnings)
    }
}
