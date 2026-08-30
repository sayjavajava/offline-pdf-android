package com.offgridpdf.android.ui.dashboard

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Trivial by design — this scaffolding PR (A-1) ships no tool logic. Its
 * only job is proving `testDebugUnitTest` actually runs a plain-JUnit test
 * with no Robolectric/emulator involved, the same "plain javac/java, no
 * Android runtime needed" path `NATIVE_ANDROID_SPIKE.md` verified for
 * PdfBox-Android itself.
 */
class PdfToolTest {
    @Test
    fun `all four categories are represented in ToolCategory`() {
        assertEquals(4, ToolCategory.entries.size)
    }

    @Test
    fun `pdfTools is empty until the first tool PR adds an entry`() {
        assertTrue(pdfTools.isEmpty())
    }
}
