package com.offgridpdf.android.pdf

import com.tom_roush.pdfbox.pdmodel.PDDocument
import com.tom_roush.pdfbox.pdmodel.PDPage
import com.tom_roush.pdfbox.pdmodel.encryption.AccessPermission
import com.tom_roush.pdfbox.pdmodel.encryption.StandardProtectionPolicy
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream

/**
 * Direct unit tests, no Robolectric or emulator — same "plain javac/java"
 * path `NATIVE_ANDROID_SPIKE.md` verified for PdfBox-Android itself
 * (tool-docs repo), made to actually work here via
 * `testOptions.unitTests.isReturnDefaultValues` in app/build.gradle.kts
 * rather than a hand-written `android.util.Log` stub — same effect, less
 * code. Fixtures are built with PdfBox-Android's own APIs, not checked-in
 * binaries, matching the sibling web repo's own test-fixture convention.
 */
class PdfLoaderTest {

    private fun buildPlainPdf(pageCount: Int): ByteArray {
        PDDocument().use { document ->
            repeat(pageCount) { document.addPage(PDPage()) }
            val out = ByteArrayOutputStream()
            document.save(out)
            return out.toByteArray()
        }
    }

    /** Mirrors the real recipe `NATIVE_ANDROID_SPIKE.md` verified against qpdf. */
    private fun buildEncryptedPdf(userPassword: String, ownerPassword: String = "owner-$userPassword"): ByteArray {
        PDDocument().use { document ->
            document.addPage(PDPage())
            val policy = StandardProtectionPolicy(ownerPassword, userPassword, AccessPermission())
            policy.setEncryptionKeyLength(256)
            policy.setPreferAES(true)
            document.protect(policy)
            val out = ByteArrayOutputStream()
            document.save(out)
            return out.toByteArray()
        }
    }

    @Test
    fun `loadPdf succeeds and reports the right page count for a plain PDF`() {
        val bytes = buildPlainPdf(pageCount = 3)

        val result = loadPdf(ByteArrayInputStream(bytes))

        assertTrue(result is PdfLoadResult.Success)
        val document = (result as PdfLoadResult.Success).document
        assertEquals(3, document.numberOfPages)
        document.close()
    }

    @Test
    fun `loadPdf requires a password for an encrypted PDF opened with none`() {
        val bytes = buildEncryptedPdf(userPassword = "secret123")

        val result = loadPdf(ByteArrayInputStream(bytes))

        assertEquals(PdfLoadResult.PasswordRequired, result)
    }

    @Test
    fun `loadPdf requires a password for an encrypted PDF opened with the wrong one`() {
        val bytes = buildEncryptedPdf(userPassword = "secret123")

        val result = loadPdf(ByteArrayInputStream(bytes), password = "wrong-password")

        assertEquals(PdfLoadResult.PasswordRequired, result)
    }

    @Test
    fun `loadPdf succeeds for an encrypted PDF opened with the correct password`() {
        val bytes = buildEncryptedPdf(userPassword = "secret123")

        val result = loadPdf(ByteArrayInputStream(bytes), password = "secret123")

        assertTrue(result is PdfLoadResult.Success)
        (result as PdfLoadResult.Success).document.close()
    }

    @Test
    fun `loadPdf succeeds for an encrypted PDF opened with the owner password`() {
        val bytes = buildEncryptedPdf(userPassword = "secret123", ownerPassword = "ownerSecret456")

        val result = loadPdf(ByteArrayInputStream(bytes), password = "ownerSecret456")

        assertTrue(result is PdfLoadResult.Success)
        (result as PdfLoadResult.Success).document.close()
    }

    @Test
    fun `loadPdf fails with a message for bytes that aren't a PDF at all`() {
        val bytes = "this is definitely not a PDF".toByteArray()

        val result = loadPdf(ByteArrayInputStream(bytes))

        assertTrue(result is PdfLoadResult.Failure)
        assertTrue((result as PdfLoadResult.Failure).message.isNotBlank())
    }
}
