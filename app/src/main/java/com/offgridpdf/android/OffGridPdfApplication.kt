package com.offgridpdf.android

import android.app.Application
import com.offgridpdf.android.files.clearChainCache
import com.offgridpdf.android.ui.theme.ThemeState
import com.tom_roush.pdfbox.android.PDFBoxResourceLoader
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

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
 * renders a first frame in the wrong palette — and where the tool-chaining
 * cache is emptied (`files/ChainFile.kt`), so a document handed between
 * tools in a previous session isn't still on disk in this one.
 */
class OffGridPdfApplication : Application() {
    /**
     * For work that outlives any one screen and has nothing to cancel it —
     * currently just the startup cache sweep. `SupervisorJob` so a failure
     * in one such task can't take down the others.
     */
    private val appScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    override fun onCreate() {
        super.onCreate()
        PDFBoxResourceLoader.init(applicationContext)
        ThemeState.initialize(applicationContext)

        // Off the main thread: this is disk I/O, and onCreate blocks the
        // first frame. Nothing waits on it — a chain started in this
        // session writes its own file after this has already run.
        appScope.launch {
            runCatching { clearChainCache(applicationContext) }
        }
    }
}
