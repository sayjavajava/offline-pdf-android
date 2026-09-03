package com.offgridpdf.android.ui.tool

import android.net.Uri
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.KeyboardType
import com.offgridpdf.android.chain.PendingFile
import com.offgridpdf.android.files.SavedFile
import com.offgridpdf.android.files.TOO_LARGE_MESSAGE
import com.offgridpdf.android.files.ZipEntryData
import com.offgridpdf.android.files.createZip
import com.offgridpdf.android.files.rememberCreateDocumentLauncher
import com.offgridpdf.android.files.rememberOpenDocumentLauncher
import com.offgridpdf.android.files.saveResult
import com.offgridpdf.android.files.savedFileOrNull
import com.offgridpdf.android.files.suggestedBaseName
import com.offgridpdf.android.pdf.PdfLoadResult
import com.offgridpdf.android.pdf.RenderedPage
import com.offgridpdf.android.pdf.loadPdfFromUri
import com.offgridpdf.android.pdf.renderPdfPagesToPng
import com.offgridpdf.android.ui.common.NullableUriSaver
import com.offgridpdf.android.ui.common.ToolTextField
import com.offgridpdf.android.ui.common.rememberDisplayName
import com.offgridpdf.android.ui.common.userMessageFor
import com.offgridpdf.android.ui.theme.LocalOffGridPalette
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Web reference: `PdfToImagesTool.tsx` + `renderPdfPages` (`pdf-render.ts`).
 * No live thumbnail preview (unlike the web version's F-5/F-20 preview) —
 * that needs page rendering to already be cheap and interactive, which
 * makes sense to add once this tool's own real device-timing numbers are
 * in (see Spike A's write-up, `ANDROID_CODE_AUDIT.md`, tool-docs repo);
 * shipping the export path first rather than blocking it on a preview
 * that's a separate, later enhancement on the web side too.
 */
@Composable
fun PdfToImagesScreen() {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    var pickedUri by rememberSaveable(stateSaver = NullableUriSaver) { mutableStateOf(PendingFile.consume()) }
    // Plain `remember`, deliberately: a document password is never written
    // to saved instance state (see `ui/common/Savers.kt`).
    var password by remember { mutableStateOf("") }
    var pagesText by rememberSaveable { mutableStateOf("") }
    var scaleText by rememberSaveable { mutableStateOf("2") }
    var running by remember { mutableStateOf(false) }
    var resultMessage by rememberSaveable { mutableStateOf<String?>(null) }
    // The file this run produced, once it is really on disk. Not
    // saveable: it holds the bytes, and a Bundle caps out around 1 MB.
    var savedFile by remember { mutableStateOf<SavedFile?>(null) }

    // Set once the result bytes are ready, consumed by whichever save
    // launcher's onResult fires next. Only one of savePngLauncher /
    // saveZipLauncher is ever launched per run, so there's no ambiguity
    // about which one a given pending value belongs to.
    var pendingBytes by remember { mutableStateOf<ByteArray?>(null) }
    // Paired with pendingBytes, which a Bundle cannot hold — so neither is
    // saved (see `ui/common/Savers.kt`).
    var pendingSuccessMessage by remember { mutableStateOf("") }

    val pickLauncher = rememberOpenDocumentLauncher { uri ->
        pickedUri = uri
        password = ""
        resultMessage = null
        savedFile = null
    }

    val savePngLauncher = rememberCreateDocumentLauncher("image/png") { uri ->
        val bytes = pendingBytes
        if (uri != null && bytes != null) {
            scope.launch {
                val outcome = saveResult(context, uri, bytes, pendingSuccessMessage)
                resultMessage = outcome.message
                savedFile = outcome.savedFileOrNull
            }
        }
        pendingBytes = null
    }

    val saveZipLauncher = rememberCreateDocumentLauncher("application/zip") { uri ->
        val bytes = pendingBytes
        if (uri != null && bytes != null) {
            scope.launch {
                val outcome = saveResult(context, uri, bytes, pendingSuccessMessage)
                resultMessage = outcome.message
                savedFile = outcome.savedFileOrNull
            }
        }
        pendingBytes = null
    }

    val accent = LocalOffGridPalette.current.convert
    ToolScaffold(
        title = "PDF to Images",
        accent = accent,
        pickedFileName = rememberDisplayName(pickedUri),
        onPickFile = { pickLauncher.launch(arrayOf("application/pdf")) },
        password = password,
        onPasswordChange = { password = it },
        runEnabled = pickedUri != null,
        running = running,
        onRun = {
            // ToolScaffold only invokes onRun while runEnabled (pickedUri
            // != null) is true.
            pickedUri?.let { uri ->
                running = true
                resultMessage = null
                savedFile = null
                val pageRange = pagesText.ifBlank { "all" }
                val scale = scaleText.toFloatOrNull()

                scope.launch {
                    val baseName = suggestedBaseName(context, uri)
                    if (scale == null) {
                        resultMessage = "Enter a valid scale."
                        running = false
                        return@launch
                    }
                    when (val result = loadPdfFromUri(context, uri, password.ifBlank { null })) {
                        is PdfLoadResult.Success -> {
                            try {
                                val rendered = withContext(Dispatchers.Default) {
                                    renderPdfPagesToPng(result.document, pageRange, scale)
                                }
                                val plural = if (rendered.size == 1) "" else "s"
                                pendingSuccessMessage = "Exported ${rendered.size} page$plural."
                                if (rendered.size == 1) {
                                    val page = rendered[0]
                                    pendingBytes = page.bytes
                                    savePngLauncher.launch("${baseName}_page${page.pageNumber}.png")
                                } else {
                                    pendingBytes = withContext(Dispatchers.Default) {
                                        createZip(zipEntriesFor(rendered))
                                    }
                                    saveZipLauncher.launch("${baseName}_pages.zip")
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
        runLabel = if (running) "Exporting..." else "Export Pages as PNG",
        resultMessage = resultMessage,
        savedFile = savedFile,
        options = {
            ToolTextField(
                value = pagesText,
                onValueChange = { pagesText = it },
                label = "Pages (blank = all)",
                accent = accent,
                placeholder = "e.g. 1, 3-5",
            )
            ToolTextField(
                value = scaleText,
                onValueChange = { scaleText = it },
                label = "Scale (1 = 72dpi)",
                accent = accent,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
            )
        },
    )
}

/** Repeated page numbers are possible (a range like "3,1,1" keeps duplicates on purpose — see `resolvePageIndices`'s doc); suffix collisions rather than silently overwrite one zip entry with another, same as `SplitScreen.kt`'s own `zipEntriesFor`. */
private fun zipEntriesFor(pages: List<RenderedPage>): List<ZipEntryData> {
    val seen = mutableMapOf<Int, Int>()
    return pages.map { page ->
        val occurrence = (seen[page.pageNumber] ?: 0) + 1
        seen[page.pageNumber] = occurrence
        val suffix = if (occurrence > 1) "-copy$occurrence" else ""
        ZipEntryData(
            name = "page-${page.pageNumber.toString().padStart(3, '0')}$suffix.png",
            bytes = page.bytes,
        )
    }
}
