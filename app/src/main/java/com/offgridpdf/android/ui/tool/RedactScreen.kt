package com.offgridpdf.android.ui.tool

import android.net.Uri
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import com.offgridpdf.android.files.rememberCreateDocumentLauncher
import com.offgridpdf.android.files.rememberOpenDocumentLauncher
import com.offgridpdf.android.files.suggestedBaseName
import com.offgridpdf.android.files.writeBytesToUri
import com.offgridpdf.android.pdf.ApplyToRangeResult
import com.offgridpdf.android.pdf.PdfLoadResult
import com.offgridpdf.android.pdf.PixelPoint
import com.offgridpdf.android.pdf.RedactionRect
import com.offgridpdf.android.pdf.applyBoxesToRange
import com.offgridpdf.android.pdf.loadPdfFromUri
import com.offgridpdf.android.pdf.pixelToPdfRect
import com.offgridpdf.android.pdf.redactPdf
import com.offgridpdf.android.pdf.resolvePageIndices
import com.offgridpdf.android.pdf.toPixelRect
import com.tom_roush.pdfbox.pdmodel.PDDocument
import com.tom_roush.pdfbox.rendering.PDFRenderer
import kotlin.math.abs
import kotlinx.coroutines.launch

/** Pixels per PDF point for the on-screen preview render. Independent of the export scale in `PdfRedact.kt` — boxes are converted to point-space immediately on drawing, so this only affects preview legibility. Web reference: `PREVIEW_SCALE` (`RedactTool.tsx`). */
private const val PREVIEW_SCALE = 1.5f

/** Ignore accidental clicks/taps, same as `RedactTool.tsx`'s own `finishDrag` threshold. */
private const val MIN_BOX_PT = 4f / PREVIEW_SCALE

