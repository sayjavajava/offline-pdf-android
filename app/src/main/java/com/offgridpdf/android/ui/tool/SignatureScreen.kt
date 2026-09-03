package com.offgridpdf.android.ui.tool

import android.net.Uri
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.offgridpdf.android.chain.PendingFile
import com.offgridpdf.android.files.SavedFile
import com.offgridpdf.android.files.TOO_LARGE_MESSAGE
import com.offgridpdf.android.files.readBytesFromUri
import com.offgridpdf.android.files.rememberCreateDocumentLauncher
import com.offgridpdf.android.files.rememberOpenDocumentLauncher
import com.offgridpdf.android.files.saveResult
import com.offgridpdf.android.files.savedFileOrNull
import com.offgridpdf.android.files.suggestedBaseName
import com.offgridpdf.android.pdf.PREVIEW_SCALE
import com.offgridpdf.android.pdf.PdfLoadResult
import com.offgridpdf.android.pdf.PdfRect
import com.offgridpdf.android.pdf.SignaturePlacement
import com.offgridpdf.android.pdf.addSignature
import com.offgridpdf.android.pdf.loadPdfFromUri
import com.offgridpdf.android.pdf.renderPageForPreview
import com.offgridpdf.android.ui.common.NullableUriSaver
import com.offgridpdf.android.ui.common.PageOverlay
import com.offgridpdf.android.ui.common.PageOverlayStyle
import com.offgridpdf.android.ui.common.PagePreview
import com.offgridpdf.android.ui.common.FilePickerCard
import com.offgridpdf.android.ui.common.OptionChip
import com.offgridpdf.android.ui.common.OptionChipRow
import com.offgridpdf.android.ui.common.PrimaryButton
import com.offgridpdf.android.ui.common.PrivacyLine
import com.offgridpdf.android.ui.common.RunningIndicator
import com.offgridpdf.android.ui.common.SecondaryButton
import com.offgridpdf.android.ui.common.SectionLabel
import com.offgridpdf.android.ui.common.ToolBodyText
import com.offgridpdf.android.ui.common.ToolCompletion
import com.offgridpdf.android.ui.common.ToolScreenScaffold
import com.offgridpdf.android.ui.common.ToolTextField
import com.offgridpdf.android.ui.common.rememberDisplayName
import com.offgridpdf.android.ui.common.userMessageFor
import com.offgridpdf.android.ui.theme.LocalOffGridPalette
import com.tom_roush.pdfbox.pdmodel.PDDocument
import kotlin.math.roundToInt
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

private enum class SignatureMode { TYPE, DRAW, UPLOAD }

/**
 * Web reference: `SignatureTool.tsx` + `addSignature`/`placeSignatureImage`
 * (`pdf-ops.ts`/`pdf-signature.ts`).
 *
 * Placement is a rect dragged onto a rendered preview of the page, with
 * the x/y/width/height fields kept alongside it and bound both ways:
 * dragging rewrites them, typing moves the rect. This tool originally
 * shipped with the numeric fields alone, deliberately, because the
 * project had no page rendering at the time (`ANDROID_IMPLEMENTATION_PLAN.md`'s
 * Spike A) and the plan said not to block the tool on it. Spike A has
 * since landed and Redact's preview machinery is now shared
 * (`ui/common/PagePreview.kt`), so the live preview it promised is here.
 *
 * The fields stay rather than being replaced: a signature often has to
 * land at an exact offset a finger cannot hit, and that was the one thing
 * the old UI was genuinely good at.
 */
