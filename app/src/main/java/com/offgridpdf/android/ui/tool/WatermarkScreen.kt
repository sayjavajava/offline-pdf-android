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
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.KeyboardType
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
import com.offgridpdf.android.pdf.WatermarkColor
import com.offgridpdf.android.pdf.WatermarkOptions
import com.offgridpdf.android.pdf.addWatermark
import com.offgridpdf.android.ui.common.UriListSaver
import com.offgridpdf.android.ui.theme.LocalOffGridPalette
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

    var pickedFiles by rememberSaveable(stateSaver = UriListSaver) { mutableStateOf(PendingFile.consume()?.let { listOf(it) } ?: emptyList<Uri>()) }
    // Plain `remember`, deliberately: a document password is never written
    // to saved instance state (see `ui/common/Savers.kt`).
    var password by remember { mutableStateOf("") }
    var text by rememberSaveable { mutableStateOf("CONFIDENTIAL") }
    var fontSizeText by rememberSaveable { mutableStateOf("50") }
    var opacityText by rememberSaveable { mutableStateOf("0.5") }
    var rotationText by rememberSaveable { mutableStateOf("45") }
    // The chosen preset's index, not the colour itself: an Int is something a
    // Bundle can hold, a colour data class is not, and every colour the UI can
    // pick comes from COLOR_PRESETS anyway.
    var colorIndex by rememberSaveable { mutableStateOf(0) }
    val color = COLOR_PRESETS[colorIndex].color
    var tile by rememberSaveable { mutableStateOf(false) }
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
                savedFile = null
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
        savedFile = savedFile,
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
                COLOR_PRESETS.forEachIndexed { index, preset ->
                    if (index == colorIndex) {
                        Button(onClick = { colorIndex = index }) { Text(preset.label) }
                    } else {
                        OutlinedButton(onClick = { colorIndex = index }) { Text(preset.label) }
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
