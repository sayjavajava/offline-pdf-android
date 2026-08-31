package com.offgridpdf.android.pdf

import com.tom_roush.pdfbox.cos.COSName
import com.tom_roush.pdfbox.pdmodel.PDDocument
import com.tom_roush.pdfbox.pdmodel.PDPage
import com.tom_roush.pdfbox.pdmodel.PDResources
import com.tom_roush.pdfbox.pdmodel.graphics.color.PDDeviceGray
import com.tom_roush.pdfbox.pdmodel.graphics.color.PDDeviceRGB
import com.tom_roush.pdfbox.pdmodel.graphics.image.PDImageXObject
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.util.zip.Deflater
import java.util.zip.DeflaterOutputStream
import java.util.zip.Inflater

/**
 * Web reference: `extractImagesFromDocument` (`image-extract.ts`), verified
 * against `ExtractImagesTool.tsx`.
 *
 * Unlike A-12/A-15's `imagesToPdf`/`addSignature` (blocked on
 * `android.graphics.BitmapFactory`, which no-ops under plain JUnit),
 * extraction never decodes pixels through a bitmap — it reads raw COS
 * stream bytes and, for `FlateDecode` images, hand-builds a PNG using pure
 * `java.util.zip`. That makes the real extraction behavior — including
 * pixel content, not just "no exception was thrown" — directly verifiable
 * here: [decodePngSamples] below parses the produced PNG's own chunks and
 * inflates its `IDAT` data to recover the exact bytes [extractImages]
 * wrote, closing the loop without needing any image decoder.
 */
class PdfExtractImagesTest {

    private fun deflate(bytes: ByteArray): ByteArray {
        val out = ByteArrayOutputStream()
        val deflater = Deflater()
        DeflaterOutputStream(out, deflater).use { it.write(bytes) }
        deflater.end()
        return out.toByteArray()
    }

    private fun inflate(bytes: ByteArray): ByteArray {
        val inflater = Inflater()
        inflater.setInput(bytes)
        val out = ByteArrayOutputStream()
        val buffer = ByteArray(8192)
        while (!inflater.finished()) {
            val count = inflater.inflate(buffer)
            if (count == 0 && (inflater.needsInput() || inflater.needsDictionary())) break
            out.write(buffer, 0, count)
        }
        inflater.end()
        return out.toByteArray()
    }

    private fun readUInt32(bytes: ByteArray, offset: Int): Int =
        ((bytes[offset].toInt() and 0xFF) shl 24) or
            ((bytes[offset + 1].toInt() and 0xFF) shl 16) or
            ((bytes[offset + 2].toInt() and 0xFF) shl 8) or
            (bytes[offset + 3].toInt() and 0xFF)

    /** Parses [png]'s chunks, returning (width, height, raw un-filtered sample bytes). */
    private fun decodePngSamples(png: ByteArray, channels: Int): Triple<Int, Int, ByteArray> {
        val signature = byteArrayOf(0x89.toByte(), 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A)
        assertArrayEquals(signature, png.copyOfRange(0, 8))

        var offset = 8
        var width = -1
        var height = -1
        val idat = ByteArrayOutputStream()
        while (offset < png.size) {
            val length = readUInt32(png, offset)
            val type = String(png, offset + 4, 4, Charsets.US_ASCII)
            val data = png.copyOfRange(offset + 8, offset + 8 + length)
            when (type) {
                "IHDR" -> {
                    width = readUInt32(data, 0)
                    height = readUInt32(data, 4)
                    assertEquals(8, data[8].toInt()) // bit depth
                }
                "IDAT" -> idat.write(data)
            }
            offset += 8 + length + 4 // length + type + data + crc
        }

        val framed = inflate(idat.toByteArray())
        val rowLength = width * channels
        val samples = ByteArray(rowLength * height)
        for (y in 0 until height) {
            assertEquals(0, framed[y * (rowLength + 1)].toInt()) // filter byte: "none"
            System.arraycopy(framed, y * (rowLength + 1) + 1, samples, y * rowLength, rowLength)
        }
        return Triple(width, height, samples)
    }

