package com.offgridpdf.android.ui.tool

import android.net.Uri
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import com.offgridpdf.android.files.readBytesFromUri
import com.offgridpdf.android.files.rememberCreateDocumentLauncher
import com.offgridpdf.android.files.rememberOpenDocumentLauncher
import com.offgridpdf.android.files.suggestedBaseName
import com.offgridpdf.android.files.writeBytesToUri
import com.offgridpdf.android.pdf.PdfLoadResult
import com.offgridpdf.android.pdf.compressPdf
import com.offgridpdf.android.pdf.loadPdfFromUri
import kotlinx.coroutines.launch
import kotlin.math.roundToInt

/** Web reference: `CompressTool.tsx` + `compressPdf` (`qpdf-engine.ts`). No options beyond the file and password `ToolScaffold` already provides. */
@Composable
fun CompressScreen() {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    var pickedUri by remember { mutableStateOf<Uri?>(null) }
    var password by remember { mutableStateOf("") }
    var running by remember { mutableStateOf(false) }
    var resultMessage by remember { mutableStateOf<String?>(null) }
    var pendingBytes by remember { mutableStateOf<ByteArray?>(null) }
    var pendingSuccessMessage by remember { mutableStateOf("") }

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
                resultMessage = pendingSuccessMessage
            }
        }
        pendingBytes = null
    }

    ToolScaffold(
        title = "Compress PDF",
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
                val baseName = suggestedBaseName(uri)

                scope.launch {
                    when (val result = loadPdfFromUri(context, uri, password.ifBlank { null })) {
                        is PdfLoadResult.Success -> {
                            val originalSize = readBytesFromUri(context, uri).size
                            val compressed = compressPdf(result.document)
                            result.document.close()
                            pendingBytes = compressed
                            pendingSuccessMessage = compressionMessage(originalSize, compressed.size)
                            saveLauncher.launch("${baseName}_compressed.pdf")
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
        runLabel = if (running) "Compressing..." else "Compress PDF",
        resultMessage = resultMessage,
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
