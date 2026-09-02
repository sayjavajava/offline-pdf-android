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
 * All four point values are in PDF points, not pixels: the bitmap is
 * `scale` times larger, and every coordinate that ends up in a PDF
 * operation has to be in points.
 *
 * There are two boxes here because a page has two, and they are not always
 * the same:
 *
 *  - **CropBox** ([renderedWidthPts]/[renderedHeightPts]) is the region
 *    PDFBox actually rasterises — verified in
 *    `PDFRenderer.renderImage`, which calls `PDPage.getCropBox()`. So this
 *    is the box the *pixels* correspond to, and the one to convert against
 *    when turning a touch into page coordinates. `cropPdf` also works in
 *    this space.
 *  - **MediaBox** ([pageWidthPts]/[pageHeightPts]) is the full sheet.
 *
 * For the large majority of PDFs the two are identical. Where they differ,
 * which one a caller wants depends on what it will do with the answer, so
 * both are reported rather than one being guessed at.
 *
 * Note that `PdfRedact.kt` works in MediaBox space throughout while its
 * preview shows the CropBox, so a document whose CropBox is smaller than
 * its MediaBox places boxes inconsistently there. That predates this class
 * and is not something it can fix on its own -- the export path has to
 * agree on one space first.
 */
data class RenderedPagePreview(
    val bitmap: Bitmap,
    val pageWidthPts: Float,
    val pageHeightPts: Float,
    val renderedWidthPts: Float,
    val renderedHeightPts: Float,
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
    val page = document.getPage(pageIndex)
    val mediaBox = page.mediaBox
    val cropBox = page.cropBox
    // Budget against the CropBox, since that is what is about to be
    // rasterised.
    requireRenderableAtScale(
        widthPts = cropBox.width,
        heightPts = cropBox.height,
        scale = scale,
        pageNumber = pageIndex + 1,
        advice = "This page cannot be previewed on this device.",
    )
    val bitmap = PDFRenderer(document).renderImageWithDPI(pageIndex, scale * 72f)
    return RenderedPagePreview(
        bitmap = bitmap,
        pageWidthPts = mediaBox.width,
        pageHeightPts = mediaBox.height,
        renderedWidthPts = cropBox.width,
        renderedHeightPts = cropBox.height,
    )
}