@Composable
fun SignatureScreen() {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    // Signature is an EditEnhance tool (see PdfTool.kt), so it takes that
    // category's accent -- same convention as every other tool screen.
    val accent = LocalOffGridPalette.current.edit

    var pickedUri by rememberSaveable(stateSaver = NullableUriSaver) { mutableStateOf(PendingFile.consume()) }
    // Plain `remember`, deliberately: a document password is never written
    // to saved instance state (see `ui/common/Savers.kt`).
    var password by remember { mutableStateOf("") }
    var openDocument by remember { mutableStateOf<PDDocument?>(null) }
    // Both describe a document that is open in `openDocument`, which cannot be
    // saved. A restored non-null pageCount would hide the Load button and
    // claim a document is ready when none is.
    var pageCount by remember { mutableStateOf<Int?>(null) }
    var loadError by remember { mutableStateOf<String?>(null) }

    // The rendered page behind the placement rect. Same cluster as
    // openDocument above, so likewise not saveable.
    var previewImage by remember { mutableStateOf<ImageBitmap?>(null) }
    var previewBitmapWidth by remember { mutableStateOf(0) }
    var previewBitmapHeight by remember { mutableStateOf(0) }
    var previewPageHeightPts by remember { mutableStateOf(0f) }
    var previewMessage by remember { mutableStateOf<String?>(null) }
    var renderingPreview by remember { mutableStateOf(false) }

    var mode by rememberSaveable { mutableStateOf(SignatureMode.TYPE) }
    var typedName by rememberSaveable { mutableStateOf("") }
    var strokes by remember { mutableStateOf<List<List<Offset>>>(emptyList()) }
    var currentStroke by remember { mutableStateOf<List<Offset>>(emptyList()) }
    var signatureBytes by remember { mutableStateOf<ByteArray?>(null) }

    var pageText by rememberSaveable { mutableStateOf("1") }
    var xText by rememberSaveable { mutableStateOf("36") }
    var yText by rememberSaveable { mutableStateOf("36") }
    var widthText by rememberSaveable { mutableStateOf("150") }
    var heightText by rememberSaveable { mutableStateOf("50") }

    var running by remember { mutableStateOf(false) }
    var resultMessage by rememberSaveable { mutableStateOf<String?>(null) }
    // The file this run produced, once it is really on disk. Not
    // saveable: it holds the bytes, and a Bundle caps out around 1 MB.
    var savedFile by remember { mutableStateOf<SavedFile?>(null) }
    var pendingBytes by remember { mutableStateOf<ByteArray?>(null) }

    val pickLauncher = rememberOpenDocumentLauncher { uri ->
        pickedUri = uri
        password = ""
        openDocument?.close()
        openDocument = null
        pageCount = null
        loadError = null
        resultMessage = null
        savedFile = null
    }

    val uploadLauncher = rememberOpenDocumentLauncher { uri ->
        if (uri != null) {
            scope.launch {
                signatureBytes = readBytesFromUri(context, uri)
            }
        }
    }

    val saveLauncher = rememberCreateDocumentLauncher("application/pdf") { uri ->
        val bytes = pendingBytes
        if (uri != null && bytes != null) {
            scope.launch {
                val outcome = saveResult(context, uri, bytes, "Signature added.")
                resultMessage = outcome.message
                savedFile = outcome.savedFileOrNull
            }
        }
        pendingBytes = null
    }

    fun loadDocument() {
        val uri = pickedUri ?: return
        scope.launch {
            when (val result = loadPdfFromUri(context, uri, password.ifBlank { null })) {
                is PdfLoadResult.Success -> {
                    openDocument?.close()
                    openDocument = result.document
                    pageCount = result.document.numberOfPages
                    loadError = null
                }
                PdfLoadResult.PasswordRequired -> {
                    loadError = if (password.isBlank()) "This PDF needs a password." else "Wrong password — try again."
                }
                is PdfLoadResult.Failure -> loadError = result.message
            }
        }
    }

    // This screen keeps a PDDocument open for the whole placement session
    // rather than only inside one button press, so it needs explicit
    // cleanup -- otherwise navigating away mid-placement leaks it. Same
    // reasoning as RedactScreen's.
    DisposableEffect(Unit) {
        onDispose { openDocument?.close() }
    }

    // Re-renders whenever the loaded document or the chosen page changes.
    // Keyed on pageText rather than driven from the field's onValueChange so
    // that loading a document and typing a page number both land here, and
    // so that only the newest render survives: typing "12" passes through
    // "1", and LaunchedEffect cancels the page-1 render when the key changes
    // rather than letting it finish last and win.
    LaunchedEffect(openDocument, pageText) {
        val doc = openDocument
        val page = pageText.toIntOrNull()
        if (doc == null || page == null || page < 1 || page > doc.numberOfPages) {
            previewImage = null
            previewMessage = null
            return@LaunchedEffect
        }
        renderingPreview = true
        try {
            val rendered = withContext(Dispatchers.Default) {
                renderPageForPreview(doc, page - 1)
            }
            previewImage = rendered.bitmap.asImageBitmap()
            previewBitmapWidth = rendered.bitmapWidth
            previewBitmapHeight = rendered.bitmapHeight
            previewPageHeightPts = rendered.pageHeightPts
            previewMessage = null
        } catch (e: CancellationException) {
            // A page turn cancels this render; that is the mechanism working,
            // not a failure to report. Rethrow so the coroutine unwinds
            // normally instead of falling into the error branch below.
            throw e
        } catch (e: Exception) {
            previewImage = null
            previewMessage = userMessageFor(e)
        } catch (e: OutOfMemoryError) {
            previewImage = null
            previewMessage = TOO_LARGE_MESSAGE
        } finally {
            // finally, not after the catches: a page turn cancels this
            // coroutine mid-render, and without this the spinner from the
            // abandoned render would never clear.
            renderingPreview = false
        }
    }

    ToolScreenScaffold(
        title = "Add Signature",
        // Dragging *on* the preview places the signature rather than
        // scrolling, since the preview consumes the gesture -- same trade
        // RedactScreen's preview already makes; scroll from anywhere else.
        bottomBar = {
            if (running) {
                RunningIndicator(accent = accent)
            }
            resultMessage?.let { message ->
                ToolCompletion(message = message, savedFile = savedFile, accent = accent)
            }
            PrivacyLine()
            PrimaryButton(
                text = if (running) "Adding signature..." else "Add Signature & Download",
                accent = accent,
                enabled = !running,
                onClick = {
                    val document = openDocument
                    val uri = pickedUri
                    val bytes = signatureBytes
                    if (document == null || uri == null) {
                        resultMessage = "Load a PDF first."
                        return@PrimaryButton
                    }
                    if (bytes == null) {
                        resultMessage = "Type, draw, or upload a signature first."
                        return@PrimaryButton
                    }
                    val page = pageText.toIntOrNull()
                    val x = xText.toFloatOrNull()
                    val y = yText.toFloatOrNull()
                    val width = widthText.toFloatOrNull()
                    val height = heightText.toFloatOrNull()
                    if (page == null || x == null || y == null || width == null || height == null) {
                        resultMessage = "Page, X, Y, width, and height must all be numbers."
                        return@PrimaryButton
                    }

                    running = true
                    resultMessage = null
                    savedFile = null
                    scope.launch {
                        val baseName = suggestedBaseName(context, uri)
                        try {
                            pendingBytes = withContext(Dispatchers.Default) {
                                addSignature(
                                    document,
                                    bytes,
                                    "signature.png",
                                    SignaturePlacement(page, x, y, width, height),
                                )
                            }
                            saveLauncher.launch("${baseName}_signed.pdf")
                        } catch (e: Exception) {
                            resultMessage = userMessageFor(e)
                        } catch (e: OutOfMemoryError) {
                            resultMessage = TOO_LARGE_MESSAGE
                        }
                        running = false
                    }
                },
            )
        },
    ) {
        FilePickerCard(
            fileName = rememberDisplayName(pickedUri),
            onClick = { pickLauncher.launch(arrayOf("application/pdf")) },
        )

        ToolBodyText(
            "Stamp a visual signature — typed, drawn, or an uploaded image — onto a page. " +
                "This is a visual mark, not a cryptographic digital signature.",
        )
        ToolTextField(
            value = password,
            onValueChange = { password = it },
            label = "Password (if encrypted)",
            accent = accent,
        )
        if (pickedUri != null && pageCount == null) {
            SecondaryButton(
                text = "Load PDF",
                onClick = { loadDocument() },
                accent = accent,
                modifier = Modifier.fillMaxWidth(),
            )
        }
        pageCount?.let { count -> ToolBodyText("This PDF has $count page(s).") }
        loadError?.let { ToolBodyText(it) }

        SectionLabel("Signature")
        OptionChipRow {
            for ((label, candidate) in listOf("Type" to SignatureMode.TYPE, "Draw" to SignatureMode.DRAW, "Upload" to SignatureMode.UPLOAD)) {
                OptionChip(
                    label = label,
                    selected = mode == candidate,
                    accent = accent,
                    onClick = { mode = candidate },
                )
            }
        }

        when (mode) {
            SignatureMode.TYPE -> {
                ToolTextField(
                    value = typedName,
                    onValueChange = { typedName = it },
                    label = "Your name",
                    accent = accent,
                )
                SecondaryButton(
                    text = "Use this signature",
                    onClick = { signatureBytes = renderTypedSignature(typedName) },
                    accent = accent,
                )
            }
            SignatureMode.DRAW -> {
                Canvas(
                    modifier = Modifier
                        .size(320.dp, 120.dp)
                        .background(Color.White)
                        .border(1.dp, Color.Gray)
                        .pointerInput(Unit) {
                            detectDragGestures(
                                onDragStart = { offset -> currentStroke = listOf(offset) },
                                onDrag = { change, _ -> currentStroke = currentStroke + change.position },
                                onDragEnd = {
                                    strokes = strokes + listOf(currentStroke)
                                    currentStroke = emptyList()
                                },
                            )
                        },
                ) {
                    for (stroke in strokes + listOf(currentStroke)) {
                        for (i in 0 until stroke.size - 1) {
                            drawLine(Color.Black, stroke[i], stroke[i + 1], strokeWidth = 4f)
                        }
                    }
                }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    SecondaryButton(
                        text = "Clear",
                        onClick = { strokes = emptyList(); currentStroke = emptyList() },
                        accent = accent,
                    )
                    SecondaryButton(
                        text = "Use this signature",
                        onClick = { signatureBytes = renderDrawnSignature(strokes) },
                        accent = accent,
                    )
                }
            }
            SignatureMode.UPLOAD -> {
                SecondaryButton(
                    text = "Choose signature image",
                    onClick = { uploadLauncher.launch(arrayOf("image/png", "image/jpeg")) },
                    accent = accent,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        }

        if (signatureBytes != null) {
            ToolBodyText("Signature ready.")
        }

        SectionLabel("Placement")

        val placementRect = placementRectOf(xText, yText, widthText, heightText)
        val image = previewImage
        if (image != null) {
            Text(
                "Drag on the page to place your signature, or type exact " +
                    "coordinates below.",
                style = MaterialTheme.typography.bodySmall,
                color = LocalOffGridPalette.current.inkTertiary,
            )
            PagePreview(
                image = image,
                bitmapWidth = previewBitmapWidth,
                bitmapHeight = previewBitmapHeight,
                pageHeightPts = previewPageHeightPts,
                contentDescription = if (placementRect != null) {
                    "Page $pageText, with the signature's placement marked"
                } else {
                    "Page $pageText"
                },
                scale = PREVIEW_SCALE,
                // Outlined, not filled: the whole point is to see what the
                // signature will sit on top of.
                overlays = placementRect?.let {
                    listOf(PageOverlay(it, PageOverlayStyle.Outlined(accent)))
                }.orEmpty(),
                onRectDragged = { rect ->
                    // Whole points. A finger on a preview rendered at
                    // PREVIEW_SCALE cannot resolve better than about a
                    // point anyway, and round numbers are what someone
                    // then nudges by hand in the fields below.
                    xText = rect.x.roundToInt().toString()
                    yText = rect.y.roundToInt().toString()
                    widthText = rect.width.roundToInt().toString()
                    heightText = rect.height.roundToInt().toString()
                },
                dragIndicatorColor = accent,
                minDraggedSizePts = MIN_PLACEMENT_PT,
            )
        }
        if (renderingPreview) {
            Text(
                "Rendering page...",
                style = MaterialTheme.typography.bodySmall,
                color = LocalOffGridPalette.current.inkTertiary,
            )
        }
        previewMessage?.let { ToolBodyText(it) }

        Text(
            "Coordinates are in points from the page's bottom-left corner.",
            style = MaterialTheme.typography.bodySmall,
            color = LocalOffGridPalette.current.inkTertiary,
        )
        ToolTextField(
            value = pageText,
            onValueChange = { pageText = it },
            label = "Page number",
            accent = accent,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
        )
        ToolTextField(
            value = xText,
            onValueChange = { xText = it },
            label = "X",
            accent = accent,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
        )
        ToolTextField(
            value = yText,
            onValueChange = { yText = it },
            label = "Y",
            accent = accent,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
        )
        ToolTextField(
            value = widthText,
            onValueChange = { widthText = it },
            label = "Width",
            accent = accent,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
        )
        ToolTextField(
            value = heightText,
            onValueChange = { heightText = it },
            label = "Height",
            accent = accent,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
        )

    }
}
/**
 * Ignore an accidental tap on the preview. Points, so it is a real size on
 * the page rather than a number of screen pixels that would mean something
 * different on every device.
 */
private const val MIN_PLACEMENT_PT = 8f

/**
 * The placement fields as a rect to draw, or null when they do not yet
 * describe one.
 *
 * A half-typed field ("3" mid-way to "36", or an empty box) is normal while
 * someone is editing, so this returns null rather than treating it as an
 * error -- the outline simply disappears until the numbers make sense again.
 * A non-positive width or height is rejected for the same reason it is at
 * apply time: it is not a rect.
 *
 * `internal` rather than private so it can be unit-tested directly: it is
 * the only real logic on this screen that does not need a composition.
 */
internal fun placementRectOf(
    xText: String,
    yText: String,
    widthText: String,
    heightText: String,
): PdfRect? {
    val x = xText.toFloatOrNull() ?: return null
    val y = yText.toFloatOrNull() ?: return null
    val width = widthText.toFloatOrNull() ?: return null
    val height = heightText.toFloatOrNull() ?: return null
    if (width <= 0f || height <= 0f) return null
    return PdfRect(x, y, width, height)
}
