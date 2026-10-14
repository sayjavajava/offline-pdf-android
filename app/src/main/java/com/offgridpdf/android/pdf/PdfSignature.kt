package com.offgridpdf.android.pdf

import com.tom_roush.pdfbox.pdmodel.PDDocument
import com.tom_roush.pdfbox.pdmodel.PDPageContentStream
import com.tom_roush.pdfbox.pdmodel.graphics.image.PDImageXObject
import com.tom_roush.pdfbox.util.Matrix
import java.io.ByteArrayOutputStream
import kotlin.math.cos
import kotlin.math.sin

/**
 * A visual stamp — drawn, typed, or an uploaded image — placed on one
 * page, not a cryptographic PKI signature (a PDF `/Sig` field, which
 * needs a private key and a trust chain this offline-only app has no way
 * to issue or verify). Same distinction most "sign a PDF" tools outside
 * enterprise contract software make. Web reference: `placeSignatureImage`
 * (`pdf-signature.ts`).
 *
 * [page] is 1-based, matching the web version's own `SignaturePlacement`.
 * [x]/[y]/[width]/[height] are in PDF points, bottom-left origin, on the
 * page *as displayed*: after `/Rotate`, relative to the visible box. That
 * is the space a drag on the rendered preview produces and what the screen
 * means by "points from the page's bottom-left corner". [addSignature]
 * converts it to the page's own coordinates (`PageSpace.kt`), which differ
 * on rotated or offset pages.
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

    val (a, b, c, d, e, f) = signatureImageTransform(placement, visibleBox(page), page.rotation)
    PDPageContentStream(document, page, PDPageContentStream.AppendMode.APPEND, true, true).use { stream ->
        stream.drawImage(xObject, Matrix(a, b, c, d, e, f))
    }

    val out = ByteArrayOutputStream()
    document.save(out)
    return out.toByteArray()
}

/** An affine transform `[a b c d e f]`, as the PDF `cm` operator takes it. */
data class Transform(val a: Float, val b: Float, val c: Float, val d: Float, val e: Float, val f: Float)

/**
 * Pure. Where the image's unit square must go in user space so it lands on
 * [placement] (displayed space) and reads upright: scaled to width × height,
 * turned counter-clockwise by the page's own rotation (undoing the clockwise
 * turn the page is displayed with), and moved to the placement's displayed
 * bottom-left corner mapped into user space. Split out from [addSignature]
 * so it can be unit-tested; embedding the image itself needs Android's
 * `BitmapFactory`. Web reference: `displayedToUserSpace` (`page-space.ts`).
 */
fun signatureImageTransform(placement: SignaturePlacement, box: PageBox, rotation: Int): Transform {
    val r = normalizeRotation(rotation)
    val (anchorX, anchorY) = toUserSpace(placement.x, placement.y, box, r)
    val radians = Math.toRadians(r.toDouble())
    val cos = cos(radians).toFloat()
    val sin = sin(radians).toFloat()
    return Transform(
        placement.width * cos, placement.width * sin,
        -placement.height * sin, placement.height * cos,
        anchorX, anchorY,
    )
}
