package com.offgridpdf.android.pdf

import com.tom_roush.pdfbox.pdmodel.PDDocument
import com.tom_roush.pdfbox.pdmodel.PDPage
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.ByteArrayInputStream

/**
 * Web reference: `protectPdf`/`protectPdfWithPermissions` (`pdf-ops.ts`),
 * `encryptPdfBytesWithPermissions` (`qpdf-engine.ts`) for the validation
 * rules. The actual encryption recipe was already verified end to end
 * against a real qpdf oracle in `NATIVE_ANDROID_SPIKE.md` (tool-docs
 * repo) — these tests confirm this Kotlin wiring produces the same
 * observable behavior, not the underlying crypto itself again.
 */
class PdfProtectTest {

    private fun buildPdf(): PDDocument {
        val document = PDDocument()
        document.addPage(PDPage())
        return document
    }

    // --- protectPdf ------------------------------------------------------

    @Test
    fun `protectPdf rejects an empty password`() {
        val doc = buildPdf()
        val error = assertThrows(IllegalArgumentException::class.java) {
            protectPdf(doc, "")
        }
        assertEquals("Enter a password.", error.message)
        doc.close()
    }

    @Test
    fun `protectPdf rejects a password shorter than 4 characters`() {
        val doc = buildPdf()
        val error = assertThrows(IllegalArgumentException::class.java) {
            protectPdf(doc, "abc")
        }
        assertEquals("Use a password of at least 4 characters.", error.message)
        doc.close()
    }

    @Test
    fun `protectPdf output requires the password to open`() {
        val doc = buildPdf()
        val bytes = protectPdf(doc, "secret123")
        doc.close()

        val withNoPassword = loadPdf(ByteArrayInputStream(bytes))
        assertEquals(PdfLoadResult.PasswordRequired, withNoPassword)

        val withWrongPassword = loadPdf(ByteArrayInputStream(bytes), password = "wrong-password")
        assertEquals(PdfLoadResult.PasswordRequired, withWrongPassword)

        val withCorrectPassword = loadPdf(ByteArrayInputStream(bytes), password = "secret123")
        assertTrue(withCorrectPassword is PdfLoadResult.Success)
        (withCorrectPassword as PdfLoadResult.Success).document.close()
    }

    // --- protectPdfWithPermissions ----------------------------------------

    @Test
    fun `protectPdfWithPermissions rejects an empty permissions password`() {
        val doc = buildPdf()
        val error = assertThrows(IllegalArgumentException::class.java) {
            protectPdfWithPermissions(doc, "open123", "", PdfPermissions(PrintPermission.FULL, true, ModifyPermission.ALL))
        }
        assertEquals("Enter a permissions password.", error.message)
        doc.close()
    }

    @Test
    fun `protectPdfWithPermissions rejects a permissions password shorter than 4 characters`() {
        val doc = buildPdf()
        val error = assertThrows(IllegalArgumentException::class.java) {
            protectPdfWithPermissions(doc, "open123", "abc", PdfPermissions(PrintPermission.FULL, true, ModifyPermission.ALL))
        }
        assertEquals("Use a permissions password of at least 4 characters.", error.message)
        doc.close()
    }

    @Test
    fun `protectPdfWithPermissions rejects a permissions password equal to the open password`() {
        val doc = buildPdf()
        val error = assertThrows(IllegalArgumentException::class.java) {
            protectPdfWithPermissions(doc, "samePass", "samePass", PdfPermissions(PrintPermission.FULL, true, ModifyPermission.ALL))
        }
        assertTrue(error.message!!.contains("must differ from the open password"))
        doc.close()
    }

    @Test
    fun `protectPdfWithPermissions allows an empty open password`() {
        val doc = buildPdf()
        val bytes = protectPdfWithPermissions(
            doc, "", "ownerSecret456", PdfPermissions(PrintPermission.FULL, true, ModifyPermission.ALL),
        )
        doc.close()

        // An empty open password means loadPdf's own no-password attempt
        // (which PDDocument.load(InputStream) treats as "") succeeds.
        val result = loadPdf(ByteArrayInputStream(bytes))
        assertTrue(result is PdfLoadResult.Success)
        (result as PdfLoadResult.Success).document.close()
    }

    @Test
    fun `restrictions apply when opened with the open password but not the permissions password`() {
        val doc = buildPdf()
        val bytes = protectPdfWithPermissions(
            doc,
            "open123",
            "ownerSecret456",
            PdfPermissions(print = PrintPermission.NONE, extract = false, modify = ModifyPermission.NONE),
        )
        doc.close()

        ByteArrayInputStream(bytes).use { input ->
            PDDocument.load(input, "open123").use { opened ->
                val access = opened.currentAccessPermission
                assertFalse(access.canPrint())
                assertFalse(access.canExtractContent())
                assertFalse(access.canModify())
            }
        }

        // The owner/permissions password bypasses restrictions entirely --
        // the whole reason encryptPdfBytesWithPermissions's own comment
        // rejects permissionsPassword == openPassword.
        ByteArrayInputStream(bytes).use { input ->
            PDDocument.load(input, "ownerSecret456").use { opened ->
                val access = opened.currentAccessPermission
                assertTrue(access.canPrint())
                assertTrue(access.canExtractContent())
                assertTrue(access.canModify())
            }
        }
    }

    @Test
    fun `low print permission allows printing but not at full quality`() {
        val doc = buildPdf()
        val bytes = protectPdfWithPermissions(
            doc,
            "open123",
            "ownerSecret456",
            PdfPermissions(print = PrintPermission.LOW, extract = true, modify = ModifyPermission.ALL),
        )
        doc.close()

        ByteArrayInputStream(bytes).use { input ->
            PDDocument.load(input, "open123").use { opened ->
                val access = opened.currentAccessPermission
                assertTrue(access.canPrint())
                assertFalse(access.canPrintFaithful())
            }
        }
    }
}
