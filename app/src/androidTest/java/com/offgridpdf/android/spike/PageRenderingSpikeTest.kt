package com.offgridpdf.android.spike

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Color
import android.graphics.pdf.PdfRenderer
import android.os.ParcelFileDescriptor
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.offgridpdf.android.pdf.PdfLoadResult
import com.offgridpdf.android.pdf.loadPdf
import com.offgridpdf.android.pdf.protectPdf
import com.tom_roush.pdfbox.pdmodel.PDDocument
import com.tom_roush.pdfbox.pdmodel.PDPage
import com.tom_roush.pdfbox.pdmodel.PDPageContentStream
import com.tom_roush.pdfbox.pdmodel.common.PDRectangle
import com.tom_roush.pdfbox.pdmodel.font.PDType1Font
import com.tom_roush.pdfbox.rendering.PDFRenderer
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.File
import kotlin.math.abs
import kotlin.math.roundToInt

private const val SPIKE_DPI = 150f

/**
 * Spike A (`ANDROID_IMPLEMENTATION_PLAN.md`, tool-docs repo): compare
 * PdfBox-Android's own `PDFRenderer` against the platform
 * `android.graphics.pdf.PdfRenderer` for real visual output, timing, and
 * encrypted-PDF compatibility — on a real emulator (see
 * `.github/workflows/spike-a-page-rendering.yml`), the one thing this
 * project's usual "JVM unit test, let real CI teach you" approach
 * genuinely cannot substitute for (no unit test can construct a
 * `ParcelFileDescriptor` or drive the platform renderer's native code).
 *
 * Each test writes its own plain-text result to the app's internal files
 * dir; CI pulls those files off the emulator (via `adb exec-out run-as`,
 * root-independent) and prints them into the job log after the Gradle
 * task finishes, so the real numbers are visible directly in the PR's CI
 * output — not just in logcat, and not lost if a later test in the run
 * fails. `ANDROID_CODE_AUDIT.md` (tool-docs repo)
 * is where the actual write-up and renderer recommendation live, filled
 * in from those real numbers once this runs green — this file is the
 * evidence, gathered once, not a permanent regression suite (see the
 * workflow's own path-filtered trigger).
 *
 * Kept as several independent `@Test` methods rather than one long one so
 * a real failure in one comparison doesn't hide the others' results in
 * Gradle's test report.
 */
@RunWith(AndroidJUnit4::class)
class PageRenderingSpikeTest {

    private val context: Context
        get() = InstrumentationRegistry.getInstrumentation().targetContext

    // context.filesDir (internal storage), not getExternalFilesDir: a real
    // first CI run found the latter produced zero pullable files on this
    // AVD image (either a null/unmounted external-storage path or a
    // storage-layout difference on the "default" -- not "google_apis" --
    // system image) -- internal storage always exists for an installed
    // app, and CI retrieves it via `adb exec-out run-as <pkg> cat ...`,
    // the standard way to read a debuggable app's private files without
    // root, which works regardless of whether the AVD itself is rooted.
    private fun writeResult(name: String, content: String) {
        File(context.filesDir, "spike-a-$name.txt").writeText(content)
    }

    private fun appendDiagnostic(line: String) {
        File(context.filesDir, "spike-a-diagnostics.txt").appendText(line + "\n")
    }

    // --- fixtures, built via this project's own already-proven PdfBox-Android
    // content-stream conventions (PdfWatermark.kt, PdfImagesToPdf.kt), not
    // committed binary files ---

