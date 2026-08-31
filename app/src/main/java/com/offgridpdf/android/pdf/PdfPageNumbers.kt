package com.offgridpdf.android.pdf

import com.tom_roush.pdfbox.pdmodel.PDDocument
import com.tom_roush.pdfbox.pdmodel.PDPageContentStream
import com.tom_roush.pdfbox.pdmodel.font.PDType1Font
import java.io.ByteArrayOutputStream

enum class PageNumberFormat { N, N_OF_TOTAL, BATES }

enum class PageNumberPosition {
    BOTTOM_CENTER, BOTTOM_LEFT, BOTTOM_RIGHT, TOP_CENTER, TOP_LEFT, TOP_RIGHT,
}

data class PageNumberColor(val red: Float, val green: Float, val blue: Float)

data class PageNumberOptions(
    val format: PageNumberFormat = PageNumberFormat.N,
    val start: Int = 1,
    val prefix: String = "",
    val digits: Int = 6,
    val position: PageNumberPosition = PageNumberPosition.BOTTOM_CENTER,
    val fontSize: Float = 12f,
    val margin: Float = 36f,
    val color: PageNumberColor = PageNumberColor(0f, 0f, 0f),
    val pages: String = "all",
)

/**
 * Renders the label for one stamp. Web reference: `formatPageNumber`
 * (`pdf-ops.ts`) -- exported so the UI can show a live preview, same as
 * the web tool does.
 */
fun formatPageNumber(value: Int, total: Int, format: PageNumberFormat, prefix: String, digits: Int): String {
    return when (format) {
        PageNumberFormat.BATES -> "$prefix${value.toString().padStart(digits, '0')}"
        PageNumberFormat.N_OF_TOTAL -> "$prefix$value of $total"
        PageNumberFormat.N -> "$prefix$value"
    }
}

/**
 * Stamps sequential page numbers, or Bates numbers, onto [document]. Web
 * reference: `addPageNumbers` (`pdf-ops.ts`).
 *
 * Numbering counts from [PageNumberOptions.start] across the *stamped*
 * pages only, by ordinal position among the targets -- not the document's
 * absolute page index. "Number these 5 pages starting at 1" numbers them
 * 1..5 even when they're pages 10-14 of the source document, matching the
 * web version's `start + ordinal` (not `start + pageIndex`).
 */
fun addPageNumbers(document: PDDocument, options: PageNumberOptions = PageNumberOptions()): ByteArray {
    if (!options.fontSize.isFinite() || options.fontSize <= 0f || options.fontSize > 300f) {
        throw IllegalArgumentException("Font size must be between 1 and 300.")
    }
    if (!options.margin.isFinite() || options.margin < 0f || options.margin > 300f) {
        throw IllegalArgumentException("Margin must be between 0 and 300 points.")
    }
    if (options.start < 0) {
        throw IllegalArgumentException("Starting number must be a whole number of 0 or more.")
    }
    if (options.format == PageNumberFormat.BATES && (options.digits < 1 || options.digits > 20)) {
        throw IllegalArgumentException("Bates padding must be between 1 and 20 digits.")
    }
    val color = options.color
    if (!color.red.isFinite() || color.red < 0f || color.red > 1f ||
        !color.green.isFinite() || color.green < 0f || color.green > 1f ||
        !color.blue.isFinite() || color.blue < 0f || color.blue > 1f
    ) {
        throw IllegalArgumentException("Colour channels must each be between 0 and 1.")
    }

    val pageCount = document.numberOfPages
    val targets = if (options.pages.isBlank() || options.pages.trim().equals("all", ignoreCase = true)) {
        (0 until pageCount).toList()
    } else {
        val parsed = parsePageRange(options.pages, pageCount)
        if (parsed.errors.isNotEmpty()) {
            throw IllegalArgumentException(parsed.errors.joinToString(" "))
        }
        parsed.indices
    }
    if (targets.isEmpty()) {
        throw IllegalArgumentException("No pages selected to number.")
    }

    val font = PDType1Font.HELVETICA

    targets.forEachIndexed { ordinal, pageIndex ->
        val page = document.getPage(pageIndex)
        val mediaBox = page.mediaBox
        val width = mediaBox.width
        val height = mediaBox.height
        val label = formatPageNumber(options.start + ordinal, targets.size, options.format, options.prefix, options.digits)
        val labelWidth = font.getStringWidth(label) / 1000f * options.fontSize

        val x = when (options.position) {
            PageNumberPosition.BOTTOM_LEFT, PageNumberPosition.TOP_LEFT -> options.margin
            PageNumberPosition.BOTTOM_RIGHT, PageNumberPosition.TOP_RIGHT -> width - options.margin - labelWidth
            PageNumberPosition.BOTTOM_CENTER, PageNumberPosition.TOP_CENTER -> (width - labelWidth) / 2f
        }
        // For a top stamp the margin is measured from the top edge down to
        // the baseline, so the glyph height has to come off it -- same
        // convention as the web version.
        val y = when (options.position) {
            PageNumberPosition.TOP_LEFT, PageNumberPosition.TOP_CENTER, PageNumberPosition.TOP_RIGHT ->
                height - options.margin - options.fontSize
            PageNumberPosition.BOTTOM_LEFT, PageNumberPosition.BOTTOM_CENTER, PageNumberPosition.BOTTOM_RIGHT ->
                options.margin
        }

        PDPageContentStream(document, page, PDPageContentStream.AppendMode.APPEND, true, true).use { stream ->
            stream.beginText()
            stream.setFont(font, options.fontSize)
            stream.setNonStrokingColor(color.red, color.green, color.blue)
            stream.newLineAtOffset(x, y)
            stream.showText(label)
            stream.endText()
        }
    }

    val out = ByteArrayOutputStream()
    document.save(out)
    return out.toByteArray()
}
