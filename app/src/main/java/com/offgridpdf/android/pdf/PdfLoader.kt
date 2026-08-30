package com.offgridpdf.android.pdf

import com.tom_roush.pdfbox.pdmodel.PDDocument
import com.tom_roush.pdfbox.pdmodel.encryption.InvalidPasswordException
import java.io.IOException
import java.io.InputStream

/**
 * Outcome of trying to open a PDF. `PasswordRequired` covers both "no
 * password was supplied and the file needs one" and "the supplied password
 * was wrong" — PdfBox-Android's own `InvalidPasswordException` doesn't
 * distinguish the two, and the UI treats them the same way: show the
 * password field and let the user try again.
 */
sealed interface PdfLoadResult {
    data class Success(val document: PDDocument) : PdfLoadResult
    data object PasswordRequired : PdfLoadResult
    data class Failure(val message: String) : PdfLoadResult
}

/**
 * Opens a PDF from a plain [InputStream] — no Android framework classes
 * involved beyond PdfBox-Android itself, which `NATIVE_ANDROID_SPIKE.md`
 * (tool-docs repo) verified runs under plain `java`/`javac`. That's what
 * makes this directly unit-testable (`PdfLoaderTest.kt`) without
 * Robolectric or an emulator — see that file for how the same "no
 * password needed" vs. "needs a password" vs. "wrong password" cases the
 * web app's own `loadPdf` (`pdf-ops.ts`) handles are exercised here.
 *
 * The caller owns the stream's lifecycle; this function does not close it.
 */
fun loadPdf(input: InputStream, password: String? = null): PdfLoadResult {
    return try {
        val document = if (password != null) {
            PDDocument.load(input, password)
        } else {
            PDDocument.load(input)
        }
        PdfLoadResult.Success(document)
    } catch (e: InvalidPasswordException) {
        PdfLoadResult.PasswordRequired
    } catch (e: IOException) {
        PdfLoadResult.Failure(e.message ?: "This file could not be read as a PDF.")
    }
}
