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
}
