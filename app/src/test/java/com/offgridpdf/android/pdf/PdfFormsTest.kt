package com.offgridpdf.android.pdf

import com.tom_roush.pdfbox.cos.COSDictionary
import com.tom_roush.pdfbox.cos.COSName
import com.tom_roush.pdfbox.pdmodel.PDDocument
import com.tom_roush.pdfbox.pdmodel.PDPage
import com.tom_roush.pdfbox.pdmodel.PDResources
import com.tom_roush.pdfbox.pdmodel.common.PDRectangle
import com.tom_roush.pdfbox.pdmodel.font.PDType1Font
import com.tom_roush.pdfbox.pdmodel.interactive.annotation.PDAnnotationWidget
import com.tom_roush.pdfbox.pdmodel.interactive.annotation.PDAppearanceDictionary
import com.tom_roush.pdfbox.pdmodel.interactive.annotation.PDAppearanceEntry
import com.tom_roush.pdfbox.pdmodel.interactive.annotation.PDAppearanceStream
import com.tom_roush.pdfbox.pdmodel.interactive.form.PDAcroForm
import com.tom_roush.pdfbox.pdmodel.interactive.form.PDCheckBox
import com.tom_roush.pdfbox.pdmodel.interactive.form.PDComboBox
import com.tom_roush.pdfbox.pdmodel.interactive.form.PDPushButton
import com.tom_roush.pdfbox.pdmodel.interactive.form.PDRadioButton
import com.tom_roush.pdfbox.pdmodel.interactive.form.PDTerminalField
import com.tom_roush.pdfbox.pdmodel.interactive.form.PDTextField
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.ByteArrayInputStream

/** Web reference: `readFormFields`/`applyFormFieldValues` (`pdf-forms.ts`), verified against `FillFormTool.tsx`. */
class PdfFormsTest {

    private fun buildFormDocument(): Pair<PDDocument, PDAcroForm> {
        val document = PDDocument()
        val page = PDPage(PDRectangle.LETTER)
        document.addPage(page)

        val acroForm = PDAcroForm(document)
        document.documentCatalog.acroForm = acroForm
        val resources = PDResources()
        resources.put(COSName.getPDFName("Helv"), PDType1Font.HELVETICA)
        acroForm.defaultResources = resources
        acroForm.setDefaultAppearance("/Helv 0 Tf 0 g")

        return document to acroForm
    }

    private fun attachWidget(page: PDPage, acroForm: PDAcroForm, field: PDTerminalField): PDAnnotationWidget {
        val widget = field.widgets[0]
        widget.rectangle = PDRectangle(10f, 10f, 100f, 20f)
        widget.page = page
        page.annotations.add(widget)
        acroForm.fields.add(field)
        return widget
    }

    /**
     * Real PDF checkbox/radio fields need a normal-appearance (AP/N)
     * dictionary naming their "on" state -- `getOnValue()`/`getOnValues()`
     * read that dictionary's keys directly, and without one they'd
     * degrade to an empty string rather than a real export value. The
     * appearance streams themselves are empty; only the key names matter
     * for what this test actually verifies (the underlying field value).
     */
    private fun addOnOffAppearance(document: PDDocument, widget: PDAnnotationWidget, onName: String) {
        val subDictionary = COSDictionary()
        subDictionary.setItem(COSName.getPDFName(onName), PDAppearanceStream(document).cosObject)
        subDictionary.setItem(COSName.Off, PDAppearanceStream(document).cosObject)
        val apDictionary = PDAppearanceDictionary()
        apDictionary.setNormalAppearance(PDAppearanceEntry(subDictionary))
        widget.appearance = apDictionary
        widget.setAppearanceState(COSName.Off.name)
    }

    // --- readFormFields ------------------------------------------------

    @Test
    fun `a PDF with no AcroForm returns an empty result, not an error`() {
        val document = PDDocument()
        document.addPage(PDPage())
        val result = readFormFields(document)
        document.close()

        assertEquals(emptyList<FormFieldInfo>(), result.fields)
        assertEquals(emptyList<String>(), result.unsupportedFields)
    }

    @Test
    fun `reads a text field's value, read-only flag, and max length`() {
        val (document, acroForm) = buildFormDocument()
        val page = document.getPage(0)
        val field = PDTextField(acroForm)
        field.partialName = "name"
        field.setMaxLen(40)
        attachWidget(page, acroForm, field)
        field.setValue("Ada Lovelace")

        val result = readFormFields(document)
        document.close()

        val text = result.fields.single() as FormFieldInfo.Text
        assertEquals("name", text.name)
        assertEquals("Ada Lovelace", text.value)
        assertFalse(text.readOnly)
        assertEquals(40, text.maxLength)
    }

    @Test
    fun `reads a checkbox's checked state`() {
        val (document, acroForm) = buildFormDocument()
        val page = document.getPage(0)
        val field = PDCheckBox(acroForm)
        field.partialName = "subscribe"
        val widget = attachWidget(page, acroForm, field)
        addOnOffAppearance(document, widget, "Yes")
        field.check()

        val result = readFormFields(document)
        document.close()

        val checkbox = result.fields.single() as FormFieldInfo.Checkbox
        assertEquals("subscribe", checkbox.name)
        assertTrue(checkbox.value)
    }

