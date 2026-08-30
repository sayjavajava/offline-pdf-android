package com.offgridpdf.android.pdf

import com.tom_roush.pdfbox.pdmodel.PDDocument
import com.tom_roush.pdfbox.pdmodel.PDPage
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.io.ByteArrayInputStream

/** Web reference: `editPdfMetadata` (`pdf-ops.ts`), verified against `EditTool.tsx`'s field set. */
class PdfMetadataTest {

    private fun buildPdf(): PDDocument {
        val document = PDDocument()
        document.addPage(PDPage())
        return document
    }

    private fun reload(bytes: ByteArray): PDDocument =
        PDDocument.load(ByteArrayInputStream(bytes))

    @Test
    fun `sets only the fields provided`() {
        val doc = buildPdf()
        val bytes = editPdfMetadata(doc, PdfMetadataEdit(title = "My Title", author = "Jane Doe"))
        doc.close()

        reload(bytes).use { reloaded ->
            val info = reloaded.documentInformation
            assertEquals("My Title", info.title)
            assertEquals("Jane Doe", info.author)
            assertNull(info.subject)
            assertNull(info.keywords)
        }
    }

    @Test
    fun `a blank field leaves existing metadata untouched, it does not clear it`() {
        val doc = buildPdf()
        // Simulate a file that already has a title, then "edit" it with an
        // empty title field -- exactly the shape the web UI's controlled
        // inputs default to before the user types anything.
        doc.documentInformation.title = "Existing Title"

        val bytes = editPdfMetadata(doc, PdfMetadataEdit(title = "", author = "New Author"))
        doc.close()

        reload(bytes).use { reloaded ->
            val info = reloaded.documentInformation
            assertEquals("Existing Title", info.title)
            assertEquals("New Author", info.author)
        }
    }

    @Test
    fun `a null field leaves existing metadata untouched, same as a blank one`() {
        val doc = buildPdf()
        doc.documentInformation.subject = "Existing Subject"

        val bytes = editPdfMetadata(doc, PdfMetadataEdit(title = "New Title"))
        doc.close()

        reload(bytes).use { reloaded ->
            val info = reloaded.documentInformation
            assertEquals("New Title", info.title)
            assertEquals("Existing Subject", info.subject)
        }
    }

    @Test
    fun `keywords are trimmed and rejoined`() {
        val doc = buildPdf()
        val bytes = editPdfMetadata(doc, PdfMetadataEdit(keywords = "  pdf ,  android,offline "))
        doc.close()

        reload(bytes).use { reloaded ->
            assertEquals("pdf, android, offline", reloaded.documentInformation.keywords)
        }
    }

    @Test
    fun `sets every supported field when all are provided`() {
        val doc = buildPdf()
        val bytes = editPdfMetadata(
            doc,
            PdfMetadataEdit(
                title = "T",
                author = "A",
                subject = "S",
                keywords = "k1, k2",
                producer = "P",
                creator = "C",
            ),
        )
        doc.close()

        reload(bytes).use { reloaded ->
            val info = reloaded.documentInformation
            assertEquals("T", info.title)
            assertEquals("A", info.author)
            assertEquals("S", info.subject)
            assertEquals("k1, k2", info.keywords)
            assertEquals("P", info.producer)
            assertEquals("C", info.creator)
        }
    }
}
