package com.offgridpdf.android.ui.tool

import android.net.Uri
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.Checkbox
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
import com.offgridpdf.android.files.ZipEntryData
import com.offgridpdf.android.files.createZip
import com.offgridpdf.android.files.rememberCreateDocumentLauncher
import com.offgridpdf.android.files.rememberOpenDocumentLauncher
import com.offgridpdf.android.files.writeBytesToUri
import com.offgridpdf.android.pdf.PdfLoadResult
import com.offgridpdf.android.pdf.SplitPage
import com.offgridpdf.android.pdf.loadPdfFromUri
import com.offgridpdf.android.pdf.splitPdfToPages
import com.offgridpdf.android.pdf.splitPdfToSingleFile
import kotlinx.coroutines.launch

/** Web reference: `SplitTool.tsx` + `splitPdf`/`splitPdfToZip` (`pdf-ops.ts`). */
@Composable
fun SplitScreen() {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    var pickedUri by remember { mutableStateOf<Uri?>(null) }
    var password by remember { mutableStateOf("") }
    var pagesText by remember { mutableStateOf("") }
    var separateFiles by remember { mutableStateOf(false) }
    var running by remember { mutableStateOf(false) }
    var resultMessage by remember { mutableStateOf<String?>(null) }

    // Set once the result bytes are ready, consumed by whichever save
    // launcher's onResult fires next. Only one of savePdfLauncher /
    // saveZipLauncher is ever launched per run, so there's no ambiguity
    // about which one a given pending value belongs to.
    var pendingBytes by remember { mutableStateOf<ByteArray?>(null) }
    var pendingSuccessMessage by remember { mutableStateOf("") }

    val pickLauncher = rememberOpenDocumentLauncher { uri ->
        pickedUri = uri
        password = ""
        resultMessage = null
    }

    val savePdfLauncher = rememberCreateDocumentLauncher("application/pdf") { uri ->
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

    ToolScaffold(
        title = "Split PDF",
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
                val baseName = suggestedBaseName(uri)

                scope.launch {
                    when (val result = loadPdfFromUri(context, uri, password.ifBlank { null })) {
                        is PdfLoadResult.Success -> {
                            try {
                                if (separateFiles) {
                                    val pages = splitPdfToPages(result.document, pageRange)
                                    val plural = if (pages.size == 1) "" else "s"
                                    pendingSuccessMessage = "Split into ${pages.size} file$plural."
                                    if (pages.size == 1) {
                                        val page = pages[0]
                                        pendingBytes = page.bytes
                                        val suggestedName = "${baseName}_page-${page.pageNumber.toString().padStart(3, '0')}.pdf"
                                        savePdfLauncher.launch(suggestedName)
                                    } else {
                                        pendingBytes = createZip(zipEntriesFor(pages))
                                        saveZipLauncher.launch("${baseName}_split.zip")
                                    }
                                } else {
                                    pendingBytes = splitPdfToSingleFile(result.document, pageRange)
                                    pendingSuccessMessage = "Your PDF has been split successfully."
                                    savePdfLauncher.launch("${baseName}_split.pdf")
                                }
                            } catch (e: IllegalArgumentException) {
                                resultMessage = e.message
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
        runLabel = if (running) "Splitting..." else "Split PDF",
        resultMessage = resultMessage,
        options = {
            OutlinedTextField(
                value = pagesText,
                onValueChange = { pagesText = it },
                label = { Text("Pages to extract") },
                placeholder = { Text("e.g. 1, 3-5, 8 — or \"all\"") },
                modifier = Modifier.fillMaxWidth(),
            )
            Row {
                Checkbox(checked = separateFiles, onCheckedChange = { separateFiles = it })
                Text("Download as separate files (zip)")
            }
        },
    )
}

/**
 * Repeated page numbers are possible (a range like "3,1,1" keeps
 * duplicates on purpose — see `PdfSplit.kt`'s `parsePageRange` doc);
 * suffix collisions rather than silently overwrite one zip entry with
 * another, same as `SplitTool.tsx` does.
 */
private fun zipEntriesFor(pages: List<SplitPage>): List<ZipEntryData> {
    val seen = mutableMapOf<Int, Int>()
    return pages.map { page ->
        val occurrence = (seen[page.pageNumber] ?: 0) + 1
        seen[page.pageNumber] = occurrence
        val suffix = if (occurrence > 1) "-copy$occurrence" else ""
        ZipEntryData(
            name = "page-${page.pageNumber.toString().padStart(3, '0')}$suffix.pdf",
            bytes = page.bytes,
        )
    }
}

private fun suggestedBaseName(uri: Uri): String {
    val name = uri.lastPathSegment?.substringAfterLast('/') ?: "document"
    return name.removeSuffix(".pdf").removeSuffix(".PDF")
}