    @Test
    fun `reads a dropdown's selected value and options`() {
        val (document, acroForm) = buildFormDocument()
        val page = document.getPage(0)
        val field = PDComboBox(acroForm)
        field.partialName = "country"
        field.setOptions(listOf("US", "CA", "MX"))
        attachWidget(page, acroForm, field)
        field.setValue("CA")

        val result = readFormFields(document)
        document.close()

        val dropdown = result.fields.single() as FormFieldInfo.Dropdown
        assertEquals("country", dropdown.name)
        assertEquals("CA", dropdown.value)
        assertEquals(listOf("US", "CA", "MX"), dropdown.options)
    }

    @Test
    fun `reads a radio field's selected value, empty when unselected`() {
        val (document, acroForm) = buildFormDocument()
        val page = document.getPage(0)
        val field = PDRadioButton(acroForm)
        field.partialName = "size"
        val widget = attachWidget(page, acroForm, field)
        addOnOffAppearance(document, widget, "Large")

        val beforeSelection = readFormFields(document).fields.single() as FormFieldInfo.Radio
        assertEquals("", beforeSelection.value)

        field.setValue("Large")
        val afterSelection = readFormFields(document).fields.single() as FormFieldInfo.Radio
        assertEquals("Large", afterSelection.value)
        document.close()
    }

    @Test
    fun `reports a push button as unsupported rather than skipping or crashing`() {
        val (document, acroForm) = buildFormDocument()
        val page = document.getPage(0)
        val field = PDPushButton(acroForm)
        field.partialName = "submit"
        attachWidget(page, acroForm, field)

        val result = readFormFields(document)
        document.close()

        assertTrue(result.fields.isEmpty())
        assertEquals(listOf("submit"), result.unsupportedFields)
    }

    // --- applyFormFieldValues / fillFormFields --------------------------

    @Test
    fun `fills a text field and the value round-trips`() {
        val (document, acroForm) = buildFormDocument()
        val page = document.getPage(0)
        val field = PDTextField(acroForm)
        field.partialName = "name"
        attachWidget(page, acroForm, field)

        val bytes = fillFormFields(document, mapOf("name" to "Grace Hopper"))
        document.close()

        PDDocument.load(ByteArrayInputStream(bytes)).use { reloaded ->
            val text = readFormFields(reloaded).fields.single() as FormFieldInfo.Text
            assertEquals("Grace Hopper", text.value)
        }
    }

    @Test
    fun `an empty dropdown value leaves the current selection untouched`() {
        val (document, acroForm) = buildFormDocument()
        val page = document.getPage(0)
        val field = PDComboBox(acroForm)
        field.partialName = "country"
        field.setOptions(listOf("US", "CA"))
        attachWidget(page, acroForm, field)
        field.setValue("US")

        applyFormFieldValues(document, mapOf("country" to ""))

        val dropdown = readFormFields(document).fields.single() as FormFieldInfo.Dropdown
        document.close()
        assertEquals("US", dropdown.value)
    }

    @Test
    fun `an empty radio value clears the selection`() {
        val (document, acroForm) = buildFormDocument()
        val page = document.getPage(0)
        val field = PDRadioButton(acroForm)
        field.partialName = "size"
        val widget = attachWidget(page, acroForm, field)
        addOnOffAppearance(document, widget, "Large")
        field.setValue("Large")

        applyFormFieldValues(document, mapOf("size" to ""))

        val radio = readFormFields(document).fields.single() as FormFieldInfo.Radio
        document.close()
        assertEquals("", radio.value)
    }

    @Test
    fun `a value for a field name that no longer exists is silently skipped`() {
        val (document, acroForm) = buildFormDocument()
        val page = document.getPage(0)
        val field = PDTextField(acroForm)
        field.partialName = "name"
        attachWidget(page, acroForm, field)

        // Should not throw for a key with no matching field.
        applyFormFieldValues(document, mapOf("nonexistent" to "value"))
        document.close()
    }

    @Test
    fun `flatten removes the AcroForm's fields`() {
        val (document, acroForm) = buildFormDocument()
        val page = document.getPage(0)
        val field = PDTextField(acroForm)
        field.partialName = "name"
        attachWidget(page, acroForm, field)

        val bytes = fillFormFields(document, mapOf("name" to "Ada"), flatten = true)
        document.close()

        PDDocument.load(ByteArrayInputStream(bytes)).use { reloaded ->
            val form = reloaded.documentCatalog.acroForm
            assertTrue(form == null || form.fields.isEmpty())
        }
    }

    @Test
    fun `unflattened output keeps the field editable`() {
        val (document, acroForm) = buildFormDocument()
        val page = document.getPage(0)
        val field = PDTextField(acroForm)
        field.partialName = "name"
        attachWidget(page, acroForm, field)

        val bytes = fillFormFields(document, mapOf("name" to "Ada"), flatten = false)
        document.close()

        PDDocument.load(ByteArrayInputStream(bytes)).use { reloaded ->
            val text = readFormFields(reloaded).fields.single() as FormFieldInfo.Text
            assertEquals("Ada", text.value)
        }
    }
}