/**
 * Web reference: `RedactTool.tsx` + `redactPdf`/`toPixelRect` (`pdf-redact.ts`).
 * Hand-drawn boxes only (A-19) — the web version's Find (F-24) is a
 * separate, later item (A-21, `ANDROID_IMPLEMENTATION_PLAN.md`), gated on
 * Spike D's own per-character text-position work, which this tool does
 * not need.
 *
 * Unlike every other tool screen, this one keeps a loaded `PDDocument`
 * open across the whole editing session (pick → draw boxes across
 * several pages → apply-to-range → Apply & Download) rather than loading
 * and closing it within a single button press — genuinely necessary here
 * since drawing and page navigation both need the same open document.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RedactScreen() {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    var pickedUri by remember { mutableStateOf<Uri?>(null) }
    var password by remember { mutableStateOf("") }
    var document by remember { mutableStateOf<PDDocument?>(null) }
    var pageCount by remember { mutableStateOf(0) }
    var pageIndex by remember { mutableStateOf(0) }
    var loading by remember { mutableStateOf(false) }
    var loadMessage by remember { mutableStateOf<String?>(null) }

    var previewBitmapWidth by remember { mutableStateOf(0) }
    var previewBitmapHeight by remember { mutableStateOf(0) }
    var previewImage by remember { mutableStateOf<ImageBitmap?>(null) }
    var previewHeightPts by remember { mutableStateOf(0f) }
    var displaySize by remember { mutableStateOf<IntSize?>(null) }

    var redactions by remember { mutableStateOf<Map<Int, List<RedactionRect>>>(emptyMap()) }
    var dragStart by remember { mutableStateOf<Offset?>(null) }
    var dragCurrent by remember { mutableStateOf<Offset?>(null) }

    var applyRangeText by remember { mutableStateOf("") }
    var applying by remember { mutableStateOf(false) }
    var resultMessage by remember { mutableStateOf<String?>(null) }

    var pendingBytes by remember { mutableStateOf<ByteArray?>(null) }

    // This screen (unlike every other tool screen) keeps a PDDocument
    // open for the whole editing session, not just within one button
    // press, so it needs its own explicit cleanup on leaving composition
    // -- otherwise navigating away mid-edit leaks it.
    DisposableEffect(Unit) {
        onDispose { document?.close() }
    }

    val pageNumber = pageIndex + 1
    val currentPageBoxes = redactions[pageNumber].orEmpty()
    val totalBoxes = redactions.values.sumOf { it.size }
    val pagesWithBoxes = redactions.values.count { it.isNotEmpty() }

    fun renderCurrentPage(doc: PDDocument, index: Int) {
        val mediaBox = doc.getPage(index).mediaBox
        previewHeightPts = mediaBox.height
        val bitmap = PDFRenderer(doc).renderImageWithDPI(index, PREVIEW_SCALE * 72f)
        previewBitmapWidth = bitmap.width
        previewBitmapHeight = bitmap.height
        previewImage = bitmap.asImageBitmap()
    }

    val pickLauncher = rememberOpenDocumentLauncher { uri ->
        document?.close()
        document = null
        pickedUri = uri
        password = ""
        pageCount = 0
        pageIndex = 0
        redactions = emptyMap()
        applyRangeText = ""
        resultMessage = null
        loadMessage = null
        previewImage = null
    }

    val saveLauncher = rememberCreateDocumentLauncher("application/pdf") { uri ->
        val bytes = pendingBytes
        if (uri != null && bytes != null) {
            scope.launch {
                writeBytesToUri(context, uri, bytes)
                resultMessage = "Redacted $totalBoxes box${if (totalBoxes == 1) "" else "es"} across $pagesWithBoxes page${if (pagesWithBoxes == 1) "" else "s"}."
            }
        }
        pendingBytes = null
    }

    fun loadDocument() {
        val uri = pickedUri ?: return
        loading = true
        loadMessage = null
        scope.launch {
            when (val result = loadPdfFromUri(context, uri, password.ifBlank { null })) {
                is PdfLoadResult.Success -> {
                    document?.close()
                    document = result.document
                    pageCount = result.document.numberOfPages
                    pageIndex = 0
                    redactions = emptyMap()
                    try {
                        renderCurrentPage(result.document, 0)
                    } catch (e: Exception) {
                        loadMessage = "Loaded, but could not render page 1: ${e.message}"
                    }
                }
                PdfLoadResult.PasswordRequired -> {
                    loadMessage = if (password.isBlank()) "This PDF needs a password." else "Wrong password — try again."
                }
                is PdfLoadResult.Failure -> {
                    loadMessage = result.message
                }
            }
            loading = false
        }
    }

    fun goToPage(newIndex: Int) {
        val doc = document ?: return
        if (newIndex < 0 || newIndex >= pageCount) return
        pageIndex = newIndex
        dragStart = null
        dragCurrent = null
        try {
            renderCurrentPage(doc, newIndex)
        } catch (e: Exception) {
            loadMessage = "Could not render page ${newIndex + 1}: ${e.message}"
        }
    }

    Scaffold(topBar = { TopAppBar(title = { Text("Redact PDF") }) }) { innerPadding ->
        LazyColumn(
            modifier = Modifier.padding(innerPadding).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            item {
                Text(
                    "Draw boxes over content to permanently remove — this deletes the underlying text and " +
                        "image data, not just draws over it, so nothing under a box stays selectable, copyable, " +
                        "or searchable. Every page you redact loses its own text layer entirely, since it is " +
                        "rebuilt as a plain image; pages you leave untouched keep theirs.",
                )
            }
            item {
                Button(onClick = { pickLauncher.launch(arrayOf("application/pdf")) }, modifier = Modifier.fillMaxWidth()) {
                    Text(pickedUri?.lastPathSegment ?: "Choose a PDF")
                }
            }
            if (pickedUri != null && document == null) {
                item {
                    OutlinedTextField(
                        value = password,
                        onValueChange = { password = it },
                        label = { Text("Password (if encrypted)") },
                        visualTransformation = PasswordVisualTransformation(),
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
                item {
                    Button(onClick = { loadDocument() }, enabled = !loading, modifier = Modifier.fillMaxWidth()) {
                        Text(if (loading) "Loading..." else "Load")
                    }
                }
                loadMessage?.let { item { Text(it) } }
            }

            val doc = document
            val image = previewImage
            if (doc != null && image != null) {
                item {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedButton(onClick = { goToPage(pageIndex - 1) }, enabled = pageIndex > 0) { Text("Previous") }
                        Text(
                            "Page $pageNumber of $pageCount" +
                                if (currentPageBoxes.isNotEmpty()) " — ${currentPageBoxes.size} box${if (currentPageBoxes.size == 1) "" else "es"}" else "",
                        )
                        OutlinedButton(onClick = { goToPage(pageIndex + 1) }, enabled = pageIndex < pageCount - 1) { Text("Next") }
                    }
                }
                item {
                    val aspect = if (previewBitmapHeight > 0) previewBitmapWidth.toFloat() / previewBitmapHeight else 1f
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .aspectRatio(aspect)
                            .onSizeChanged { displaySize = it }
                            .pointerInput(pageIndex, image) {
                                detectDragGestures(
                                    onDragStart = { offset -> dragStart = offset; dragCurrent = offset },
                                    onDrag = { change, _ -> dragCurrent = change.position },
                                    onDragEnd = {
                                        val start = dragStart
                                        val current = dragCurrent
                                        val size = displaySize
                                        if (start != null && current != null && size != null && size.width > 0 && size.height > 0) {
                                            val ratioX = previewBitmapWidth.toFloat() / size.width
                                            val ratioY = previewBitmapHeight.toFloat() / size.height
                                            val a = PixelPoint(start.x * ratioX, start.y * ratioY)
                                            val b = PixelPoint(current.x * ratioX, current.y * ratioY)
                                            val rect = pixelToPdfRect(a, b, previewHeightPts, PREVIEW_SCALE)
                                            if (rect.width >= MIN_BOX_PT && rect.height >= MIN_BOX_PT) {
                                                redactions = redactions + (pageNumber to (redactions[pageNumber].orEmpty() + rect))
                                            }
                                        }
                                        dragStart = null
                                        dragCurrent = null
                                    },
                                )
                            },
                    ) {
                        Image(
                            bitmap = image,
                            contentDescription = "Page $pageNumber",
                            contentScale = ContentScale.FillBounds,
                            modifier = Modifier.fillMaxWidth(),
                        )
                        val size = displaySize
                        if (size != null && size.width > 0 && previewBitmapWidth > 0) {
                            val displayScale = size.width.toFloat() / previewBitmapWidth
                            Canvas(modifier = Modifier.fillMaxWidth().aspectRatio(aspect)) {
                                for (rect in currentPageBoxes) {
                                    val px = toPixelRect(rect, previewHeightPts, PREVIEW_SCALE)
                                    drawRect(
                                        color = Color(0x80DC2626),
                                        topLeft = Offset(px.x * displayScale, px.y * displayScale),
                                        size = Size(px.width * displayScale, px.height * displayScale),
                                    )
                                }
                                val start = dragStart
                                val current = dragCurrent
                                if (start != null && current != null) {
                                    val left = minOf(start.x, current.x)
                                    val top = minOf(start.y, current.y)
                                    drawRect(
                                        color = Color(0x4DDC2626),
                                        topLeft = Offset(left, top),
                                        size = Size(abs(current.x - start.x), abs(current.y - start.y)),
                                    )
                                }
                            }
                        }
                    }
                }
                if (currentPageBoxes.isNotEmpty()) {
                    items(currentPageBoxes.size) { i ->
                        val rect = currentPageBoxes[i]
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            Text("Box ${i + 1}: ${rect.width.toInt()}×${rect.height.toInt()} pt")
                            TextButton(onClick = {
                                redactions = redactions + (pageNumber to currentPageBoxes.filterIndexed { index, _ -> index != i })
                            }) { Text("Remove") }
                        }
                    }
                }
                if (currentPageBoxes.isNotEmpty()) {
                    item {
                        OutlinedTextField(
                            value = applyRangeText,
                            onValueChange = { applyRangeText = it },
                            label = { Text("Apply this page's box${if (currentPageBoxes.size == 1) "" else "es"} to other pages") },
                            placeholder = { Text("e.g. 2-50 — or leave blank for every other page") },
                            modifier = Modifier.fillMaxWidth(),
                        )
                    }
                    item {
                        OutlinedButton(
                            onClick = {
                                val targets = if (applyRangeText.isBlank() || applyRangeText.trim().equals("all", ignoreCase = true)) {
                                    (1..pageCount).toList()
                                } else {
                                    try {
                                        resolvePageIndices(applyRangeText, pageCount).map { it + 1 }
                                    } catch (e: IllegalArgumentException) {
                                        resultMessage = e.message
                                        emptyList()
                                    }
                                }
                                if (targets.isNotEmpty()) {
                                    val result: ApplyToRangeResult = applyBoxesToRange(doc, redactions, pageNumber, targets)
                                    redactions = result.redactions
                                    resultMessage = when {
                                        result.applied.isEmpty() -> "No pages in range match this page's size — nothing was copied."
                                        result.skipped.isEmpty() -> "Copied ${currentPageBoxes.size} box${if (currentPageBoxes.size == 1) "" else "es"} to ${result.applied.size} page${if (result.applied.size == 1) "" else "s"}."
                                        else -> "Copied to ${result.applied.size} page${if (result.applied.size == 1) "" else "s"}. Skipped ${result.skipped.size} with a different page size: ${result.skipped.take(8).joinToString(", ")}."
                                    }
                                }
                            },
                            modifier = Modifier.fillMaxWidth(),
                        ) { Text("Apply to Range") }
                    }
                }
            }

            item {
                Text(
                    if (totalBoxes == 0) {
                        "No redaction boxes yet — drag on the page above to draw one."
                    } else {
                        "$totalBoxes box${if (totalBoxes == 1) "" else "es"} across $pagesWithBoxes page${if (pagesWithBoxes == 1) "" else "s"} will be redacted."
                    },
                )
            }
            item {
                Button(
                    onClick = {
                        val doc = document ?: return@Button
                        applying = true
                        resultMessage = null
                        scope.launch {
                            try {
                                pendingBytes = redactPdf(doc, redactions)
                                val name = pickedUri?.let { suggestedBaseName(it) } ?: "document"
                                saveLauncher.launch("${name}_redacted.pdf")
                            } catch (e: IllegalArgumentException) {
                                resultMessage = e.message
                            } catch (e: Exception) {
                                resultMessage = e.message ?: "Could not redact this PDF."
                            }
                            applying = false
                        }
                    },
                    enabled = document != null && totalBoxes > 0 && !applying,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text(if (applying) "Redacting..." else "Apply Redactions & Download")
                }
            }
            if (applying) {
                item { CircularProgressIndicator() }
            }
            resultMessage?.let { message -> item { Text(message) } }
        }
    }
}
