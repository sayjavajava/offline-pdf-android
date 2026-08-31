package com.offgridpdf.android.pdf

import com.tom_roush.pdfbox.cos.COSName
import com.tom_roush.pdfbox.pdmodel.PDDocument
import com.tom_roush.pdfbox.pdmodel.PDPage
import com.tom_roush.pdfbox.pdmodel.PDResources
import com.tom_roush.pdfbox.pdmodel.graphics.color.PDDeviceRGB
import com.tom_roush.pdfbox.pdmodel.graphics.image.PDImageXObject
import org.junit.Assert.assertEquals
import org.junit.Test
import java.io.ByteArrayInputStream

/**
 * Web reference: `compressPdf` (`qpdf-engine.ts`), verified against
 * `CompressTool.tsx`. Real image recompression quality/size cannot be
 * verified here — see `PdfCompress.kt`'s doc comment for why (the same
 * `android.graphics.Bitmap`/`BitmapFactory` gap as A-12/A-15). What *is*
 * verified: the walk doesn't throw, a document with no images comes back
 * as a safe no-op, and the result stays a valid, loadable PDF either way.
 */
class PdfCompressTest {

    @Test
    fun `a document with no images is returned unchanged in page count and stays a valid PDF`() {
        val document = PDDocument()
        document.addPage(PDPage())
        document.addPage(PDPage())

        val result = compressPdf(document)
        document.close()

        val reloaded = PDDocument.load(ByteArrayInputStream(result))
        assertEquals(2, reloaded.numberOfPages)
        reloaded.close()
    }

    @Test
    fun `a document with a real embedded image does not throw and stays a valid, loadable PDF`() {
        val document = PDDocument()
        val page = PDPage()
        document.addPage(page)

        val jpegBytes = byteArrayOf(0xFF.toByte(), 0xD8.toByte(), 1, 2, 3, 4, 0xFF.toByte(), 0xD9.toByte())
        val xObject = PDImageXObject(
            document,
            ByteArrayInputStream(jpegBytes),
            COSName.DCT_DECODE,
            10,
            10,
            8,
            PDDeviceRGB.INSTANCE,
        )
        val resources = PDResources()
        resources.put(COSName.getPDFName("Im0"), xObject)
        page.resources = resources

        val result = compressPdf(document)
        document.close()

        val reloaded = PDDocument.load(ByteArrayInputStream(result))
        assertEquals(1, reloaded.numberOfPages)
        reloaded.close()
    }
}