    private fun addImageXObject(
        document: PDDocument,
        page: PDPage,
        name: String,
        encodedBytes: ByteArray,
        filter: COSName,
        width: Int,
        height: Int,
        bitsPerComponent: Int,
        colorSpace: com.tom_roush.pdfbox.pdmodel.graphics.color.PDColorSpace,
    ): PDImageXObject {
        val xObject = PDImageXObject(
            document,
            ByteArrayInputStream(encodedBytes),
            filter,
            width,
            height,
            bitsPerComponent,
            colorSpace,
        )
        val resources = PDResources()
        resources.put(COSName.getPDFName(name), xObject)
        page.resources = resources
        return xObject
    }

    @Test
    fun `extracts a DCTDecode image with its raw bytes passed through unchanged`() {
        val document = PDDocument()
        val page = PDPage()
        document.addPage(page)
        val jpegBytes = byteArrayOf(0xFF.toByte(), 0xD8.toByte(), 1, 2, 3, 4, 0xFF.toByte(), 0xD9.toByte())
        addImageXObject(document, page, "Im0", jpegBytes, COSName.DCT_DECODE, 10, 5, 8, PDDeviceRGB.INSTANCE)

        val result = extractImages(document)

        assertEquals(1, result.images.size)
        assertTrue(result.skipped.isEmpty())
        val image = result.images[0]
        assertEquals("image-001-p1.jpg", image.name)
        assertEquals("jpg", image.format)
        assertEquals(10, image.width)
        assertEquals(5, image.height)
        assertArrayEquals(jpegBytes, image.bytes)
        document.close()
    }

    @Test
    fun `extracts a FlateDecode RGB image and re-wraps it into a structurally valid PNG`() {
        val document = PDDocument()
        val page = PDPage()
        document.addPage(page)
        val width = 3
        val height = 2
        val samples = ByteArray(width * height * 3) { (it * 7 + 1).toByte() }
        addImageXObject(document, page, "Im0", deflate(samples), COSName.FLATE_DECODE, width, height, 8, PDDeviceRGB.INSTANCE)

        val result = extractImages(document)

        assertEquals(1, result.images.size)
        assertTrue(result.skipped.isEmpty())
        val image = result.images[0]
        assertEquals("image-001-p1.png", image.name)
        assertEquals("png", image.format)
        val (decodedWidth, decodedHeight, decodedSamples) = decodePngSamples(image.bytes, channels = 3)
        assertEquals(width, decodedWidth)
        assertEquals(height, decodedHeight)
        assertArrayEquals(samples, decodedSamples)
        document.close()
    }

    @Test
    fun `extracts a FlateDecode grayscale image correctly`() {
        val document = PDDocument()
        val page = PDPage()
        document.addPage(page)
        val width = 4
        val height = 3
        val samples = ByteArray(width * height) { (it * 11 + 5).toByte() }
        addImageXObject(document, page, "Im0", deflate(samples), COSName.FLATE_DECODE, width, height, 8, PDDeviceGray.INSTANCE)

        val result = extractImages(document)

        assertEquals(1, result.images.size)
        val (decodedWidth, decodedHeight, decodedSamples) = decodePngSamples(result.images[0].bytes, channels = 1)
        assertEquals(width, decodedWidth)
        assertEquals(height, decodedHeight)
        assertArrayEquals(samples, decodedSamples)
        document.close()
    }

    @Test
    fun `the same image reused across pages is only extracted once`() {
        val document = PDDocument()
        val page1 = PDPage()
        val page2 = PDPage()
        document.addPage(page1)
        document.addPage(page2)
        val jpegBytes = byteArrayOf(1, 2, 3, 4, 5)
        val xObject = addImageXObject(document, page1, "Im0", jpegBytes, COSName.DCT_DECODE, 10, 10, 8, PDDeviceRGB.INSTANCE)
        val resources2 = PDResources()
        resources2.put(COSName.getPDFName("Im0"), xObject)
        page2.resources = resources2

        val result = extractImages(document)

        assertEquals(1, result.images.size)
        document.close()
    }

