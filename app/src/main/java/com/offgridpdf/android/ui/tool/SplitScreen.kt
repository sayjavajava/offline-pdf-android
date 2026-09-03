package com.offgridpdf.android.ui.tool

import android.net.Uri
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import com.offgridpdf.android.chain.ChainOrigin
import com.offgridpdf.android.chain.PendingFile
import com.offgridpdf.android.files.SavedFile
import com.offgridpdf.android.files.TOO_LARGE_MESSAGE
import com.offgridpdf.android.files.ZipEntryData
import com.offgridpdf.android.files.createZip
import com.offgridpdf.android.files.rememberCreateDocumentLauncher
import com.offgridpdf.android.files.rememberOpenDocumentLauncher
import com.offgridpdf.android.files.saveResult
import com.offgridpdf.android.files.savedFileOrNull
import com.offgridpdf.android.files.suggestedBaseName
import com.offgridpdf.android.pdf.PdfLoadResult
import com.offgridpdf.android.pdf.SplitPage
import com.offgridpdf.android.pdf.loadPdfFromUri
import com.offgridpdf.android.pdf.splitPdfToPages
import com.offgridpdf.android.pdf.splitPdfToSingleFile
import com.offgridpdf.android.ui.common.NullableUriSaver
import com.offgridpdf.android.ui.common.CheckboxRow
import com.offgridpdf.android.ui.common.ToolTextField
import com.offgridpdf.android.ui.common.rememberDisplayName
import com.offgridpdf.android.ui.common.userMessageFor
import com.offgridpdf.android.ui.theme.LocalOffGridPalette
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** Web reference: `SplitTool.tsx` + `splitPdf`/`splitPdfToZip` (`pdf-ops.ts`). */
@Composable
fun SplitScreen() {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val accent = LocalOffGridPalette.current.organize

    var pickedUri by rememberSaveable(stateSaver = NullableUriSaver) { mutableStateOf(PendingFile.consume()) }
    // Non-null only when this screen was opened via "Continue with another
    // tool" — the original file's base name, from before the *previous*
    // hop's own operation. Null means this screen's own picked file IS the
    // origin (either a fresh pick, or the app's entry point).
    var inheritedChainOrigin by rememberSaveable { mutableStateOf(ChainOrigin.consume()) }
    // Plain `remember`, deliberately: a document password is never written
    // to saved instance state (see `ui/common/Savers.kt`).
    var password by remember { mutableStateOf("") }
    var pagesText by rememberSaveable { mutableStateOf("") }
    var separateFiles by rememberSaveable { mutableStateOf(false) }
    var running by remember { mutableStateOf(false) }
    var resultMessage by rememberSaveable { mutableStateOf<String?>(null) }
    // The file this run produced, once it is really on disk. Not
    // saveable: it holds the bytes, and a Bundle caps out around 1 MB.
    var savedFile by remember { mutableStateOf<SavedFile?>(null) }

    // Set once the result bytes are ready, consumed by whichever save
    // launcher's onResult fires next. Only one of savePdfLauncher /
    // saveZipLauncher is ever launched per run, so there's no ambiguity
    // about which one a given pending value belongs to.
    var pendingBytes by remember { mutableStateOf<ByteArray?>(null) }
    // Paired with pendingBytes, which a Bundle cannot hold — so neither is
    // saved (see `ui/common/Savers.kt`).
    var pendingSuccessMessage by remember { mutableStateOf("") }

    // Only ever a single PDF's bytes — a zip (multiple output files) isn't
    // something the next tool screen can open, so chaining stays null for
    // that path (see the "separate files" branch below).
    var lastResultBytes by remember { mutableStateOf<ByteArray?>(null) }
    // Captured at the same moment as lastResultBytes, from the same
    // originBaseName used for the Save suggestion — see ContinueChainAction's
    // doc for why the chain name must not accumulate suffixes across hops.
    var chainOriginBaseName by remember { mutableStateOf("") }
    var chainedFileName by remember { mutableStateOf("") }

    val pickLauncher = rememberOpenDocumentLauncher { uri ->
        pickedUri = uri
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

    ToolScaffold(
        title = "Split PDF",
        accent = accent,
        pickedFileName = rememberDisplayName(pickedUri),
        onPickFile = { pickLauncher.launch(arrayOf("application/pdf")) },
        password = password,
        onPasswordChange = { password = it },
        runEnabled = pickedUri != null,
        running = running,
        onRun = {
            // ToolScaffold only invokes onRun while runEnabled (pickedUri
            // != null) is true.
            pickedUri?.let { uri ->
                running = true
                resultMessage = null
                savedFile = null
                val pageRange = pagesText.ifBlank { "all" }

                scope.launch {
                    val baseName = suggestedBaseName(context, uri)
                    val originBaseName = inheritedChainOrigin ?: baseName
                    when (val result = loadPdfFromUri(context, uri, password.ifBlank { null })) {
                        is PdfLoadResult.Success -> {
                            try {
                                lastResultBytes = null
                                if (separateFiles) {
                                    val pages = withContext(Dispatchers.Default) {
                                        splitPdfToPages(result.document, pageRange)
                                    }
                                    val plural = if (pages.size == 1) "" else "s"
                                    pendingSuccessMessage = "Split into ${pages.size} file$plural."
                                    if (pages.size == 1) {
                                        val page = pages[0]
                                        pendingBytes = page.bytes
                                        lastResultBytes = pendingBytes
                                        chainOriginBaseName = originBaseName
                                        chainedFileName = "${originBaseName}_split.pdf"
                                        val suggestedName = "${baseName}_page-${page.pageNumber.toString().padStart(3, '0')}.pdf"
                                        savePdfLauncher.launch(suggestedName)
                                    } else {
                                        pendingBytes = withContext(Dispatchers.Default) {
                                            createZip(zipEntriesFor(pages))
                                        }
                                        saveZipLauncher.launch("${baseName}_split.zip")
                                    }
                                } else {
                                    pendingBytes = withContext(Dispatchers.Default) {
                                        splitPdfToSingleFile(result.document, pageRange)
                                    }
                                    lastResultBytes = pendingBytes
                                    chainOriginBaseName = originBaseName
                                    chainedFileName = "${originBaseName}_split.pdf"
                                    pendingSuccessMessage = "Your PDF has been split successfully."
                                    savePdfLauncher.launch("${baseName}_split.pdf")
                                }
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
                        is PdfLoadResult.Failure -> {
                            resultMessage = result.message
                        }
                    }
                    running = false
                }
            }
        },
        runLabel = if (running) "Splitting..." else "Split PDF",
        resultMessage = resultMessage,
        savedFile = savedFile,
        chainableBytes = lastResultBytes,
        options = {
            ToolTextField(
                value = pagesText,
                onValueChange = { pagesText = it },
                label = "Pages to extract",
                accent = accent,
                placeholder = "e.g. 1, 3-5, 8 — or \"all\"",
            )
            CheckboxRow(
                checked = separateFiles,
                onCheckedChange = { separateFiles = it },
                label = "Download as separate files (zip)",
                accent = accent,
            )
        },
    )
}

/**
 * Repeated page numbers are possible (a range like "3,1,1" keeps
 * duplicates on purpose — see `PdfSplit.kt`'s `parsePageRange` doc);
 * suffix collisions rather than silently overwrite one zip entry with
 * another, same as `SplitTool.tsx` does.
 */
private fun zipEntriesFor(pages: List<SplitPage>): List<ZipEntryData> {
    val seen = mutableMapOf<Int, Int>()
    return pages.map { page ->
        val occurrence = (seen[page.pageNumber] ?: 0) + 1
        seen[page.pageNumber] = occurrence
        val suffix = if (occurrence > 1) "-copy$occurrence" else ""
        ZipEntryData(
            name = "page-${page.pageNumber.toString().padStart(3, '0')}$suffix.pdf",
            bytes = page.bytes,
        )
    }
}
