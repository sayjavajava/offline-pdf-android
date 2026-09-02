package com.offgridpdf.android.ui.tool

import android.net.Uri
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
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
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import com.offgridpdf.android.chain.PendingFile
import com.offgridpdf.android.files.TOO_LARGE_MESSAGE
import com.offgridpdf.android.files.rememberCreateDocumentLauncher
import com.offgridpdf.android.files.rememberOpenDocumentLauncher
import com.offgridpdf.android.files.saveResult
import com.offgridpdf.android.files.suggestedBaseName
import com.offgridpdf.android.pdf.ApplyToRangeResult
import com.offgridpdf.android.pdf.FindMatchResult
import com.offgridpdf.android.pdf.PdfLoadResult
import com.offgridpdf.android.pdf.PixelPoint
import com.offgridpdf.android.pdf.RedactionRect
import com.offgridpdf.android.pdf.applyBoxesToRange
import com.offgridpdf.android.pdf.findTextMatches
import com.offgridpdf.android.pdf.loadPdfFromUri
import com.offgridpdf.android.pdf.pixelToPdfRect
import com.offgridpdf.android.pdf.redactPdf
import com.offgridpdf.android.pdf.resolvePageIndices
import com.offgridpdf.android.pdf.toPixelRect
import com.offgridpdf.android.ui.common.ContinueChainAction
import com.offgridpdf.android.ui.common.FilePickerCard
import com.offgridpdf.android.ui.common.PrimaryButton
import com.offgridpdf.android.ui.common.PrivacyLine
import com.offgridpdf.android.ui.common.ScreenTopBar
import com.offgridpdf.android.ui.common.userMessageFor
import com.offgridpdf.android.ui.theme.LocalOffGridPalette
import com.offgridpdf.android.ui.theme.PlexMono
import com.tom_roush.pdfbox.pdmodel.PDDocument
import com.tom_roush.pdfbox.rendering.PDFRenderer
import kotlin.math.abs
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** Pixels per PDF point for the on-screen preview render. Independent of the export scale in `PdfRedact.kt` — boxes are converted to point-space immediately on drawing, so this only affects preview legibility. Web reference: `PREVIEW_SCALE` (`RedactTool.tsx`). */
private const val PREVIEW_SCALE = 1.5f

/** Ignore accidental clicks/taps, same as `RedactTool.tsx`'s own `finishDrag` threshold. */
private const val MIN_BOX_PT = 4f / PREVIEW_SCALE