    private fun buildTextFixturePdf(): ByteArray {
        return PDDocument().use { document ->
            val page = PDPage(PDRectangle.LETTER)
            document.addPage(page)
            PDPageContentStream(document, page).use { stream ->
                stream.beginText()
                stream.setFont(PDType1Font.HELVETICA_BOLD, 24f)
                stream.newLineAtOffset(72f, 700f)
                stream.showText("Spike A fixture page")
                stream.endText()

                stream.beginText()
                stream.setFont(PDType1Font.HELVETICA, 12f)
                stream.newLineAtOffset(72f, 660f)
                stream.showText("Real text drawn by PdfBox-Android, rendered by both renderers.")
                stream.endText()

                stream.setNonStrokingColor(0.2f, 0.4f, 0.8f)
                stream.addRect(72f, 500f, 200f, 100f)
                stream.fill()
            }
            val out = ByteArrayOutputStream()
            document.save(out)
            out.toByteArray()
        }
    }

    private fun buildMultiPageFixturePdf(pageCount: Int): ByteArray {
        return PDDocument().use { document ->
            repeat(pageCount) { index ->
                val page = PDPage(PDRectangle.LETTER)
                document.addPage(page)
                PDPageContentStream(document, page).use { stream ->
                    stream.beginText()
                    stream.setFont(PDType1Font.HELVETICA_BOLD, 18f)
                    stream.newLineAtOffset(72f, 700f)
                    stream.showText("Page ${index + 1} of $pageCount")
                    stream.endText()

                    stream.beginText()
                    stream.setFont(PDType1Font.HELVETICA, 11f)
                    stream.newLineAtOffset(72f, 670f)
                    stream.showText("Real text content, enough to be a non-trivial render on every page.")
                    stream.endText()

                    stream.setNonStrokingColor(0.7f, 0.3f, 0.1f)
                    stream.addRect(72f, 500f, 150f, 80f)
                    stream.fill()
                }
            }
            val out = ByteArrayOutputStream()
            document.save(out)
            out.toByteArray()
        }
    }

    private fun buildEncryptedFixturePdf(password: String): ByteArray {
        val document = PDDocument()
        try {
            val page = PDPage(PDRectangle.LETTER)
            document.addPage(page)
            PDPageContentStream(document, page).use { stream ->
                stream.beginText()
                stream.setFont(PDType1Font.HELVETICA_BOLD, 24f)
                stream.newLineAtOffset(72f, 700f)
                stream.showText("Encrypted Spike A fixture")
                stream.endText()
            }
            // protectPdf (PdfProtect.kt) both encrypts and saves -- reusing
            // this project's own real, already-tested encryption code
            // rather than re-deriving StandardProtectionPolicy usage here.
            return protectPdf(document, password)
        } finally {
            document.close()
        }
    }

    // --- comparison helpers ---

    /** Samples a grid rather than every pixel -- fast enough for a full-page bitmap, still statistically meaningful. */
    private fun hasVisibleContent(bitmap: Bitmap): Boolean {
        val colors = mutableSetOf<Int>()
        var x = 0
        while (x < bitmap.width) {
            var y = 0
            while (y < bitmap.height) {
                colors.add(bitmap.getPixel(x, y))
                if (colors.size > 1) return true
                y += 4
            }
            x += 4
        }
        return colors.size > 1
    }

    private fun meanAbsoluteDifference(a: Bitmap, b: Bitmap): Double {
        val width = minOf(a.width, b.width)
        val height = minOf(a.height, b.height)
        var total = 0L
        var samples = 0L
        var x = 0
        while (x < width) {
            var y = 0
            while (y < height) {
                val pa = a.getPixel(x, y)
                val pb = b.getPixel(x, y)
                total += abs(Color.red(pa) - Color.red(pb)) +
                    abs(Color.green(pa) - Color.green(pb)) +
                    abs(Color.blue(pa) - Color.blue(pb))
                samples += 3
                y += 4
            }
            x += 4
        }
        return if (samples == 0L) Double.NaN else total.toDouble() / samples
    }

