package com.offgridpdf.android.ui.tool

import android.net.Uri
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import com.offgridpdf.android.files.rememberOpenDocumentLauncher
import com.offgridpdf.android.pdf.PdfLoadResult
import com.offgridpdf.android.pdf.loadPdfFromUri
import kotlinx.coroutines.launch

/**
 * Throwaway proof that `PdfLoader`/`DocumentPicker` actually work end to
 * end on a device — open a PDF, report its page count, handle a
 * password-protected one. The logic it exercises is unit-tested for real
 * (`PdfLoaderTest.kt`); this screen itself is a manual/visual check, not a
 * CI-verified one — see `ANDROID_IMPLEMENTATION_PLAN.md` A-2's acceptance
 * criteria (tool-docs repo). Reachable via the dashboard's overflow menu
 * until A-3 (Split PDF) gives this app its first real tool.
 */
@Composable
fun SmokeTestScreen() {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    var pickedUri by remember { mutableStateOf<Uri?>(null) }
    var password by remember { mutableStateOf("") }
    var passwordRequired by remember { mutableStateOf(false) }
    var running by remember { mutableStateOf(false) }
    var resultMessage by remember { mutableStateOf<String?>(null) }

    val pickLauncher = rememberOpenDocumentLauncher { uri ->
        pickedUri = uri
        password = ""
        passwordRequired = false
        resultMessage = null
    }

    ToolScaffold(
        title = "Smoke test: open a PDF",
        pickedFileName = pickedUri?.lastPathSegment,
        onPickFile = { pickLauncher.launch(arrayOf("application/pdf")) },
        passwordRequired = passwordRequired,
        password = password,
        onPasswordChange = { password = it },
        runEnabled = pickedUri != null,
        running = running,
        onRun = {
            // ToolScaffold only invokes onRun while runEnabled (pickedUri
            // != null) is true, so this is never actually null here — the
            // `let` just avoids a non-null assertion for a value the type
            // system alone can't see is guaranteed.
            pickedUri?.let { uri ->
                running = true
                resultMessage = null
                scope.launch {
                    when (val result = loadPdfFromUri(context, uri, password.ifBlank { null })) {
                        is PdfLoadResult.Success -> {
                            resultMessage = "${result.document.numberOfPages} page(s)."
                            result.document.close()
                            passwordRequired = false
                        }
                        PdfLoadResult.PasswordRequired -> {
                            passwordRequired = true
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
        runLabel = "Open",
        resultMessage = resultMessage,
    )
}
