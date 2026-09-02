package com.offgridpdf.android.ui.tool

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
import com.offgridpdf.android.chain.PendingFile
import com.offgridpdf.android.files.TOO_LARGE_MESSAGE
import com.offgridpdf.android.files.rememberCreateDocumentLauncher
import com.offgridpdf.android.files.rememberOpenDocumentLauncher
import com.offgridpdf.android.files.saveResult
import com.offgridpdf.android.files.suggestedBaseName
import com.offgridpdf.android.pdf.PdfLoadResult
import com.offgridpdf.android.pdf.loadPdfFromUri
import com.offgridpdf.android.pdf.rearrangePdf
import com.offgridpdf.android.ui.common.userMessageFor
import com.offgridpdf.android.ui.theme.LocalOffGridPalette
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** Web reference: `RearrangeTool.tsx` + `rearrangePdf` (`pdf-ops.ts`). */
@Composable
fun RearrangeScreen() {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    var pickedUri by remember { mutableStateOf(PendingFile.consume()) }
    var password by remember { mutableStateOf("") }
    var pagesText by remember { mutableStateOf("") }
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
                resultMessage = saveResult(context, uri, bytes, "Pages rearranged successfully.")
            }
        }
        pendingBytes = null
    }

    val accent = LocalOffGridPalette.current.organize
    ToolScaffold(
        title = "Delete / Reorder Pages",
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
                if (pagesText.isBlank()) {
                    resultMessage = "Enter the pages to keep, in the desired order."
                } else {
                    running = true
                    resultMessage = null
                    val baseName = suggestedBaseName(uri)

                    scope.launch {
                        when (val result = loadPdfFromUri(context, uri, password.ifBlank { null })) {
                            is PdfLoadResult.Success -> {
                                try {
                                    pendingBytes = withContext(Dispatchers.Default) {
                                        rearrangePdf(result.document, pagesText)
                                    }
                                    lastResultBytes = pendingBytes
                                    saveLauncher.launch("${baseName}_rearranged.pdf")
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
            }
        },
        runLabel = if (running) "Rearranging..." else "Apply",
        resultMessage = resultMessage,
        chainableBytes = lastResultBytes,
        options = {
            Text("Keep only the pages you list, in that order. Omit a page to delete it; list a page more than once to duplicate it.")
            OutlinedTextField(
                value = pagesText,
                onValueChange = { pagesText = it },
                label = { Text("Pages to keep (in order)") },
                placeholder = { Text("e.g. 5,1,3 — omits page 2 and 4") },
                modifier = Modifier.fillMaxWidth(),
            )
        },
    )
}