/**
 * Web reference: `RedactTool.tsx` + `redactPdf`/`toPixelRect` (`pdf-redact.ts`),
 * plus its Find UI + `findTextMatches` (`pdf-search.ts`). Hand-drawn boxes
 * (A-19) and Find (A-21, built on Spike D's `PdfTextPositionSpike.kt`) both
 * land on the same `redactions` box-list state — "Add all" is a plain map
 * merge, no translation layer, exactly like the web's own
 * `handleAddAllMatches`.
 *
 * Unlike every other tool screen, this one keeps a loaded `PDDocument`
 * open across the whole editing session (pick → draw boxes or search
 * across several pages → apply-to-range → Apply & Download) rather than
 * loading and closing it within a single button press — genuinely
 * necessary here since drawing, page navigation, and Find all need the
 * same open document.
 *
 * Restyled to the "paper & ink" redesign (see the UI redesign mockups):
 * applied boxes render solid ink (permanent, not a translucent overlay)
 * and the box being dragged renders as a dashed security-accent outline
 * with the same visual language `ToolScaffold`/`FilePickerCard` use
 * elsewhere, even though this screen keeps its own bespoke `Scaffold`
 * rather than using `ToolScaffold` (its multi-step load → edit → apply
 * flow doesn't fit that single-button shape).
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RedactScreen() {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val palette = LocalOffGridPalette.current
    val accent = palette.security

    var pickedUri by remember { mutableStateOf(PendingFile.consume()) }
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

    var searchQuery by remember { mutableStateOf("") }
    var caseSensitive by remember { mutableStateOf(false) }
    var searching by remember { mutableStateOf(false) }
    var findResult by remember { mutableStateOf<FindMatchResult?>(null) }
    var findMessage by remember { mutableStateOf<String?>(null) }

    var pendingBytes by remember { mutableStateOf<ByteArray?>(null) }
    var lastResultBytes by remember { mutableStateOf<ByteArray?>(null) }

    // The in-flight page render, so a newer page turn can cancel an older
    // one (see goToPage).
    var renderJob by remember { mutableStateOf<Job?>(null) }

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

    // Suspending, and rasterizing on Dispatchers.Default: a full-page render
    // is the single most expensive thing this screen does, and it used to run
    // on the main thread on every page turn — a visible freeze on a dense
    // page, and an ANR on a really heavy one.
    suspend fun renderCurrentPage(doc: PDDocument, index: Int) {
        val mediaBox = doc.getPage(index).mediaBox
        val bitmap = withContext(Dispatchers.Default) {
            PDFRenderer(doc).renderImageWithDPI(index, PREVIEW_SCALE * 72f)
        }
        previewHeightPts = mediaBox.height
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
        lastResultBytes = null
        loadMessage = null
        previewImage = null
        searchQuery = ""
        findResult = null
        findMessage = null
    }

    val saveLauncher = rememberCreateDocumentLauncher("application/pdf") { uri ->
        val bytes = pendingBytes
        if (uri != null && bytes != null) {
            scope.launch {
                resultMessage = saveResult(context, uri, bytes, "Redacted $totalBoxes box${if (totalBoxes == 1) "" else "es"} across $pagesWithBoxes page${if (pagesWithBoxes == 1) "" else "s"}.")
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
                    searchQuery = ""
                    findResult = null
                    findMessage = null
                    try {
                        renderCurrentPage(result.document, 0)
                    } catch (e: Exception) {
                        loadMessage = "Loaded, but could not render page 1: ${userMessageFor(e)}"
                    } catch (e: OutOfMemoryError) {
                        loadMessage = "Loaded, but could not render page 1: $TOO_LARGE_MESSAGE"
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
        // Now that rendering is asynchronous, tapping through pages quickly
        // could otherwise leave a slower earlier render finishing last and
        // showing the wrong page. Only the newest request survives.
        renderJob?.cancel()
        renderJob = scope.launch {
            try {
                renderCurrentPage(doc, newIndex)
            } catch (e: Exception) {
                loadMessage = "Could not render page ${newIndex + 1}: ${userMessageFor(e)}"
            } catch (e: OutOfMemoryError) {
                loadMessage = "Could not render page ${newIndex + 1}: $TOO_LARGE_MESSAGE"
            }
        }
    }

    val fieldColors = OutlinedTextFieldDefaults.colors(
        focusedBorderColor = accent,
        unfocusedBorderColor = palette.hairlineStrong,
        focusedContainerColor = palette.paperRaised,
        unfocusedContainerColor = palette.paperRaised,
        focusedTextColor = palette.ink,
        unfocusedTextColor = palette.ink,
        focusedLabelColor = accent,
        unfocusedLabelColor = palette.inkTertiary,
    )

    Scaffold(
        topBar = {
            ScreenTopBar(title = "Redact PDF") {
                if (pageCount > 0) {
                    Text(
                        "$pageNumber / $pageCount",
                        style = MaterialTheme.typography.bodyMedium.copy(fontFamily = PlexMono),
                        color = palette.inkTertiary,
                    )
                }
            }
        },
        containerColor = palette.paper,
    ) { innerPadding ->
        // Width capped and centered — a no-op on a phone, but keeps the
        // page preview and controls from stretching edge to edge in the
        // wide detail pane OffGridNavHost.kt gives this screen on a
        // tablet/expanded-width window (see ToolScaffold.kt's own copy of
        // this treatment).
        Box(modifier = Modifier.fillMaxSize().padding(innerPadding), contentAlignment = Alignment.TopCenter) {
        LazyColumn(
            modifier = Modifier.fillMaxWidth().widthIn(max = 560.dp).padding(horizontal = 22.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            item {
                Text(
                    "Draw boxes over content to permanently remove — this deletes the underlying text and " +
                        "image data, not just draws over it, so nothing under a box stays selectable, copyable, " +
                        "or searchable. Every page you redact loses its own text layer entirely, since it is " +
                        "rebuilt as a plain image; pages you leave untouched keep theirs. Or search for text " +
                        "below to find every occurrence across the document and turn them into boxes " +
                        "automatically — review them like any other box before applying.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = palette.inkSecondary,
                )
            }
            item {
                FilePickerCard(fileName = pickedUri?.lastPathSegment, onClick = { pickLauncher.launch(arrayOf("application/pdf")) })
            }
            if (pickedUri != null && document == null) {
                item {
                    OutlinedTextField(
                        value = password,
                        onValueChange = { password = it },
                        label = { Text("Password (if encrypted)") },
                        visualTransformation = PasswordVisualTransformation(),
                        colors = fieldColors,
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
                item {
                    PrimaryButton(
                        text = if (loading) "Loading..." else "Load",
                        onClick = { loadDocument() },
                        accent = accent,
                        enabled = !loading,
                    )
                }
                loadMessage?.let { item { Text(it, style = MaterialTheme.typography.bodySmall, color = palette.inkSecondary) } }
            }

            val doc = document
            val image = previewImage
            if (doc != null && image != null) {
                item {
                    Text("Find text to redact", style = MaterialTheme.typography.titleMedium, color = palette.ink)
                }
                item {
                    OutlinedTextField(
                        value = searchQuery,
                        onValueChange = { searchQuery = it },
                        label = { Text("e.g. a name or account number") },
                        colors = fieldColors,
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
                item {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Checkbox(checked = caseSensitive, onCheckedChange = { caseSensitive = it })
                        Text("Match case", style = MaterialTheme.typography.bodyMedium, color = palette.inkSecondary)
                    }
                }
                item {
                    PrimaryButton(
                        text = if (searching) "Searching..." else "Find",
                        onClick = {
                            searching = true
                            findResult = null
                            findMessage = null
                            scope.launch {
                                try {
                                    findResult = withContext(Dispatchers.Default) {
                                        findTextMatches(doc, searchQuery, caseSensitive)
                                    }
                                } catch (e: Exception) {
                                    findMessage = userMessageFor(e)
                                } catch (e: OutOfMemoryError) {
                                    findMessage = TOO_LARGE_MESSAGE
                                }
                                searching = false
                            }
                        },
                        accent = accent,
                        enabled = !searching,
                    )
                }
                findMessage?.let { message -> item { Text(message, style = MaterialTheme.typography.bodySmall, color = palette.inkSecondary) } }
                findResult?.let { result ->
                    item {
                        Text(
                            if (result.totalMatches == 0) {
                                "No matches found for \"$searchQuery\"."
                            } else {
                                "Found ${result.totalMatches} match${if (result.totalMatches == 1) "" else "es"} across " +
                                    "${result.matchesByPage.size} page${if (result.matchesByPage.size == 1) "" else "s"}."
                            },
                            style = MaterialTheme.typography.bodyMedium,
                            color = palette.inkSecondary,
                        )
                    }
                    val totalSkipped = result.skippedByPage.values.sum()
                    if (totalSkipped > 0) {
                        item {
                            Text(
                                "$totalSkipped match${if (totalSkipped == 1) "" else "es"} skipped — " +
                                    "${if (totalSkipped == 1) "it spans" else "they span"} a line break, so draw " +
                                    "${if (totalSkipped == 1) "that one" else "those"} by hand.",
                                style = MaterialTheme.typography.bodySmall,
                                color = palette.inkTertiary,
                            )
                        }
                    }
                    if (result.noTextLayerPages.isNotEmpty()) {
                        item {
                            Text(
                                "No text layer on page${if (result.noTextLayerPages.size == 1) "" else "s"} " +
                                    "${result.noTextLayerPages.joinToString(", ")} — likely scanned; redact " +
                                    "${if (result.noTextLayerPages.size == 1) "that one" else "those"} manually.",
                                style = MaterialTheme.typography.bodySmall,
                                color = palette.inkTertiary,
                            )
                        }
                    }
                    if (result.totalMatches > 0) {
                        item {
                            OutlinedButton(
                                onClick = {
                                    val merged = redactions.toMutableMap()
                                    for ((page, rects) in result.matchesByPage) {
                                        merged[page] = merged[page].orEmpty() + rects
                                    }
                                    redactions = merged
                                    findMessage = "Added ${result.totalMatches} match${if (result.totalMatches == 1) "" else "es"} " +
                                        "as redaction box${if (result.totalMatches == 1) "" else "es"}."
                                    findResult = null
                                },
                                modifier = Modifier.fillMaxWidth(),
                            ) {
                                Text("Add all ${result.totalMatches} as redaction box${if (result.totalMatches == 1) "" else "es"}")
                            }
                        }
                    }
                }
                item {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedButton(onClick = { goToPage(pageIndex - 1) }, enabled = pageIndex > 0) { Text("Previous") }
                        Text(
                            "Page $pageNumber of $pageCount" +
                                if (currentPageBoxes.isNotEmpty()) " — ${currentPageBoxes.size} box${if (currentPageBoxes.size == 1) "" else "es"}" else "",
                            style = MaterialTheme.typography.bodySmall.copy(fontFamily = PlexMono),
                            color = palette.inkTertiary,
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
                                        color = palette.ink,
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
                                        color = accent,
                                        topLeft = Offset(left, top),
                                        size = Size(abs(current.x - start.x), abs(current.y - start.y)),
                                        style = Stroke(
                                            width = 3f,
                                            pathEffect = PathEffect.dashPathEffect(floatArrayOf(14f, 10f)),
                                        ),
                                    )
                                }
                            }
                        }
                    }
                }
                if (currentPageBoxes.isNotEmpty()) {
                    items(currentPageBoxes.size) { i ->
                        val rect = currentPageBoxes[i]
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween,
                            modifier = Modifier.fillMaxWidth(),
                        ) {
                            Text(
                                "${rect.width.toInt()} × ${rect.height.toInt()} pt",
                                style = MaterialTheme.typography.bodyMedium.copy(fontFamily = PlexMono),
                                color = palette.ink,
                            )
                            TextButton(onClick = {
                                redactions = redactions + (pageNumber to currentPageBoxes.filterIndexed { index, _ -> index != i })
                            }) { Text("Remove", color = palette.securityLabel) }
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
                            colors = fieldColors,
                            modifier = Modifier.fillMaxWidth(),
                        )
                    }
                    item {
                        OutlinedButton(
                            onClick = {
                                val targets = if (applyRangeText.isBlank() || applyRangeText.trim().equals("all", ignoreCase = true)) {
                                    (1..pageCount).toList()
                                } else {
                                    // Every branch has to yield the same list
                                    // type — this `try` is an expression.
                                    try {
                                        resolvePageIndices(applyRangeText, pageCount).map { it + 1 }
                                    } catch (e: Exception) {
                                        resultMessage = userMessageFor(e)
                                        emptyList<Int>()
                                    } catch (e: OutOfMemoryError) {
                                        resultMessage = TOO_LARGE_MESSAGE
                                        emptyList<Int>()
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
                    style = MaterialTheme.typography.bodyMedium,
                    color = palette.inkSecondary,
                )
            }
            item {
                if (applying) {
                    CircularProgressIndicator(color = accent, modifier = Modifier.padding(bottom = 4.dp))
                }
                PrivacyLine("This permanently removes the content — not just a visual overlay.")
            }
            item {
                PrimaryButton(
                    text = if (applying) "Redacting..." else "Apply Redactions & Download",
                    onClick = {
                        val doc2 = document ?: return@PrimaryButton
                        applying = true
                        resultMessage = null
                        lastResultBytes = null
                        scope.launch {
                            try {
                                val bytes = withContext(Dispatchers.Default) {
                                    redactPdf(doc2, redactions)
                                }
                                pendingBytes = bytes
                                lastResultBytes = bytes
                                val name = pickedUri?.let { suggestedBaseName(it) } ?: "document"
                                saveLauncher.launch("${name}_redacted.pdf")
                            } catch (e: Exception) {
                                resultMessage = userMessageFor(e)
                            } catch (e: OutOfMemoryError) {
                                resultMessage = TOO_LARGE_MESSAGE
                            }
                            applying = false
                        }
                    },
                    accent = accent,
                    enabled = document != null && totalBoxes > 0 && !applying,
                )
            }
            resultMessage?.let { message -> item { Text(message, style = MaterialTheme.typography.bodySmall, color = palette.inkSecondary) } }
            if (resultMessage != null) {
                item { ContinueChainAction(bytes = lastResultBytes, accent = accent) }
            }
            item { Box(modifier = Modifier.padding(bottom = 18.dp)) }
        }
        }
    }
}
