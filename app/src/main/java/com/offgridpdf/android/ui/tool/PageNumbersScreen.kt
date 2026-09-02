package com.offgridpdf.android.ui.tool

import com.offgridpdf.android.chain.PendingFile

import com.offgridpdf.android.ui.theme.LocalOffGridPalette

import android.net.Uri
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Button
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.offgridpdf.android.files.batchResultMessage
import com.offgridpdf.android.files.rememberCreateDocumentLauncher
import com.offgridpdf.android.files.rememberOpenMultipleDocumentsLauncher
import com.offgridpdf.android.files.runOnEachPdf
import com.offgridpdf.android.files.suggestedBaseName
import com.offgridpdf.android.files.writeBytesToUri
import com.offgridpdf.android.pdf.PageNumberColor
import com.offgridpdf.android.pdf.PageNumberFormat
import com.offgridpdf.android.pdf.PageNumberOptions
import com.offgridpdf.android.pdf.PageNumberPosition
import com.offgridpdf.android.pdf.addPageNumbers
import com.offgridpdf.android.pdf.formatPageNumber
import kotlinx.coroutines.launch

private data class FormatOption(val label: String, val format: PageNumberFormat)

private val FORMATS = listOf(
    FormatOption("Page number (1, 2, 3...)", PageNumberFormat.N),
    FormatOption("Page x of y", PageNumberFormat.N_OF_TOTAL),
    FormatOption("Bates (zero-padded)", PageNumberFormat.BATES),
)

private data class PositionOption(val label: String, val position: PageNumberPosition)

private val POSITIONS = listOf(
    PositionOption("Bottom centre", PageNumberPosition.BOTTOM_CENTER),
    PositionOption("Bottom left", PageNumberPosition.BOTTOM_LEFT),
    PositionOption("Bottom right", PageNumberPosition.BOTTOM_RIGHT),
    PositionOption("Top centre", PageNumberPosition.TOP_CENTER),
    PositionOption("Top left", PageNumberPosition.TOP_LEFT),
    PositionOption("Top right", PageNumberPosition.TOP_RIGHT),
)

private data class PageNumberColorPreset(val label: String, val color: PageNumberColor)

private val COLOR_PRESETS = listOf(
    PageNumberColorPreset("Black", PageNumberColor(0f, 0f, 0f)),
    PageNumberColorPreset("Red", PageNumberColor(1f, 0f, 0f)),
    PageNumberColorPreset("Blue", PageNumberColor(0f, 0f, 1f)),
    PageNumberColorPreset("Gray", PageNumberColor(0.5f, 0.5f, 0.5f)),
)

/**
 * Web reference: `PageNumbersTool.tsx` + `addPageNumbers`/`formatPageNumber`
 * (`pdf-ops.ts`).
 *
 * Batch mode (`files/BatchRun.kt`): the same format/position/style settings
 * apply to every picked file, so more than one file just zips the results.
 */
