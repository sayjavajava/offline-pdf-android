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
import com.offgridpdf.android.files.TOO_LARGE_MESSAGE
import com.offgridpdf.android.files.readBytesFromUri
import com.offgridpdf.android.files.rememberCreateDocumentLauncher
import com.offgridpdf.android.files.rememberOpenMultipleDocumentsLauncher
import com.offgridpdf.android.files.saveResult
import com.offgridpdf.android.pdf.ImageFile
import com.offgridpdf.android.pdf.imagesToPdf
import com.offgridpdf.android.ui.common.ScreenTopBar
import com.offgridpdf.android.ui.common.userMessageFor
import com.offgridpdf.android.ui.theme.LocalOffGridPalette
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Web reference: `ConvertTool.tsx` (the image half only — DOCX conversion
 * is A-25, gated separately) + `convertImageToPdf`/`detectImageFormat`
 * (`pdf-ops.ts`).
 *
 * Deliberately **not** built on `ToolScaffold`, same reasoning as
 * `MergeScreen.kt`: multi-file, no password field, and the web tool has
 * no such field for this either.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ImagesToPdfScreen() {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    var files by remember { mutableStateOf<List<Uri>>(emptyList()) }
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
            val success = if (files.size > 1) {
                "Combined ${files.size} images into one PDF."
            } else {
                "Your image has been converted to PDF."
            }
            scope.launch { resultMessage = saveResult(context, uri, bytes, success) }
        }
        pendingBytes = null
    }

    Scaffold(
        topBar = { ScreenTopBar(title = "Convert Images to PDF") },
        containerColor = LocalOffGridPalette.current.paper,
    ) { innerPadding ->
        Column(
            modifier = Modifier.fillMaxSize().padding(innerPadding).padding(16.dp),
        ) {
            Text(
                "Convert JPEG or PNG images to PDF — select several to combine them " +
                    "into one multi-page PDF, in the order shown.",
            )

            Button(
                onClick = { pickLauncher.launch(arrayOf("image/jpeg", "image/png")) },
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text(if (files.isEmpty()) "Choose image(s)" else "${files.size} image(s) selected")
            }

            if (files.isNotEmpty()) {
                Text(files.joinToString("\n") { it.lastPathSegment ?: it.toString() })
            }

            Button(
                onClick = {
                    if (files.isEmpty()) {
                        resultMessage = "Select at least one image."
                    } else {
                        running = true
                        resultMessage = null
                        val toConvert = files
                        scope.launch {
                            try {
                                val images = toConvert.map { uri ->
                                    val name = uri.lastPathSegment ?: uri.toString()
                                    ImageFile(name, readBytesFromUri(context, uri))
                                }
                                pendingBytes = withContext(Dispatchers.Default) { imagesToPdf(images) }
                                saveLauncher.launch(
                                    if (toConvert.size > 1) "combined.pdf" else "converted.pdf",
                                )
                            } catch (e: Exception) {
                                resultMessage = userMessageFor(e)
                            } catch (e: OutOfMemoryError) {
                                resultMessage = TOO_LARGE_MESSAGE
                            }
                            running = false
                        }
                    }
                },
                enabled = !running,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text(if (running) "Converting..." else "Convert to PDF")
            }

            if (running) {
                CircularProgressIndicator()
            }

            resultMessage?.let { Text(it) }
        }
    }
}
