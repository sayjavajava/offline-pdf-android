package com.offgridpdf.android.pdf

import com.tom_roush.pdfbox.cos.COSName
import com.tom_roush.pdfbox.cos.COSStream
import com.tom_roush.pdfbox.pdmodel.PDDocument
import java.io.ByteArrayOutputStream
import java.util.zip.Deflater
import java.util.zip.DeflaterOutputStream
import java.util.zip.Inflater

/**
 * Spike C (`ANDROID_IMPLEMENTATION_PLAN.md`) — a feasibility experiment for
 * A-22's *lossless, stream-level* compression lever only: real, measured
 * evidence for how much a `FlateDecode` stream shrinks when uncompressed
 * and re-deflated at the maximum ratio, using nothing beyond
 * `java.util.zip` — fully testable under plain JUnit, unlike the web
 * version's `qpdf --optimize-images` lever, which needs a real JPEG
 * encoder (`android.graphics.Bitmap`) this sandbox cannot verify. See
 * `CODE_AUDIT.md`'s Spike C write-up for the full honest scoping,
 * including what this experiment deliberately does *not* attempt
 * (lossy image re-encoding, xref/object-stream compaction — PdfBox-Android
 * exposes no equivalent to qpdf's `--object-streams=generate`, confirmed
 * against the real `PDDocument`/`COSWriter` source before concluding so).
 *
 * Not wired into any tool yet — a real A-22 "Compress PDF" tool is a
 * separate, later item once this spike's write-up settles the approach.
 */
data class StreamRecompressionResult(val originalBytes: Int, val recompressedBytes: Int)

private fun inflateAll(bytes: ByteArray): ByteArray {
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

private fun deflateAllAtMaxRatio(bytes: ByteArray): ByteArray {
    val out = ByteArrayOutputStream()
    val deflater = Deflater(Deflater.BEST_COMPRESSION)
    DeflaterOutputStream(out, deflater).use { it.write(bytes) }
    deflater.end()
    return out.toByteArray()
}

/**
 * Re-deflates [stream] (already `FlateDecode`-filtered) at the maximum
 * compression ratio, in place. Returns the stream's raw byte count before
 * and after, for measurement — the caller decides what, if anything, to
 * do with the difference.
 */
private fun recompressFlateStream(stream: COSStream): StreamRecompressionResult {
    val originalBytes = stream.createRawInputStream().use { it.readBytes() }
    val decoded = inflateAll(originalBytes)
    val recompressed = deflateAllAtMaxRatio(decoded)
    stream.setItem(COSName.FILTER, COSName.FLATE_DECODE)
    stream.createOutputStream(COSName.FLATE_DECODE).use { it.write(recompressed) }
    return StreamRecompressionResult(originalBytes.size, recompressed.size)
}

/**
 * Recompresses every page's own content stream in [document] (the single-
 * `COSStream` shape `PDPageContentStream`'s overwrite mode produces — the
 * common case, and the one this experiment measures; a `COSArray` of
 * multiple content streams per page is left untouched here, out of scope
 * for a feasibility spike). Returns the summed byte counts across every
 * page that had a single-stream, already-Flate-filtered content stream.
 */
fun recompressPageContentStreams(document: PDDocument): StreamRecompressionResult {
    var originalTotal = 0
    var recompressedTotal = 0
    for (page in document.pages) {
        val contents = page.cosObject.getDictionaryObject(COSName.CONTENTS)
        if (contents is COSStream) {
            val result = recompressFlateStream(contents)
            originalTotal += result.originalBytes
            recompressedTotal += result.recompressedBytes
        }
    }
    return StreamRecompressionResult(originalTotal, recompressedTotal)
}
