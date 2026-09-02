package com.offgridpdf.android.ui.tool

import android.net.Uri
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import com.offgridpdf.android.chain.PendingFile
import com.offgridpdf.android.files.ZipEntryData
import com.offgridpdf.android.files.createZip
import com.offgridpdf.android.files.rememberCreateDocumentLauncher
import com.offgridpdf.android.files.rememberOpenDocumentLauncher
import com.offgridpdf.android.files.saveResult
import com.offgridpdf.android.files.suggestedBaseName
import com.offgridpdf.android.pdf.PdfLoadResult
import com.offgridpdf.android.pdf.extractImages
import com.offgridpdf.android.pdf.loadPdfFromUri
import com.offgridpdf.android.ui.theme.LocalOffGridPalette
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Web reference: `ExtractImagesTool.tsx` + `extractImagesFromDocument`
 * (`image-extract.ts`). A single embedded image downloads as itself; more
 * than one is bundled into a zip, matching the web version's own choice
 * not to fire off a dozen separate downloads.
 */
@Composable
fun ExtractImagesScreen() {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    var pickedUri by remember { mutableStateOf(PendingFile.consume()) }
    var password by remember { mutableStateOf("") }
    var running by remember { mutableStateOf(false) }
    var resultMessage by remember { mutableStateOf<String?>(null) }

    // Only one of these three is ever launched per run, so there's no
    // ambiguity about which save launcher a pending value belongs to
    // (same convention as SplitScreen).
    var pendingBytes by remember { mutableStateOf<ByteArray?>(null) }
    var pendingSuccessMessage by remember { mutableStateOf("") }

    val pickLauncher = rememberOpenDocumentLauncher { uri ->
        pickedUri = uri
        password = ""
        resultMessage = null
    }

    val saveJpegLauncher = rememberCreateDocumentLauncher("image/jpeg") { uri ->
        val bytes = pendingBytes
        if (uri != null && bytes != null) {
            scope.launch {
                resultMessage = saveResult(context, uri, bytes, pendingSuccessMessage)
            }
        }
        pendingBytes = null
    }

    val savePngLauncher = rememberCreateDocumentLauncher("image/png") { uri ->
        val bytes = pendingBytes
        if (uri != null && bytes != null) {
            scope.launch {
                resultMessage = saveResult(context, uri, bytes, pendingSuccessMessage)
            }
        }
        pendingBytes = null
    }

    val saveZipLauncher = rememberCreateDocumentLauncher("application/zip") { uri ->
        val bytes = pendingBytes
        if (uri != null && bytes != null) {
            scope.launch {
                resultMessage = saveResult(context, uri, bytes, pendingSuccessMessage)
            }
        }
        pendingBytes = null
    }

    val accent = LocalOffGridPalette.current.convert
    ToolScaffold(
        title = "Extract Images",
        accent = accent,
        pickedFileName = pickedUri?.lastPathSegment,
        onPickFile = { pickLauncher.launch(arrayOf("application/pdf")) },
        password = password,
        onPasswordChange = { password = it },
        runEnabled = pickedUri != null,
        running = running,
        onRun = {
            pickedUri?.let { uri ->
                running = true
                resultMessage = null
                val baseName = suggestedBaseName(uri)

                scope.launch {
                    when (val result = loadPdfFromUri(context, uri, password.ifBlank { null })) {
                        is PdfLoadResult.Success -> {
                            try {
                                val extracted = withContext(Dispatchers.Default) {
                                    extractImages(result.document)
                                }
                                when {
                                    extracted.images.isEmpty() -> {
                                        resultMessage = if (extracted.skipped.isNotEmpty()) {
                                            "This PDF has no exportable images. ${extracted.skipped[0]}"
                                        } else {
                                            "This PDF does not contain any embedded images."
                                        }
                                    }
                                    extracted.images.size == 1 -> {
                                        val only = extracted.images[0]
                                        pendingBytes = only.bytes
                                        pendingSuccessMessage = successMessage(extracted.images.size, extracted.skipped.size)
                                        val suggestedName = "${baseName}_${only.name}"
                                        if (only.format == "jpg") {
                                            saveJpegLauncher.launch(suggestedName)
                                        } else {
                                            savePngLauncher.launch(suggestedName)
                                        }
                                    }
                                    else -> {
                                        pendingBytes = withContext(Dispatchers.Default) {
                                            createZip(extracted.images.map { ZipEntryData(it.name, it.bytes) })
                                        }
                                        pendingSuccessMessage = successMessage(extracted.images.size, extracted.skipped.size)
                                        saveZipLauncher.launch("${baseName}_images.zip")
                                    }
                                }
                            } catch (e: Exception) {
                                resultMessage = userMessageFor(e)
                            } catch (e: OutOfMemoryError) {
                                resultMessage = TOO_LARGE_MESSAGE
                            } finally {
                                result.document.close()
                            }
                        }
                        PdfLoadResult.PasswordRequired -> {
                            resultMessage = if (password.isBlank()) {
                                "This PDF needs a password."
                            } else {
                                "Wrong password — try again."
                            }
                        }
                        is PdfLoadResult.Failure -> {
                            resultMessage = result.message
                        }
                    }
                    running = false
                }
            }
        },
        runLabel = if (running) "Extracting..." else "Extract Images",
        resultMessage = resultMessage,
    )
}

private fun successMessage(imageCount: Int, skippedCount: Int): String {
    val plural = if (imageCount == 1) "" else "s"
    val skippedNote = if (skippedCount > 0) " $skippedCount could not be exported." else ""
    return "Extracted $imageCount image$plural.$skippedNote"
}
