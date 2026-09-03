package com.offgridpdf.android.ui.tool

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.offgridpdf.android.chain.PendingFile
import com.offgridpdf.android.files.SavedFile
import com.offgridpdf.android.files.TOO_LARGE_MESSAGE
import com.offgridpdf.android.files.queryDisplayName
import com.offgridpdf.android.files.rememberCreateDocumentLauncher
import com.offgridpdf.android.files.rememberOpenMultipleDocumentsLauncher
import com.offgridpdf.android.files.saveResult
import com.offgridpdf.android.files.savedFileOrNull
import com.offgridpdf.android.pdf.PdfLoadResult
import com.offgridpdf.android.pdf.loadPdfFromUri
import com.offgridpdf.android.pdf.mergePdf
import com.offgridpdf.android.ui.common.FilePickerCard
import com.offgridpdf.android.ui.common.PrimaryButton
import com.offgridpdf.android.ui.common.PrivacyLine
import com.offgridpdf.android.ui.common.RunningIndicator
import com.offgridpdf.android.ui.common.ToolBodyText
import com.offgridpdf.android.ui.common.ToolCompletion
import com.offgridpdf.android.ui.common.ToolScreenScaffold
import com.offgridpdf.android.ui.common.UriListSaver
import com.offgridpdf.android.ui.common.rememberDisplayNames
import com.offgridpdf.android.ui.common.userMessageFor
import com.offgridpdf.android.ui.theme.LocalOffGridPalette
import com.offgridpdf.android.ui.theme.PlexMono
import com.tom_roush.pdfbox.pdmodel.PDDocument
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Web reference: `MergeTool.tsx` + `mergePdf` (`pdf-ops.ts`).
 *
 * Deliberately **not** built on `ToolScaffold` — that shape (one file, one
 * optional password) doesn't fit a multi-file, no-password tool at all.
 * `MergeTool.tsx` itself has no password field; an encrypted file in the
 * batch just fails by name, same as any other unreadable file (see below).
 * Forcing this through `ToolScaffold` would mean bending its API around a
 * shape it was never modeled on — worse than a second small screen.
 */
@Composable
fun MergeScreen() {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    // OrganizePages in PdfTool.kt, so that category's accent -- same
    // convention as every other tool screen.
    val accent = LocalOffGridPalette.current.organize

    var files by rememberSaveable(stateSaver = UriListSaver) { mutableStateOf(PendingFile.consume()?.let { listOf(it) } ?: emptyList()) }
    var running by remember { mutableStateOf(false) }
    var resultMessage by rememberSaveable { mutableStateOf<String?>(null) }
    // The file this run produced, once it is really on disk. Not
    // saveable: it holds the bytes, and a Bundle caps out around 1 MB.
    var savedFile by remember { mutableStateOf<SavedFile?>(null) }
    var pendingBytes by remember { mutableStateOf<ByteArray?>(null) }
    val palette = LocalOffGridPalette.current

    val pickLauncher = rememberOpenMultipleDocumentsLauncher { uris ->
        files = uris
        resultMessage = null
        savedFile = null
    }

    val fileNames = rememberDisplayNames(files)

    val saveLauncher = rememberCreateDocumentLauncher("application/pdf") { uri ->
        val bytes = pendingBytes
        if (uri != null && bytes != null) {
            scope.launch {
                val outcome = saveResult(context, uri, bytes, "Your PDFs have been merged successfully.")
                resultMessage = outcome.message
                savedFile = outcome.savedFileOrNull
            }
        }
        pendingBytes = null
    }

    ToolScreenScaffold(
        title = "Merge PDF",
        bottomBar = {
            if (running) {
                RunningIndicator(accent = accent)
            }
            resultMessage?.let { message ->
                ToolCompletion(message = message, savedFile = savedFile, accent = accent)
            }
            PrivacyLine()
            PrimaryButton(
                text = if (running) "Merging..." else "Merge PDFs",
                accent = accent,
                enabled = !running,
                onClick = {
                    if (files.size < 2) {
                        resultMessage = "Please select at least two PDF files to merge."
                    } else {
                        running = true
                        resultMessage = null
                        savedFile = null
                        val toMerge = files
                        scope.launch {
                            val opened = mutableListOf<PDDocument>()
                            var failureMessage: String? = null

                            for (uri in toMerge) {
                                val name = queryDisplayName(context, uri)
                                when (val result = loadPdfFromUri(context, uri)) {
                                    is PdfLoadResult.Success -> opened.add(result.document)
                                    PdfLoadResult.PasswordRequired -> {
                                        failureMessage = "Could not read \"$name\": this file needs a password."
                                    }
                                    is PdfLoadResult.Failure -> {
                                        failureMessage = "Could not read \"$name\": ${result.message}"
                                    }
                                }
                                if (failureMessage != null) break
                            }

                            if (failureMessage != null) {
                                resultMessage = failureMessage
                            } else {
                                try {
                                    pendingBytes = withContext(Dispatchers.Default) { mergePdf(opened) }
                                    saveLauncher.launch("merged.pdf")
                                } catch (e: Exception) {
                                    resultMessage = userMessageFor(e)
                                } catch (e: OutOfMemoryError) {
                                    resultMessage = TOO_LARGE_MESSAGE
                                }
                            }
                            opened.forEach { it.close() }
                            running = false
                        }
                    }
                },
            )
        },
    ) {
        FilePickerCard(
            fileName = when (files.size) {
                0 -> null
                1 -> fileNames.firstOrNull()
                else -> "${files.size} files selected"
            },
            onClick = { pickLauncher.launch(arrayOf("application/pdf")) },
        )

        ToolBodyText("Files are merged in the order shown. Pick at least two.")

        // One line per file, in merge order, rather than the single
        // newline-joined blob this screen used to render -- that had no
        // spacing, no numbering, and no way to tell where one name ended.
        if (fileNames.isNotEmpty()) {
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                fileNames.forEachIndexed { index, name ->
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        Text(
                            "${index + 1}.",
                            style = MaterialTheme.typography.bodySmall.copy(fontFamily = PlexMono),
                            color = palette.inkTertiary,
                        )
                        Text(
                            name,
                            style = MaterialTheme.typography.bodySmall.copy(fontFamily = PlexMono),
                            color = palette.inkSecondary,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                }
            }
        }
    }
}
