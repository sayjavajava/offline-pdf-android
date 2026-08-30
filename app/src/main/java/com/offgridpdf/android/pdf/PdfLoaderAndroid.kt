package com.offgridpdf.android.pdf

import android.content.Context
import android.net.Uri
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Thin Android-specific wrapper around [loadPdf]: resolves a
 * Storage-Access-Framework `Uri` (from `DocumentPicker.kt`) to a stream and
 * delegates the actual parsing to the plain-JVM, directly-unit-tested
 * function in `PdfLoader.kt`. Deliberately not unit-tested itself — it's a
 * one-branch null check plus a stream open, not logic worth a
 * Robolectric/instrumented test on its own (see
 * `ANDROID_IMPLEMENTATION_PLAN.md` §4's testing guidance in the tool-docs
 * repo: reach for those only when logic genuinely needs framework classes).
 *
 * Runs on `Dispatchers.IO`: PDDocument.load() reads and parses the whole
 * file synchronously.
 */
suspend fun loadPdfFromUri(context: Context, uri: Uri, password: String? = null): PdfLoadResult =
    withContext(Dispatchers.IO) {
        val stream = context.contentResolver.openInputStream(uri)
            ?: return@withContext PdfLoadResult.Failure("Could not open the selected file.")
        stream.use { loadPdf(it, password) }
    }
