package com.offgridpdf.android.ui.tool

import com.offgridpdf.android.chain.PendingFile

import com.offgridpdf.android.ui.theme.LocalOffGridPalette

import android.net.Uri
import androidx.compose.foundation.layout.fillMaxWidth
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
import com.offgridpdf.android.pdf.PdfLoadResult
import com.offgridpdf.android.pdf.PdfMetadataEdit
import com.offgridpdf.android.pdf.editPdfMetadata
import com.offgridpdf.android.pdf.loadPdfFromUri
import kotlinx.coroutines.launch

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

    var pickedUri by remember { mutableStateOf(PendingFile.consume()) }
    var password by remember { mutableStateOf("") }
    var title by remember { mutableStateOf("") }
    var author by remember { mutableStateOf("") }
    var subject by remember { mutableStateOf("") }
    var keywords by remember { mutableStateOf("") }
    var running by remember { mutableStateOf(false) }
    var resultMessage by remember { mutableStateOf<String?>(null) }
    var pendingBytes by remember { mutableStateOf<ByteArray?>(null) }
    var lastResultBytes by remember { mutableStateOf<ByteArray?>(null) }

    val pickLauncher = rememberOpenDocumentLauncher { uri ->
        pickedUri = uri
        password = ""
        resultMessage = null
    }

    val saveLauncher = rememberCreateDocumentLauncher("application/pdf") { uri ->
        val bytes = pendingBytes
        if (uri != null && bytes != null) {
            scope.launch {
                writeBytesToUri(context, uri, bytes)
                resultMessage = "The PDF metadata has been updated."
            }
        }
        pendingBytes = null
    }

    val accent = LocalOffGridPalette.current.edit
    ToolScaffold(
        title = "Edit PDF Metadata",
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

                scope.launch {
                    when (val result = loadPdfFromUri(context, uri, password.ifBlank { null })) {
                        is PdfLoadResult.Success -> {
                            pendingBytes = editPdfMetadata(
                                result.document,
                                PdfMetadataEdit(title = title, author = author, subject = subject, keywords = keywords),
                            )
                            result.document.close()
                            lastResultBytes = pendingBytes
                            saveLauncher.launch("${suggestedBaseName(uri)}_edited.pdf")
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
        chainableBytes = lastResultBytes,
        options = {
            OutlinedTextField(
                value = title,
                onValueChange = { title = it },
                label = { Text("Title") },
                modifier = Modifier.fillMaxWidth(),
            )
            OutlinedTextField(
                value = author,
                onValueChange = { author = it },
                label = { Text("Author") },
                modifier = Modifier.fillMaxWidth(),
            )
            OutlinedTextField(
                value = subject,
                onValueChange = { subject = it },
                label = { Text("Subject") },
                modifier = Modifier.fillMaxWidth(),
            )
            OutlinedTextField(
                value = keywords,
                onValueChange = { keywords = it },
                label = { Text("Keywords (comma-separated)") },
                modifier = Modifier.fillMaxWidth(),
            )
        },
    )
}
