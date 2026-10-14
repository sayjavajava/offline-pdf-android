package com.offgridpdf.android.pdf

import com.tom_roush.pdfbox.pdmodel.PDPage

/**
 * The two coordinate spaces a page has, and how to get between them. Web
 * reference: `page-space.ts`.
 *
 * - **Displayed space** is the page as a reader shows it: turned by its
 *   `/Rotate`, cropped to its visible box, origin at the bottom-left of what
 *   you see. A drag on a rendered preview is in this space (PDFBox's
 *   `PDFRenderer` rotates and crops before rasterising), and so is "the
 *   bottom-centre of the page" as a user means it.
 * - **User space** is where content-stream operators write: the page's own,
 *   unrotated coordinates, with the origin wherever its MediaBox puts it.
 *
 * The two only coincide for an unrotated page whose box starts at (0,0).
 * Every tool that draws onto a page from a displayed-space position has to
 * convert, or its stamp lands in the wrong place, the wrong way up, or off
 * the page entirely on rotated or offset pages (landscape scans, cropped
 * documents).
 */
data class PageBox(val x: Float, val y: Float, val width: Float, val height: Float)

/**
 * `/Rotate` as 0, 90, 180 or 270; anything that isn't a multiple of 90 is
 * treated as 0, as PDFBox's own `PDPage.getRotation()` and pdf.js both do.
 */
fun normalizeRotation(rotation: Int): Int = if (rotation % 90 == 0) ((rotation % 360) + 360) % 360 else 0

/**
 * The region `PDFRenderer` rasterises: the CropBox, which PDFBox's
 * `PDPage.getCropBox()` already clips to the MediaBox (and falls back to the
 * MediaBox when there is none).
 */
fun visibleBox(page: PDPage): PageBox {
    val crop = page.cropBox
    return PageBox(crop.lowerLeftX, crop.lowerLeftY, crop.width, crop.height)
}

/** The page's width and height as displayed — swapped for a quarter turn. */
fun displayedSize(box: PageBox, rotation: Int): Pair<Float, Float> =
    when (normalizeRotation(rotation)) {
        90, 270 -> box.height to box.width
        else -> box.width to box.height
    }

/**
 * Pure. A displayed-space point, in user space. [rotation] is the page's
 * `/Rotate`, which turns the page clockwise for display.
 */
fun toUserSpace(x: Float, y: Float, box: PageBox, rotation: Int): Pair<Float, Float> =
    when (normalizeRotation(rotation)) {
        90 -> (box.x + box.width - y) to (box.y + x)
        180 -> (box.x + box.width - x) to (box.y + box.height - y)
        270 -> (box.x + y) to (box.y + box.height - x)
        else -> (box.x + x) to (box.y + y)
    }
