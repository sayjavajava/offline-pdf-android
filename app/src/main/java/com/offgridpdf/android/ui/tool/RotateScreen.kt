package com.offgridpdf.android.ui.tool

import android.net.Uri
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.Button
import androidx.compose.material3.OutlinedButton
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
import androidx.compose.ui.unit.dp
import com.offgridpdf.android.chain.PendingFile
import com.offgridpdf.android.files.SavedFile
import com.offgridpdf.android.files.batchResultMessage
import com.offgridpdf.android.files.rememberCreateDocumentLauncher
import com.offgridpdf.android.files.rememberOpenMultipleDocumentsLauncher
import com.offgridpdf.android.files.runOnEachPdf
import com.offgridpdf.android.files.saveResult
import com.offgridpdf.android.files.savedFileOrNull
import com.offgridpdf.android.files.suggestedBaseName
import com.offgridpdf.android.pdf.rotatePdf
import com.offgridpdf.android.ui.common.UriListSaver
import com.offgridpdf.android.ui.theme.LocalOffGridPalette
import kotlinx.coroutines.launch

private val ANGLES = listOf(90, 180, 270)

/**
 * Web reference: `RotateTool.tsx` + `rotatePdf` (`pdf-ops.ts`).
 *
 * Batch mode (`files/BatchRun.kt`): the same angle and page range apply to
 * every picked file, so more than one file just zips the results.
 */
@Composable
fun RotateScreen() {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    var pickedFiles by rememberSaveable(stateSaver = UriListSaver) { mutableStateOf(PendingFile.consume()?.let { listOf(it) } ?: emptyList<Uri>()) }
    // Plain `remember`, deliberately: a document password is never written
    // to saved instance state (see `ui/common/Savers.kt`).
    var password by remember { mutableStateOf("") }
    var pagesText by rememberSaveable { mutableStateOf("") }
    var angle by rememberSaveable { mutableStateOf(90) }
    var running by remember { mutableStateOf(false) }
    var resultMessage by rememberSaveable { mutableStateOf<String?>(null) }
    // The file this run produced, once it is really on disk. Not
    // saveable: it holds the bytes, and a Bundle caps out around 1 MB.
    var savedFile by remember { mutableStateOf<SavedFile?>(null) }
    var pendingBytes by remember { mutableStateOf<ByteArray?>(null) }
    // Paired with pendingBytes, which a Bundle cannot hold — so neither is
    // saved (see `ui/common/Savers.kt`).
    var pendingSuccessMessage by remember { mutableStateOf("") }
    var lastResultBytes by remember { mutableStateOf<ByteArray?>(null) }

    val pickLauncher = rememberOpenMultipleDocumentsLauncher { uris ->
        pickedFiles = uris
        password = ""
        resultMessage = null
        savedFile = null
    }

    val savePdfLauncher = rememberCreateDocumentLauncher("application/pdf") { uri ->
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

    val accent = LocalOffGridPalette.current.organize
    val fileName = when {
        pickedFiles.isEmpty() -> null
        pickedFiles.size == 1 -> pickedFiles[0].lastPathSegment
        else -> "${pickedFiles.size} files selected"
    }

    ToolScaffold(
        title = "Rotate Pages",
        accent = accent,
        pickedFileName = fileName,
        onPickFile = { pickLauncher.launch(arrayOf("application/pdf")) },
        password = password,
        onPasswordChange = { password = it },
        runEnabled = pickedFiles.isNotEmpty(),
        running = running,
        onRun = {
            running = true
            resultMessage = null
            savedFile = null
            lastResultBytes = null
            val files = pickedFiles
            val pageRange = pagesText.ifBlank { "all" }

            scope.launch {
                val result = runOnEachPdf(
                    context = context,
                    files = files,
                    password = password,
                    zipEntrySuffix = "_rotated$angle",
                    operate = { document -> rotatePdf(document, angle, pageRange) },
                )
                when {
                    result.singleBytes != null -> {
                        pendingBytes = result.singleBytes
                        lastResultBytes = result.singleBytes
                        pendingSuccessMessage = "Pages rotated $angle°."
                        savePdfLauncher.launch("${suggestedBaseName(files[0])}_rotated$angle.pdf")
                    }
                    result.zipBytes != null -> {
                        pendingBytes = result.zipBytes
                        pendingSuccessMessage = batchResultMessage("Rotated", result)
                        saveZipLauncher.launch("rotated_pdfs.zip")
                    }
                    else -> {
                        resultMessage = result.failures.firstOrNull() ?: "Could not rotate this PDF."
                    }
                }
                running = false
            }
        },
        runLabel = when {
            running -> "Rotating..."
            pickedFiles.size > 1 -> "Rotate ${pickedFiles.size} Files"
            else -> "Rotate Pages"
        },
        resultMessage = resultMessage,
        savedFile = savedFile,
        chainableBytes = lastResultBytes,
        batchNote = if (pickedFiles.size > 1) {
            "Rotate will run with the same settings on all ${pickedFiles.size} files, saved as one zip."
        } else {
            null
        },
        options = {
            Text("Rotation")
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                for (candidate in ANGLES) {
                    if (candidate == angle) {
                        Button(onClick = { angle = candidate }) { Text("$candidate°") }
                    } else {
                        OutlinedButton(onClick = { angle = candidate }) { Text("$candidate°") }
                    }
                }
            }
            OutlinedTextField(
                value = pagesText,
                onValueChange = { pagesText = it },
                label = { Text("Pages (blank = all)") },
                placeholder = { Text("e.g. 1, 3-5 — or leave blank for all") },
                modifier = Modifier.fillMaxWidth(),
            )
        },
    )
}
