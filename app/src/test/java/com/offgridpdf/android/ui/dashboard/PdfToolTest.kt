package com.offgridpdf.android.ui.dashboard

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class PdfToolTest {
    @Test
    fun `all four categories are represented in ToolCategory`() {
        assertEquals(4, ToolCategory.entries.size)
    }

    @Test
    fun `every tool has a unique id`() {
        val ids = pdfTools.map { it.id }
        assertEquals(ids.distinct(), ids)
    }

    @Test
    fun `split is registered under Organize Pages`() {
        val split = pdfTools.single { it.id == "split" }
        assertEquals(ToolCategory.OrganizePages, split.category)
        assertTrue(split.title.isNotBlank())
    }

    @Test
    fun `merge is registered under Organize Pages`() {
        val merge = pdfTools.single { it.id == "merge" }
        assertEquals(ToolCategory.OrganizePages, merge.category)
        assertTrue(merge.title.isNotBlank())
    }

    @Test
    fun `rotate is registered under Organize Pages`() {
        val rotate = pdfTools.single { it.id == "rotate" }
        assertEquals(ToolCategory.OrganizePages, rotate.category)
        assertTrue(rotate.title.isNotBlank())
    }

    @Test
    fun `rearrange is registered under Organize Pages`() {
        val rearrange = pdfTools.single { it.id == "rearrange" }
        assertEquals(ToolCategory.OrganizePages, rearrange.category)
        assertTrue(rearrange.title.isNotBlank())
    }

    @Test
    fun `crop-resize is registered under Organize Pages`() {
        val cropResize = pdfTools.single { it.id == "crop-resize" }
        assertEquals(ToolCategory.OrganizePages, cropResize.category)
        assertTrue(cropResize.title.isNotBlank())
    }

    @Test
    fun `protect is registered under Security`() {
        val protect = pdfTools.single { it.id == "protect" }
        assertEquals(ToolCategory.Security, protect.category)
        assertTrue(protect.title.isNotBlank())
    }

    @Test
    fun `unlock is registered under Security`() {
        val unlock = pdfTools.single { it.id == "unlock" }
        assertEquals(ToolCategory.Security, unlock.category)
        assertTrue(unlock.title.isNotBlank())
    }

    @Test
    fun `edit is registered under Edit and Enhance`() {
        val edit = pdfTools.single { it.id == "edit" }
        assertEquals(ToolCategory.EditEnhance, edit.category)
        assertTrue(edit.title.isNotBlank())
    }

    @Test
    fun `watermark is registered under Edit and Enhance`() {
        val watermark = pdfTools.single { it.id == "watermark" }
        assertEquals(ToolCategory.EditEnhance, watermark.category)
        assertTrue(watermark.title.isNotBlank())
    }

    @Test
    fun `page-numbers is registered under Edit and Enhance`() {
        val pageNumbers = pdfTools.single { it.id == "page-numbers" }
        assertEquals(ToolCategory.EditEnhance, pageNumbers.category)
        assertTrue(pageNumbers.title.isNotBlank())
    }

    @Test
    fun `images-to-pdf is registered under Convert and Export`() {
        val imagesToPdf = pdfTools.single { it.id == "images-to-pdf" }
        assertEquals(ToolCategory.ConvertExport, imagesToPdf.category)
        assertTrue(imagesToPdf.title.isNotBlank())
    }

    @Test
    fun `fill-form is registered under Edit and Enhance`() {
        val fillForm = pdfTools.single { it.id == "fill-form" }
        assertEquals(ToolCategory.EditEnhance, fillForm.category)
        assertTrue(fillForm.title.isNotBlank())
    }
}
