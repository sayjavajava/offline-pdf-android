package com.offgridpdf.android.pdf

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import com.tom_roush.pdfbox.pdmodel.PDDocument
import com.tom_roush.pdfbox.pdmodel.PDPage
import com.tom_roush.pdfbox.pdmodel.PDPageContentStream
import com.tom_roush.pdfbox.pdmodel.common.PDRectangle
import com.tom_roush.pdfbox.pdmodel.graphics.image.LosslessFactory
import com.tom_roush.pdfbox.rendering.PDFRenderer
import java.io.ByteArrayOutputStream
import kotlin.math.abs

/**
 * A redaction box in PDF point-space: origin at the page's bottom-left,
 * y increasing upward — the same convention `PDRectangle`/content-stream
 * drawing already uses throughout this codebase. Web reference:
 * `RedactionRect` (`pdf-redact.ts`).
 */
data class RedactionRect(val x: Float, val y: Float, val width: Float, val height: Float)

/** A point in pixel-space (top-left origin) — where a user actually touched the rendered page preview. */
data class PixelPoint(val x: Float, val y: Float)

/** A rect in pixel-space (top-left origin), the convention `android.graphics.Canvas` uses. */
data class PixelRect(val x: Float, val y: Float, val width: Float, val height: Float)

/**
 * Converts a PDF-point-space rect (bottom-left origin) to pixel-space
 * (top-left origin) for a raster of a page [pageHeightPts] tall,
 * rendered at [scale] pixels per point. Web reference: `toPixelRect`
 * (`pdf-redact.ts`) — same formula, pulled out as its own pure function
 * for exactly the same reason the web version does: the coordinate math
 * is the one part of this feature most likely to have an off-by-a-flip
 * bug, so it gets a direct unit test against known coordinates rather
 * than relying on eyeballing a rendered page.
 */
fun toPixelRect(rect: RedactionRect, pageHeightPts: Float, scale: Float): PixelRect {
    return PixelRect(
        x = rect.x * scale,
        y = (pageHeightPts - rect.y - rect.height) * scale,
        width = rect.width * scale,
        height = rect.height * scale,
    )
}

/**
 * The inverse of [toPixelRect]: converts two pixel-space corner points
 * (as dragged on the rendered preview) into a PDF-point-space
 * [RedactionRect]. Web reference: `pixelToPdfRect` (`RedactTool.tsx`).
 */
fun pixelToPdfRect(a: PixelPoint, b: PixelPoint, pageHeightPts: Float, scale: Float): RedactionRect {
    val x1 = minOf(a.x, b.x)
    val x2 = maxOf(a.x, b.x)
    val y1 = minOf(a.y, b.y)
    val y2 = maxOf(a.y, b.y)
    return RedactionRect(
        x = x1 / scale,
        y = pageHeightPts - y2 / scale,
        width = (x2 - x1) / scale,
        height = (y2 - y1) / scale,
    )
}

private fun assertValidRect(rect: RedactionRect, pageNumber: Int) {
    val values = listOf(rect.x, rect.y, rect.width, rect.height)
    if (values.any { !it.isFinite() } || rect.width <= 0f || rect.height <= 0f) {
        throw IllegalArgumentException("Invalid redaction box on page $pageNumber.")
    }
}

/** Pixels per PDF point for the rasterized replacement page. Higher keeps unredacted content on that page legible. Web reference: `EXPORT_SCALE` (`pdf-redact.ts`). */
private const val EXPORT_SCALE = 2f

/**
 * Applies redaction boxes to [document] (A-19). [redactions] maps a
 * 1-based page number to the boxes to black out (and permanently delete)
 * on that page. Pages absent from [redactions], or mapped to an empty
 * list, are copied through untouched — their original text layer
 * survives. Web reference: `redactPdf` (`pdf-redact.ts`).
 *
 * Real deletion, not a cosmetic overlay: a redacted page is rasterized
 * via PdfBox-Android's own `PDFRenderer` — Spike A's real, on-device
 * finding (`ANDROID_CODE_AUDIT.md`, tool-docs repo) is that the platform
 * `android.graphics.pdf.PdfRenderer` can't even open a PDF this app's
 * own `PDDocument.save()` produced, so PdfBox-Android's renderer is the
 * only real option here — with the box baked into the pixels, then
 * rebuilt in the output as a page containing nothing but that one
 * flattened image: no text, no annotations, no copied content stream.
 * There is no "content underneath" left for a box to fail to cover.
 */
