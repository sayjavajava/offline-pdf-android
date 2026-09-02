package com.offgridpdf.android.pdf

/**
 * Coordinate types and conversions shared by every tool that lets the user
 * point at a place on a rendered page.
 *
 * These started out in `PdfRedact.kt`, where redaction boxes were the only
 * thing drawn on a preview. Signature placement and crop margins are the
 * same geometry problem with a different name on it, so the types live here
 * now and `RedactionRect` stays as redaction's own name for one (see
 * `PdfRedact.kt`).
 *
 * Two coordinate spaces are in play throughout, and mixing them up is the
 * single easiest way to get a box in the wrong place:
 *
 *  - **PDF point-space** — origin at the page's bottom-left, y increasing
 *    *upward*. What `PDRectangle` and content-stream drawing use, and what
 *    every value that reaches a PDF operation must be in.
 *  - **Pixel-space** — origin at the top-left, y increasing *downward*.
 *    What a rendered bitmap, `android.graphics.Canvas` and a touch event
 *    use.
 */

/**
 * A rect in PDF point-space: origin at the page's bottom-left, y increasing
 * upward. Web reference: `RedactionRect` (`pdf-redact.ts`).
 */
data class PdfRect(val x: Float, val y: Float, val width: Float, val height: Float)

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
fun toPixelRect(rect: PdfRect, pageHeightPts: Float, scale: Float): PixelRect {
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
 * [PdfRect]. Web reference: `pixelToPdfRect` (`RedactTool.tsx`).
 */
fun pixelToPdfRect(a: PixelPoint, b: PixelPoint, pageHeightPts: Float, scale: Float): PdfRect {
    val x1 = minOf(a.x, b.x)
    val x2 = maxOf(a.x, b.x)
    val y1 = minOf(a.y, b.y)
    val y2 = maxOf(a.y, b.y)
    return PdfRect(
        x = x1 / scale,
        y = pageHeightPts - y2 / scale,
        width = (x2 - x1) / scale,
        height = (y2 - y1) / scale,
    )
}
