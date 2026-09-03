package com.offgridpdf.android.ui.tool

import android.net.Uri
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
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Scaffold
import androidx.compose.material3.MaterialTheme
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
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.offgridpdf.android.files.SavedFile
import com.offgridpdf.android.files.TOO_LARGE_MESSAGE
import com.offgridpdf.android.files.queryDisplayName
import com.offgridpdf.android.files.readBytesFromUri
import com.offgridpdf.android.files.rememberCreateDocumentLauncher
import com.offgridpdf.android.files.rememberOpenMultipleDocumentsLauncher
import com.offgridpdf.android.files.saveResult
import com.offgridpdf.android.files.savedFileOrNull
import com.offgridpdf.android.pdf.ImageFile
import com.offgridpdf.android.pdf.imagesToPdf
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
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Web reference: `ConvertTool.tsx` (the image half only — DOCX conversion
 * is A-25, gated separately) + `convertImageToPdf`/`detectImageFormat`
 * (`pdf-ops.ts`).
 *
 * Deliberately **not** built on `ToolScaffold`, same reasoning as
 * `MergeScreen.kt`: multi-file, no password field, and the web tool has
 * no such field for this either.
 */
@Composable
fun ImagesToPdfScreen() {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    // ConvertExport in PdfTool.kt, so that category's accent -- same
    // convention as every other tool screen.
    val accent = LocalOffGridPalette.current.convert

    var files by rememberSaveable(stateSaver = UriListSaver) { mutableStateOf<List<Uri>>(emptyList()) }
    var running by remember { mutableStateOf(false) }
    var resultMessage by rememberSaveable { mutableStateOf<String?>(null) }
    // The file this run produced, once it is really on disk. Not
    // saveable: it holds the bytes, and a Bundle caps out around 1 MB.
    var savedFile by remember { mutableStateOf<SavedFile?>(null) }
    var pendingBytes by remember { mutableStateOf<ByteArray?>(null) }

    val pickLauncher = rememberOpenMultipleDocumentsLauncher { uris ->
        files = uris
        resultMessage = null
        savedFile = null
    }

    val saveLauncher = rememberCreateDocumentLauncher("application/pdf") { uri ->
        val bytes = pendingBytes
        if (uri != null && bytes != null) {
            val success = if (files.size > 1) {
                "Combined ${files.size} images into one PDF."
            } else {
                "Your image has been converted to PDF."
            }
            scope.launch {
                val outcome = saveResult(context, uri, bytes, success)
                resultMessage = outcome.message
                savedFile = outcome.savedFileOrNull
            }
        }
        pendingBytes = null
    }

    val fileNames = rememberDisplayNames(files)
    val palette = LocalOffGridPalette.current

    ToolScreenScaffold(
        title = "Convert Images to PDF",
        bottomBar = {
            if (running) {
                RunningIndicator(accent = accent)
            }
            resultMessage?.let { message ->
                ToolCompletion(message = message, savedFile = savedFile, accent = accent)
            }
            PrivacyLine()
            PrimaryButton(
                text = if (running) "Converting..." else "Convert to PDF",
                accent = accent,
                enabled = !running,
                onClick = {
                    if (files.isEmpty()) {
                        resultMessage = "Select at least one image."
                    } else {
                        running = true
                        resultMessage = null
                        savedFile = null
                        val toConvert = files
                        scope.launch {
                            try {
                                val images = toConvert.map { uri ->
                                    val name = queryDisplayName(context, uri)
                                    ImageFile(name, readBytesFromUri(context, uri))
                                }
                                pendingBytes = withContext(Dispatchers.Default) { imagesToPdf(images) }
                                saveLauncher.launch(
                                    if (toConvert.size > 1) "combined.pdf" else "converted.pdf",
                                )
                            } catch (e: Exception) {
                                resultMessage = userMessageFor(e)
                            } catch (e: OutOfMemoryError) {
                                resultMessage = TOO_LARGE_MESSAGE
                            }
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
                else -> "${files.size} images selected"
            },
            onClick = { pickLauncher.launch(arrayOf("image/jpeg", "image/png")) },
        )

        ToolBodyText(
            "Convert JPEG or PNG images to PDF — select several to combine them " +
                "into one multi-page PDF, in the order shown.",
        )

        // One numbered line per image, in page order. Previously a single
        // newline-joined blob with no spacing between names.
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
