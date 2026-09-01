package com.offgridpdf.android.spike

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Shader
import android.util.Base64
import android.util.Log
import android.util.Xml
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.offgridpdf.android.pdf.compressPdf
import com.offgridpdf.android.pdf.convertDocxToPdf
import com.offgridpdf.android.pdf.extractDocxDocumentXml
import com.tom_roush.pdfbox.pdmodel.PDDocument
import com.tom_roush.pdfbox.pdmodel.PDPage
import com.tom_roush.pdfbox.pdmodel.PDPageContentStream
import com.tom_roush.pdfbox.pdmodel.common.PDRectangle
import com.tom_roush.pdfbox.pdmodel.graphics.image.PDImageXObject
import com.tom_roush.pdfbox.rendering.PDFRenderer
import org.junit.Test
import org.junit.runner.RunWith
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

/**
 * A one-off, ad hoc follow-up check on the two visual gaps this project's
 * own `CODE_AUDIT.md` (tool-docs repo) has carried as disclosed and open
 * since A-22/A-25 shipped: whether A-22's recompressed-image quality and
 * A-25's laid-out DOCX-to-PDF page actually *look* right to a human eye.
 * Not a plan item — `ANDROID_IMPLEMENTATION_PLAN.md`'s build order is
 * already complete — and not a permanent regression suite, same framing
 * as Spike A's own `PageRenderingSpikeTest.kt`, which this file borrows
 * its conventions from directly (real `PDDocument`/`PDPageContentStream`
 * fixtures, `Log.i` + `adb logcat -d` as the only proven-reliable
 * evidence channel off this project's CI emulator — see
 * `PageRenderingSpikeTest.kt`'s own header comment for why a post-hoc
 * `adb pull` doesn't work here: `connectedDebugAndroidTest` uninstalls
 * the app, wiping its private storage, before any later script step can
 * read it back).
 *
 * Neither A-22 nor A-25's own unit tests can show real pixel output —
 * A-22's `compressPdf` genuinely needs `android.graphics.Bitmap`/
 * `BitmapFactory` (a no-op under the JVM stub); A-25's `convertDocxToPdf`
 * is itself fully JVM-testable (real `XmlPullParser`, no `Bitmap`
 * involved), but whether the resulting page *looks* right — heading
 * sizes, spacing, wrapping — is a human-eyeball judgment call, not
 * something an assertion can check. So this logs small preview images
 * (base64-encoded, JPEG-recompressed for transmission size, not the
 * actual fixture data) rather than asserting pass/fail on either.
 */
@RunWith(AndroidJUnit4::class)
class VisualCheckSpikeTest {

    companion object {
        private const val TAG = "VisualCheck"

        /** Kept small deliberately -- this is a preview for a human to
         * glance at, not the real fixture output, so it doesn't need to
         * be large or lossless. Logcat entries have a real per-line size
         * limit (~4KB including metadata), so the base64 payload is sent
         * in indexed chunks small enough to stay well under it. */
        private const val PREVIEW_MAX_WIDTH_PX = 260
        private const val CHUNK_SIZE = 2800
    }

    /** Base64-encodes [bitmap] (re-encoded as JPEG for a smaller preview
     * payload -- this is only a human-legibility check, not a fidelity
     * test) and logs it as indexed chunks under [name], so the CI log
     * output can be reassembled into a real, viewable PNG/JPEG file
     * afterward. */
    private fun logPreview(name: String, bitmap: Bitmap) {
        val scale = PREVIEW_MAX_WIDTH_PX.toFloat() / bitmap.width
        val scaled = if (scale < 1f) {
            Bitmap.createScaledBitmap(bitmap, PREVIEW_MAX_WIDTH_PX, (bitmap.height * scale).toInt().coerceAtLeast(1), true)
        } else {
            bitmap
        }
        val out = ByteArrayOutputStream()
        scaled.compress(Bitmap.CompressFormat.JPEG, 70, out)
        val encoded = Base64.encodeToString(out.toByteArray(), Base64.NO_WRAP)

        Log.i(TAG, "===PREVIEW $name (${scaled.width}x${scaled.height}, ${out.size()} bytes jpeg)===")
        var index = 0
        var offset = 0
        while (offset < encoded.length) {
            val end = (offset + CHUNK_SIZE).coerceAtMost(encoded.length)
            Log.i(TAG, "CHUNK $name $index ${encoded.substring(offset, end)}")
            offset = end
            index++
        }
        Log.i(TAG, "===END PREVIEW $name (${index} chunks)===")
    }

