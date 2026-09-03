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
import com.offgridpdf.android.chain.PendingFile
import com.offgridpdf.android.files.SavedFile
import com.offgridpdf.android.files.batchResultMessage
import com.offgridpdf.android.files.readBytesFromUri
import com.offgridpdf.android.files.rememberCreateDocumentLauncher
import com.offgridpdf.android.files.rememberOpenMultipleDocumentsLauncher
import com.offgridpdf.android.files.runOnEachPdf
import com.offgridpdf.android.files.saveResult
import com.offgridpdf.android.files.savedFileOrNull
import com.offgridpdf.android.files.suggestedBaseName
import com.offgridpdf.android.pdf.compressPdf
import com.offgridpdf.android.ui.common.UriListSaver
import com.offgridpdf.android.ui.theme.LocalOffGridPalette
import kotlin.math.roundToInt
import kotlinx.coroutines.launch

/**
 * Web reference: `CompressTool.tsx` + `compressPdf` (`qpdf-engine.ts`). No
 * options beyond the file and password `ToolScaffold` already provides.
 *
 * Batch mode (`files/BatchRun.kt`): picking more than one file compresses
 * each with the same settings (there are none to vary here beyond the
 * shared password) and saves one zip; a single file keeps its own
 * size-reduction message exactly as it always has — that number doesn't
 * mean much averaged across several different files, so batch mode reports
 * a plain count instead.
 */
@Composable
fun CompressScreen() {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    var pickedFiles by rememberSaveable(stateSaver = UriListSaver) { mutableStateOf(PendingFile.consume()?.let { listOf(it) } ?: emptyList<Uri>()) }
    // Plain `remember`, deliberately: a document password is never written
    // to saved instance state (see `ui/common/Savers.kt`).
    var password by remember { mutableStateOf("") }
    var running by remember { mutableStateOf(false) }
    var resultMessage by rememberSaveable { mutableStateOf<String?>(null) }
    // The file this run produced, once it is really on disk. Not
    // saveable: it holds the bytes, and a Bundle caps out around 1 MB.
    var savedFile by remember { mutableStateOf<SavedFile?>(null) }
    var lastResultBytes by remember { mutableStateOf<ByteArray?>(null) }
    var pendingBytes by remember { mutableStateOf<ByteArray?>(null) }
    // Paired with pendingBytes, which a Bundle cannot hold — so neither is
    // saved (see `ui/common/Savers.kt`).
    var pendingSuccessMessage by remember { mutableStateOf("") }

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

    val accent = LocalOffGridPalette.current.edit
    val fileName = when {
        pickedFiles.isEmpty() -> null
        pickedFiles.size == 1 -> pickedFiles[0].lastPathSegment
        else -> "${pickedFiles.size} files selected"
    }

    ToolScaffold(
        title = "Compress PDF",
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

            scope.launch {
                // Only for the "X KB → Y KB" message. A failure to read it is
                // not a reason to fail the run, so it degrades to no size
                // comparison rather than throwing out of this coroutine.
                val singleOriginalSize = if (files.size == 1) {
                    runCatching { readBytesFromUri(context, files[0]).size }.getOrNull()
                } else {
                    null
                }
                val result = runOnEachPdf(
                    context = context,
                    files = files,
                    password = password,
                    zipEntrySuffix = "_compressed",
                    operate = { document -> compressPdf(document) },
                )
                when {
                    result.singleBytes != null -> {
                        pendingBytes = result.singleBytes
                        lastResultBytes = result.singleBytes
                        pendingSuccessMessage = if (singleOriginalSize != null) {
                            compressionMessage(singleOriginalSize, result.singleBytes.size)
                        } else {
                            "Compressed to ${formatSize(result.singleBytes.size)}."
                        }
                        savePdfLauncher.launch("${suggestedBaseName(files[0])}_compressed.pdf")
                    }
                    result.zipBytes != null -> {
                        pendingBytes = result.zipBytes
                        pendingSuccessMessage = batchResultMessage("Compressed", result)
                        saveZipLauncher.launch("compressed_pdfs.zip")
                    }
                    else -> {
                        resultMessage = result.failures.firstOrNull() ?: "Could not compress this PDF."
                    }
                }
                running = false
            }
        },
        runLabel = when {
            running -> "Compressing..."
            pickedFiles.size > 1 -> "Compress ${pickedFiles.size} Files"
            else -> "Compress PDF"
        },
        resultMessage = resultMessage,
        savedFile = savedFile,
        chainableBytes = lastResultBytes,
        batchNote = if (pickedFiles.size > 1) {
            "Compress will run with the same settings on all ${pickedFiles.size} files, saved as one zip."
        } else {
            null
        },
    )
}

private fun formatSize(bytes: Int): String =
    if (bytes < 1024 * 1024) {
        "${maxOf(1, (bytes / 1024f).roundToInt())} KB"
    } else {
        "%.1f MB".format(bytes / (1024f * 1024f))
    }

private fun compressionMessage(originalSize: Int, compressedSize: Int): String {
    val before = formatSize(originalSize)
    val after = formatSize(compressedSize)
    val saved = if (originalSize > 0) ((1 - compressedSize.toFloat() / originalSize) * 100).roundToInt() else 0
    return if (saved > 0) {
        "$before → $after ($saved% smaller)."
    } else {
        "$before → $after. This PDF was already efficiently compressed."
    }
}
