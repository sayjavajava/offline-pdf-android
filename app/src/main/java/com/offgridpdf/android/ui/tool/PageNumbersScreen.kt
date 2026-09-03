package com.offgridpdf.android.ui.tool

import android.net.Uri
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.runtime.Composable
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.KeyboardType
import com.offgridpdf.android.chain.ChainOrigin
import com.offgridpdf.android.chain.PendingFile
import com.offgridpdf.android.files.SavedFile
import com.offgridpdf.android.files.batchResultMessage
import com.offgridpdf.android.files.rememberCreateDocumentLauncher
import com.offgridpdf.android.files.rememberOpenMultipleDocumentsLauncher
import com.offgridpdf.android.files.runOnEachPdf
import com.offgridpdf.android.files.saveResult
import com.offgridpdf.android.files.savedFileOrNull
import com.offgridpdf.android.files.suggestedBaseName
import com.offgridpdf.android.pdf.PageNumberColor
import com.offgridpdf.android.pdf.PageNumberFormat
import com.offgridpdf.android.pdf.PageNumberOptions
import com.offgridpdf.android.pdf.PageNumberPosition
import com.offgridpdf.android.pdf.addPageNumbers
import com.offgridpdf.android.pdf.formatPageNumber
import com.offgridpdf.android.ui.common.OptionChip
import com.offgridpdf.android.ui.common.OptionChipRow
import com.offgridpdf.android.ui.common.SectionLabel
import com.offgridpdf.android.ui.common.ToolBodyText
import com.offgridpdf.android.ui.common.ToolTextField
import com.offgridpdf.android.ui.common.UriListSaver
import com.offgridpdf.android.ui.common.rememberDisplayNames
import com.offgridpdf.android.ui.theme.LocalOffGridPalette
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

    var pickedFiles by rememberSaveable(stateSaver = UriListSaver) { mutableStateOf(PendingFile.consume()?.let { listOf(it) } ?: emptyList<Uri>()) }
    var inheritedChainOrigin by rememberSaveable { mutableStateOf(ChainOrigin.consume()) }
    // Plain `remember`, deliberately: a document password is never written
    // to saved instance state (see `ui/common/Savers.kt`).
    var password by remember { mutableStateOf("") }
    var format by rememberSaveable { mutableStateOf(PageNumberFormat.N) }
    var startText by rememberSaveable { mutableStateOf("1") }
    var prefix by rememberSaveable { mutableStateOf("") }
    var digitsText by rememberSaveable { mutableStateOf("6") }
    var position by rememberSaveable { mutableStateOf(PageNumberPosition.BOTTOM_CENTER) }
    var fontSizeText by rememberSaveable { mutableStateOf("12") }
    var marginText by rememberSaveable { mutableStateOf("36") }
    // The chosen preset's index, not the colour itself: an Int is something a
    // Bundle can hold, a colour data class is not, and every colour the UI can
    // pick comes from COLOR_PRESETS anyway.
    var colorIndex by rememberSaveable { mutableStateOf(0) }
    val color = COLOR_PRESETS[colorIndex].color
    var pagesText by rememberSaveable { mutableStateOf("") }
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
    var chainOriginBaseName by remember { mutableStateOf("") }
    var chainedFileName by remember { mutableStateOf("") }

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
        savedFile = null
        inheritedChainOrigin = null
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
    // Kept separate from pickedFiles.size in the labels above/below: those
    // read the count synchronously off the Uri list, but a name needs an
    // async provider query (see rememberDisplayNames), so it can lag one
    // frame behind a fresh pick -- fine for this label, wrong for a count.
    val pickedFileNames = rememberDisplayNames(pickedFiles)
    val fileName = when {
        pickedFiles.isEmpty() -> null
        pickedFileNames.size == 1 -> pickedFileNames[0]
        pickedFiles.size == 1 -> null
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
                savedFile = null
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
                            val baseName = suggestedBaseName(context, files[0])
                            val originBaseName = inheritedChainOrigin ?: baseName
                            chainOriginBaseName = originBaseName
                            chainedFileName = "${originBaseName}_numbered.pdf"
                            pendingSuccessMessage = "Page numbers added to your PDF."
                            savePdfLauncher.launch("${baseName}_numbered.pdf")
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
        savedFile = savedFile,
        chainableBytes = lastResultBytes,
        chainOriginBaseName = chainOriginBaseName,
        chainedFileName = chainedFileName,
        batchNote = if (pickedFiles.size > 1) {
            "Page numbers will run with the same settings on all ${pickedFiles.size} files, saved as one zip."
        } else {
            null
        },
        options = {
            SectionLabel("Format")
            OptionChipRow {
                for (option in FORMATS) {
                    OptionChip(
                        label = option.label,
                        selected = option.format == format,
                        accent = accent,
                        onClick = { format = option.format },
                    )
                }
            }
            ToolTextField(
                value = prefix,
                onValueChange = { prefix = it },
                label = "Prefix (optional)",
                accent = accent,
                placeholder = "e.g. ABC-",
            )
            ToolBodyText("First stamp will read: $preview")
            ToolTextField(
                value = startText,
                onValueChange = { startText = it },
                label = "Start at",
                accent = accent,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
            )
            if (format == PageNumberFormat.BATES) {
                ToolTextField(
                    value = digitsText,
                    onValueChange = { digitsText = it },
                    label = "Digits (1–20)",
                    accent = accent,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                )
            }
            SectionLabel("Position")
            OptionChipRow {
                for (option in POSITIONS) {
                    OptionChip(
                        label = option.label,
                        selected = option.position == position,
                        accent = accent,
                        onClick = { position = option.position },
                    )
                }
            }
            ToolTextField(
                value = fontSizeText,
                onValueChange = { fontSizeText = it },
                label = "Font Size (1–300)",
                accent = accent,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
            )
            ToolTextField(
                value = marginText,
                onValueChange = { marginText = it },
                label = "Margin (points, 0–300)",
                accent = accent,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
            )
            SectionLabel("Colour")
            OptionChipRow {
                COLOR_PRESETS.forEachIndexed { index, preset ->
                    OptionChip(
                        label = preset.label,
                        selected = index == colorIndex,
                        accent = accent,
                        onClick = { colorIndex = index },
                    )
                }
            }
            ToolTextField(
                value = pagesText,
                onValueChange = { pagesText = it },
                label = "Pages to number (blank = all)",
                accent = accent,
                placeholder = "e.g. 2-10 — or leave blank for all",
            )
        },
    )
}
