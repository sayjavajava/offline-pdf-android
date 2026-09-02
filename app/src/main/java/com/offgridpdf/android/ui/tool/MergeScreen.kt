package com.offgridpdf.android.ui.tool

import android.net.Uri
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.offgridpdf.android.chain.PendingFile
import com.offgridpdf.android.files.TOO_LARGE_MESSAGE
import com.offgridpdf.android.files.rememberCreateDocumentLauncher
import com.offgridpdf.android.files.rememberOpenMultipleDocumentsLauncher
import com.offgridpdf.android.files.saveResult
import com.offgridpdf.android.pdf.PdfLoadResult
import com.offgridpdf.android.pdf.loadPdfFromUri
import com.offgridpdf.android.pdf.mergePdf
import com.offgridpdf.android.ui.common.ScreenTopBar
import com.offgridpdf.android.ui.common.userMessageFor
import com.offgridpdf.android.ui.theme.LocalOffGridPalette
import com.tom_roush.pdfbox.pdmodel.PDDocument
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Web reference: `MergeTool.tsx` + `mergePdf` (`pdf-ops.ts`).
 *
 * Deliberately **not** built on `ToolScaffold` — that shape (one file, one
 * optional password) doesn't fit a multi-file, no-password tool at all.
 * `MergeTool.tsx` itself has no password field; an encrypted file in the
 * batch just fails by name, same as any other unreadable file (see below).
 * Forcing this through `ToolScaffold` would mean bending its API around a
 * shape it was never modeled on — worse than a second small screen.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MergeScreen() {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    var files by remember { mutableStateOf(PendingFile.consume()?.let { listOf(it) } ?: emptyList()) }
    var running by remember { mutableStateOf(false) }
    var resultMessage by remember { mutableStateOf<String?>(null) }
    var pendingBytes by remember { mutableStateOf<ByteArray?>(null) }

    val pickLauncher = rememberOpenMultipleDocumentsLauncher { uris ->
        files = uris
        resultMessage = null
    }

    val saveLauncher = rememberCreateDocumentLauncher("application/pdf") { uri ->
        val bytes = pendingBytes
        if (uri != null && bytes != null) {
            scope.launch {
                resultMessage = saveResult(context, uri, bytes, "Your PDFs have been merged successfully.")
            }
        }
        pendingBytes = null
    }

    Scaffold(
        topBar = { ScreenTopBar(title = "Merge PDF") },
        containerColor = LocalOffGridPalette.current.paper,
    ) { innerPadding ->
        Column(
            modifier = Modifier.fillMaxSize().padding(innerPadding).padding(16.dp),
        ) {
            Button(
                onClick = { pickLauncher.launch(arrayOf("application/pdf")) },
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text(if (files.isEmpty()) "Choose PDF files" else "${files.size} file(s) selected")
            }

            if (files.isNotEmpty()) {
                Text(files.joinToString("\n") { it.lastPathSegment ?: it.toString() })
            }

            Button(
                onClick = {
                    if (files.size < 2) {
                        resultMessage = "Please select at least two PDF files to merge."
                    } else {
                        running = true
                        resultMessage = null
                        val toMerge = files
                        scope.launch {
                            val opened = mutableListOf<PDDocument>()
                            var failureMessage: String? = null

                            for (uri in toMerge) {
                                val name = uri.lastPathSegment ?: uri.toString()
                                when (val result = loadPdfFromUri(context, uri)) {
                                    is PdfLoadResult.Success -> opened.add(result.document)
                                    PdfLoadResult.PasswordRequired -> {
                                        failureMessage = "Could not read \"$name\": this file needs a password."
                                    }
                                    is PdfLoadResult.Failure -> {
                                        failureMessage = "Could not read \"$name\": ${result.message}"
                                    }
                                }
                                if (failureMessage != null) break
                            }

                            if (failureMessage != null) {
                                resultMessage = failureMessage
                            } else {
                                try {
                                    pendingBytes = withContext(Dispatchers.Default) { mergePdf(opened) }
                                    saveLauncher.launch("merged.pdf")
                                } catch (e: Exception) {
                                    resultMessage = userMessageFor(e)
                                } catch (e: OutOfMemoryError) {
                                    resultMessage = TOO_LARGE_MESSAGE
                                }
                            }
                            opened.forEach { it.close() }
                            running = false
                        }
                    }
                },
                enabled = !running,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text(if (running) "Merging..." else "Merge PDFs")
            }

            if (running) {
                CircularProgressIndicator()
            }

            resultMessage?.let { Text(it) }
        }
    }
}
