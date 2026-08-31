package com.offgridpdf.android.pdf

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Test

/**
 * Web reference: `detectImageFormat`/`convertImageToPdf` (`pdf-ops.ts`),
 * verified against `ConvertTool.tsx`'s image half.
 *
 * Only [detectImageFormat] (pure byte logic) and [imagesToPdf]'s
 * empty-selection validation are covered here — see the doc comment on
 * `imagesToPdf` (`PdfImagesToPdf.kt`) for why the actual image-embedding
 * behavior can't be exercised under this project's plain-JUnit setup.
 */
class PdfImagesToPdfTest {

    private val jpegMagicBytes = byteArrayOf(0xFF.toByte(), 0xD8.toByte(), 0xFF.toByte(), 0xE0.toByte())
    private val pngMagicBytes = byteArrayOf(
        0x89.toByte(), 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A,
    )

    @Test
    fun `detects jpeg by magic bytes`() {
        assertEquals(ImageFormat.JPEG, detectImageFormat("photo.bin", jpegMagicBytes))
    }

    @Test
    fun `detects png by magic bytes`() {
        assertEquals(ImageFormat.PNG, detectImageFormat("photo.bin", pngMagicBytes))
    }

    @Test
    fun `falls back to a jpg or jpeg extension when magic bytes don't match`() {
        val notAnImage = byteArrayOf(0, 0, 0, 0)
        assertEquals(ImageFormat.JPEG, detectImageFormat("photo.jpg", notAnImage))
        assertEquals(ImageFormat.JPEG, detectImageFormat("PHOTO.JPEG", notAnImage))
    }

    @Test
    fun `falls back to a png extension when magic bytes don't match`() {
        val notAnImage = byteArrayOf(0, 0, 0, 0)
        assertEquals(ImageFormat.PNG, detectImageFormat("PHOTO.PNG", notAnImage))
    }

    @Test
    fun `returns null for an unrecognized file`() {
        assertNull(detectImageFormat("document.pdf", byteArrayOf(0x25, 0x50, 0x44, 0x46)))
    }

    @Test
    fun `returns null for an empty file with no recognizable extension`() {
        assertNull(detectImageFormat("mystery", byteArrayOf()))
    }

    @Test
    fun `rejects an empty image selection`() {
        val error = assertThrows(IllegalArgumentException::class.java) {
            imagesToPdf(emptyList())
        }
        assertEquals("Select at least one image.", error.message)
    }

    @Test
    fun `rejects a file that is not a recognizable image before touching PDFBox`() {
        val error = assertThrows(IllegalArgumentException::class.java) {
            imagesToPdf(listOf(ImageFile("notes.txt", byteArrayOf(1, 2, 3))))
        }
        assertEquals(
            "\"notes.txt\": unsupported image type. Please use JPEG or PNG.",
            error.message,
        )
    }
}
