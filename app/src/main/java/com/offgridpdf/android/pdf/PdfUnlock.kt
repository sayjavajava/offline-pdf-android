package com.offgridpdf.android.pdf

import com.tom_roush.pdfbox.pdmodel.PDDocument
import java.io.ByteArrayOutputStream

/**
 * Removes password protection from [document], which the caller has
 * already opened successfully with its password (via `loadPdfFromUri`).
 * Web reference: `removePdfPassword` (`pdf-ops.ts`).
 *
 * `setAllSecurityToBeRemoved(true)` is not optional here: without it,
 * `PDDocument.save()` re-encrypts with the original protection policy it
 * still holds internally after a successful decrypt, silently keeping the
 * output encrypted — the exact "reports success but the file is still
 * password-protected" bug class `ANDROID_IMPLEMENTATION_PLAN.md`'s A-8
 * entry (tool-docs repo) warns about, ported from a real bug this
 * project already hit once on the web side. Verified against the actual
 * output in `PdfUnlockTest.kt` — round-tripped through `loadPdf` with no
 * password — not just "no exception thrown".
 */
fun removePdfPassword(document: PDDocument): ByteArray {
    document.setAllSecurityToBeRemoved(true)
    val out = ByteArrayOutputStream()
    document.save(out)
    return out.toByteArray()
}
