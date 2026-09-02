package com.offgridpdf.android.pdf

import android.graphics.Bitmap
import com.tom_roush.pdfbox.pdmodel.PDDocument
import com.tom_roush.pdfbox.rendering.PDFRenderer

/**
 * Pixels per PDF point for an on-screen page preview.
 *
 * Independent of any export scale (see `EXPORT_SCALE` in `PdfRedact.kt`):
 * anything a user points at on a preview is converted to point-space
 * immediately, so this only decides how legible the preview looks, never
 * how good the output is. Web reference: `PREVIEW_SCALE`
 * (`RedactTool.tsx`).
 */
const val PREVIEW_SCALE = 1.5f

/**
 * One rendered page, with everything a caller needs to map between what is
 * on screen and what is in the document.
 *
 * [pageWidthPts]/[pageHeightPts] come from the page's MediaBox, not from
 * the bitmap: the bitmap is [PREVIEW_SCALE] times larger, and every
 * coordinate that ends up in a PDF operation has to be in points.
 */
data class RenderedPagePreview(
    val bitmap: Bitmap,
    val pageWidthPts: Float,
    val pageHeightPts: Float,
) {
    val bitmapWidth: Int get() = bitmap.width
    val bitmapHeight: Int get() = bitmap.height
}

/**
 * Rasterises one page for on-screen display.
 *
 * Blocking and CPU-bound — call it from `Dispatchers.Default`, never the
 * main thread. A full-page render is the most expensive thing any of the
 * preview screens do, and on a dense page it is a visible freeze.
 *
 * The budget check is the same one export renders use
 * ([requireRenderableAtScale]), with different advice: the user cannot
 * lower a preview's scale, so the only honest thing to tell them is that
 * this page will not preview on this device.
 */
fun renderPageForPreview(
    document: PDDocument,
    pageIndex: Int,
    scale: Float = PREVIEW_SCALE,
): RenderedPagePreview {
    val mediaBox = document.getPage(pageIndex).mediaBox
    requireRenderableAtScale(
        widthPts = mediaBox.width,
        heightPts = mediaBox.height,
        scale = scale,
        pageNumber = pageIndex + 1,
        advice = "This page cannot be previewed on this device.",
    )
    val bitmap = PDFRenderer(document).renderImageWithDPI(pageIndex, scale * 72f)
    return RenderedPagePreview(
        bitmap = bitmap,
        pageWidthPts = mediaBox.width,
        pageHeightPts = mediaBox.height,
    )
}