    private fun logLine(line: String) {
        Log.i(TAG, line)
    }

    // --- A-25: DOCX-to-PDF layout ---

    private fun buildDocxFixture(): ByteArray {
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
        val bodyXml =
            "<w:p><w:pPr><w:pStyle w:val=\"Heading1\"/></w:pPr><w:r><w:t>Visual Check Report</w:t></w:r></w:p>" +
                "<w:p><w:r><w:t>This paragraph is plain body text, long enough to wrap onto a second line " +
                "so the layout's line-wrapping behavior is actually exercised here, not just single-line " +
                "paragraphs that never need to wrap at all.</w:t></w:r></w:p>" +
                "<w:p><w:r><w:rPr><w:b/></w:rPr><w:t>This whole line is bold. </w:t></w:r>" +
                "<w:r><w:rPr><w:i/></w:rPr><w:t>This whole line is italic.</w:t></w:r></w:p>" +
                "<w:p><w:pPr><w:pStyle w:val=\"Heading2\"/></w:pPr><w:r><w:t>A Second Section</w:t></w:r></w:p>" +
                "<w:p><w:r><w:t>One more ordinary paragraph under the second heading, to see how the " +
                "spacing between a heading and the paragraph that follows it actually looks.</w:t></w:r></w:p>"
        // Plain concatenation, not a shared trimIndent() block -- see
        // PdfDocxToPdfTest.kt's own buildDocx() comment for the real bug
        // this avoids (a nested trimIndent()'d bodyXml corrupts the outer
        // template's own leading "<?xml...?>" whitespace).
        val documentXml = "<?xml version=\"1.0\" encoding=\"UTF-8\" standalone=\"yes\"?>\n" +
            "<w:document xmlns:w=\"http://schemas.openxmlformats.org/wordprocessingml/2006/main\">" +
            "<w:body>$bodyXml</w:body></w:document>"

        val out = ByteArrayOutputStream()
        ZipOutputStream(out).use { zip ->
            for ((entryName, content) in listOf(
                "[Content_Types].xml" to contentTypes,
                "_rels/.rels" to rootRels,
                "word/document.xml" to documentXml,
            )) {
                zip.putNextEntry(ZipEntry(entryName))
                zip.write(content.toByteArray(Charsets.UTF_8))
                zip.closeEntry()
            }
        }
        return out.toByteArray()
    }

    @Test
    fun a25_docxToPdf_real_layout_preview() {
        val docxBytes = buildDocxFixture()

        // Diagnostic, not assumed: a first run's preview came back blank
        // (870-byte JPEG, no visible marks) despite zero warnings and a
        // real page being produced -- the same real-vs-JVM-stub
        // discrepancy this whole device check exists to catch. Log
        // android.util.Xml.newPullParser()'s real FEATURE_PROCESS_NAMESPACES
        // default and the actual tag name XmlPullParser.getName() returns
        // for a real <w:p> element on this real device, rather than
        // guessing why from source reading or a web search alone.
        val diagParser = Xml.newPullParser()
        logLine("[a25][diag] FEATURE_PROCESS_NAMESPACES default = ${diagParser.getFeature(org.xmlpull.v1.XmlPullParser.FEATURE_PROCESS_NAMESPACES)}")
        val diagDocXml = extractDocxDocumentXml(docxBytes)
        diagParser.setInput(java.io.InputStreamReader(ByteArrayInputStream(diagDocXml), Charsets.UTF_8))
        var diagEvent = diagParser.eventType
        var firstTagName: String? = null
        var firstTagPrefix: String? = null
        while (diagEvent != org.xmlpull.v1.XmlPullParser.END_DOCUMENT && firstTagName == null) {
            if (diagEvent == org.xmlpull.v1.XmlPullParser.START_TAG) {
                firstTagName = diagParser.name
                firstTagPrefix = try { diagParser.prefix } catch (e: Exception) { "threw ${e.javaClass.simpleName}" }
            }
            diagEvent = diagParser.next()
        }
        logLine("[a25][diag] first START_TAG: getName()=\"$firstTagName\" getPrefix()=\"$firstTagPrefix\"")

        val result = convertDocxToPdf(docxBytes, Xml.newPullParser())
        logLine("[a25] warnings: ${result.warnings}")

        val bitmap = PDDocument.load(ByteArrayInputStream(result.bytes)).use { document ->
            logLine("[a25] page count: ${document.numberOfPages}")
            PDFRenderer(document).renderImageWithDPI(0, 120f)
        }
        logPreview("a25-docx-to-pdf-page1", bitmap)
    }

