package com.offgridpdf.android.pdf

import com.tom_roush.pdfbox.pdmodel.PDDocument
import java.io.ByteArrayOutputStream

/**
 * A field left as `null` (or blank) is left **untouched** in the PDF —
 * this tool edits whichever fields you actually fill in, it doesn't clear
 * the rest. Matches `editPdfMetadata`'s own truthy-check behavior
 * (`pdf-ops.ts`) exactly: `if (metadata.title) pdfDoc.setTitle(...)`
 * means an empty string is a no-op, not "clear the title."
 */
data class PdfMetadataEdit(
    val title: String? = null,
    val author: String? = null,
    val subject: String? = null,
    val keywords: String? = null,
    val producer: String? = null,
    val creator: String? = null,
)

/** Web reference: `editPdfMetadata` (`pdf-ops.ts`). */
fun editPdfMetadata(document: PDDocument, edit: PdfMetadataEdit): ByteArray {
    val info = document.documentInformation

    if (!edit.title.isNullOrEmpty()) info.title = edit.title
    if (!edit.author.isNullOrEmpty()) info.author = edit.author
    if (!edit.subject.isNullOrEmpty()) info.subject = edit.subject
    if (!edit.keywords.isNullOrEmpty()) {
        // PDFBox stores /Keywords as a single string; the web version's
        // split-into-array-then-set only exists because that's the shape
        // its own pdf-lib API wants, not a different stored format --
        // both end up with the same comma-joined text in the PDF, so no
        // split/rejoin is needed here.
        info.keywords = edit.keywords.split(",").joinToString(", ") { it.trim() }
    }
    if (!edit.producer.isNullOrEmpty()) info.producer = edit.producer
    if (!edit.creator.isNullOrEmpty()) info.creator = edit.creator

    val out = ByteArrayOutputStream()
    document.save(out)
    return out.toByteArray()
}
