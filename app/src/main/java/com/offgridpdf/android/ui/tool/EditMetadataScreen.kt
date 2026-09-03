package com.offgridpdf.android.ui.tool

import android.net.Uri
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import com.offgridpdf.android.chain.ChainOrigin
import com.offgridpdf.android.chain.PendingFile
import com.offgridpdf.android.files.SavedFile
import com.offgridpdf.android.files.TOO_LARGE_MESSAGE
import com.offgridpdf.android.files.rememberCreateDocumentLauncher
import com.offgridpdf.android.files.rememberOpenDocumentLauncher
import com.offgridpdf.android.files.saveResult
import com.offgridpdf.android.files.savedFileOrNull
import com.offgridpdf.android.files.suggestedBaseName
import com.offgridpdf.android.pdf.PdfLoadResult
import com.offgridpdf.android.pdf.PdfMetadataEdit
import com.offgridpdf.android.pdf.editPdfMetadata
import com.offgridpdf.android.pdf.loadPdfFromUri
import com.offgridpdf.android.ui.common.NullableUriSaver
import com.offgridpdf.android.ui.common.ToolTextField
import com.offgridpdf.android.ui.common.rememberDisplayName
import com.offgridpdf.android.ui.common.userMessageFor
import com.offgridpdf.android.ui.theme.LocalOffGridPalette
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Web reference: `EditTool.tsx` + `editPdfMetadata` (`pdf-ops.ts`). Exposes
 * the same four fields the web UI does (title/author/subject/keywords) —
 * `editPdfMetadata` (this app's) also supports producer/creator, same as
 * the web version's library function, but neither UI exposes them.
 */
@Composable
fun EditMetadataScreen() {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    var pickedUri by rememberSaveable(stateSaver = NullableUriSaver) { mutableStateOf(PendingFile.consume()) }
    var inheritedChainOrigin by rememberSaveable { mutableStateOf(ChainOrigin.consume()) }
    // Plain `remember`, deliberately: a document password is never written
    // to saved instance state (see `ui/common/Savers.kt`).
    var password by remember { mutableStateOf("") }
    var title by rememberSaveable { mutableStateOf("") }
    var author by rememberSaveable { mutableStateOf("") }
    var subject by rememberSaveable { mutableStateOf("") }
    var keywords by rememberSaveable { mutableStateOf("") }
    var running by remember { mutableStateOf(false) }
    var resultMessage by rememberSaveable { mutableStateOf<String?>(null) }
    // The file this run produced, once it is really on disk. Not
    // saveable: it holds the bytes, and a Bundle caps out around 1 MB.
    var savedFile by remember { mutableStateOf<SavedFile?>(null) }
    var pendingBytes by remember { mutableStateOf<ByteArray?>(null) }
    var lastResultBytes by remember { mutableStateOf<ByteArray?>(null) }
    var chainOriginBaseName by remember { mutableStateOf("") }
    var chainedFileName by remember { mutableStateOf("") }

    val pickLauncher = rememberOpenDocumentLauncher { uri ->
        pickedUri = uri
        password = ""
        resultMessage = null
        savedFile = null
        inheritedChainOrigin = null
    }

    val saveLauncher = rememberCreateDocumentLauncher("application/pdf") { uri ->
        val bytes = pendingBytes
        if (uri != null && bytes != null) {
            scope.launch {
                val outcome = saveResult(context, uri, bytes, "The PDF metadata has been updated.")
                resultMessage = outcome.message
                savedFile = outcome.savedFileOrNull
            }
        }
        pendingBytes = null
    }

    val accent = LocalOffGridPalette.current.edit
    ToolScaffold(
        title = "Edit PDF Metadata",
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

                scope.launch {
                    when (val result = loadPdfFromUri(context, uri, password.ifBlank { null })) {
                        is PdfLoadResult.Success -> {
                            try {
                                pendingBytes = withContext(Dispatchers.Default) {
                                    editPdfMetadata(
                                        result.document,
                                        PdfMetadataEdit(title = title, author = author, subject = subject, keywords = keywords),
                                    )
                                }
                                lastResultBytes = pendingBytes
                                val baseName = suggestedBaseName(context, uri)
                                val originBaseName = inheritedChainOrigin ?: baseName
                                chainOriginBaseName = originBaseName
                                chainedFileName = "${originBaseName}_edited.pdf"
                                saveLauncher.launch("${baseName}_edited.pdf")
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
        runLabel = if (running) "Saving..." else "Save Metadata",
        resultMessage = resultMessage,
        savedFile = savedFile,
        chainableBytes = lastResultBytes,
        chainOriginBaseName = chainOriginBaseName,
        chainedFileName = chainedFileName,
        options = {
            ToolTextField(
                value = title,
                onValueChange = { title = it },
                label = "Title",
                accent = accent,
            )
            ToolTextField(
                value = author,
                onValueChange = { author = it },
                label = "Author",
                accent = accent,
            )
            ToolTextField(
                value = subject,
                onValueChange = { subject = it },
                label = "Subject",
                accent = accent,
            )
            ToolTextField(
                value = keywords,
                onValueChange = { keywords = it },
                label = "Keywords (comma-separated)",
                accent = accent,
            )
        },
    )
}
