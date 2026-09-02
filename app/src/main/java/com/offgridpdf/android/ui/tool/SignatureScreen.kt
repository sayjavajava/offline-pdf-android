package com.offgridpdf.android.ui.tool

import android.net.Uri
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.text.KeyboardOptions
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
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.offgridpdf.android.chain.PendingFile
import com.offgridpdf.android.files.TOO_LARGE_MESSAGE
import com.offgridpdf.android.files.readBytesFromUri
import com.offgridpdf.android.files.rememberCreateDocumentLauncher
import com.offgridpdf.android.files.rememberOpenDocumentLauncher
import com.offgridpdf.android.files.saveResult
import com.offgridpdf.android.files.suggestedBaseName
import com.offgridpdf.android.pdf.PdfLoadResult
import com.offgridpdf.android.pdf.SignaturePlacement
import com.offgridpdf.android.pdf.addSignature
import com.offgridpdf.android.pdf.loadPdfFromUri
import com.offgridpdf.android.ui.common.NullableUriSaver
import com.offgridpdf.android.ui.common.ScreenTopBar
import com.offgridpdf.android.ui.common.userMessageFor
import com.offgridpdf.android.ui.theme.LocalOffGridPalette
import com.tom_roush.pdfbox.pdmodel.PDDocument
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

private enum class SignatureMode { TYPE, DRAW, UPLOAD }

/**
 * Web reference: `SignatureTool.tsx` + `addSignature`/`placeSignatureImage`
 * (`pdf-ops.ts`/`pdf-signature.ts`).
 *
 * Placement is numeric x/y/width/height fields in PDF points against the
 * page's own known size, rather than a drag-to-position rect on a
 * rendered preview — this project has no page-rendering yet
 * (`ANDROID_IMPLEMENTATION_PLAN.md`'s Spike A), and the plan is explicit
 * that this tool should ship with that simpler placement UI rather than
 * be blocked on rendering, revisiting the live preview once Spike A
 * lands.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SignatureScreen() {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

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
    var pendingBytes by remember { mutableStateOf<ByteArray?>(null) }

    val pickLauncher = rememberOpenDocumentLauncher { uri ->
        pickedUri = uri
        password = ""
        openDocument?.close()
        openDocument = null
        pageCount = null
        loadError = null
        resultMessage = null
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
                resultMessage = saveResult(context, uri, bytes, "Signature added.")
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

    Scaffold(
        topBar = { ScreenTopBar(title = "Add Signature") },
        containerColor = LocalOffGridPalette.current.paper,
    ) { innerPadding ->
        Column(
            modifier = Modifier.fillMaxSize().padding(innerPadding).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text(
                "Stamp a visual signature — typed, drawn, or an uploaded image — onto a page. " +
                    "This is a visual mark, not a cryptographic digital signature.",
            )

            Button(
                onClick = { pickLauncher.launch(arrayOf("application/pdf")) },
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text(pickedUri?.lastPathSegment ?: "Choose a PDF file")
            }
            OutlinedTextField(
                value = password,
                onValueChange = { password = it },
                label = { Text("Password (if encrypted)") },
                modifier = Modifier.fillMaxWidth(),
            )
            if (pickedUri != null && pageCount == null) {
                Button(onClick = { loadDocument() }, modifier = Modifier.fillMaxWidth()) {
                    Text("Load PDF")
                }
            }
            pageCount?.let { count -> Text("This PDF has $count page(s).") }
            loadError?.let { Text(it) }

            Text("Signature")
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                for ((label, candidate) in listOf("Type" to SignatureMode.TYPE, "Draw" to SignatureMode.DRAW, "Upload" to SignatureMode.UPLOAD)) {
                    if (mode == candidate) {
                        Button(onClick = { mode = candidate }) { Text(label) }
                    } else {
                        OutlinedButton(onClick = { mode = candidate }) { Text(label) }
                    }
                }
            }

            when (mode) {
                SignatureMode.TYPE -> {
                    OutlinedTextField(
                        value = typedName,
                        onValueChange = { typedName = it },
                        label = { Text("Your name") },
                        modifier = Modifier.fillMaxWidth(),
                    )
                    Button(onClick = { signatureBytes = renderTypedSignature(typedName) }) {
                        Text("Use this signature")
                    }
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
                        OutlinedButton(onClick = { strokes = emptyList(); currentStroke = emptyList() }) {
                            Text("Clear")
                        }
                        OutlinedButton(onClick = { signatureBytes = renderDrawnSignature(strokes) }) {
                            Text("Use this signature")
                        }
                    }
                }
                SignatureMode.UPLOAD -> {
                    Button(onClick = { uploadLauncher.launch(arrayOf("image/png", "image/jpeg")) }) {
                        Text("Choose signature image")
                    }
                }
            }

            if (signatureBytes != null) {
                Text("Signature ready.")
            }

            Text("Placement (points, bottom-left origin)")
            OutlinedTextField(
                value = pageText,
                onValueChange = { pageText = it },
                label = { Text("Page number") },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                modifier = Modifier.fillMaxWidth(),
            )
            OutlinedTextField(
                value = xText,
                onValueChange = { xText = it },
                label = { Text("X") },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                modifier = Modifier.fillMaxWidth(),
            )
            OutlinedTextField(
                value = yText,
                onValueChange = { yText = it },
                label = { Text("Y") },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                modifier = Modifier.fillMaxWidth(),
            )
            OutlinedTextField(
                value = widthText,
                onValueChange = { widthText = it },
                label = { Text("Width") },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                modifier = Modifier.fillMaxWidth(),
            )
            OutlinedTextField(
                value = heightText,
                onValueChange = { heightText = it },
                label = { Text("Height") },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                modifier = Modifier.fillMaxWidth(),
            )

            Button(
                onClick = {
                    val document = openDocument
                    val uri = pickedUri
                    val bytes = signatureBytes
                    if (document == null || uri == null) {
                        resultMessage = "Load a PDF first."
                        return@Button
                    }
                    if (bytes == null) {
                        resultMessage = "Type, draw, or upload a signature first."
                        return@Button
                    }
                    val page = pageText.toIntOrNull()
                    val x = xText.toFloatOrNull()
                    val y = yText.toFloatOrNull()
                    val width = widthText.toFloatOrNull()
                    val height = heightText.toFloatOrNull()
                    if (page == null || x == null || y == null || width == null || height == null) {
                        resultMessage = "Page, X, Y, width, and height must all be numbers."
                        return@Button
                    }

                    running = true
                    resultMessage = null
                    val baseName = suggestedBaseName(uri)
                    scope.launch {
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
                enabled = !running,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text(if (running) "Adding signature..." else "Add Signature & Download")
            }

            if (running) {
                CircularProgressIndicator()
            }

            resultMessage?.let { Text(it) }
        }
    }
}
