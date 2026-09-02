package com.offgridpdf.android.ui.tool

import com.offgridpdf.android.chain.PendingFile

import com.offgridpdf.android.ui.theme.LocalOffGridPalette

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
import com.offgridpdf.android.files.rememberCreateDocumentLauncher
import com.offgridpdf.android.files.rememberOpenDocumentLauncher
import com.offgridpdf.android.files.suggestedBaseName
import com.offgridpdf.android.files.writeBytesToUri
import com.offgridpdf.android.pdf.ExtractedPageText
import com.offgridpdf.android.pdf.PdfLoadResult
import com.offgridpdf.android.pdf.extractText
import com.offgridpdf.android.pdf.loadPdfFromUri
import kotlinx.coroutines.launch

/** Web reference: `ExtractTextTool.tsx` + `extractPdfText` (`pdf-render.ts`). */
@Composable
fun ExtractTextScreen() {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    var pickedUri by remember { mutableStateOf(PendingFile.consume()) }
    var password by remember { mutableStateOf("") }
    var pagesText by remember { mutableStateOf("") }
    var pageMarkers by remember { mutableStateOf(true) }
    var running by remember { mutableStateOf(false) }
    var resultMessage by remember { mutableStateOf<String?>(null) }
    var pendingBytes by remember { mutableStateOf<ByteArray?>(null) }
    var pendingSuccessMessage by remember { mutableStateOf("") }

    val pickLauncher = rememberOpenDocumentLauncher { uri ->
        pickedUri = uri
        password = ""
        resultMessage = null
    }

    val saveLauncher = rememberCreateDocumentLauncher("text/plain") { uri ->
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
        title = "Extract Text",
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
                val pageRange = pagesText.ifBlank { "all" }

                scope.launch {
                    when (val result = loadPdfFromUri(context, uri, password.ifBlank { null })) {
                        is PdfLoadResult.Success -> {
                            try {
                                val pages = extractText(result.document, pageRange)
                                // A scanned document is pictures of words with no
                                // text layer, so this comes back empty. Saying so
                                // beats handing over a blank file that looks like
                                // the tool worked.
                                if (pages.all { it.text.isBlank() }) {
                                    resultMessage = "This PDF has no text layer — it is most likely scanned. " +
                                        "Extracting its words would need OCR, which this tool does not do."
                                } else {
                                    pendingBytes = joinPages(pages, pageMarkers).toByteArray(Charsets.UTF_8)
                                    val emptyPages = pages.count { it.text.isBlank() }
                                    val plural = if (pages.size == 1) "" else "s"
                                    pendingSuccessMessage = "Extracted text from ${pages.size} page$plural." +
                                        if (emptyPages > 0) " $emptyPages had no text layer." else ""
                                    saveLauncher.launch("${baseName}_text.txt")
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
        runLabel = if (running) "Extracting..." else "Extract Text",
        resultMessage = resultMessage,
        options = {
            OutlinedTextField(
                value = pagesText,
                onValueChange = { pagesText = it },
                label = { Text("Pages (blank = all)") },
                placeholder = { Text("e.g. 1, 3-5") },
                modifier = Modifier.fillMaxWidth(),
            )
            Row {
                Checkbox(checked = pageMarkers, onCheckedChange = { pageMarkers = it })
                Text("Mark where each page starts")
            }
        },
    )
}

private fun joinPages(pages: List<ExtractedPageText>, withMarkers: Boolean): String =
    if (withMarkers) {
        pages.joinToString("\n\n") { "--- Page ${it.pageNumber} ---\n${it.text}" }
    } else {
        pages.joinToString("\n\n") { it.text }
    }
