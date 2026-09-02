package com.offgridpdf.android.ui.tool

import android.net.Uri
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import com.offgridpdf.android.ui.common.ScreenTopBar
import com.offgridpdf.android.ui.theme.LocalOffGridPalette
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.offgridpdf.android.files.rememberCreateDocumentLauncher
import com.offgridpdf.android.files.rememberOpenDocumentLauncher
import com.offgridpdf.android.files.suggestedBaseName
import com.offgridpdf.android.files.writeBytesToUri
import com.offgridpdf.android.pdf.FormFieldInfo
import com.offgridpdf.android.pdf.PdfLoadResult
import com.offgridpdf.android.pdf.fillFormFields
import com.offgridpdf.android.pdf.loadPdfFromUri
import com.offgridpdf.android.pdf.readFormFields
import com.tom_roush.pdfbox.pdmodel.PDDocument
import kotlinx.coroutines.launch

/**
 * Web reference: `FillFormTool.tsx` + `readFormFields`/`applyFormFieldValues`
 * (`pdf-forms.ts`), `getFormFields`/`fillFormFields` (`pdf-ops.ts`).
 *
 * A two-phase flow, not `ToolScaffold`: pick a file and load its fields
 * first, *then* fill them in — the options depend on what's actually in
 * this specific PDF, so there's no fixed options slot to render up front,
 * matching the web tool's own load-then-fill shape.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FillFormScreen() {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    var pickedUri by remember { mutableStateOf<Uri?>(null) }
    var password by remember { mutableStateOf("") }
    var loadingFields by remember { mutableStateOf(false) }
    var filling by remember { mutableStateOf(false) }
    var openDocument by remember { mutableStateOf<PDDocument?>(null) }
    var fields by remember { mutableStateOf<List<FormFieldInfo>?>(null) }
    var unsupportedFields by remember { mutableStateOf<List<String>>(emptyList()) }
    var values by remember { mutableStateOf<Map<String, Any>>(emptyMap()) }
    var flatten by remember { mutableStateOf(true) }
    var resultMessage by remember { mutableStateOf<String?>(null) }
    var pendingBytes by remember { mutableStateOf<ByteArray?>(null) }

    fun reset() {
        openDocument?.close()
        openDocument = null
        fields = null
        unsupportedFields = emptyList()
        values = emptyMap()
        resultMessage = null
    }

    val pickLauncher = rememberOpenDocumentLauncher { uri ->
        pickedUri = uri
        password = ""
        reset()
    }

    val saveLauncher = rememberCreateDocumentLauncher("application/pdf") { uri ->
        val bytes = pendingBytes
        if (uri != null && bytes != null) {
            scope.launch {
                writeBytesToUri(context, uri, bytes)
                resultMessage = "The form has been filled."
            }
        }
        pendingBytes = null
    }

    Scaffold(
        topBar = { ScreenTopBar(title = "Fill PDF Forms") },
        containerColor = LocalOffGridPalette.current.paper,
    ) { innerPadding ->
        Column(
            modifier = Modifier.fillMaxSize().padding(innerPadding).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text(
                "Fill in a PDF's fillable fields — text boxes, checkboxes, dropdowns, and radio " +
                    "buttons — and download the result. Choose to flatten the form so it looks " +
                    "identical in every reader, or leave it editable so the fields can still be " +
                    "changed later.",
            )

            val currentFields = fields
            if (currentFields == null) {
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
                Button(
                    onClick = {
                        pickedUri?.let { uri ->
                            loadingFields = true
                            resultMessage = null
                            scope.launch {
                                when (val result = loadPdfFromUri(context, uri, password.ifBlank { null })) {
                                    is PdfLoadResult.Success -> {
                                        val loaded = readFormFields(result.document)
                                        openDocument = result.document
                                        fields = loaded.fields
                                        unsupportedFields = loaded.unsupportedFields
                                        values = loaded.fields.associate { info ->
                                            info.name to when (info) {
                                                is FormFieldInfo.Text -> info.value
                                                is FormFieldInfo.Checkbox -> info.value
                                                is FormFieldInfo.Dropdown -> info.value
                                                is FormFieldInfo.Radio -> info.value
                                            }
                                        }
                                    }
                                    PdfLoadResult.PasswordRequired -> {
                                        resultMessage = if (password.isBlank()) {
                                            "This PDF needs a password."
                                        } else {
                                            "Wrong password — try again."
                                        }
                                    }
                                    is PdfLoadResult.Failure -> resultMessage = result.message
                                }
                                loadingFields = false
                            }
                        }
                    },
                    enabled = pickedUri != null && !loadingFields,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text(if (loadingFields) "Reading form fields..." else "Load Form Fields")
                }
            } else {
                OutlinedButton(onClick = { reset() }, modifier = Modifier.fillMaxWidth()) {
                    Text("Choose a different file")
                }

                if (currentFields.isEmpty() && unsupportedFields.isEmpty()) {
                    Text("This PDF has no fillable form fields.")
                }
                if (unsupportedFields.isNotEmpty()) {
                    Text(
                        "${unsupportedFields.size} field(s) in this PDF (${unsupportedFields.joinToString(", ")}) " +
                            "are a type this tool doesn't edit (buttons, option lists, or signature " +
                            "fields) and will be left as-is.",
                    )
                }

                for (field in currentFields) {
                    when (field) {
                        is FormFieldInfo.Text -> OutlinedTextField(
                            value = values[field.name] as? String ?: "",
                            onValueChange = { values = values + (field.name to it) },
                            label = { Text(field.name) },
                            enabled = !field.readOnly,
                            modifier = Modifier.fillMaxWidth(),
                        )
                        is FormFieldInfo.Checkbox -> Row {
                            Checkbox(
                                checked = values[field.name] as? Boolean ?: false,
                                onCheckedChange = { values = values + (field.name to it) },
                                enabled = !field.readOnly,
                            )
                            Text(field.name)
                        }
                        is FormFieldInfo.Dropdown -> Column {
                            Text(field.name)
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                // "(none)" matches the web tool's own option --
                                // an empty value leaves the field's current
                                // selection untouched rather than clearing it
                                // (see applyFormFieldValues's dropdown case).
                                for (option in listOf("(none)") + field.options) {
                                    val actualValue = if (option == "(none)") "" else option
                                    val selected = (values[field.name] as? String ?: "") == actualValue
                                    if (selected) {
                                        Button(onClick = { values = values + (field.name to actualValue) }) { Text(option) }
                                    } else {
                                        OutlinedButton(
                                            onClick = { values = values + (field.name to actualValue) },
                                            enabled = !field.readOnly,
                                        ) { Text(option) }
                                    }
                                }
                            }
                        }
                        is FormFieldInfo.Radio -> Column {
                            Text(field.name)
                            for (option in field.options) {
                                Row {
                                    RadioButton(
                                        selected = values[field.name] as? String == option,
                                        onClick = { values = values + (field.name to option) },
                                        enabled = !field.readOnly,
                                    )
                                    Text(option)
                                }
                            }
                        }
                    }
                }

                if (currentFields.isNotEmpty()) {
                    Row {
                        Checkbox(checked = flatten, onCheckedChange = { flatten = it })
                        Text("Flatten form after filling (fields can no longer be edited)")
                    }
                }

                Button(
                    onClick = {
                        val document = openDocument ?: return@Button
                        val uri = pickedUri ?: return@Button
                        filling = true
                        resultMessage = null
                        val baseName = suggestedBaseName(uri)
                        val filledValues = values
                        scope.launch {
                            try {
                                pendingBytes = fillFormFields(document, filledValues, flatten)
                                saveLauncher.launch("${baseName}_filled.pdf")
                            } catch (e: IllegalArgumentException) {
                                resultMessage = e.message
                            } finally {
                                document.close()
                                openDocument = null
                            }
                            filling = false
                        }
                    },
                    enabled = !filling,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text(if (filling) "Filling..." else "Fill & Download")
                }
            }

            if (loadingFields || filling) {
                CircularProgressIndicator()
            }

            resultMessage?.let { Text(it) }
        }
    }
}
