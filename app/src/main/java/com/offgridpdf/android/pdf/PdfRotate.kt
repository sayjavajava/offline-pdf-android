package com.offgridpdf.android.pdf

import com.tom_roush.pdfbox.pdmodel.PDDocument
import java.io.ByteArrayOutputStream

private val ALLOWED_ROTATIONS = setOf(90, 180, 270, -90, -180, -270)

/**
 * Rotates [pages] (or "all"/blank for the whole document) of [document] by
 * [angle] degrees clockwise, relative to each page's *current* rotation —
 * not set to an absolute value. Web reference: `rotatePdf` (`pdf-ops.ts`).
 *
 * Mutates and re-saves the passed-in [document] directly rather than
 * building a new one — same as the web version, and unlike Split/Merge
 * (A-3/A-4), which build a fresh document because they're recombining
 * pages, not just changing a page property.
 *
 * Deliberate: the resolved page indices are **deduplicated**
 * (`[...new Set(...)]` on the web side) — unlike Split, which keeps
 * duplicates on purpose (extracting the same page twice is meaningful;
 * rotating it "twice" by whatever was last requested isn't). A
 * genuinely different, correct choice per tool — not an inconsistency to
 * unify with Split's behavior.
 */
fun rotatePdf(document: PDDocument, angle: Int, pages: String = "all"): ByteArray {
    if (angle !in ALLOWED_ROTATIONS) {
        throw IllegalArgumentException("Rotation angle must be 90, 180, or 270 degrees.")
    }

    val pageCount = document.numberOfPages
    val indices = if (pages.isBlank() || pages.trim().equals("all", ignoreCase = true)) {
        (0 until pageCount).toList()
    } else {
        val parsed = parsePageRange(pages, pageCount)
        if (parsed.errors.isNotEmpty()) {
            throw IllegalArgumentException(parsed.errors.joinToString(" "))
        }
        if (parsed.indices.isEmpty()) {
            throw IllegalArgumentException("Invalid page range specified.")
        }
        parsed.indices.distinct()
    }

    for (index in indices) {
        val page = document.getPage(index)
        page.rotation = page.rotation + angle
    }

    val out = ByteArrayOutputStream()
    document.save(out)
    return out.toByteArray()
}
