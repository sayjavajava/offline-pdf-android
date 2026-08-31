package com.offgridpdf.android.pdf

import com.tom_roush.pdfbox.pdmodel.PDDocument
import com.tom_roush.pdfbox.pdmodel.PDPage
import com.tom_roush.pdfbox.pdmodel.PDPageContentStream
import com.tom_roush.pdfbox.pdmodel.common.PDRectangle
import com.tom_roush.pdfbox.pdmodel.graphics.image.PDImageXObject
import java.io.ByteArrayOutputStream

enum class ImageFormat { JPEG, PNG }

data class ImageFile(val name: String, val bytes: ByteArray)

/**
 * Resolve an image's format by magic bytes, falling back to its filename
 * extension. Web reference: `detectImageFormat` (`pdf-ops.ts`) — ported
 * without the web version's third fallback (a `File`'s declared MIME
 * type), since a raw byte array read from a Storage-Access-Framework
 * `Uri` has no equivalent to check.
 */
fun detectImageFormat(name: String, bytes: ByteArray): ImageFormat? {
    if (bytes.size >= 3 &&
        bytes[0] == 0xFF.toByte() && bytes[1] == 0xD8.toByte() && bytes[2] == 0xFF.toByte()
    ) {
        return ImageFormat.JPEG
    }
    if (bytes.size >= 8 &&
        bytes[0] == 0x89.toByte() && bytes[1] == 0x50.toByte() &&
        bytes[2] == 0x4E.toByte() && bytes[3] == 0x47.toByte() &&
        bytes[4] == 0x0D.toByte() && bytes[5] == 0x0A.toByte() &&
        bytes[6] == 0x1A.toByte() && bytes[7] == 0x0A.toByte()
    ) {
        return ImageFormat.PNG
    }

    val lower = name.lowercase()
    if (lower.endsWith(".jpg") || lower.endsWith(".jpeg")) return ImageFormat.JPEG
    if (lower.endsWith(".png")) return ImageFormat.PNG
    return null
}

/**
 * Converts one or more images (JPEG/PNG) to a PDF, one page per image in
 * the given order, each page sized to that image's own pixel dimensions —
 * 1 image pixel = 1 PDF point, no scaling or margin, matching the web
 * version's `pdfDoc.addPage([image.width, image.height])`. Web reference:
 * `convertImageToPdf` (`pdf-ops.ts`).
 *
 * A single file produces a single-page PDF; several combine into one
 * multi-page PDF instead of requiring a separate Merge step, same as the
 * web tool.
 *
 * **Real, honestly-documented gap**: unlike every prior tool in this
 * codebase, `PDImageXObject.createFromByteArray` reads pixel data via
 * `android.graphics.BitmapFactory` — a real Android framework class.
 * Under this project's plain-JUnit unit tests (no Robolectric, per A-1's
 * `isReturnDefaultValues` approach) that call is stubbed to a no-op
 * rather than actually decoding, so *this function's* image-embedding
 * behavior cannot be exercised by `PdfImagesToPdfTest.kt` the way every
 * prior tool's PDFBox-only logic could be. Only [detectImageFormat] (pure
 * byte logic, no Android/PDFBox APIs) and the empty-selection validation
 * below are unit-testable here; real embedding needs manual verification
 * on a device/emulator before this is trusted end-to-end.
 */
fun imagesToPdf(images: List<ImageFile>): ByteArray {
    if (images.isEmpty()) {
        throw IllegalArgumentException("Select at least one image.")
    }

    PDDocument().use { document ->
        for (image in images) {
            if (detectImageFormat(image.name, image.bytes) == null) {
                throw IllegalArgumentException("\"${image.name}\": unsupported image type. Please use JPEG or PNG.")
            }

            val xObject = try {
                PDImageXObject.createFromByteArray(document, image.bytes, image.name)
            } catch (e: Exception) {
                throw IllegalArgumentException("\"${image.name}\": ${e.message}", e)
            }

            val page = PDPage(PDRectangle(xObject.width.toFloat(), xObject.height.toFloat()))
            document.addPage(page)
            PDPageContentStream(document, page).use { stream ->
                stream.drawImage(xObject, 0f, 0f, xObject.width.toFloat(), xObject.height.toFloat())
            }
        }

        val out = ByteArrayOutputStream()
        document.save(out)
        return out.toByteArray()
    }
}
