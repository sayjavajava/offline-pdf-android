package com.offgridpdf.android.pdf

import com.tom_roush.pdfbox.pdmodel.PDDocument
import com.tom_roush.pdfbox.pdmodel.PDPageContentStream
import com.tom_roush.pdfbox.pdmodel.common.PDRectangle
import com.tom_roush.pdfbox.util.Matrix
import java.io.ByteArrayOutputStream
import kotlin.math.min

/**
 * Which pages [cropPdf]/[resizePdf] touch: "all"/blank means every page,
 * otherwise a page range via [parsePageRange] — same shape and dedup
 * behavior (`[...new Set(...)]` on the web side) as the real
 * `cropPdf`/`resizePdf` (`pdf-ops.ts`) both use, and the same as
 * `rotatePdf` (`PdfRotate.kt`) already established on this side.
 */
private fun resolveCropResizePageIndices(pages: String, pageCount: Int): List<Int> {
    if (pages.isBlank() || pages.trim().equals("all", ignoreCase = true)) {
        return (0 until pageCount).toList()
    }
    val parsed = parsePageRange(pages, pageCount)
    if (parsed.errors.isNotEmpty()) {
        throw IllegalArgumentException(parsed.errors.joinToString(" "))
    }
    if (parsed.indices.isEmpty()) {
        throw IllegalArgumentException("Invalid page range specified.")
    }
    return parsed.indices.distinct()
}

data class CropMargins(val top: Float, val bottom: Float, val left: Float, val right: Float)

/**
 * Crops [pages] (or all) of [document] by a fixed margin per edge.
 * Non-destructive: only the CropBox moves — content and MediaBox are
 * untouched, so nothing is discarded and the crop can be undone by
 * re-running with the MediaBox's own dimensions. Web reference: `cropPdf`
 * (`pdf-ops.ts`).
 */
fun cropPdf(document: PDDocument, margins: CropMargins, pages: String = "all"): ByteArray {
    for ((label, value) in listOf(
        "top" to margins.top,
        "bottom" to margins.bottom,
        "left" to margins.left,
        "right" to margins.right,
    )) {
        if (!value.isFinite() || value < 0f) {
            throw IllegalArgumentException("The $label margin must be a number of 0 or more.")
        }
    }

    val indices = resolveCropResizePageIndices(pages, document.numberOfPages)

    for (i in indices) {
        val page = document.getPage(i)
        val box = page.cropBox
        val width = box.width - margins.left - margins.right
        val height = box.height - margins.top - margins.bottom
        if (width <= 0f || height <= 0f) {
            throw IllegalArgumentException(
                "The margins are larger than page ${i + 1} (${box.width.toInt()}×${box.height.toInt()} pt).",
            )
        }
        page.cropBox = PDRectangle(box.lowerLeftX + margins.left, box.lowerLeftY + margins.bottom, width, height)
    }

    val out = ByteArrayOutputStream()
    document.save(out)
    return out.toByteArray()
}

data class PaperSize(val width: Float, val height: Float)

/** Common paper sizes in points (72 pt/in), portrait orientation. Web reference: `PAPER_SIZES` (`pdf-ops.ts`). */
val PAPER_SIZES: Map<String, PaperSize> = mapOf(
    "A4" to PaperSize(595.28f, 841.89f),
    "Letter" to PaperSize(612f, 792f),
    "Legal" to PaperSize(612f, 1008f),
)

/**
 * Resizes [pages] (or all) of [document] to [target], scaling page
 * content along with the box (not just the box dimensions — that would
 * clip or misplace content instead of actually resizing it). Web
 * reference: `resizePdf` (`pdf-ops.ts`).
 *
 * Defaults to scale-to-fit preserving aspect ratio (uniform, centered)
 * rather than stretching, since a non-uniform stretch visibly distorts
 * text and images — same default as the web version.
 *
 * PDFBox-Android has no `page.scale()` convenience the way pdf-lib does;
 * the equivalent is prepending a `cm` (concatenate matrix) content-stream
 * operator via [PDPageContentStream.AppendMode.PREPEND], which scales
 * every existing drawing operator that follows it for the rest of the
 * page's content stream — verified against `PDPageContentStream.transform`
 * source, the standard PDFBox recipe for this. Centering math ported
 * directly from `resizePdf`'s own comment: after scaling, content's own
 * box has moved to `(origX*scale, origY*scale)` while the target box
 * stays anchored at the origin, so the final box is shifted by half the
 * leftover space on each axis to re-center it around the already-scaled
 * content.
 *
 * **Known simplification, not yet matched to the web version**: this
 * does not scale annotation rectangles the way pdf-lib's `page.scale()`
 * does. No tool in this codebase creates or reads PDF annotations yet,
 * so this is a real but currently-inconsequential gap — worth fixing if
 * a future tool (Fill Forms, Add Signature) starts producing PDFs with
 * annotations that then get resized.
 */
fun resizePdf(document: PDDocument, target: PaperSize, pages: String = "all", stretch: Boolean = false): ByteArray {
    if (!target.width.isFinite() || !target.height.isFinite() || target.width <= 0f || target.height <= 0f) {
        throw IllegalArgumentException("Target page size must be a positive width and height.")
    }

    val indices = resolveCropResizePageIndices(pages, document.numberOfPages)

    for (i in indices) {
        val page = document.getPage(i)
        val box = page.mediaBox
        val sx = if (stretch) target.width / box.width else min(target.width / box.width, target.height / box.height)
        val sy = if (stretch) target.height / box.height else sx

        PDPageContentStream(document, page, PDPageContentStream.AppendMode.PREPEND, true, false).use { stream ->
            stream.transform(Matrix.getScaleInstance(sx, sy))
        }

        val scaledWidth = box.width * sx
        val scaledHeight = box.height * sy
        val offsetX = (target.width - scaledWidth) / 2f
        val offsetY = (target.height - scaledHeight) / 2f
        page.mediaBox = PDRectangle(
            box.lowerLeftX * sx - offsetX,
            box.lowerLeftY * sy - offsetY,
            target.width,
            target.height,
        )
    }

    val out = ByteArrayOutputStream()
    document.save(out)
    return out.toByteArray()
}
