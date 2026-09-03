package com.offgridpdf.android.files

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test

/**
 * The name a share copy is written under comes from a content provider,
 * which is to say from outside this app. A name containing a path separator
 * would put the copy somewhere other than the share folder, so this is the
 * one piece of the share path worth pinning down directly.
 */
class ShareFileNameTest {

    @Test
    fun `an ordinary name is left alone`() {
        assertEquals("report_split.pdf", sanitizeFileName("report_split.pdf"))
    }

    @Test
    fun `spaces and unicode survive, because real filenames have them`() {
        assertEquals("My Report (final).pdf", sanitizeFileName("My Report (final).pdf"))
        assertEquals("informe_espanol.pdf", sanitizeFileName("informe_espanol.pdf"))
    }

    @Test
    fun `a path separator cannot escape the share folder`() {
        val cleaned = sanitizeFileName("../../databases/secrets.db")
        assertFalse("must not contain a separator: $cleaned", cleaned.contains("/"))
        assertFalse("must not start with a dot segment: $cleaned", cleaned.startsWith("."))
    }

    @Test
    fun `a backslash is treated the same as a forward slash`() {
        val cleaned = sanitizeFileName("..\\windows\\thing.pdf")
        assertFalse("must not contain a backslash: $cleaned", cleaned.contains("\\"))
    }

    @Test
    fun `control characters are replaced rather than written into a name`() {
        val cleaned = sanitizeFileName("odd\u0000name\u000a.pdf")
        assertFalse(cleaned.any { it.isISOControl() })
    }

    @Test
    fun `a name that sanitizes away still produces something writable`() {
        assertEquals("document", sanitizeFileName(""))
        assertEquals("document", sanitizeFileName("   "))
        assertEquals("document", sanitizeFileName("..."))
    }
}