    // --- A-22: compressed-image quality ---

    private fun buildPhotoLikeBitmap(): Bitmap {
        val width = 500
        val height = 350
        val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)

        // A flat-color fixture would compress trivially either way and
        // say nothing real about JPEG recompression quality -- this
        // deliberately builds visual complexity (a gradient, a checker
        // pattern, and text) closer to a real photo, so a genuine
        // quality difference has something to actually show up in.
        val gradientPaint = Paint().apply {
            shader = LinearGradient(0f, 0f, width.toFloat(), height.toFloat(), Color.RED, Color.BLUE, Shader.TileMode.CLAMP)
        }
        canvas.drawRect(0f, 0f, width.toFloat(), height.toFloat(), gradientPaint)

        val checkerPaint = Paint().apply { color = Color.WHITE; alpha = 160 }
        var y = 0
        while (y < height) {
            var x = if ((y / 20) % 2 == 0) 0 else 20
            while (x < width) {
                canvas.drawRect(x.toFloat(), y.toFloat(), (x + 20).toFloat(), (y + 20).toFloat(), checkerPaint)
                x += 40
            }
            y += 20
        }

        val textPaint = Paint().apply {
            color = Color.BLACK
            textSize = 48f
            isAntiAlias = true
        }
        canvas.drawText("Visual Check A-22", 30f, height - 40f, textPaint)

        return bitmap
    }

    @Test
    fun a22_compressPdf_real_before_and_after_preview() {
        val sourceBitmap = buildPhotoLikeBitmap()
        val pngBytes = ByteArrayOutputStream().also {
            sourceBitmap.compress(Bitmap.CompressFormat.PNG, 100, it)
        }.toByteArray()

        val originalPdfBytes = PDDocument().use { document ->
            val page = PDPage(PDRectangle.LETTER)
            document.addPage(page)
            val xObject = PDImageXObject.createFromByteArray(document, pngBytes, "fixture")
            PDPageContentStream(document, page).use { stream ->
                stream.drawImage(xObject, 36f, 400f, xObject.width.toFloat(), xObject.height.toFloat())
            }
            val out = ByteArrayOutputStream()
            document.save(out)
            out.toByteArray()
        }

        val compressedPdfBytes = PDDocument.load(ByteArrayInputStream(originalPdfBytes)).use { document ->
            compressPdf(document)
        }

        logLine(
            "[a22] original PDF: ${originalPdfBytes.size} bytes, compressed PDF: ${compressedPdfBytes.size} bytes " +
                "(${"%.1f".format(100.0 * compressedPdfBytes.size / originalPdfBytes.size)}% of original), " +
                "embedded PNG source was ${pngBytes.size} bytes",
        )

        val originalPageBitmap = PDDocument.load(ByteArrayInputStream(originalPdfBytes)).use { document ->
            PDFRenderer(document).renderImageWithDPI(0, 100f)
        }
        val compressedPageBitmap = PDDocument.load(ByteArrayInputStream(compressedPdfBytes)).use { document ->
            PDFRenderer(document).renderImageWithDPI(0, 100f)
        }
        logPreview("a22-original", originalPageBitmap)
        logPreview("a22-compressed", compressedPageBitmap)
    }
}
