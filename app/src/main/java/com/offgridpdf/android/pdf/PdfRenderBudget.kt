package com.offgridpdf.android.pdf

/**
 * Guards the one place this app's memory use genuinely explodes: rasterising
 * a PDF page.
 *
 * A page's *content* is small — a text page is a few KB of drawing commands.
 * Its *raster* is not: a Letter page at scale 8 (576dpi) is 4896 x 6336
 * pixels, and an `ARGB_8888` bitmap of that is 124 MB, for one page. That is
 * a 10-100x expansion over the source, and it is why rendering is the only
 * operation here that needs a ceiling — Split or Merge produce output roughly
 * the size of their input, which the user already has on disk, so they are
 * bounded by something real. A raster is bounded only by the scale the user
 * typed.
 *
 * The budget is taken from the device's own heap rather than hardcoded,
 * because "too big" is a property of the phone, not of the PDF: the same
 * export that is fine on a flagship will die on a budget device with a 128 MB
 * heap. [maxSafeBitmapBytes] is deliberately a fraction of the total, not all
 * of it — the document, the accumulated output and Compose's own memory all
 * have to fit alongside the bitmap.
 */

/** Bytes per pixel for `Bitmap.Config.ARGB_8888`, the config every render here uses. */
private const val BYTES_PER_PIXEL = 4L

/**
 * Share of the heap a single page's bitmap may take. A quarter leaves room
 * for the loaded `PDDocument`, the output accumulated so far, and the UI —
 * all of which are live at the same moment as the bitmap.
 */
private const val HEAP_FRACTION_FOR_ONE_BITMAP = 4L

/** The per-bitmap ceiling for this device. */
fun maxSafeBitmapBytes(): Long = Runtime.getRuntime().maxMemory() / HEAP_FRACTION_FOR_ONE_BITMAP

/**
 * Bytes an `ARGB_8888` bitmap of a [widthPts] x [heightPts] page rendered at
 * [scale] pixels per point would occupy. Pure arithmetic, in `Long` because
 * the product overflows `Int` well before it stops being a plausible request
 * (a 4000x4000pt page at scale 8 is already 4 GB).
 */
fun estimateBitmapBytes(widthPts: Float, heightPts: Float, scale: Float): Long {
    val widthPx = (widthPts * scale).toLong()
    val heightPx = (heightPts * scale).toLong()
    return widthPx * heightPx * BYTES_PER_PIXEL
}

/**
 * Throws [IllegalArgumentException] if rendering this page at this scale
 * would not fit in [budgetBytes].
 *
 * Failing here beats failing during `renderImageWithDPI`: an
 * `OutOfMemoryError` mid-render can take other work down with it, and it
 * cannot say what to do differently.
 *
 * [advice] closes the message with what the user can actually do about it.
 * It defaults to suggesting a smaller scale, which is right when the user
 * typed the scale — but a screen that renders at a fixed scale of its own
 * (a page preview) has to say something else, since there is no knob there
 * to turn.
 */
fun requireRenderableAtScale(
    widthPts: Float,
    heightPts: Float,
    scale: Float,
    pageNumber: Int,
    budgetBytes: Long = maxSafeBitmapBytes(),
    advice: String = "Try a smaller scale.",
) {
    val needed = estimateBitmapBytes(widthPts, heightPts, scale)
    if (needed > budgetBytes) {
        throw IllegalArgumentException(
            "Page $pageNumber is too large to render at this scale on this device " +
                "(${needed / (1024 * 1024)} MB needed, ${budgetBytes / (1024 * 1024)} MB available). " +
                advice,
        )
    }
}
