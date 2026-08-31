package com.offgridpdf.android.pdf

import com.tom_roush.pdfbox.cos.COSName
import com.tom_roush.pdfbox.pdmodel.PDDocument
import com.tom_roush.pdfbox.pdmodel.graphics.image.PDImageXObject
import java.io.ByteArrayOutputStream
import java.util.zip.CRC32
import java.util.zip.Deflater
import java.util.zip.DeflaterOutputStream
import java.util.zip.Inflater

data class ExtractedImage(val name: String, val bytes: ByteArray, val width: Int, val height: Int, val format: String)

data class ExtractImagesResult(
    val images: List<ExtractedImage>,
    /** Human-readable reasons for images that could not be exported. */
    val skipped: List<String>,
)

private fun inflateBytes(bytes: ByteArray): ByteArray {
    val inflater = Inflater()
    inflater.setInput(bytes)
    val out = ByteArrayOutputStream(bytes.size * 3)
    val buffer = ByteArray(8192)
    while (!inflater.finished()) {
        val count = inflater.inflate(buffer)
        if (count == 0 && (inflater.needsInput() || inflater.needsDictionary())) break
        out.write(buffer, 0, count)
    }
    inflater.end()
    return out.toByteArray()
}

private fun deflateBytes(bytes: ByteArray): ByteArray {
    val out = ByteArrayOutputStream()
    val deflater = Deflater()
    DeflaterOutputStream(out, deflater).use { it.write(bytes) }
    deflater.end()
    return out.toByteArray()
}

private fun writeUInt32(out: ByteArrayOutputStream, value: Int) {
    out.write((value ushr 24) and 0xFF)
    out.write((value ushr 16) and 0xFF)
    out.write((value ushr 8) and 0xFF)
    out.write(value and 0xFF)
}

private fun writeUInt32At(array: ByteArray, offset: Int, value: Int) {
    array[offset] = ((value ushr 24) and 0xFF).toByte()
    array[offset + 1] = ((value ushr 16) and 0xFF).toByte()
    array[offset + 2] = ((value ushr 8) and 0xFF).toByte()
    array[offset + 3] = (value and 0xFF).toByte()
}

private fun pngChunk(type: String, data: ByteArray): ByteArray {
    val typeBytes = type.toByteArray(Charsets.US_ASCII)
    val out = ByteArrayOutputStream(12 + data.size)
    writeUInt32(out, data.size)
    out.write(typeBytes)
    out.write(data)
    val crc = CRC32()
    crc.update(typeBytes)
    crc.update(data)
    writeUInt32(out, crc.value.toInt())
    return out.toByteArray()
}

/** Wraps raw [samples] into a minimal PNG. [channels] is 1 (grey) or 3 (RGB). */
private fun encodePng(samples: ByteArray, width: Int, height: Int, channels: Int): ByteArray {
    val rowLength = width * channels
    // PNG scanlines are each prefixed with a filter byte; 0 means "no filter".
    val framed = ByteArray((rowLength + 1) * height)
    for (y in 0 until height) {
        framed[y * (rowLength + 1)] = 0
        System.arraycopy(samples, y * rowLength, framed, y * (rowLength + 1) + 1, rowLength)
    }

    val ihdr = ByteArray(13)
    writeUInt32At(ihdr, 0, width)
    writeUInt32At(ihdr, 4, height)
    ihdr[8] = 8 // bit depth
    ihdr[9] = if (channels == 1) 0 else 2 // colour type: greyscale or truecolour
    ihdr[10] = 0 // compression
    ihdr[11] = 0 // filter
    ihdr[12] = 0 // interlace

    val out = ByteArrayOutputStream()
    out.write(byteArrayOf(0x89.toByte(), 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A)) // PNG signature
    out.write(pngChunk("IHDR", ihdr))
    out.write(pngChunk("IDAT", deflateBytes(framed)))
    out.write(pngChunk("IEND", ByteArray(0)))
    return out.toByteArray()
}

/**
 * Extracts embedded images from [document], read-only — the source
 * document is never touched. Matches the web version's own approach of
 * reading the raw (undecoded) stream bytes directly rather than decoding
 * through a bitmap: `DCTDecode` streams are already complete JPEG files,
 * written out as-is; `FlateDecode` streams hold raw pixel samples,
 * manually inflated here and re-wrapped into a hand-built PNG using pure
 * `java.util.zip` (no Android framework dependency). Unlike A-12/A-15,
 * this makes the actual extraction behavior directly unit-testable —
 * `PDImageXObject.createFromByteArray`'s `android.graphics.BitmapFactory`
 * gap simply never comes up, because this never decodes pixels through
 * one. Anything else (JPX, JBIG2, CCITT, indexed/CMYK colour, chained
 * filters) is reported as skipped rather than written out wrong. Web
 * reference: `extractImagesFromDocument` (`image-extract.ts`).
 */
fun extractImages(document: PDDocument): ExtractImagesResult {
    val images = mutableListOf<ExtractedImage>()
    val skipped = mutableListOf<String>()
    val seen = mutableSetOf<String>()
    var counter = 0

    for (pageIndex in 0 until document.numberOfPages) {
        val resources = document.getPage(pageIndex).resources ?: continue
        for (name in resources.xObjectNames) {
            val xObject = resources.getXObject(name)
            if (xObject !is PDImageXObject) continue
            val cosStream = xObject.cosObject

            val rawBytes = cosStream.createRawInputStream().use { it.readBytes() }
            // The same image reused across pages appears once per page.
            val key = "${name.name}:${rawBytes.size}"
            if (!seen.add(key)) continue

            val filter = cosStream.getNameAsString(COSName.FILTER)
            val width = cosStream.getInt(COSName.WIDTH)
            val height = cosStream.getInt(COSName.HEIGHT)
            val bpc = cosStream.getInt(COSName.BITS_PER_COMPONENT)
            val colorSpace = cosStream.getNameAsString(COSName.COLORSPACE)
            counter += 1
            val base = "image-${counter.toString().padStart(3, '0')}-p${pageIndex + 1}"

            when {
                filter == "DCTDecode" -> {
                    images.add(ExtractedImage("$base.jpg", rawBytes, width, height, "jpg"))
                }
                filter == "FlateDecode" && bpc == 8 && (colorSpace == "DeviceRGB" || colorSpace == "DeviceGray") -> {
                    try {
                        val samples = inflateBytes(rawBytes)
                        val channels = if (colorSpace == "DeviceRGB") 3 else 1
                        if (samples.size < width * height * channels) {
                            skipped.add("$base: image data is shorter than its declared ${width}×${height} size.")
                        } else {
                            val png = encodePng(samples, width, height, channels)
                            images.add(ExtractedImage("$base.png", png, width, height, "png"))
                        }
                    } catch (e: Exception) {
                        skipped.add("$base: could not decompress the image data.")
                    }
                }
                else -> {
                    val colorSpaceSuffix = if (!colorSpace.isNullOrEmpty()) ", $colorSpace" else ""
                    skipped.add("$base: unsupported image encoding (${filter ?: "none"}$colorSpaceSuffix).")
                }
            }
        }
    }

    return ExtractImagesResult(images, skipped)
}
