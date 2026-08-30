package com.offgridpdf.android.files

import java.io.ByteArrayOutputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

/** One named entry to include in a zip built by [createZip]. */
data class ZipEntryData(val name: String, val bytes: ByteArray)

/**
 * Builds a zip archive in memory — this app's equivalent of the web app's
 * own `zip.ts` (`createZip`), used wherever a tool can produce more than
 * one output file (Split PDF's "separate files" option here in A-3, and
 * later PRs: PDF to Images, Extract Images).
 */
fun createZip(entries: List<ZipEntryData>): ByteArray {
    val out = ByteArrayOutputStream()
    ZipOutputStream(out).use { zip ->
        for (entry in entries) {
            zip.putNextEntry(ZipEntry(entry.name))
            zip.write(entry.bytes)
            zip.closeEntry()
        }
    }
    return out.toByteArray()
}
