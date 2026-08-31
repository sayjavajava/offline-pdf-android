package com.offgridpdf.android.pdf

import com.tom_roush.pdfbox.cos.COSName
import com.tom_roush.pdfbox.cos.COSStream
import com.tom_roush.pdfbox.pdmodel.PDDocument
import java.io.ByteArrayOutputStream
import java.util.zip.Inflater

/**
 * Spike C (`ANDROID_IMPLEMENTATION_PLAN.md`) — a feasibility experiment for
 * A-22's *lossless, stream-level* compression lever: real, measured
 * evidence for what re-writing a `FlateDecode` stream through PdfBox-
 * Android's own filter actually does to its size.
 *
 * A first version of this experiment tried to control the compression
 * *ratio* directly (re-deflating at `Deflater.BEST_COMPRESSION` before
 * writing) — a real bug, caught by CI: `COSStream.createOutputStream(COSBase
 * filters)` already applies Flate encoding itself to whatever bytes are
 * written to it (confirmed against the real source after the fact), so
 * writing already-deflated bytes through it double-compressed the stream,
 * corrupting it (`PDFStreamParser` choked on the still-compressed "operators"
 * on the next read). The fix below writes the raw, uncompressed content and
 * lets PdfBox-Android's own filter do the only compression that happens —
 * which also surfaces a real, concrete limitation worth recording: **there
 * is no public API here to choose a compression level**, unlike qpdf's
 * explicit `--compression-level=9`. See `CODE_AUDIT.md`'s Spike C write-up
 * for the full honest scoping, including what this deliberately does *not*
 * attempt (lossy image re-encoding needs a real JPEG encoder —
 * `android.graphics.Bitmap`, same gap as A-12/A-15 — and xref/object-stream
 * compaction has no PdfBox-Android equivalent to qpdf's
 * `--object-streams=generate`, confirmed against the real
 * `PDDocument`/`COSWriter` source).
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

/**
 * Decodes [stream]'s current `FlateDecode` bytes and writes the raw
 * content straight back out through the stream's own filter — the only
 * "recompression" this API surface allows, since there is no way to pick
 * a compression level. Returns the stream's raw byte count before and
 * after, for measurement.
 */
private fun recompressFlateStream(stream: COSStream): StreamRecompressionResult {
    val originalBytes = stream.createRawInputStream().use { it.readBytes() }
    val decoded = inflateAll(originalBytes)
    stream.createOutputStream(COSName.FLATE_DECODE).use { it.write(decoded) }
    val recompressedBytes = stream.createRawInputStream().use { it.readBytes() }
    return StreamRecompressionResult(originalBytes.size, recompressedBytes.size)
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
