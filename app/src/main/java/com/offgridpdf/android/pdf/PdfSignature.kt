package com.offgridpdf.android.pdf

import com.tom_roush.pdfbox.pdmodel.PDDocument
import com.tom_roush.pdfbox.pdmodel.PDPageContentStream
import com.tom_roush.pdfbox.pdmodel.graphics.image.PDImageXObject
import java.io.ByteArrayOutputStream

/**
 * A visual stamp — drawn, typed, or an uploaded image — placed on one
 * page, not a cryptographic PKI signature (a PDF `/Sig` field, which
 * needs a private key and a trust chain this offline-only app has no way
 * to issue or verify). Same distinction most "sign a PDF" tools outside
 * enterprise contract software make. Web reference: `placeSignatureImage`
 * (`pdf-signature.ts`).
 *
 * [page] is 1-based, matching the web version's own `SignaturePlacement`.
 * [x]/[y]/[width]/[height] are in PDF points, bottom-left origin — the
 * page's own native coordinate space, not pixels. This project has no
 * page-rendering yet (`ANDROID_IMPLEMENTATION_PLAN.md`'s Spike A, not
 * done), so unlike the web tool's drag-on-a-rendered-preview placement,
 * this placement is entered directly in that same point space rather
 * than converted from a pixel drag — the plan's own explicit fallback
 * for exactly this situation ("ship with a simpler placement UI... don't
 * block this tool on rendering").
 */
data class SignaturePlacement(val page: Int, val x: Float, val y: Float, val width: Float, val height: Float)

/**
 * Embeds [imageBytes] (JPEG or PNG — reuses [detectImageFormat] from
 * `PdfImagesToPdf.kt` rather than a second image-format type, matching
 * the web version's own reuse of `detectImageFormat` here) and draws it
 * at [placement] on [document]. Web reference: `addSignature`
 * (`pdf-ops.ts`), `placeSignatureImage` (`pdf-signature.ts`).
 *
 * Shares A-12's real, honestly-documented gap: `PDImageXObject.createFromByteArray`
 * reads pixel data via `android.graphics.BitmapFactory`, a genuine
 * Android framework class this project's plain-JUnit unit tests (no
 * Robolectric) can't exercise -- see `PdfImagesToPdf.kt`'s doc comment
 * for the full explanation. Only validation is unit-testable here.
 */
fun addSignature(document: PDDocument, imageBytes: ByteArray, imageName: String, placement: SignaturePlacement): ByteArray {
    val pageCount = document.numberOfPages
    if (placement.page < 1 || placement.page > pageCount) {
        throw IllegalArgumentException(
            "Page ${placement.page} does not exist in this PDF (it has $pageCount page(s)).",
        )
    }
    if (detectImageFormat(imageName, imageBytes) == null) {
        throw IllegalArgumentException("Could not read the signature image: unsupported image type. Please use JPEG or PNG.")
    }

    val page = document.getPage(placement.page - 1)
    val xObject = try {
        PDImageXObject.createFromByteArray(document, imageBytes, imageName)
    } catch (e: Exception) {
        throw IllegalArgumentException("Could not read the signature image: ${e.message}", e)
    }

    PDPageContentStream(document, page, PDPageContentStream.AppendMode.APPEND, true, true).use { stream ->
        stream.drawImage(xObject, placement.x, placement.y, placement.width, placement.height)
    }

    val out = ByteArrayOutputStream()
    document.save(out)
    return out.toByteArray()
}
