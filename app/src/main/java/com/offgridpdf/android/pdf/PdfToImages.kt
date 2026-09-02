package com.offgridpdf.android.pdf

import android.graphics.Bitmap
import com.tom_roush.pdfbox.pdmodel.PDDocument
import com.tom_roush.pdfbox.rendering.PDFRenderer
import java.io.ByteArrayOutputStream

/** One rendered page, PNG-encoded. `pageNumber` is 1-based. */
data class RenderedPage(val pageNumber: Int, val bytes: ByteArray)

private const val MIN_SCALE = 0f
private const val MAX_SCALE = 8f

/**
 * Web reference: `renderPdfPages` (`pdf-render.ts`). Renders [pages]
 * (a page-range string, or "all") to PNG at [scale] — `1` is 72dpi,
 * matching the web version's own convention exactly (a PDF point already
 * *is* 1/72 inch, so scale is just dpi/72 either way); PdfBox-Android's
 * `renderImageWithDPI` takes an absolute DPI, so this multiplies by 72
 * rather than exposing a second, differently-scaled knob.
 *
 * Uses PdfBox-Android's own `PDFRenderer`, not the platform
 * `android.graphics.pdf.PdfRenderer` — Spike A's own real, on-device
 * finding (`ANDROID_CODE_AUDIT.md`, tool-docs repo): the platform
 * renderer cannot open a PDF this app's own `PDDocument.save()`
 * produced, which is exactly what every page here would be re-rendering
 * output from (this tool included, transitively, once its own output is
 * re-opened by another tool).
 */
fun renderPdfPagesToPng(document: PDDocument, pages: String, scale: Float): List<RenderedPage> {
    if (!scale.isFinite() || scale <= MIN_SCALE || scale > MAX_SCALE) {
        throw IllegalArgumentException("Scale must be between $MIN_SCALE and $MAX_SCALE.")
    }
    val indices = resolvePageIndices(pages, document.numberOfPages)

    // Checked for every page up front, before any rendering: a 200-page
    // export that is going to fail on page 3 should say so before spending
    // minutes on pages 1 and 2.
    for (index in indices) {
        val box = document.getPage(index).mediaBox
        requireRenderableAtScale(box.width, box.height, scale, index + 1)
    }

    val renderer = PDFRenderer(document)
    return indices.map { index ->
        val bitmap = renderer.renderImageWithDPI(index, scale * 72f)
        val out = ByteArrayOutputStream()
        try {
            bitmap.compress(Bitmap.CompressFormat.PNG, 100, out)
        } finally {
            // Without this, every page's bitmap stays live until the GC
            // happens to collect it, so a long export holds N full-page
            // rasters at once instead of one. The PNG in `out` is all that
            // needs to outlive this iteration, and it is one to two orders
            // of magnitude smaller.
            bitmap.recycle()
        }
        RenderedPage(pageNumber = index + 1, bytes = out.toByteArray())
    }
}
