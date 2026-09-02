package com.offgridpdf.android.ui.tool

import android.net.Uri
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.OutlinedButton
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
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.offgridpdf.android.chain.PendingFile
import com.offgridpdf.android.files.TOO_LARGE_MESSAGE
import com.offgridpdf.android.files.rememberCreateDocumentLauncher
import com.offgridpdf.android.files.rememberOpenDocumentLauncher
import com.offgridpdf.android.files.saveResult
import com.offgridpdf.android.files.suggestedBaseName
import com.offgridpdf.android.pdf.CropMargins
import com.offgridpdf.android.pdf.PAPER_SIZES
import com.offgridpdf.android.pdf.PaperSize
import com.offgridpdf.android.pdf.PdfLoadResult
import com.offgridpdf.android.pdf.cropPdf
import com.offgridpdf.android.pdf.loadPdfFromUri
import com.offgridpdf.android.pdf.resizePdf
import com.offgridpdf.android.ui.common.userMessageFor
import com.offgridpdf.android.ui.theme.LocalOffGridPalette
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

private enum class Mode { CROP, RESIZE }

private const val CUSTOM_PAPER_OPTION = "Custom"

/** Web reference: `CropResizeTool.tsx` + `cropPdf`/`resizePdf`/`PAPER_SIZES` (`pdf-ops.ts`). */
@Composable
fun CropResizeScreen() {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    var pickedUri by remember { mutableStateOf(PendingFile.consume()) }
    var password by remember { mutableStateOf("") }
    var mode by remember { mutableStateOf(Mode.CROP) }
    var pagesText by remember { mutableStateOf("") }

    // Crop state.
    var topText by remember { mutableStateOf("0") }
    var bottomText by remember { mutableStateOf("0") }
    var leftText by remember { mutableStateOf("0") }
    var rightText by remember { mutableStateOf("0") }

    // Resize state.
    var paperOption by remember { mutableStateOf("A4") }
    var customWidthText by remember { mutableStateOf("595.28") }
    var customHeightText by remember { mutableStateOf("841.89") }
    var stretch by remember { mutableStateOf(false) }

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
                resultMessage = saveResult(context, uri, bytes, if (mode == Mode.CROP) "Pages cropped." else "Pages resized.")
            }
        }
        pendingBytes = null
    }

    val accent = LocalOffGridPalette.current.organize
    ToolScaffold(
        title = "Crop / Resize Pages",
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
                val pageRange = pagesText.ifBlank { "all" }

                if (mode == Mode.CROP) {
                    val top = topText.toFloatOrNull()
                    val bottom = bottomText.toFloatOrNull()
                    val left = leftText.toFloatOrNull()
                    val right = rightText.toFloatOrNull()
                    if (top == null || bottom == null || left == null || right == null) {
                        resultMessage = "All four margins must be numbers."
                        return@let
                    }
                    running = true
                    resultMessage = null
                    val baseName = suggestedBaseName(uri)
                    val margins = CropMargins(top, bottom, left, right)

                    scope.launch {
                        when (val result = loadPdfFromUri(context, uri, password.ifBlank { null })) {
                            is PdfLoadResult.Success -> {
                                try {
                                    pendingBytes = withContext(Dispatchers.Default) {
                                        cropPdf(result.document, margins, pageRange)
                                    }
                                    lastResultBytes = pendingBytes
                                    saveLauncher.launch("${baseName}_cropped.pdf")
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
                            is PdfLoadResult.Failure -> resultMessage = result.message
                        }
                        running = false
                    }
                } else {
                    val target = if (paperOption == CUSTOM_PAPER_OPTION) {
                        val width = customWidthText.toFloatOrNull()
                        val height = customHeightText.toFloatOrNull()
                        if (width == null || height == null) {
                            resultMessage = "Custom width and height must be numbers."
                            return@let
                        }
                        PaperSize(width, height)
                    } else {
                        PAPER_SIZES.getValue(paperOption)
                    }
                    running = true
                    resultMessage = null
                    val baseName = suggestedBaseName(uri)

                    scope.launch {
                        when (val result = loadPdfFromUri(context, uri, password.ifBlank { null })) {
                            is PdfLoadResult.Success -> {
                                try {
                                    pendingBytes = withContext(Dispatchers.Default) {
                                        resizePdf(result.document, target, pageRange, stretch)
                                    }
                                    lastResultBytes = pendingBytes
                                    saveLauncher.launch("${baseName}_resized.pdf")
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
                            is PdfLoadResult.Failure -> resultMessage = result.message
                        }
                        running = false
                    }
                }
            }
        },
        runLabel = when {
            running && mode == Mode.CROP -> "Cropping..."
            running -> "Resizing..."
            mode == Mode.CROP -> "Crop Pages"
            else -> "Resize Pages"
        },
        resultMessage = resultMessage,
        chainableBytes = lastResultBytes,
        options = {
            Text(
                "Crop trims margins non-destructively — the underlying content is untouched, only " +
                    "the visible window shrinks. Resize actually rescales content and page size " +
                    "together; it defaults to scale-to-fit so nothing is distorted.",
            )
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                if (mode == Mode.CROP) {
                    Button(onClick = { mode = Mode.CROP }) { Text("Crop") }
                } else {
                    OutlinedButton(onClick = { mode = Mode.CROP }) { Text("Crop") }
                }
                if (mode == Mode.RESIZE) {
                    Button(onClick = { mode = Mode.RESIZE }) { Text("Resize") }
                } else {
                    OutlinedButton(onClick = { mode = Mode.RESIZE }) { Text("Resize") }
                }
            }

            if (mode == Mode.CROP) {
                OutlinedTextField(
                    value = topText,
                    onValueChange = { topText = it },
                    label = { Text("Top margin (pt)") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    modifier = Modifier.fillMaxWidth(),
                )
                OutlinedTextField(
                    value = bottomText,
                    onValueChange = { bottomText = it },
                    label = { Text("Bottom margin (pt)") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    modifier = Modifier.fillMaxWidth(),
                )
                OutlinedTextField(
                    value = leftText,
                    onValueChange = { leftText = it },
                    label = { Text("Left margin (pt)") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    modifier = Modifier.fillMaxWidth(),
                )
                OutlinedTextField(
                    value = rightText,
                    onValueChange = { rightText = it },
                    label = { Text("Right margin (pt)") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    modifier = Modifier.fillMaxWidth(),
                )
            } else {
                Text("Target page size")
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    for (name in PAPER_SIZES.keys + CUSTOM_PAPER_OPTION) {
                        if (paperOption == name) {
                            Button(onClick = { paperOption = name }) { Text(name) }
                        } else {
                            OutlinedButton(onClick = { paperOption = name }) { Text(name) }
                        }
                    }
                }
                if (paperOption == CUSTOM_PAPER_OPTION) {
                    OutlinedTextField(
                        value = customWidthText,
                        onValueChange = { customWidthText = it },
                        label = { Text("Width (pt)") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        modifier = Modifier.fillMaxWidth(),
                    )
                    OutlinedTextField(
                        value = customHeightText,
                        onValueChange = { customHeightText = it },
                        label = { Text("Height (pt)") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
                Row {
                    Checkbox(checked = stretch, onCheckedChange = { stretch = it })
                    Text("Stretch to fill exactly (distorts proportions)")
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
