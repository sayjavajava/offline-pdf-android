package com.offgridpdf.android.ui.tool

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
import com.offgridpdf.android.files.rememberCreateDocumentLauncher
import com.offgridpdf.android.files.rememberOpenDocumentLauncher
import com.offgridpdf.android.files.suggestedBaseName
import com.offgridpdf.android.files.writeBytesToUri
import com.offgridpdf.android.pdf.PdfLoadResult
import com.offgridpdf.android.pdf.WatermarkColor
import com.offgridpdf.android.pdf.WatermarkOptions
import com.offgridpdf.android.pdf.addWatermark
import com.offgridpdf.android.pdf.loadPdfFromUri
import kotlinx.coroutines.launch

private data class ColorPreset(val label: String, val color: WatermarkColor)

private val COLOR_PRESETS = listOf(
    ColorPreset("Red", WatermarkColor(1f, 0f, 0f)),
    ColorPreset("Black", WatermarkColor(0f, 0f, 0f)),
    ColorPreset("Blue", WatermarkColor(0f, 0f, 1f)),
    ColorPreset("Gray", WatermarkColor(0.5f, 0.5f, 0.5f)),
)

/** Web reference: `AddWatermarkTool.tsx` + `addWatermark`/`WatermarkOptions` (`pdf-ops.ts`). */
@Composable
fun WatermarkScreen() {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    var pickedUri by remember { mutableStateOf<Uri?>(null) }
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
                resultMessage = "Watermark added to your PDF."
            }
        }
        pendingBytes = null
    }

    val accent = LocalOffGridPalette.current.edit
    ToolScaffold(
        title = "Add Watermark",
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
                val fontSize = fontSizeText.toFloatOrNull()
                val opacity = opacityText.toFloatOrNull()
                val rotation = rotationText.toFloatOrNull()
                if (fontSize == null || opacity == null || rotation == null) {
                    resultMessage = "Font size, opacity, and rotation must all be numbers."
                } else {
                    running = true
                    resultMessage = null
                    val baseName = suggestedBaseName(uri)
                    val options = WatermarkOptions(fontSize, color, opacity, rotation, tile)

                    scope.launch {
                        when (val result = loadPdfFromUri(context, uri, password.ifBlank { null })) {
                            is PdfLoadResult.Success -> {
                                try {
                                    pendingBytes = addWatermark(result.document, text, options)
                                    saveLauncher.launch("${baseName}_watermarked.pdf")
                                } catch (e: IllegalArgumentException) {
                                    resultMessage = e.message
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
        runLabel = if (running) "Adding Watermark..." else "Add Watermark",
        resultMessage = resultMessage,
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
