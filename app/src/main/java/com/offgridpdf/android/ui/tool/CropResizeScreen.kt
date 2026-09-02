package com.offgridpdf.android.ui.tool

import android.net.Uri
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.MaterialTheme
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
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.offgridpdf.android.chain.PendingFile
import com.offgridpdf.android.files.SavedFile
import com.offgridpdf.android.files.TOO_LARGE_MESSAGE
import com.offgridpdf.android.files.rememberCreateDocumentLauncher
import com.offgridpdf.android.files.rememberOpenDocumentLauncher
import com.offgridpdf.android.files.saveResult
import com.offgridpdf.android.files.savedFileOrNull
import com.offgridpdf.android.files.suggestedBaseName
import com.offgridpdf.android.pdf.CropMargins
import com.offgridpdf.android.pdf.PAPER_SIZES
import com.offgridpdf.android.pdf.PREVIEW_SCALE
import com.offgridpdf.android.pdf.PaperSize
import com.offgridpdf.android.pdf.PdfLoadResult
import com.offgridpdf.android.pdf.PdfRect
import com.offgridpdf.android.pdf.cropPdf
import com.offgridpdf.android.pdf.loadPdfFromUri
import com.offgridpdf.android.pdf.renderPageForPreview
import com.offgridpdf.android.pdf.resizePdf
import com.offgridpdf.android.pdf.resolvePageIndices
import com.offgridpdf.android.ui.common.NullableUriSaver
import com.offgridpdf.android.ui.common.PageOverlay
import com.offgridpdf.android.ui.common.PageOverlayStyle
import com.offgridpdf.android.ui.common.PagePreview
import com.offgridpdf.android.ui.common.userMessageFor
import com.offgridpdf.android.ui.theme.LocalOffGridPalette
import kotlin.math.roundToInt
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

    var pickedUri by rememberSaveable(stateSaver = NullableUriSaver) { mutableStateOf(PendingFile.consume()) }
    // Plain `remember`, deliberately: a document password is never written
    // to saved instance state (see `ui/common/Savers.kt`).
    var password by remember { mutableStateOf("") }
    var mode by rememberSaveable { mutableStateOf(Mode.CROP) }
    var pagesText by rememberSaveable { mutableStateOf("") }

    // Crop state.
    var topText by rememberSaveable { mutableStateOf("0") }
    var bottomText by rememberSaveable { mutableStateOf("0") }
    var leftText by rememberSaveable { mutableStateOf("0") }
    var rightText by rememberSaveable { mutableStateOf("0") }

    // Resize state.
    var paperOption by rememberSaveable { mutableStateOf("A4") }
    var customWidthText by rememberSaveable { mutableStateOf("595.28") }
    var customHeightText by rememberSaveable { mutableStateOf("841.89") }
    var stretch by rememberSaveable { mutableStateOf(false) }

    // Crop preview. Unlike Redact and Signature this screen holds no open
    // PDDocument: it only needs one page's pixels and that page's size, so
    // the document is opened, rendered and closed in one go and nothing has
    // to be kept alive or cleaned up afterwards.
    var previewImage by remember { mutableStateOf<ImageBitmap?>(null) }
    var previewBitmapWidth by remember { mutableStateOf(0) }
    var previewBitmapHeight by remember { mutableStateOf(0) }
    // The CropBox, which is both what the bitmap shows and what cropPdf
    // trims -- see RenderedPagePreview's note on the two boxes.
    var previewWidthPts by remember { mutableStateOf(0f) }
    var previewHeightPts by remember { mutableStateOf(0f) }
    var previewPageNumber by remember { mutableStateOf(1) }
    var previewMessage by remember { mutableStateOf<String?>(null) }
    var previewLoading by remember { mutableStateOf(false) }

    var running by remember { mutableStateOf(false) }
    var resultMessage by rememberSaveable { mutableStateOf<String?>(null) }
    // The file this run produced, once it is really on disk. Not
    // saveable: it holds the bytes, and a Bundle caps out around 1 MB.
    var savedFile by remember { mutableStateOf<SavedFile?>(null) }
    var pendingBytes by remember { mutableStateOf<ByteArray?>(null) }
    var lastResultBytes by remember { mutableStateOf<ByteArray?>(null) }

    val pickLauncher = rememberOpenDocumentLauncher { uri ->
        pickedUri = uri
        password = ""
        resultMessage = null
        savedFile = null
        previewImage = null
        previewMessage = null
    }

    val saveLauncher = rememberCreateDocumentLauncher("application/pdf") { uri ->
        val bytes = pendingBytes
        if (uri != null && bytes != null) {
            scope.launch {
                val outcome = saveResult(context, uri, bytes, if (mode == Mode.CROP) "Pages cropped." else "Pages resized.")
                resultMessage = outcome.message
                savedFile = outcome.savedFileOrNull
            }
        }
        pendingBytes = null
    }

    // Opens the document, renders the first page the crop would touch, and
    // closes it again. Deliberately on demand rather than on file pick: this
    // tool works perfectly well without a preview, and rendering a page is
    // the most expensive thing it can do.
    fun loadPreview() {
        val uri = pickedUri ?: return
        previewLoading = true
        previewMessage = null
        scope.launch {
            when (val result = loadPdfFromUri(context, uri, password.ifBlank { null })) {
                is PdfLoadResult.Success -> {
                    try {
                        // The first page the range actually covers, so the
                        // preview shows a page that will really be cropped.
                        val index = firstTargetPageIndex(pagesText, result.document.numberOfPages)
                        val rendered = withContext(Dispatchers.Default) {
                            renderPageForPreview(result.document, index)
                        }
                        previewImage = rendered.bitmap.asImageBitmap()
                        previewBitmapWidth = rendered.bitmapWidth
                        previewBitmapHeight = rendered.bitmapHeight
                        previewWidthPts = rendered.renderedWidthPts
                        previewHeightPts = rendered.renderedHeightPts
                        previewPageNumber = index + 1
                    } catch (e: Exception) {
                        previewImage = null
                        previewMessage = userMessageFor(e)
                    } catch (e: OutOfMemoryError) {
                        previewImage = null
                        previewMessage = TOO_LARGE_MESSAGE
                    } finally {
                        result.document.close()
                    }
                }
                PdfLoadResult.PasswordRequired -> {
                    previewMessage = if (password.isBlank()) {
                        "This PDF needs a password."
                    } else {
                        "Wrong password -- try again."
                    }
                }
                is PdfLoadResult.Failure -> previewMessage = result.message
            }
            previewLoading = false
        }
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
                    savedFile = null
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
                    savedFile = null
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
        savedFile = savedFile,
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

                val image = previewImage
                if (image == null) {
                    OutlinedButton(
                        onClick = { loadPreview() },
                        enabled = pickedUri != null && !previewLoading,
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Text(if (previewLoading) "Rendering page..." else "Preview the crop")
                    }
                } else {
                    val kept = keptRegionOf(
                        topText,
                        bottomText,
                        leftText,
                        rightText,
                        previewWidthPts,
                        previewHeightPts,
                    )
                    Text(
                        if (kept != null) {
                            "Page $previewPageNumber. The outline is what the crop keeps: " +
                                "${kept.width.roundToInt()} x ${kept.height.roundToInt()} pt " +
                                "of ${previewWidthPts.roundToInt()} x ${previewHeightPts.roundToInt()} pt."
                        } else {
                            "Page $previewPageNumber. These margins leave nothing of the page."
                        },
                        style = MaterialTheme.typography.bodySmall,
                        color = LocalOffGridPalette.current.inkTertiary,
                    )
                    PagePreview(
                        image = image,
                        bitmapWidth = previewBitmapWidth,
                        bitmapHeight = previewBitmapHeight,
                        pageHeightPts = previewHeightPts,
                        contentDescription = "Page $previewPageNumber, with the crop boundary marked",
                        scale = PREVIEW_SCALE,
                        // Outlined: a crop only moves the visible window, so
                        // the content outside it still exists in the file.
                        // Covering it would tell the wrong story about what
                        // this tool does.
                        overlays = kept?.let {
                            listOf(PageOverlay(it, PageOverlayStyle.Outlined(accent)))
                        }.orEmpty(),
                    )
                    OutlinedButton(
                        onClick = { loadPreview() },
                        enabled = !previewLoading,
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Text(if (previewLoading) "Rendering page..." else "Refresh preview")
                    }
                }
                previewMessage?.let { Text(it) }
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

/**
 * The first page a crop with this range would touch, as a 0-based index.
 *
 * A preview of page 1 is misleading when the range starts at page 12, so the
 * preview follows the range. A range that does not parse falls back to the
 * first page rather than refusing to preview: the range field is validated
 * at apply time, and someone half-way through typing one still deserves to
 * see a page.
 *
 * Uses [resolvePageIndices] rather than `PdfCropResize.kt`'s own private
 * resolver. The two differ only in a `.distinct()` on the result, which
 * cannot change the first element, so both name the same first page for
 * every input -- checked rather than assumed, since a preview of a page the
 * crop will not touch would be worse than no preview.
 */
private fun firstTargetPageIndex(pagesText: String, pageCount: Int): Int {
    val range = pagesText.ifBlank { "all" }
    return runCatching { resolvePageIndices(range, pageCount).firstOrNull() }
        .getOrNull()
        ?: 0
}

/**
 * What the crop keeps, in PDF points, or null when these margins leave
 * nothing of the page.
 *
 * Works in CropBox space, the same space `cropPdf` trims and the same space
 * the rendered preview shows. Returns null rather than throwing for the same
 * reason the signature tool's equivalent does: a field mid-edit is normal,
 * and the honest response is to draw no outline until the numbers mean
 * something. `cropPdf` still validates properly at apply time, per page,
 * with its own message.
 */
internal fun keptRegionOf(
    topText: String,
    bottomText: String,
    leftText: String,
    rightText: String,
    pageWidthPts: Float,
    pageHeightPts: Float,
): PdfRect? {
    val top = topText.toFloatOrNull() ?: return null
    val bottom = bottomText.toFloatOrNull() ?: return null
    val left = leftText.toFloatOrNull() ?: return null
    val right = rightText.toFloatOrNull() ?: return null
    if (listOf(top, bottom, left, right).any { it < 0f }) return null

    val width = pageWidthPts - left - right
    val height = pageHeightPts - top - bottom
    if (width <= 0f || height <= 0f) return null

    // Bottom-left origin: the left margin is the new x, and the *bottom*
    // margin is the new y. Using the top margin here would flip the crop
    // vertically on any page with uneven top/bottom trims.
    return PdfRect(x = left, y = bottom, width = width, height = height)
}
