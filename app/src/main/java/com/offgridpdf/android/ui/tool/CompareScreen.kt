package com.offgridpdf.android.ui.tool

import android.net.Uri
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
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
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import com.offgridpdf.android.chain.PendingFile
import com.offgridpdf.android.files.SavedFile
import com.offgridpdf.android.files.rememberCreateDocumentLauncher
import com.offgridpdf.android.files.rememberOpenDocumentLauncher
import com.offgridpdf.android.files.saveResult
import com.offgridpdf.android.files.savedFileOrNull
import com.offgridpdf.android.pdf.CompareResult
import com.offgridpdf.android.pdf.PageComparison
import com.offgridpdf.android.pdf.PdfLoadResult
import com.offgridpdf.android.pdf.buildCompareReport
import com.offgridpdf.android.pdf.comparePdfs
import com.offgridpdf.android.pdf.describeComparison
import com.offgridpdf.android.pdf.loadPdfFromUri
import com.offgridpdf.android.ui.common.NullableUriSaver
import com.offgridpdf.android.ui.common.ScreenTopBar
import com.offgridpdf.android.ui.common.ToolCompletion
import com.offgridpdf.android.ui.common.rememberDisplayName
import com.offgridpdf.android.ui.theme.LocalOffGridPalette
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Web reference: `CompareTool.tsx` + `comparePdfs` (`pdf-compare.ts`).
 * Read-only — nothing is modified, and no PDF is produced, just a report
 * of what differs between PDF A and PDF B, page by page.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CompareScreen() {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    // ConvertExport in PdfTool.kt, so that category's accent -- same
    // convention as every other tool screen.
    val accent = LocalOffGridPalette.current.convert

    var uriA by rememberSaveable(stateSaver = NullableUriSaver) { mutableStateOf(PendingFile.consume()) }
    var uriB by rememberSaveable(stateSaver = NullableUriSaver) { mutableStateOf<Uri?>(null) }
    // Real filenames, resolved once here and reused for both the button
    // labels below and the report's document labels -- see
    // rememberDisplayName's own doc for why a Uri's path segment is not
    // a real filename.
    val displayNameA = rememberDisplayName(uriA)
    val displayNameB = rememberDisplayName(uriB)
    // Plain `remember`, deliberately: a document password is never written
    // to saved instance state (see `ui/common/Savers.kt`).
    var passwordA by remember { mutableStateOf("") }
    var passwordB by remember { mutableStateOf("") }
    var running by remember { mutableStateOf(false) }
    var result by remember { mutableStateOf<CompareResult?>(null) }
    var resultMessage by rememberSaveable { mutableStateOf<String?>(null) }
    // The file this run produced, once it is really on disk. Not
    // saveable: it holds the bytes, and a Bundle caps out around 1 MB.
    var savedFile by remember { mutableStateOf<SavedFile?>(null) }
    var pendingReportBytes by remember { mutableStateOf<ByteArray?>(null) }

    val pickLauncherA = rememberOpenDocumentLauncher { uri ->
        uriA = uri
        passwordA = ""
        result = null
        resultMessage = null
        savedFile = null
    }
    val pickLauncherB = rememberOpenDocumentLauncher { uri ->
        uriB = uri
        passwordB = ""
        result = null
        resultMessage = null
        savedFile = null
    }

    val saveReportLauncher = rememberCreateDocumentLauncher("text/plain") { uri ->
        val bytes = pendingReportBytes
        if (uri != null && bytes != null) {
            scope.launch {
                val outcome = saveResult(context, uri, bytes, "Report saved.")
                resultMessage = outcome.message
                savedFile = outcome.savedFileOrNull
            }
        }
        pendingReportBytes = null
    }

    Scaffold(
        topBar = { ScreenTopBar(title = "Compare PDFs") },
        containerColor = LocalOffGridPalette.current.paper,
        // Bottom and horizontal only. The top inset belongs to ScreenTopBar,
        // which applies it itself, so asking Scaffold for it as well risks
        // counting the status bar twice. Bottom is safeDrawing rather than
        // navigationBars so content also clears the keyboard.
        contentWindowInsets = WindowInsets.safeDrawing.only(
            WindowInsetsSides.Bottom + WindowInsetsSides.Horizontal,
        ),
    ) { innerPadding ->
        LazyColumn(
            modifier = Modifier.padding(innerPadding).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            item {
                Text(
                    "Find out what changed between two versions of a document — page by page, both " +
                        "what the text says and what the page looks like. Read-only: nothing is " +
                        "modified, and no PDF is produced, just a report of what differs.",
                )
            }
            item {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Button(onClick = { pickLauncherA.launch(arrayOf("application/pdf")) }, modifier = Modifier.fillMaxWidth()) {
                        Text(displayNameA ?: "Choose PDF A")
                    }
                    OutlinedTextField(
                        value = passwordA,
                        onValueChange = { passwordA = it },
                        label = { Text("Password A (if encrypted)") },
                        visualTransformation = PasswordVisualTransformation(),
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
            }
            item {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Button(onClick = { pickLauncherB.launch(arrayOf("application/pdf")) }, modifier = Modifier.fillMaxWidth()) {
                        Text(displayNameB ?: "Choose PDF B")
                    }
                    OutlinedTextField(
                        value = passwordB,
                        onValueChange = { passwordB = it },
                        label = { Text("Password B (if encrypted)") },
                        visualTransformation = PasswordVisualTransformation(),
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
            }
            item {
                Button(
                    onClick = {
                        val a = uriA
                        val b = uriB
                        if (a == null || b == null) {
                            resultMessage = "Select a PDF for both A and B."
                            return@Button
                        }
                        running = true
                        resultMessage = null
                        savedFile = null
                        result = null
                        scope.launch {
                            when (val loadedA = loadPdfFromUri(context, a, passwordA.ifBlank { null })) {
                                is PdfLoadResult.Success -> {
                                    when (val loadedB = loadPdfFromUri(context, b, passwordB.ifBlank { null })) {
                                        is PdfLoadResult.Success -> {
                                            try {
                                                val compared = withContext(Dispatchers.Default) {
                                                    comparePdfs(loadedA.document, loadedB.document)
                                                }
                                                result = compared
                                                val diffCount = compared.pages.count { page ->
                                                    page !is PageComparison.Both || page.textDiffers == true || page.visuallyDiffers
                                                }
                                                resultMessage = if (diffCount == 0) {
                                                    "Every shared page is identical, and both files have the same page count."
                                                } else {
                                                    "$diffCount of ${compared.pages.size} page${if (compared.pages.size == 1) "" else "s"} differ."
                                                }
                                            } catch (e: Exception) {
                                                resultMessage = e.message ?: "Could not compare these PDFs."
                                            } finally {
                                                loadedB.document.close()
                                            }
                                        }
                                        PdfLoadResult.PasswordRequired -> {
                                            resultMessage = if (passwordB.isBlank()) "PDF B needs a password." else "Wrong password for PDF B — try again."
                                        }
                                        is PdfLoadResult.Failure -> resultMessage = "PDF B: ${loadedB.message}"
                                    }
                                    loadedA.document.close()
                                }
                                PdfLoadResult.PasswordRequired -> {
                                    resultMessage = if (passwordA.isBlank()) "PDF A needs a password." else "Wrong password for PDF A — try again."
                                }
                                is PdfLoadResult.Failure -> resultMessage = "PDF A: ${loadedA.message}"
                            }
                            running = false
                        }
                    },
                    enabled = !running,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text(if (running) "Comparing..." else "Compare")
                }
            }
            if (running) {
                item { CircularProgressIndicator() }
            }
            resultMessage?.let { message ->
                item { ToolCompletion(message = message, savedFile = savedFile, accent = accent) }
            }

            val current = result
            if (current != null) {
                item {
                    Text("A: ${current.pageCountA} page${if (current.pageCountA == 1) "" else "s"} · B: ${current.pageCountB} page${if (current.pageCountB == 1) "" else "s"}")
                }
                item {
                    OutlinedButton(
                        onClick = {
                            pendingReportBytes = buildCompareReport(
                                displayNameA ?: "A.pdf",
                                displayNameB ?: "B.pdf",
                                current,
                            ).toByteArray()
                            saveReportLauncher.launch("compare_report.txt")
                        },
                        modifier = Modifier.fillMaxWidth(),
                    ) { Text("Download Report") }
                }
                items(current.pages.size) { i ->
                    val p = current.pages[i]
                    val ratioText = if (p is PageComparison.Both && p.pixelDiffRatio != null) {
                        " — ${"%.1f".format(p.pixelDiffRatio * 100)}% of pixels"
                    } else {
                        ""
                    }
                    Text("Page ${p.page}: ${describeComparison(p)}$ratioText")
                }
            }
        }
    }
}