fun redactPdf(document: PDDocument, redactions: Map<Int, List<RedactionRect>>): ByteArray {
    val targetPages = redactions.filterValues { it.isNotEmpty() }
    if (targetPages.isEmpty()) {
        throw IllegalArgumentException("Draw at least one redaction box before applying.")
    }
    val pageCount = document.numberOfPages
    for ((pageNumber, rects) in targetPages) {
        if (pageNumber < 1 || pageNumber > pageCount) {
            throw IllegalArgumentException("Page $pageNumber is outside this $pageCount-page document.")
        }
        for (rect in rects) {
            assertValidRect(rect, pageNumber)
        }
    }

    val renderer = PDFRenderer(document)
    PDDocument().use { outDoc ->
        for (index in 0 until pageCount) {
            val pageNumber = index + 1
            val rects = redactions[pageNumber].orEmpty()
            if (rects.isNotEmpty()) {
                val sourceBox = document.getPage(index).mediaBox
                val widthPts = sourceBox.width
                val heightPts = sourceBox.height

                // Checked per page rather than all up front as in
                // PdfToImages: pages here are rebuilt into outDoc as the loop
                // goes, so there is no point validating page 200 before page
                // 1's work is already committed.
                requireRenderableAtScale(widthPts, heightPts, EXPORT_SCALE, pageNumber)

                val rendered = renderer.renderImageWithDPI(index, EXPORT_SCALE * 72f)
                // Canvas needs a mutable ARGB_8888 target; the render is
                // neither, so it is copied — and then released immediately,
                // rather than being held alongside the copy for the rest of
                // the page's work.
                val bitmap = rendered.copy(Bitmap.Config.ARGB_8888, true)
                rendered.recycle()

                try {
                    val canvas = Canvas(bitmap)
                    val paint = Paint().apply {
                        color = Color.BLACK
                        style = Paint.Style.FILL
                    }
                    for (rect in rects) {
                        val px = toPixelRect(rect, heightPts, EXPORT_SCALE)
                        canvas.drawRect(px.x, px.y, px.x + px.width, px.y + px.height, paint)
                    }

                    val newPage = PDPage(PDRectangle(widthPts, heightPts))
                    outDoc.addPage(newPage)
                    val image = LosslessFactory.createFromImage(outDoc, bitmap)
                    PDPageContentStream(outDoc, newPage).use { stream ->
                        stream.drawImage(image, 0f, 0f, widthPts, heightPts)
                    }
                } finally {
                    // The page's pixels are now inside outDoc; this raster is
                    // dead weight for every remaining page of the document.
                    bitmap.recycle()
                }
            } else {
                // importPage (not addPage): copies the page's resources
                // into outDoc rather than leaving it referencing
                // document's own resource dictionary — same reason
                // PdfSplit.kt's splitPdfToSingleFile uses it.
                outDoc.importPage(document.getPage(index))
            }
        }
        val out = ByteArrayOutputStream()
        outDoc.save(out)
        return out.toByteArray()
    }
}

/** The outcome of [applyBoxesToRange]: the updated redaction map, plus which target pages got the boxes copied and which were skipped for a size mismatch. */
data class ApplyToRangeResult(
    val redactions: Map<Int, List<RedactionRect>>,
    val applied: List<Int>,
    val skipped: List<Int>,
)

/** Absorbs float noise, not a real size difference — same tolerance `RedactTool.tsx`'s own `handleApplyToRange` uses. */
private const val SIZE_TOLERANCE_PT = 1f

/**
 * Copies [sourcePageNumber]'s boxes onto [targetPageNumbers] — the
 * actual gap a company rollout hits: redacting something that recurs on
 * every page (a footer, a case number) otherwise means repeating the
 * same drag hundreds of times. A box is defined in PDF-point space
 * relative to its own page, so blindly copying it onto a
 * differently-sized page would silently land it somewhere wrong — pages
 * whose size doesn't match [sourcePageNumber]'s are skipped and named,
 * not silently mismatched, same as `RedactTool.tsx`'s own
 * `handleApplyToRange`.
 */
fun applyBoxesToRange(
    document: PDDocument,
    redactions: Map<Int, List<RedactionRect>>,
    sourcePageNumber: Int,
    targetPageNumbers: List<Int>,
): ApplyToRangeResult {
    val boxes = redactions[sourcePageNumber].orEmpty()
    if (boxes.isEmpty()) return ApplyToRangeResult(redactions, emptyList(), emptyList())

    val sourceBox = document.getPage(sourcePageNumber - 1).mediaBox
    val applied = mutableListOf<Int>()
    val skipped = mutableListOf<Int>()
    for (p in targetPageNumbers.distinct()) {
        if (p == sourcePageNumber) continue
        val targetBox = document.getPage(p - 1).mediaBox
        val sameSize = abs(targetBox.width - sourceBox.width) <= SIZE_TOLERANCE_PT &&
            abs(targetBox.height - sourceBox.height) <= SIZE_TOLERANCE_PT
        if (sameSize) applied.add(p) else skipped.add(p)
    }

    val next = redactions.toMutableMap()
    for (p in applied) {
        next[p] = next[p].orEmpty() + boxes
    }
    return ApplyToRangeResult(next, applied, skipped)
}
