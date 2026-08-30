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
}