    @Test
    fun `numbers images sequentially across pages and includes the page number in the name`() {
        val document = PDDocument()
        val page1 = PDPage()
        val page2 = PDPage()
        document.addPage(page1)
        document.addPage(page2)
        addImageXObject(document, page1, "Im0", byteArrayOf(1, 2, 3), COSName.DCT_DECODE, 1, 1, 8, PDDeviceRGB.INSTANCE)
        addImageXObject(document, page2, "Im0", byteArrayOf(4, 5, 6), COSName.DCT_DECODE, 1, 1, 8, PDDeviceRGB.INSTANCE)

        val result = extractImages(document)

        assertEquals(2, result.images.size)
        assertEquals("image-001-p1.jpg", result.images[0].name)
        assertEquals("image-002-p2.jpg", result.images[1].name)
        document.close()
    }

    @Test
    fun `skips an unsupported filter with a descriptive message`() {
        val document = PDDocument()
        val page = PDPage()
        document.addPage(page)
        addImageXObject(
            document,
            page,
            "Im0",
            byteArrayOf(1, 2, 3),
            COSName.getPDFName("JPXDecode"),
            10,
            10,
            8,
            PDDeviceRGB.INSTANCE,
        )

        val result = extractImages(document)

        assertTrue(result.images.isEmpty())
        assertEquals(1, result.skipped.size)
        assertEquals(
            "image-001-p1: unsupported image encoding (JPXDecode, DeviceRGB).",
            result.skipped[0],
        )
        document.close()
    }

    @Test
    fun `skips a FlateDecode image whose bit depth isn't 8 with a descriptive message`() {
        val document = PDDocument()
        val page = PDPage()
        document.addPage(page)
        addImageXObject(document, page, "Im0", deflate(byteArrayOf(1, 2, 3)), COSName.FLATE_DECODE, 10, 10, 1, PDDeviceRGB.INSTANCE)

        val result = extractImages(document)

        assertTrue(result.images.isEmpty())
        assertEquals(
            "image-001-p1: unsupported image encoding (FlateDecode, DeviceRGB).",
            result.skipped[0],
        )
        document.close()
    }

    @Test
    fun `skips a FlateDecode image with corrupt data with a descriptive message`() {
        val document = PDDocument()
        val page = PDPage()
        document.addPage(page)
        val garbage = byteArrayOf(1, 2, 3, 4, 5, 6, 7, 8)
        addImageXObject(document, page, "Im0", garbage, COSName.FLATE_DECODE, 10, 10, 8, PDDeviceRGB.INSTANCE)

        val result = extractImages(document)

        assertTrue(result.images.isEmpty())
        assertEquals("image-001-p1: could not decompress the image data.", result.skipped[0])
        document.close()
    }

    @Test
    fun `skips a FlateDecode image whose inflated data is shorter than declared`() {
        val document = PDDocument()
        val page = PDPage()
        document.addPage(page)
        // Declares 10x10 RGB but only supplies one byte of sample data.
        addImageXObject(document, page, "Im0", deflate(byteArrayOf(1)), COSName.FLATE_DECODE, 10, 10, 8, PDDeviceRGB.INSTANCE)

        val result = extractImages(document)

        assertTrue(result.images.isEmpty())
        assertEquals(
            "image-001-p1: image data is shorter than its declared 10×10 size.",
            result.skipped[0],
        )
        document.close()
    }

    @Test
    fun `a page with no resources produces no images and no errors`() {
        val document = PDDocument()
        document.addPage(PDPage())

        val result = extractImages(document)

        assertTrue(result.images.isEmpty())
        assertTrue(result.skipped.isEmpty())
        document.close()
    }
}
