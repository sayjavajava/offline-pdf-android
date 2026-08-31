package com.offgridpdf.android.docx

import java.io.ByteArrayOutputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

/**
 * Spike B (`ANDROID_IMPLEMENTATION_PLAN.md`, tool-docs repo) — a minimal,
 * real, valid `.docx` (an OPC package: a zip of XML parts), built by hand
 * so both candidate approaches (`DocxSpikePoiTest`, `DocxSpikeXmlPullTest`)
 * are compared against the exact same real bytes rather than two different
 * hand-picked fixtures. Two paragraphs, plain runs only — no tables,
 * images, or complex formatting, matching the "rough parity" scope this
 * spike itself is bounded to.
 */
object DocxSpikeFixture {
    val paragraphs = listOf(
        "Hello from Spike B.",
        "Second paragraph, testing OOXML parsing.",
    )

    fun build(): ByteArray {
        val contentTypes = """
            <?xml version="1.0" encoding="UTF-8" standalone="yes"?>
            <Types xmlns="http://schemas.openxmlformats.org/package/2006/content-types">
              <Default Extension="rels" ContentType="application/vnd.openxmlformats-package.relationships+xml"/>
              <Default Extension="xml" ContentType="application/xml"/>
              <Override PartName="/word/document.xml" ContentType="application/vnd.openxmlformats-officedocument.wordprocessingml.document.main+xml"/>
            </Types>
        """.trimIndent()

        val rootRels = """
            <?xml version="1.0" encoding="UTF-8" standalone="yes"?>
            <Relationships xmlns="http://schemas.openxmlformats.org/package/2006/relationships">
              <Relationship Id="rId1" Type="http://schemas.openxmlformats.org/officeDocument/2006/relationships/officeDocument" Target="word/document.xml"/>
            </Relationships>
        """.trimIndent()

        val paragraphXml = paragraphs.joinToString("") { text -> "<w:p><w:r><w:t>$text</w:t></w:r></w:p>" }
        val documentXml = """
            <?xml version="1.0" encoding="UTF-8" standalone="yes"?>
            <w:document xmlns:w="http://schemas.openxmlformats.org/wordprocessingml/2006/main"><w:body>$paragraphXml</w:body></w:document>
        """.trimIndent()

        val out = ByteArrayOutputStream()
        ZipOutputStream(out).use { zip ->
            for ((name, content) in listOf(
                "[Content_Types].xml" to contentTypes,
                "_rels/.rels" to rootRels,
                "word/document.xml" to documentXml,
            )) {
                zip.putNextEntry(ZipEntry(name))
                zip.write(content.toByteArray(Charsets.UTF_8))
                zip.closeEntry()
            }
        }
        return out.toByteArray()
    }
}
