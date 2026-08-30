package com.offgridpdf.android.pdf

import com.tom_roush.pdfbox.pdmodel.PDDocument
import com.tom_roush.pdfbox.pdmodel.PDPage
import com.tom_roush.pdfbox.pdmodel.encryption.AccessPermission
import com.tom_roush.pdfbox.pdmodel.encryption.StandardProtectionPolicy
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream

/**
 * Web reference: `removePdfPassword` (`pdf-ops.ts`). The one thing worth
 * a real test here: this project already shipped this exact bug once on
 * the web side (a decrypt-then-resave reporting success while the output
 * stayed encrypted) — these tests verify the *output*, not just that no
 * exception was thrown.
 */
class PdfUnlockTest {

    private fun buildEncryptedPdf(password: String): ByteArray {
        PDDocument().use { document ->
            document.addPage(PDPage())
            val policy = StandardProtectionPolicy(password, password, AccessPermission())
            policy.setEncryptionKeyLength(256)
            policy.setPreferAES(true)
            document.protect(policy)
            val out = ByteArrayOutputStream()
            document.save(out)
            return out.toByteArray()
        }
    }

    @Test
    fun `output opens with no password at all`() {
        val encrypted = buildEncryptedPdf("secret123")

        ByteArrayInputStream(encrypted).use { input ->
            PDDocument.load(input, "secret123").use { document ->
                val unlocked = removePdfPassword(document)

                val result = loadPdf(ByteArrayInputStream(unlocked))
                assertTrue(
                    "Expected the unlocked PDF to open without a password, but got $result",
                    result is PdfLoadResult.Success,
                )
                (result as PdfLoadResult.Success).document.close()
            }
        }
    }

    @Test
    fun `output is not merely re-encrypted with the same password`() {
        val encrypted = buildEncryptedPdf("secret123")

        ByteArrayInputStream(encrypted).use { input ->
            PDDocument.load(input, "secret123").use { document ->
                val unlocked = removePdfPassword(document)

                // The specific regression this guards against: PDDocument.save()
                // silently re-encrypting with the protection policy it still
                // holds after a successful decrypt, unless
                // setAllSecurityToBeRemoved(true) is set first. If that
                // happened, this would come back PasswordRequired instead of
                // Success even though "secret123" is never supplied here.
                val result = loadPdf(ByteArrayInputStream(unlocked))
                assertTrue(result is PdfLoadResult.Success)
                (result as PdfLoadResult.Success).document.close()
            }
        }
    }

    @Test
    fun `output preserves the page count`() {
        val encrypted = buildEncryptedPdf("secret123")

        ByteArrayInputStream(encrypted).use { input ->
            PDDocument.load(input, "secret123").use { document ->
                val unlocked = removePdfPassword(document)

                ByteArrayInputStream(unlocked).use { unlockedInput ->
                    PDDocument.load(unlockedInput).use { reloaded ->
                        assertTrue(reloaded.numberOfPages == 1)
                    }
                }
            }
        }
    }
}
