package com.offgridpdf.android.ui.tool

import com.offgridpdf.android.chain.PendingFile

import com.offgridpdf.android.ui.theme.LocalOffGridPalette

import android.net.Uri
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import com.offgridpdf.android.files.rememberCreateDocumentLauncher
import com.offgridpdf.android.files.rememberOpenDocumentLauncher
import com.offgridpdf.android.files.suggestedBaseName
import com.offgridpdf.android.files.writeBytesToUri
import com.offgridpdf.android.pdf.ModifyPermission
import com.offgridpdf.android.pdf.PdfLoadResult
import com.offgridpdf.android.pdf.PdfPermissions
import com.offgridpdf.android.pdf.PrintPermission
import com.offgridpdf.android.pdf.loadPdfFromUri
import com.offgridpdf.android.pdf.protectPdf
import com.offgridpdf.android.pdf.protectPdfWithPermissions
import kotlinx.coroutines.launch

/**
 * Web reference: `ProtectTool.tsx` + `protectPdf`/`protectPdfWithPermissions`
 * (`pdf-ops.ts`).
 *
 * `ToolScaffold`'s built-in password field means exactly what it means for
 * every other tool here: the password needed to *open* the input file, if
 * it already has one. The password(s) this screen actually sets are new
 * fields in the `options` slot below -- a genuinely different concept from
 * "unlock the input", not a naming collision to resolve.
 */
@Composable
fun ProtectScreen() {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    var pickedUri by remember { mutableStateOf(PendingFile.consume()) }
    var inputPassword by remember { mutableStateOf("") }
    var newPassword by remember { mutableStateOf("") }
    var confirmPassword by remember { mutableStateOf("") }
    var restrict by remember { mutableStateOf(false) }
    var permissionsPassword by remember { mutableStateOf("") }
    var print by remember { mutableStateOf(PrintPermission.FULL) }
    var modify by remember { mutableStateOf(ModifyPermission.ALL) }
    var extract by remember { mutableStateOf(true) }
    var running by remember { mutableStateOf(false) }
    var resultMessage by remember { mutableStateOf<String?>(null) }
    var pendingBytes by remember { mutableStateOf<ByteArray?>(null) }
    var lastResultBytes by remember { mutableStateOf<ByteArray?>(null) }

    val pickLauncher = rememberOpenDocumentLauncher { uri ->
        pickedUri = uri
        inputPassword = ""
        resultMessage = null
    }

    val saveLauncher = rememberCreateDocumentLauncher("application/pdf") { uri ->
        val bytes = pendingBytes
        if (uri != null && bytes != null) {
            scope.launch {
                writeBytesToUri(context, uri, bytes)
                resultMessage = "Your PDF is now password protected."
            }
        }
        pendingBytes = null
    }

    val accent = LocalOffGridPalette.current.security
    ToolScaffold(
        title = "Protect PDF",
        accent = accent,
        pickedFileName = pickedUri?.lastPathSegment,
        onPickFile = { pickLauncher.launch(arrayOf("application/pdf")) },
        password = inputPassword,
        onPasswordChange = { inputPassword = it },
        runEnabled = pickedUri != null,
        running = running,
        onRun = {
            // ToolScaffold only invokes onRun while runEnabled (pickedUri
            // != null) is true.
            pickedUri?.let { uri ->
                if (newPassword != confirmPassword) {
                    resultMessage = "Passwords do not match — re-enter the password to confirm it."
                } else {
                    running = true
                    resultMessage = null
                    val baseName = suggestedBaseName(uri)

                    scope.launch {
                        when (val result = loadPdfFromUri(context, uri, inputPassword.ifBlank { null })) {
                            is PdfLoadResult.Success -> {
                                try {
                                    pendingBytes = if (restrict) {
                                        protectPdfWithPermissions(
                                            result.document,
                                            newPassword,
                                            permissionsPassword,
                                            PdfPermissions(print, extract, modify),
                                        )
                                    } else {
                                        protectPdf(result.document, newPassword)
                                    }
                                    lastResultBytes = pendingBytes
                                    saveLauncher.launch("${baseName}_protected.pdf")
                                } catch (e: IllegalArgumentException) {
                                    resultMessage = e.message
                                } finally {
                                    result.document.close()
                                }
                            }
                            PdfLoadResult.PasswordRequired -> {
                                resultMessage = if (inputPassword.isBlank()) {
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
            }
        },
        runLabel = if (running) "Protecting..." else "Protect PDF",
        resultMessage = resultMessage,
        chainableBytes = lastResultBytes,
        options = {
            Text("Adds a password to a PDF, so it can only be opened by someone who knows it. There is no way to recover a lost password — keep it somewhere safe.")

            OutlinedTextField(
                value = newPassword,
                onValueChange = { newPassword = it },
                label = { Text(if (restrict) "Open password (leave blank to let anyone open it)" else "Password") },
                visualTransformation = PasswordVisualTransformation(),
                modifier = Modifier.fillMaxWidth(),
            )
            OutlinedTextField(
                value = confirmPassword,
                onValueChange = { confirmPassword = it },
                label = { Text(if (restrict) "Confirm open password" else "Confirm password") },
                visualTransformation = PasswordVisualTransformation(),
                modifier = Modifier.fillMaxWidth(),
            )

            Row {
                Checkbox(checked = restrict, onCheckedChange = { restrict = it })
                Text("Restrict printing, copying, or editing")
            }

            if (restrict) {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        "PDF restrictions are enforced only for whoever opens the file with the " +
                            "open password above — a second, separate permissions password is " +
                            "needed to bypass them, so it must be different from the open " +
                            "password. This is an honor system followed by compliant PDF " +
                            "readers, not a hard security boundary: the content is still " +
                            "decryptable with the open password, so anyone with basic tooling " +
                            "can strip these restrictions. Use it to discourage casual copying " +
                            "or printing, not to protect secrets.",
                    )
                    OutlinedTextField(
                        value = permissionsPassword,
                        onValueChange = { permissionsPassword = it },
                        label = { Text("Permissions password (required, must differ from the open password)") },
                        visualTransformation = PasswordVisualTransformation(),
                        modifier = Modifier.fillMaxWidth(),
                    )

                    Text("Allow printing")
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        for (option in PrintPermission.entries) {
                            val label = when (option) {
                                PrintPermission.FULL -> "Full quality"
                                PrintPermission.LOW -> "Low resolution only"
                                PrintPermission.NONE -> "Not allowed"
                            }
                            if (option == print) {
                                Button(onClick = { print = option }) { Text(label) }
                            } else {
                                OutlinedButton(onClick = { print = option }) { Text(label) }
                            }
                        }
                    }

                    Text("Allow editing")
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        for (option in ModifyPermission.entries) {
                            val label = if (option == ModifyPermission.ALL) "Allowed" else "Not allowed"
                            if (option == modify) {
                                Button(onClick = { modify = option }) { Text(label) }
                            } else {
                                OutlinedButton(onClick = { modify = option }) { Text(label) }
                            }
                        }
                    }

                    Row {
                        Checkbox(checked = extract, onCheckedChange = { extract = it })
                        Text("Allow copying text and images")
                    }
                }
            }
        },
    )
}