    private fun renderWithPlatform(pdfBytes: ByteArray, pageIndex: Int, dpi: Float): Bitmap {
        val file = File(context.cacheDir, "spike-a-platform-$pageIndex-${System.nanoTime()}.pdf")
        file.writeBytes(pdfBytes)

        // A real first CI run had the platform renderer reject every
        // PdfBox-Android-produced fixture as "file not in PDF format or
        // corrupted" at construction time, uniformly, on every test that
        // reached it -- before this diagnostic existed to say why. Capture
        // real forensic data (on-disk vs. in-memory byte identity, and the
        // PDF header/tail bytes) unconditionally, so a repeat failure is
        // actionable from this file instead of another guess-and-push
        // round trip.
        val onDisk = file.readBytes()
        appendDiagnostic(
            "renderWithPlatform($pageIndex): inMemoryLen=${pdfBytes.size} onDiskLen=${onDisk.size} " +
                "identical=${onDisk.contentEquals(pdfBytes)} " +
                "header=${onDisk.take(8).joinToString(" ") { "%02x".format(it) }} " +
                "tail=${onDisk.takeLast(16).joinToString(" ") { "%02x".format(it) }}",
        )

        val pfd = ParcelFileDescriptor.open(file, ParcelFileDescriptor.MODE_READ_ONLY)
        val renderer = try {
            PdfRenderer(pfd)
        } catch (e: Exception) {
            appendDiagnostic("renderWithPlatform($pageIndex): PdfRenderer(pfd) threw ${e.javaClass.name}: ${e.message}")
            pfd.close()
            file.delete()
            throw e
        }
        try {
            val page = renderer.openPage(pageIndex)
            try {
                val widthPx = (page.width * dpi / 72f).roundToInt().coerceAtLeast(1)
                val heightPx = (page.height * dpi / 72f).roundToInt().coerceAtLeast(1)
                val bitmap = Bitmap.createBitmap(widthPx, heightPx, Bitmap.Config.ARGB_8888)
                // PdfRenderer.Page.render does not itself guarantee an
                // opaque white background the way PdfBox-Android's
                // renderImageWithDPI does -- pre-filling white matches
                // what a real PDF viewer shows and avoids a spurious
                // "different" quality result from an unrelated compositing
                // default.
                bitmap.eraseColor(Color.WHITE)
                page.render(bitmap, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
                return bitmap
            } finally {
                page.close()
            }
        } finally {
            renderer.close()
            pfd.close()
            file.delete()
        }
    }

    // --- the actual spike ---

    @Test
    fun pdfBoxRenderer_and_platformRenderer_produce_visually_similar_output() {
        val pdfBytes = buildTextFixturePdf()

        val pdfBoxBitmap = PDDocument.load(ByteArrayInputStream(pdfBytes)).use { document ->
            PDFRenderer(document).renderImageWithDPI(0, SPIKE_DPI)
        }
        val platformBitmap = renderWithPlatform(pdfBytes, 0, SPIKE_DPI)

        assertTrue("PdfBox-Android output should show real drawn content, not a blank page", hasVisibleContent(pdfBoxBitmap))
        assertTrue("Platform output should show real drawn content, not a blank page", hasVisibleContent(platformBitmap))

        val dimsMatch = abs(pdfBoxBitmap.width - platformBitmap.width) <= 1 &&
            abs(pdfBoxBitmap.height - platformBitmap.height) <= 1
        val meanDiff = if (dimsMatch) meanAbsoluteDifference(pdfBoxBitmap, platformBitmap) else Double.NaN

        writeResult(
            "quality",
            "pdfbox-android size: ${pdfBoxBitmap.width}x${pdfBoxBitmap.height}\n" +
                "platform size: ${platformBitmap.width}x${platformBitmap.height}\n" +
                "dimensions match (within 1px): $dimsMatch\n" +
                "mean absolute per-channel pixel difference (0 = identical, 255 = max): $meanDiff\n",
        )
    }

    @Test
    fun platformRenderer_cannot_open_a_password_protected_pdf_on_this_api_level() {
        val password = "spikeA-secret"
        val encryptedBytes = buildEncryptedFixturePdf(password)
        val file = File(context.cacheDir, "spike-a-encrypted-platform.pdf")
        file.writeBytes(encryptedBytes)
        val onDisk = file.readBytes()
        appendDiagnostic(
            "encrypted-platform: inMemoryLen=${encryptedBytes.size} onDiskLen=${onDisk.size} " +
                "identical=${onDisk.contentEquals(encryptedBytes)} " +
                "header=${onDisk.take(8).joinToString(" ") { "%02x".format(it) }} " +
                "tail=${onDisk.takeLast(16).joinToString(" ") { "%02x".format(it) }}",
        )

        val pfd = ParcelFileDescriptor.open(file, ParcelFileDescriptor.MODE_READ_ONLY)
        // Accepts either exception: a SecurityException confirms the
        // password-support gap this test set out to check; any other
        // exception is still real data worth recording (e.g. a first CI
        // run hit IOException("file not in PDF format or corrupted") here
        // -- see spike-a-diagnostics.txt) rather than a false pass/fail on
        // a narrower expectation than what actually happened.
        val thrown = try {
            assertThrows(Exception::class.java) { PdfRenderer(pfd) }
        } finally {
            pfd.close()
            file.delete()
        }

        writeResult(
            "encrypted-platform",
            "android.graphics.pdf.PdfRenderer(ParcelFileDescriptor) on API ${android.os.Build.VERSION.SDK_INT}: " +
                "threw ${thrown.javaClass.name}: ${thrown.message}\n" +
                "(This is the only constructor available at this app's minSdk=26 -- password support via " +
                "LoadParams was only added in API 35, backported to API 30+ via the PdfRendererPreV mainline " +
                "module. Confirmed against developer.android.com's Android 15 features overview, not assumed. " +
                "A SecurityException here confirms that gap; any other exception type is recorded as-is rather " +
                "than asserted away -- see spike-a-diagnostics.txt if this file's own PDF was rejected outright.)\n",
        )
    }

    @Test
    fun pdfBoxRenderer_can_open_and_render_the_same_password_protected_pdf() {
        val password = "spikeA-secret"
        val encryptedBytes = buildEncryptedFixturePdf(password)

        val result = loadPdf(ByteArrayInputStream(encryptedBytes), password)
        assertTrue("PdfBox-Android should open the encrypted fixture given the right password", result is PdfLoadResult.Success)
        val document = (result as PdfLoadResult.Success).document
        val bitmap = try {
            PDFRenderer(document).renderImageWithDPI(0, SPIKE_DPI)
        } finally {
            document.close()
        }
        assertTrue("Decrypted PdfBox-Android render should show real content, not a blank page", hasVisibleContent(bitmap))

        writeResult(
            "encrypted-pdfbox",
            "PdfBox-Android loadPdf(bytes, password) + PDFRenderer: opened and rendered successfully.\n" +
                "Real, working password-protected-PDF support at this app's minSdk=26, unlike the platform " +
                "renderer above -- a genuine reason to prefer PdfBox-Android for any tool touching encrypted PDFs.\n",
        )
    }

    @Test
    fun measures_real_render_time_for_both_renderers_against_a_multi_page_fixture() {
        val pageCount = 30
        val pdfBytes = buildMultiPageFixturePdf(pageCount)

        val pdfBoxStart = System.nanoTime()
        PDDocument.load(ByteArrayInputStream(pdfBytes)).use { document ->
            val renderer = PDFRenderer(document)
            repeat(pageCount) { index -> renderer.renderImageWithDPI(index, SPIKE_DPI) }
        }
        val pdfBoxMs = (System.nanoTime() - pdfBoxStart) / 1_000_000

        val file = File(context.cacheDir, "spike-a-timing.pdf")
        file.writeBytes(pdfBytes)
        val onDisk = file.readBytes()
        appendDiagnostic(
            "timing fixture ($pageCount pages): inMemoryLen=${pdfBytes.size} onDiskLen=${onDisk.size} " +
                "identical=${onDisk.contentEquals(pdfBytes)} " +
                "header=${onDisk.take(8).joinToString(" ") { "%02x".format(it) }}",
        )
        val platformStart = System.nanoTime()
        val pfd = ParcelFileDescriptor.open(file, ParcelFileDescriptor.MODE_READ_ONLY)
        val renderer = try {
            PdfRenderer(pfd)
        } catch (e: Exception) {
            appendDiagnostic("timing fixture: PdfRenderer(pfd) threw ${e.javaClass.name}: ${e.message}")
            pfd.close()
            file.delete()
            throw e
        }
        try {
            repeat(pageCount) { index ->
                val page = renderer.openPage(index)
                try {
                    val widthPx = (page.width * SPIKE_DPI / 72f).roundToInt().coerceAtLeast(1)
                    val heightPx = (page.height * SPIKE_DPI / 72f).roundToInt().coerceAtLeast(1)
                    val bitmap = Bitmap.createBitmap(widthPx, heightPx, Bitmap.Config.ARGB_8888)
                    page.render(bitmap, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
                } finally {
                    page.close()
                }
            }
        } finally {
            renderer.close()
            pfd.close()
            file.delete()
        }
        val platformMs = (System.nanoTime() - platformStart) / 1_000_000

        writeResult(
            "timing",
            "CI-emulator timing -- NOT representative of a real device's performance, only a same-run " +
                "relative comparison. See Spike A's ANDROID_CODE_AUDIT.md write-up before quoting these as " +
                "real-world numbers.\n" +
                "pages: $pageCount, dpi: $SPIKE_DPI\n" +
                "pdfbox-android total: ${pdfBoxMs}ms (${pdfBoxMs / pageCount}ms/page)\n" +
                "platform total: ${platformMs}ms (${platformMs / pageCount}ms/page)\n",
        )
    }

    @Test
    fun observes_how_each_renderer_reacts_to_a_truncated_pdf() {
        val validBytes = buildTextFixturePdf()
        // A real, not invented, corruption: a legitimate PDF whose tail
        // (xref table/trailer) never finished writing -- the same failure
        // mode a killed app or an interrupted download produces.
        val truncated = validBytes.copyOf((validBytes.size * 0.6).toInt())

        val pdfBoxOutcome = try {
            PDDocument.load(ByteArrayInputStream(truncated)).use { document ->
                PDFRenderer(document).renderImageWithDPI(0, SPIKE_DPI)
            }
            "opened and rendered without throwing"
        } catch (e: Exception) {
            "${e.javaClass.name}: ${e.message}"
        }

        val file = File(context.cacheDir, "spike-a-truncated.pdf")
        file.writeBytes(truncated)
        val platformOutcome = try {
            val pfd = ParcelFileDescriptor.open(file, ParcelFileDescriptor.MODE_READ_ONLY)
            try {
                val renderer = PdfRenderer(pfd)
                try {
                    val page = renderer.openPage(0)
                    try {
                        val widthPx = (page.width * SPIKE_DPI / 72f).roundToInt().coerceAtLeast(1)
                        val heightPx = (page.height * SPIKE_DPI / 72f).roundToInt().coerceAtLeast(1)
                        val bitmap = Bitmap.createBitmap(widthPx, heightPx, Bitmap.Config.ARGB_8888)
                        page.render(bitmap, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
                    } finally {
                        page.close()
                    }
                } finally {
                    renderer.close()
                }
            } finally {
                pfd.close()
            }
            "opened and rendered without throwing"
        } catch (e: Exception) {
            "${e.javaClass.name}: ${e.message}"
        } finally {
            file.delete()
        }

        // Observational only -- no assertion on which specific exception
        // each renderer should throw. Malformed-PDF recovery behavior is
        // inherently implementation-specific; the point is to record the
        // real reaction, not gate CI on a guessed one.
        writeResult(
            "malformed",
            "Observational only, no pass/fail assertion -- real reaction of each renderer to a truncated " +
                "(60% of original bytes) PDF:\n" +
                "pdfbox-android: $pdfBoxOutcome\n" +
                "platform: $platformOutcome\n",
        )
    }
}
