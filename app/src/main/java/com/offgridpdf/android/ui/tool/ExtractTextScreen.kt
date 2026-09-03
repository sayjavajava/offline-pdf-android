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
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import com.offgridpdf.android.chain.PendingFile
import com.offgridpdf.android.files.SavedFile
import com.offgridpdf.android.files.TOO_LARGE_MESSAGE
import com.offgridpdf.android.files.rememberCreateDocumentLauncher
import com.offgridpdf.android.files.rememberOpenDocumentLauncher
import com.offgridpdf.android.files.saveResult
import com.offgridpdf.android.files.savedFileOrNull
import com.offgridpdf.android.files.suggestedBaseName
import com.offgridpdf.android.pdf.ExtractedPageText
import com.offgridpdf.android.pdf.PdfLoadResult
import com.offgridpdf.android.pdf.extractText
import com.offgridpdf.android.pdf.loadPdfFromUri
import com.offgridpdf.android.ui.common.NullableUriSaver
import com.offgridpdf.android.ui.common.rememberDisplayName
import com.offgridpdf.android.ui.common.userMessageFor
import com.offgridpdf.android.ui.theme.LocalOffGridPalette
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** Web reference: `ExtractTextTool.tsx` + `extractPdfText` (`pdf-render.ts`). */
@Composable
fun ExtractTextScreen() {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    var pickedUri by rememberSaveable(stateSaver = NullableUriSaver) { mutableStateOf(PendingFile.consume()) }
    // Plain `remember`, deliberately: a document password is never written
    // to saved instance state (see `ui/common/Savers.kt`).
    var password by remember { mutableStateOf("") }
    var pagesText by rememberSaveable { mutableStateOf("") }
    var pageMarkers by rememberSaveable { mutableStateOf(true) }
    var running by remember { mutableStateOf(false) }
    var resultMessage by rememberSaveable { mutableStateOf<String?>(null) }
    // The file this run produced, once it is really on disk. Not
    // saveable: it holds the bytes, and a Bundle caps out around 1 MB.
    var savedFile by remember { mutableStateOf<SavedFile?>(null) }
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

    val saveLauncher = rememberCreateDocumentLauncher("text/plain") { uri ->
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
        title = "Extract Text",
        accent = accent,
        pickedFileName = rememberDisplayName(pickedUri),
        onPickFile = { pickLauncher.launch(arrayOf("application/pdf")) },
        password = password,
        onPasswordChange = { password = it },
        runEnabled = pickedUri != null,
        running = running,
        onRun = {
            pickedUri?.let { uri ->
                running = true
                resultMessage = null
                savedFile = null
                val pageRange = pagesText.ifBlank { "all" }

                scope.launch {
                    val baseName = suggestedBaseName(context, uri)
                    when (val result = loadPdfFromUri(context, uri, password.ifBlank { null })) {
                        is PdfLoadResult.Success -> {
                            try {
                                val pages = withContext(Dispatchers.Default) {
                                    extractText(result.document, pageRange)
                                }
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
        runLabel = if (running) "Extracting..." else "Extract Text",
        resultMessage = resultMessage,
        savedFile = savedFile,
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
