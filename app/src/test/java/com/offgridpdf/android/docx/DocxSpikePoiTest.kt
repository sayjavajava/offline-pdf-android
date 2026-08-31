package com.offgridpdf.android.docx

import org.apache.poi.xwpf.usermodel.XWPFDocument
import org.junit.Assert.assertEquals
import org.junit.Test
import java.io.ByteArrayInputStream

/**
 * Spike B, approach 2: read a real `.docx` via Apache POI's `XWPFDocument`
 * (`poi-ooxml`, added to `app/build.gradle.kts` as `implementation` so
 * `assembleDebug` — already part of CI — exercises real D8/R8 dexing, not
 * just this JVM unit test). See `CODE_AUDIT.md`'s Spike B write-up for the
 * real, measured comparison against approach 1 (`DocxSpikeXmlPullTest`)
 * and the decision.
 */
class DocxSpikePoiTest {

    @Test
    fun `reads paragraph text out of a real docx via XWPFDocument`() {
        val document = XWPFDocument(ByteArrayInputStream(DocxSpikeFixture.build()))
        val extracted = document.paragraphs.map { it.text }
        document.close()

        assertEquals(DocxSpikeFixture.paragraphs, extracted)
    }
}
