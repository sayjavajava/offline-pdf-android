package com.offgridpdf.android

import android.app.Application
import com.offgridpdf.android.ui.theme.ThemeState
import com.tom_roush.pdfbox.android.PDFBoxResourceLoader

/**
 * PdfBox-Android ships its standard-14 font metrics (Helvetica and friends
 * — used by every tool that draws text: Watermark, Page Numbers, Convert
 * DOCX to PDF, Add Signature's typed mode, ...) as bundled assets, read
 * through `PDFBoxResourceLoader`, not the JVM classpath the library's own
 * desktop-PDFBox ancestor would use. Without `init(Context)` called once,
 * first, `PDFBoxResourceLoader.getStream()` logs an error and then throws
 * a `NullPointerException` on its still-null `AssetManager` (confirmed
 * against the library's real pinned-version source) — a real crash on
 * every text-drawing tool's first real run, on every device, missed until
 * now because CI has only ever run JVM-stubbed unit tests, never a real
 * Android runtime (see Spike A, `ANDROID_IMPLEMENTATION_PLAN.md`).
 *
 * Also where [ThemeState] loads the user's saved theme choice — once, here,
 * before `MainActivity` composes anything, so `OffGridPdfTheme` never
 * renders a first frame in the wrong palette.
 */
class OffGridPdfApplication : Application() {
    override fun onCreate() {
        super.onCreate()
        PDFBoxResourceLoader.init(applicationContext)
        ThemeState.initialize(applicationContext)
    }
}