@Composable
fun PageNumbersScreen() {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    var pickedFiles by remember { mutableStateOf(PendingFile.consume()?.let { listOf(it) } ?: emptyList<Uri>()) }
    var password by remember { mutableStateOf("") }
    var format by remember { mutableStateOf(PageNumberFormat.N) }
    var startText by remember { mutableStateOf("1") }
    var prefix by remember { mutableStateOf("") }
    var digitsText by remember { mutableStateOf("6") }
    var position by remember { mutableStateOf(PageNumberPosition.BOTTOM_CENTER) }
    var fontSizeText by remember { mutableStateOf("12") }
    var marginText by remember { mutableStateOf("36") }
    var color by remember { mutableStateOf(COLOR_PRESETS[0].color) }
    var pagesText by remember { mutableStateOf("") }
    var running by remember { mutableStateOf(false) }
    var resultMessage by remember { mutableStateOf<String?>(null) }
    var pendingBytes by remember { mutableStateOf<ByteArray?>(null) }
    var pendingSuccessMessage by remember { mutableStateOf("") }
    var lastResultBytes by remember { mutableStateOf<ByteArray?>(null) }

    // Live preview of the first stamp, matching the web tool's own hint.
    val preview by remember {
        derivedStateOf {
            val start = startText.toIntOrNull() ?: 1
            val digits = digitsText.toIntOrNull() ?: 6
            formatPageNumber(start, 10, format, prefix, digits)
        }
    }

    val pickLauncher = rememberOpenMultipleDocumentsLauncher { uris ->
        pickedFiles = uris
        password = ""
        resultMessage = null
    }

    val savePdfLauncher = rememberCreateDocumentLauncher("application/pdf") { uri ->
        val bytes = pendingBytes
        if (uri != null && bytes != null) {
            scope.launch {
                writeBytesToUri(context, uri, bytes)
                resultMessage = pendingSuccessMessage
            }
        }
        pendingBytes = null
    }

    val saveZipLauncher = rememberCreateDocumentLauncher("application/zip") { uri ->
        val bytes = pendingBytes
        if (uri != null && bytes != null) {
            scope.launch {
                writeBytesToUri(context, uri, bytes)
                resultMessage = pendingSuccessMessage
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
        title = "Add Page Numbers",
        accent = accent,
        pickedFileName = fileName,
        onPickFile = { pickLauncher.launch(arrayOf("application/pdf")) },
        password = password,
        onPasswordChange = { password = it },
        runEnabled = pickedFiles.isNotEmpty(),
        running = running,
        onRun = {
            val start = startText.toIntOrNull()
            val digits = digitsText.toIntOrNull()
            val fontSize = fontSizeText.toFloatOrNull()
            val margin = marginText.toFloatOrNull()
            if (start == null || digits == null || fontSize == null || margin == null) {
                resultMessage = "Start, digits, font size, and margin must all be numbers."
            } else {
                running = true
                resultMessage = null
                lastResultBytes = null
                val files = pickedFiles
                val options = PageNumberOptions(
                    format = format,
                    start = start,
                    prefix = prefix,
                    digits = digits,
                    position = position,
                    fontSize = fontSize,
                    margin = margin,
                    color = color,
                    pages = pagesText.ifBlank { "all" },
                )

                scope.launch {
                    val result = runOnEachPdf(
                        context = context,
                        files = files,
                        password = password,
                        zipEntrySuffix = "_numbered",
                        operate = { document -> addPageNumbers(document, options) },
                    )
                    when {
                        result.singleBytes != null -> {
                            pendingBytes = result.singleBytes
                            lastResultBytes = result.singleBytes
                            pendingSuccessMessage = "Page numbers added to your PDF."
                            savePdfLauncher.launch("${suggestedBaseName(files[0])}_numbered.pdf")
                        }
                        result.zipBytes != null -> {
                            pendingBytes = result.zipBytes
                            pendingSuccessMessage = batchResultMessage("Numbered", result)
                            saveZipLauncher.launch("numbered_pdfs.zip")
                        }
                        else -> {
                            resultMessage = result.failures.firstOrNull() ?: "Could not add page numbers to this PDF."
                        }
                    }
                    running = false
                }
            }
        },
        runLabel = when {
            running -> "Adding Page Numbers..."
            pickedFiles.size > 1 -> "Number ${pickedFiles.size} Files"
            else -> "Add Page Numbers"
        },
        resultMessage = resultMessage,
        chainableBytes = lastResultBytes,
        batchNote = if (pickedFiles.size > 1) {
            "Page numbers will run with the same settings on all ${pickedFiles.size} files, saved as one zip."
        } else {
            null
        },
        options = {
            Text("Format")
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                for (option in FORMATS) {
                    if (option.format == format) {
                        Button(onClick = { format = option.format }) { Text(option.label) }
                    } else {
                        OutlinedButton(onClick = { format = option.format }) { Text(option.label) }
                    }
                }
            }
            OutlinedTextField(
                value = prefix,
                onValueChange = { prefix = it },
                label = { Text("Prefix (optional)") },
                placeholder = { Text("e.g. ABC-") },
                modifier = Modifier.fillMaxWidth(),
            )
            Text("First stamp will read: $preview")
            OutlinedTextField(
                value = startText,
                onValueChange = { startText = it },
                label = { Text("Start at") },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                modifier = Modifier.fillMaxWidth(),
            )
            if (format == PageNumberFormat.BATES) {
                OutlinedTextField(
                    value = digitsText,
                    onValueChange = { digitsText = it },
                    label = { Text("Digits (1–20)") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    modifier = Modifier.fillMaxWidth(),
                )
            }
            Text("Position")
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                for (option in POSITIONS) {
                    if (option.position == position) {
                        Button(onClick = { position = option.position }) { Text(option.label) }
                    } else {
                        OutlinedButton(onClick = { position = option.position }) { Text(option.label) }
                    }
                }
            }
            OutlinedTextField(
                value = fontSizeText,
                onValueChange = { fontSizeText = it },
                label = { Text("Font Size (1–300)") },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                modifier = Modifier.fillMaxWidth(),
            )
            OutlinedTextField(
                value = marginText,
                onValueChange = { marginText = it },
                label = { Text("Margin (points, 0–300)") },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                modifier = Modifier.fillMaxWidth(),
            )
            Text("Colour")
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                for (preset in COLOR_PRESETS) {
                    if (preset.color == color) {
                        Button(onClick = { color = preset.color }) { Text(preset.label) }
                    } else {
                        OutlinedButton(onClick = { color = preset.color }) { Text(preset.label) }
                    }
                }
            }
            OutlinedTextField(
                value = pagesText,
                onValueChange = { pagesText = it },
                label = { Text("Pages to number (blank = all)") },
                placeholder = { Text("e.g. 2-10 — or leave blank for all") },
                modifier = Modifier.fillMaxWidth(),
            )
        },
    )
}
