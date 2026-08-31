package com.offgridpdf.android.pdf

import com.tom_roush.pdfbox.pdmodel.PDDocument
import com.tom_roush.pdfbox.pdmodel.interactive.form.PDCheckBox
import com.tom_roush.pdfbox.pdmodel.interactive.form.PDComboBox
import com.tom_roush.pdfbox.pdmodel.interactive.form.PDRadioButton
import com.tom_roush.pdfbox.pdmodel.interactive.form.PDTerminalField
import com.tom_roush.pdfbox.pdmodel.interactive.form.PDTextField
import java.io.ByteArrayOutputStream

/**
 * Four field types are supported: text, checkbox, dropdown, radio. A real
 * PDF can also carry push buttons, list boxes (multi-select), and
 * signature fields — those are reported by name in [FormFieldsResult.unsupportedFields]
 * rather than silently dropped or crashed on, same "disclose what we
 * can't do" choice this project already makes elsewhere. Web reference:
 * `FormFieldInfo`/`FormFieldsResult` (`pdf-forms.ts`).
 */
sealed class FormFieldInfo {
    abstract val name: String
    abstract val readOnly: Boolean

    data class Text(
        override val name: String,
        val value: String,
        override val readOnly: Boolean,
        val maxLength: Int?,
    ) : FormFieldInfo()

    data class Checkbox(override val name: String, val value: Boolean, override val readOnly: Boolean) : FormFieldInfo()

    data class Dropdown(
        override val name: String,
        val value: String,
        val options: List<String>,
        override val readOnly: Boolean,
    ) : FormFieldInfo()

    data class Radio(
        override val name: String,
        val value: String,
        val options: List<String>,
        override val readOnly: Boolean,
    ) : FormFieldInfo()
}

data class FormFieldsResult(val fields: List<FormFieldInfo>, val unsupportedFields: List<String>)

/**
 * Reads every AcroForm field in [document] into a plain result the UI can
 * render generically — pure, no save. A PDF with no AcroForm at all
 * (`documentCatalog.acroForm == null`) returns an empty result, same as
 * the web version's `getForm()`: it's honest that there's nothing to
 * fill, not an error. Web reference: `readFormFields` (`pdf-forms.ts`).
 *
 * Only *terminal* fields (an actual text box, checkbox, etc.) are
 * considered — `PDAcroForm.getFieldTree()` also yields non-terminal
 * group/organizational nodes (`PDNonTerminalField`) that have no value or
 * widget of their own and aren't something a user could fill in, so
 * those are skipped entirely rather than reported as unsupported.
 */
fun readFormFields(document: PDDocument): FormFieldsResult {
    val form = document.documentCatalog.acroForm ?: return FormFieldsResult(emptyList(), emptyList())
    val fields = mutableListOf<FormFieldInfo>()
    val unsupported = mutableListOf<String>()

    for (field in form.fieldTree) {
        if (field !is PDTerminalField) continue
        val name = field.fullyQualifiedName ?: continue
        val readOnly = field.isReadOnly

        when (field) {
            is PDTextField -> fields.add(
                FormFieldInfo.Text(name, field.value ?: "", readOnly, field.maxLen.takeIf { it > 0 }),
            )
            is PDCheckBox -> fields.add(FormFieldInfo.Checkbox(name, field.isChecked, readOnly))
            is PDComboBox -> fields.add(
                FormFieldInfo.Dropdown(name, field.value.firstOrNull().orEmpty(), field.options, readOnly),
            )
            is PDRadioButton -> {
                val current = field.value
                fields.add(
                    FormFieldInfo.Radio(
                        name,
                        if (current == "Off") "" else current,
                        field.onValues.toList(),
                        readOnly,
                    ),
                )
            }
            else -> unsupported.add(name)
        }
    }

    return FormFieldsResult(fields, unsupported)
}

/**
 * Applies [values] (keyed by each field's fully-qualified name, as
 * returned by [readFormFields]) onto [document]'s AcroForm fields in
 * place. A value for a field name that no longer exists, or of the wrong
 * Kotlin type for that field's kind, is silently skipped — the same
 * "match by name, ignore what doesn't apply" this codebase already uses
 * for redaction boxes keyed by page number on the web side. Web
 * reference: `applyFormFieldValues` (`pdf-forms.ts`).
 *
 * A dropdown value of `""` leaves the current selection untouched (it's
 * not a valid option to select); a radio value of `""` clears the
 * selection entirely (`setValue("Off")`, the PDF spec's own default
 * "nothing selected" export value) — same asymmetry the web version's
 * `applyFormFieldValues` has, ported directly rather than "fixed" to be
 * consistent, since it's the real behavior being matched.
 */
fun applyFormFieldValues(document: PDDocument, values: Map<String, Any>) {
    val form = document.documentCatalog.acroForm ?: return

    for (field in form.fieldTree) {
        if (field !is PDTerminalField) continue
        val name = field.fullyQualifiedName ?: continue
        if (name !in values) continue
        val value = values.getValue(name)

        when {
            field is PDTextField && value is String -> field.setValue(value)
            field is PDCheckBox && value is Boolean -> if (value) field.check() else field.unCheck()
            field is PDComboBox && value is String -> if (value.isNotEmpty()) field.setValue(value)
            field is PDRadioButton && value is String -> field.setValue(value.ifEmpty { "Off" })
        }
    }
}

/**
 * Fills [document]'s AcroForm fields with [values] and, when [flatten] is
 * true, bakes the filled appearances into each page's own content stream
 * and removes the form fields (`PDAcroForm.flatten()`) — the same
 * "commit to pixels once you're done editing" tradeoff Redact PDF makes
 * for its boxes on the web side, useful here so the result looks
 * identical in every PDF reader rather than depending on that reader's
 * own form-rendering. Web reference: `fillFormFields` (`pdf-ops.ts`).
 */
fun fillFormFields(document: PDDocument, values: Map<String, Any>, flatten: Boolean = false): ByteArray {
    applyFormFieldValues(document, values)
    if (flatten) {
        document.documentCatalog.acroForm?.flatten()
    }

    val out = ByteArrayOutputStream()
    document.save(out)
    return out.toByteArray()
}
