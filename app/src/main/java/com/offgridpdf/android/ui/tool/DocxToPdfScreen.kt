package com.offgridpdf.android.ui.tool

import android.net.Uri
import android.util.Xml
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
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.offgridpdf.android.files.TOO_LARGE_MESSAGE
import com.offgridpdf.android.files.readBytesFromUri
import com.offgridpdf.android.files.rememberCreateDocumentLauncher
import com.offgridpdf.android.files.rememberOpenDocumentLauncher
import com.offgridpdf.android.files.saveResult
import com.offgridpdf.android.files.suggestedBaseName
import com.offgridpdf.android.pdf.convertDocxToPdf
import com.offgridpdf.android.ui.common.NullableUriSaver
import com.offgridpdf.android.ui.common.ScreenTopBar
import com.offgridpdf.android.ui.common.userMessageFor
import com.offgridpdf.android.ui.theme.LocalOffGridPalette
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Web reference: `ConvertTool.tsx` (the DOCX half — image conversion is
 * A-12) + `convertDocxToPdf`/`layoutHtmlToPdf` (`docx-convert.ts`,
 * `docx-layout.ts`). Deliberately its own tool rather than merged into
 * A-12's `ImagesToPdfScreen`: A-12's own plan entry gated DOCX support
 * out from the start ("the image half only — DOCX is A-25, gated
 * separately"), and this reuses A-12's established single/multi-picker
 * screen pattern rather than the web's single combined tool.
 *
 * No password field (unlike `ToolScaffold`'s tools): DOCX files aren't
 * encrypted the way PDFs are, so there's no such concept here.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DocxToPdfScreen() {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    var pickedUri by rememberSaveable(stateSaver = NullableUriSaver) { mutableStateOf<Uri?>(null) }
    var running by remember { mutableStateOf(false) }
    var resultMessage by rememberSaveable { mutableStateOf<String?>(null) }
    var pendingBytes by remember { mutableStateOf<ByteArray?>(null) }
    // Paired with pendingBytes, which a Bundle cannot hold — so neither is
    // saved (see `ui/common/Savers.kt`).
    var pendingSuccessMessage by remember { mutableStateOf("") }

    val pickLauncher = rememberOpenDocumentLauncher { uri ->
        pickedUri = uri
        resultMessage = null
    }

    val saveLauncher = rememberCreateDocumentLauncher("application/pdf") { uri ->
        val bytes = pendingBytes
        if (uri != null && bytes != null) {
            scope.launch {
                resultMessage = saveResult(context, uri, bytes, pendingSuccessMessage)
            }
        }
        pendingBytes = null
    }

    Scaffold(
        topBar = { ScreenTopBar(title = "Convert DOCX to PDF") },
        containerColor = LocalOffGridPalette.current.paper,
    ) { innerPadding ->
        Column(
            modifier = Modifier.fillMaxSize().padding(innerPadding).padding(16.dp),
        ) {
            Text(
                "Convert a Word document to a real, text-based PDF — selectable and " +
                    "searchable, not a picture of the page. Headings, paragraphs, and " +
                    "bold/italic text are supported. Tables and images aren't yet, and are " +
                    "reported rather than silently dropped; list items still convert, just " +
                    "without their bullet or number.",
            )

            Button(
                onClick = {
                    pickLauncher.launch(
                        arrayOf("application/vnd.openxmlformats-officedocument.wordprocessingml.document"),
                    )
                },
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text(pickedUri?.lastPathSegment ?: "Choose a DOCX file")
            }

            Button(
                onClick = {
                    val uri = pickedUri
                    if (uri == null) {
                        resultMessage = "Select a DOCX file first."
                    } else {
                        running = true
                        resultMessage = null
                        val baseName = suggestedBaseName(uri)
                        scope.launch {
                            try {
                                val docxBytes = readBytesFromUri(context, uri)
                                val result = withContext(Dispatchers.Default) {
                                    convertDocxToPdf(docxBytes, Xml.newPullParser())
                                }
                                pendingBytes = result.bytes
                                pendingSuccessMessage = if (result.warnings.isEmpty()) {
                                    "Converted to PDF."
                                } else {
                                    "Converted to PDF. " + result.warnings.joinToString(" ")
                                }
                                saveLauncher.launch("${baseName}.pdf")
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
