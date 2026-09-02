package com.offgridpdf.android.ui.tool

import com.offgridpdf.android.chain.PendingFile

import com.offgridpdf.android.ui.theme.LocalOffGridPalette

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
import com.offgridpdf.android.files.batchResultMessage
import com.offgridpdf.android.files.rememberCreateDocumentLauncher
import com.offgridpdf.android.files.rememberOpenMultipleDocumentsLauncher
import com.offgridpdf.android.files.runOnEachPdf
import com.offgridpdf.android.files.suggestedBaseName
import com.offgridpdf.android.files.saveResult
import com.offgridpdf.android.pdf.WatermarkColor
import com.offgridpdf.android.pdf.WatermarkOptions
import com.offgridpdf.android.pdf.addWatermark
import kotlinx.coroutines.launch

private data class ColorPreset(val label: String, val color: WatermarkColor)

private val COLOR_PRESETS = listOf(
    ColorPreset("Red", WatermarkColor(1f, 0f, 0f)),
    ColorPreset("Black", WatermarkColor(0f, 0f, 0f)),
    ColorPreset("Blue", WatermarkColor(0f, 0f, 1f)),
    ColorPreset("Gray", WatermarkColor(0.5f, 0.5f, 0.5f)),
)

/**
 * Web reference: `AddWatermarkTool.tsx` + `addWatermark`/`WatermarkOptions`
 * (`pdf-ops.ts`).
 *
 * Batch mode (`files/BatchRun.kt`): the same text/font/opacity/rotation/
 * color/tile settings apply to every picked file — there's nothing
 * per-document about a watermark — so more than one file just zips the
 * results.
 */
@Composable
fun WatermarkScreen() {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    var pickedFiles by remember { mutableStateOf(PendingFile.consume()?.let { listOf(it) } ?: emptyList<Uri>()) }
    var password by remember { mutableStateOf("") }
    var text by remember { mutableStateOf("CONFIDENTIAL") }
    var fontSizeText by remember { mutableStateOf("50") }
    var opacityText by remember { mutableStateOf("0.5") }
    var rotationText by remember { mutableStateOf("45") }
    var color by remember { mutableStateOf(COLOR_PRESETS[0].color) }
    var tile by remember { mutableStateOf(false) }
    var running by remember { mutableStateOf(false) }
    var resultMessage by remember { mutableStateOf<String?>(null) }
    var pendingBytes by remember { mutableStateOf<ByteArray?>(null) }
    var pendingSuccessMessage by remember { mutableStateOf("") }
    var lastResultBytes by remember { mutableStateOf<ByteArray?>(null) }

    val pickLauncher = rememberOpenMultipleDocumentsLauncher { uris ->
        pickedFiles = uris
        password = ""
        resultMessage = null
    }

    val savePdfLauncher = rememberCreateDocumentLauncher("application/pdf") { uri ->
        val bytes = pendingBytes
        if (uri != null && bytes != null) {
            scope.launch {
                resultMessage = saveResult(context, uri, bytes, pendingSuccessMessage)
            }
        }
        pendingBytes = null
    }

    val saveZipLauncher = rememberCreateDocumentLauncher("application/zip") { uri ->
        val bytes = pendingBytes
        if (uri != null && bytes != null) {
            scope.launch {
                resultMessage = saveResult(context, uri, bytes, pendingSuccessMessage)
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
        title = "Add Watermark",
        accent = accent,
        pickedFileName = fileName,
        onPickFile = { pickLauncher.launch(arrayOf("application/pdf")) },
        password = password,
        onPasswordChange = { password = it },
        runEnabled = pickedFiles.isNotEmpty(),
        running = running,
        onRun = {
            val fontSize = fontSizeText.toFloatOrNull()
            val opacity = opacityText.toFloatOrNull()
            val rotation = rotationText.toFloatOrNull()
            if (fontSize == null || opacity == null || rotation == null) {
                resultMessage = "Font size, opacity, and rotation must all be numbers."
            } else {
                running = true
                resultMessage = null
                lastResultBytes = null
                val files = pickedFiles
                val options = WatermarkOptions(fontSize, color, opacity, rotation, tile)

                scope.launch {
                    val result = runOnEachPdf(
                        context = context,
                        files = files,
                        password = password,
                        zipEntrySuffix = "_watermarked",
                        operate = { document -> addWatermark(document, text, options) },
                    )
                    when {
                        result.singleBytes != null -> {
                            pendingBytes = result.singleBytes
                            lastResultBytes = result.singleBytes
                            pendingSuccessMessage = "Watermark added to your PDF."
                            savePdfLauncher.launch("${suggestedBaseName(files[0])}_watermarked.pdf")
                        }
                        result.zipBytes != null -> {
                            pendingBytes = result.zipBytes
                            pendingSuccessMessage = batchResultMessage("Watermarked", result)
                            saveZipLauncher.launch("watermarked_pdfs.zip")
                        }
                        else -> {
                            resultMessage = result.failures.firstOrNull() ?: "Could not add a watermark to this PDF."
                        }
                    }
                    running = false
                }
            }
        },
        runLabel = when {
            running -> "Adding Watermark..."
            pickedFiles.size > 1 -> "Watermark ${pickedFiles.size} Files"
            else -> "Add Watermark"
        },
        resultMessage = resultMessage,
        chainableBytes = lastResultBytes,
        batchNote = if (pickedFiles.size > 1) {
            "Watermark will run with the same settings on all ${pickedFiles.size} files, saved as one zip."
        } else {
            null
        },
        options = {
            OutlinedTextField(
                value = text,
                onValueChange = { text = it },
                label = { Text("Watermark Text") },
                modifier = Modifier.fillMaxWidth(),
            )
            OutlinedTextField(
                value = fontSizeText,
                onValueChange = { fontSizeText = it },
                label = { Text("Font Size (1–300)") },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                modifier = Modifier.fillMaxWidth(),
            )
            OutlinedTextField(
                value = opacityText,
                onValueChange = { opacityText = it },
                label = { Text("Opacity (0–1)") },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
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
                value = rotationText,
                onValueChange = { rotationText = it },
                label = { Text("Rotation (degrees, -360 to 360)") },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                modifier = Modifier.fillMaxWidth(),
            )
            Row {
                Checkbox(checked = tile, onCheckedChange = { tile = it })
                Text("Repeat across the whole page")
            }
        },
    )
}
