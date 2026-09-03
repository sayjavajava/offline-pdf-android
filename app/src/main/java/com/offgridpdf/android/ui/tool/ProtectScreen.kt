package com.offgridpdf.android.ui.tool

import android.net.Uri
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import com.offgridpdf.android.chain.ChainOrigin
import com.offgridpdf.android.chain.PendingFile
import com.offgridpdf.android.files.SavedFile
import com.offgridpdf.android.files.TOO_LARGE_MESSAGE
import com.offgridpdf.android.files.rememberCreateDocumentLauncher
import com.offgridpdf.android.files.rememberOpenDocumentLauncher
import com.offgridpdf.android.files.saveResult
import com.offgridpdf.android.files.savedFileOrNull
import com.offgridpdf.android.files.suggestedBaseName
import com.offgridpdf.android.pdf.ModifyPermission
import com.offgridpdf.android.pdf.PdfLoadResult
import com.offgridpdf.android.pdf.PdfPermissions
import com.offgridpdf.android.pdf.PrintPermission
import com.offgridpdf.android.pdf.loadPdfFromUri
import com.offgridpdf.android.pdf.protectPdf
import com.offgridpdf.android.pdf.protectPdfWithPermissions
import com.offgridpdf.android.ui.common.CheckboxRow
import com.offgridpdf.android.ui.common.NullableUriSaver
import com.offgridpdf.android.ui.common.OptionChip
import com.offgridpdf.android.ui.common.OptionChipRow
import com.offgridpdf.android.ui.common.SectionLabel
import com.offgridpdf.android.ui.common.ToolBodyText
import com.offgridpdf.android.ui.common.ToolTextField
import com.offgridpdf.android.ui.common.rememberDisplayName
import com.offgridpdf.android.ui.common.userMessageFor
import com.offgridpdf.android.ui.theme.LocalOffGridPalette
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

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

    var pickedUri by rememberSaveable(stateSaver = NullableUriSaver) { mutableStateOf(PendingFile.consume()) }
    var inheritedChainOrigin by rememberSaveable { mutableStateOf(ChainOrigin.consume()) }
    // Plain `remember`, deliberately: a document password is never written
    // to saved instance state (see `ui/common/Savers.kt`).
    var inputPassword by remember { mutableStateOf("") }
    var newPassword by remember { mutableStateOf("") }
    var confirmPassword by remember { mutableStateOf("") }
    var restrict by rememberSaveable { mutableStateOf(false) }
    var permissionsPassword by remember { mutableStateOf("") }
    var print by rememberSaveable { mutableStateOf(PrintPermission.FULL) }
    var modify by rememberSaveable { mutableStateOf(ModifyPermission.ALL) }
    var extract by rememberSaveable { mutableStateOf(true) }
    var running by remember { mutableStateOf(false) }
    var resultMessage by rememberSaveable { mutableStateOf<String?>(null) }
    // The file this run produced, once it is really on disk. Not
    // saveable: it holds the bytes, and a Bundle caps out around 1 MB.
    var savedFile by remember { mutableStateOf<SavedFile?>(null) }
    var pendingBytes by remember { mutableStateOf<ByteArray?>(null) }
    var lastResultBytes by remember { mutableStateOf<ByteArray?>(null) }
    var chainOriginBaseName by remember { mutableStateOf("") }
    var chainedFileName by remember { mutableStateOf("") }

    val pickLauncher = rememberOpenDocumentLauncher { uri ->
        pickedUri = uri
        inputPassword = ""
        resultMessage = null
        savedFile = null
        inheritedChainOrigin = null
    }

    val saveLauncher = rememberCreateDocumentLauncher("application/pdf") { uri ->
        val bytes = pendingBytes
        if (uri != null && bytes != null) {
            scope.launch {
                val outcome = saveResult(context, uri, bytes, "Your PDF is now password protected.")
                resultMessage = outcome.message
                savedFile = outcome.savedFileOrNull
            }
        }
        pendingBytes = null
    }

    val accent = LocalOffGridPalette.current.security
    ToolScaffold(
        title = "Protect PDF",
        accent = accent,
        pickedFileName = rememberDisplayName(pickedUri),
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
                    savedFile = null

                    scope.launch {
                        val baseName = suggestedBaseName(context, uri)
                        val originBaseName = inheritedChainOrigin ?: baseName
                        when (val result = loadPdfFromUri(context, uri, inputPassword.ifBlank { null })) {
                            is PdfLoadResult.Success -> {
                                try {
                                    pendingBytes = withContext(Dispatchers.Default) {
                                        if (restrict) {
                                            protectPdfWithPermissions(
                                                result.document,
                                                newPassword,
                                                permissionsPassword,
                                                PdfPermissions(print, extract, modify),
                                            )
                                        } else {
                                            protectPdf(result.document, newPassword)
                                        }
                                    }
                                    lastResultBytes = pendingBytes
                                    chainOriginBaseName = originBaseName
                                    chainedFileName = "${originBaseName}_protected.pdf"
                                    saveLauncher.launch("${baseName}_protected.pdf")
                                } catch (e: Exception) {
                                    resultMessage = userMessageFor(e)
                                } catch (e: OutOfMemoryError) {
                                    resultMessage = TOO_LARGE_MESSAGE
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
        savedFile = savedFile,
        chainableBytes = lastResultBytes,
        chainOriginBaseName = chainOriginBaseName,
        chainedFileName = chainedFileName,
        options = {
            ToolBodyText("Adds a password to a PDF, so it can only be opened by someone who knows it. There is no way to recover a lost password — keep it somewhere safe.")

            ToolTextField(
                value = newPassword,
                onValueChange = { newPassword = it },
                label = if (restrict) "Open password (leave blank to let anyone open it)" else "Password",
                accent = accent,
                visualTransformation = PasswordVisualTransformation(),
            )
            ToolTextField(
                value = confirmPassword,
                onValueChange = { confirmPassword = it },
                label = if (restrict) "Confirm open password" else "Confirm password",
                accent = accent,
                visualTransformation = PasswordVisualTransformation(),
            )

            CheckboxRow(
                checked = restrict,
                onCheckedChange = { restrict = it },
                label = "Restrict printing, copying, or editing",
                accent = accent,
            )

            if (restrict) {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    ToolBodyText(
                        "PDF restrictions are enforced only for whoever opens the file with the " +
                            "open password above — a second, separate permissions password is " +
                            "needed to bypass them, so it must be different from the open " +
                            "password. This is an honor system followed by compliant PDF " +
                            "readers, not a hard security boundary: the content is still " +
                            "decryptable with the open password, so anyone with basic tooling " +
                            "can strip these restrictions. Use it to discourage casual copying " +
                            "or printing, not to protect secrets.",
                    )
                    ToolTextField(
                        value = permissionsPassword,
                        onValueChange = { permissionsPassword = it },
                        label = "Permissions password (required, must differ from the open password)",
                        accent = accent,
                        visualTransformation = PasswordVisualTransformation(),
                    )

                    SectionLabel("Allow printing")
                    OptionChipRow {
                        for (option in PrintPermission.entries) {
                            OptionChip(
                                label = when (option) {
                                    PrintPermission.FULL -> "Full quality"
                                    PrintPermission.LOW -> "Low resolution only"
                                    PrintPermission.NONE -> "Not allowed"
                                },
                                selected = option == print,
                                accent = accent,
                                onClick = { print = option },
                            )
                        }
                    }

                    SectionLabel("Allow editing")
                    OptionChipRow {
                        for (option in ModifyPermission.entries) {
                            OptionChip(
                                label = if (option == ModifyPermission.ALL) "Allowed" else "Not allowed",
                                selected = option == modify,
                                accent = accent,
                                onClick = { modify = option },
                            )
                        }
                    }

                    CheckboxRow(
                        checked = extract,
                        onCheckedChange = { extract = it },
                        label = "Allow copying text and images",
                        accent = accent,
                    )
                }
            }
        },
    )
}
