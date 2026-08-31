package com.offgridpdf.android.pdf

import android.graphics.Bitmap
import com.tom_roush.pdfbox.pdmodel.PDDocument
import com.tom_roush.pdfbox.rendering.PDFRenderer
import kotlin.math.abs

/**
 * Web reference: `PageComparison` (`pdf-compare.ts`). Two independent
 * signals per shared page — either can fire alone (a font substitution
 * can change text while rendering pixel-identical; a color or layout
 * change can differ visually with the exact same text underneath).
 *
 * [Both.textDiffers] is `null` when the two pages render at different
 * pixel dimensions: `PDFTextStripper`'s extraction is bound by each
 * page's own `mediaBox`, so a resized page (its content stream
 * byte-for-byte identical, just a smaller page around it) can come back
 * as "different text" with nothing about the actual wording having
 * changed. Reporting that would tell the user their content changed
 * when it didn't — worse than reporting nothing, so differently-sized
 * pages are left out of the text comparison entirely. The dimension
 * mismatch is already reported via [Both.visuallyDiffers], so nothing
 * about the pages differing goes unreported.
 */
sealed class PageComparison {
    abstract val page: Int

    data class OnlyInA(override val page: Int) : PageComparison()
    data class OnlyInB(override val page: Int) : PageComparison()
    data class Both(
        override val page: Int,
        val textDiffers: Boolean?,
        val visuallyDiffers: Boolean,
        /** Fraction of compared pixels that differ, 0-1. Null when the two pages render at different pixel dimensions -- a ratio would be meaningless there. */
        val pixelDiffRatio: Float?,
    ) : PageComparison()
}

data class CompareResult(val pageCountA: Int, val pageCountB: Int, val pages: List<PageComparison>)

private const val COMPARE_SCALE = 0.5f // cheap and plenty to catch a real visual change
private const val PER_CHANNEL_TOLERANCE = 24 // absorbs PNG/anti-aliasing noise, not content
private const val VISUAL_DIFF_THRESHOLD = 0.001f // a tiny fraction of noisy pixels is not "different"

private fun normalizedText(text: String): String =
    text.split("\n").joinToString("\n") { it.trim() }.trim()

private fun pixelDiffRatio(a: Bitmap, b: Bitmap): Float {
    val width = a.width
    val height = a.height
    val pixelsA = IntArray(width * height)
    val pixelsB = IntArray(width * height)
    a.getPixels(pixelsA, 0, width, 0, 0, width, height)
    b.getPixels(pixelsB, 0, width, 0, 0, width, height)
    var differing = 0
    for (i in pixelsA.indices) {
        val pa = pixelsA[i]
        val pb = pixelsB[i]
        val da = abs(((pa ushr 24) and 0xFF) - ((pb ushr 24) and 0xFF))
        val dr = abs(((pa ushr 16) and 0xFF) - ((pb ushr 16) and 0xFF))
        val dg = abs(((pa ushr 8) and 0xFF) - ((pb ushr 8) and 0xFF))
        val db = abs((pa and 0xFF) - (pb and 0xFF))
        if (da > PER_CHANNEL_TOLERANCE || dr > PER_CHANNEL_TOLERANCE || dg > PER_CHANNEL_TOLERANCE || db > PER_CHANNEL_TOLERANCE) {
            differing++
        }
    }
    return differing.toFloat() / pixelsA.size
}

/**
 * Compares [documentA] and [documentB] page by page (A-20). Read-only —
 * neither document is modified. Web reference: `comparePdfs`
 * (`pdf-compare.ts`).
 *
 * Uses PdfBox-Android's own `PDFRenderer` for the visual signal, not the
 * platform `android.graphics.pdf.PdfRenderer` — Spike A's real,
 * on-device finding applies here too: a file this tool is asked to
 * compare could easily be one this app (or another PdfBox-based tool)
 * already re-saved, which the platform renderer has been shown to
 * reject outright.
 */
fun comparePdfs(documentA: PDDocument, documentB: PDDocument): CompareResult {
    val pageCountA = documentA.numberOfPages
    val pageCountB = documentB.numberOfPages
    val commonPages = minOf(pageCountA, pageCountB)

    val textsA = if (commonPages > 0) extractText(documentA, "1-$commonPages") else emptyList()
    val textsB = if (commonPages > 0) extractText(documentB, "1-$commonPages") else emptyList()
    val rendererA = PDFRenderer(documentA)
    val rendererB = PDFRenderer(documentB)

    val pages = mutableListOf<PageComparison>()
    for (i in 0 until commonPages) {
        val pageNumber = i + 1
        val bitmapA = rendererA.renderImageWithDPI(i, COMPARE_SCALE * 72f)
        val bitmapB = rendererB.renderImageWithDPI(i, COMPARE_SCALE * 72f)
        val sameDimensions = bitmapA.width == bitmapB.width && bitmapA.height == bitmapB.height

        val textDiffers = if (sameDimensions) {
            normalizedText(textsA[i].text) != normalizedText(textsB[i].text)
        } else {
            null
        }

        val visuallyDiffers: Boolean
        val ratio: Float?
        if (sameDimensions) {
            val r = pixelDiffRatio(bitmapA, bitmapB)
            ratio = r
            visuallyDiffers = r > VISUAL_DIFF_THRESHOLD
        } else {
            ratio = null
            visuallyDiffers = true // different page dimensions is itself a real difference
        }

        pages.add(PageComparison.Both(pageNumber, textDiffers, visuallyDiffers, ratio))
    }
    for (page in (commonPages + 1)..pageCountA) pages.add(PageComparison.OnlyInA(page))
    for (page in (commonPages + 1)..pageCountB) pages.add(PageComparison.OnlyInB(page))

    return CompareResult(pageCountA, pageCountB, pages)
}

/** Web reference: `describe` (`CompareTool.tsx`). */
fun describeComparison(p: PageComparison): String = when (p) {
    is PageComparison.OnlyInA -> "Only in A (removed)"
    is PageComparison.OnlyInB -> "Only in B (added)"
    is PageComparison.Both -> when {
        p.textDiffers == null -> "Different page size"
        !p.textDiffers && !p.visuallyDiffers -> "Identical"
        p.textDiffers && p.visuallyDiffers -> "Text and visual differences"
        p.textDiffers -> "Text differs"
        else -> "Visual differences"
    }
}

/** Web reference: `buildReport` (`CompareTool.tsx`). */
fun buildCompareReport(nameA: String, nameB: String, result: CompareResult): String {
    val lines = mutableListOf(
        "Compared: $nameA (${result.pageCountA} pages) vs $nameB (${result.pageCountB} pages)",
        "",
    )
    for (p in result.pages) {
        val label = describeComparison(p)
        val ratio = if (p is PageComparison.Both && p.pixelDiffRatio != null) {
            " (${"%.1f".format(p.pixelDiffRatio * 100)}% of pixels)"
        } else {
            ""
        }
        lines.add("Page ${p.page}: $label$ratio")
    }
    return lines.joinToString("\n")
}
