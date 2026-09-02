package com.offgridpdf.android.ui.tool

import com.offgridpdf.android.ui.theme.LocalOffGridPalette

import android.net.Uri
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.KeyboardType
import com.offgridpdf.android.files.ZipEntryData
import com.offgridpdf.android.files.createZip
import com.offgridpdf.android.files.rememberCreateDocumentLauncher
import com.offgridpdf.android.files.rememberOpenDocumentLauncher
import com.offgridpdf.android.files.suggestedBaseName
import com.offgridpdf.android.files.writeBytesToUri
import com.offgridpdf.android.pdf.PdfLoadResult
import com.offgridpdf.android.pdf.RenderedPage
import com.offgridpdf.android.pdf.loadPdfFromUri
import com.offgridpdf.android.pdf.renderPdfPagesToPng
import kotlinx.coroutines.launch

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

    var pickedUri by remember { mutableStateOf<Uri?>(null) }
    var password by remember { mutableStateOf("") }
    var pagesText by remember { mutableStateOf("") }
    var scaleText by remember { mutableStateOf("2") }
    var running by remember { mutableStateOf(false) }
    var resultMessage by remember { mutableStateOf<String?>(null) }

    // Set once the result bytes are ready, consumed by whichever save
    // launcher's onResult fires next. Only one of savePngLauncher /
    // saveZipLauncher is ever launched per run, so there's no ambiguity
    // about which one a given pending value belongs to.
    var pendingBytes by remember { mutableStateOf<ByteArray?>(null) }
    var pendingSuccessMessage by remember { mutableStateOf("") }

    val pickLauncher = rememberOpenDocumentLauncher { uri ->
        pickedUri = uri
        password = ""
        resultMessage = null
    }

    val savePngLauncher = rememberCreateDocumentLauncher("image/png") { uri ->
        val bytes = pendingBytes
        if (uri != null && bytes != null) {
            scope.launch {
                writeBytesToUri(context, uri, bytes)
                resultMessage = pendingSuccessMessage
            }
        }
        pendingBytes = null
    }

    val saveZipLauncher = rememberCreateDocumentLauncher("application/zip") { uri ->
        val bytes = pendingBytes
        if (uri != null && bytes != null) {
            scope.launch {
                writeBytesToUri(context, uri, bytes)
                resultMessage = pendingSuccessMessage
            }
        }
        pendingBytes = null
    }

    val accent = LocalOffGridPalette.current.convert
    ToolScaffold(
        title = "PDF to Images",
        accent = accent,
        pickedFileName = pickedUri?.lastPathSegment,
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
                val pageRange = pagesText.ifBlank { "all" }
                val scale = scaleText.toFloatOrNull()
                val baseName = suggestedBaseName(uri)

                scope.launch {
                    if (scale == null) {
                        resultMessage = "Enter a valid scale."
                        running = false
                        return@launch
                    }
                    when (val result = loadPdfFromUri(context, uri, password.ifBlank { null })) {
                        is PdfLoadResult.Success -> {
                            try {
                                val rendered = renderPdfPagesToPng(result.document, pageRange, scale)
                                val plural = if (rendered.size == 1) "" else "s"
                                pendingSuccessMessage = "Exported ${rendered.size} page$plural."
                                if (rendered.size == 1) {
                                    val page = rendered[0]
                                    pendingBytes = page.bytes
                                    savePngLauncher.launch("${baseName}_page${page.pageNumber}.png")
                                } else {
                                    pendingBytes = createZip(zipEntriesFor(rendered))
                                    saveZipLauncher.launch("${baseName}_pages.zip")
                                }
                            } catch (e: IllegalArgumentException) {
                                resultMessage = e.message
                            } catch (e: Exception) {
                                resultMessage = e.message ?: "Could not export this PDF as images."
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
        options = {
            OutlinedTextField(
                value = pagesText,
                onValueChange = { pagesText = it },
                label = { Text("Pages (blank = all)") },
                placeholder = { Text("e.g. 1, 3-5") },
                modifier = Modifier.fillMaxWidth(),
            )
            OutlinedTextField(
                value = scaleText,
                onValueChange = { scaleText = it },
                label = { Text("Scale (1 = 72dpi)") },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                modifier = Modifier.fillMaxWidth(),
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
