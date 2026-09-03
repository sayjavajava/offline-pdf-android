package com.offgridpdf.android.spike

import android.app.Activity
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Canvas
import android.util.Base64
import android.util.Log
import androidx.test.core.app.ActivityScenario
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.offgridpdf.android.MainActivity
import com.offgridpdf.android.ui.dashboard.ACTION_OPEN_TOOL
import com.offgridpdf.android.ui.dashboard.EXTRA_TOOL_ID
import org.junit.Test
import org.junit.runner.RunWith
import java.io.ByteArrayOutputStream

/**
 * Ad hoc, one-off: a real screenshot of the app's actual UI on a real
 * device, requested directly rather than tied to any plan item. Not a
 * permanent regression suite -- same framing as `VisualCheckSpikeTest.kt`,
 * whose `logPreview` (base64-chunked-via-logcat) technique this reuses,
 * since this project's own established finding still holds:
 * `connectedDebugAndroidTest` uninstalls the app -- wiping its private
 * storage -- before any later script step could read a saved screenshot
 * file back (`run-spike-a.sh`'s own header comment).
 *
 * Real CI failure, root-caused and fixed: the first version captured via
 * `PixelCopy.request(Window, ...)`, which failed with "did not report
 * SUCCESS" on the real CI emulator -- most likely a software-rendered
 * (SwiftShader) surface-compositor timing issue `PixelCopy`'s hardware-
 * surface-dependent async path is sensitive to, not a code bug (the app
 * itself built and launched fine). Switched to `View.draw(Canvas)` on the
 * decor view instead -- a synchronous, always-available technique that
 * replays the View hierarchy's own draw calls into a software canvas, with
 * no dependency on the window compositor or a real GPU surface. Compose's
 * `AndroidComposeView` renders through the standard View drawing pipeline
 * like any other View, so this captures real Compose content correctly.
 */
@RunWith(AndroidJUnit4::class)
class UiScreenshotSpikeTest {

    companion object {
        private const val TAG = "UiScreenshot"
    }

    private fun captureWindow(activity: Activity): Bitmap {
        val decorView = activity.window.decorView
        val bitmap = Bitmap.createBitmap(decorView.width, decorView.height, Bitmap.Config.ARGB_8888)
        decorView.draw(Canvas(bitmap))
        return bitmap
    }

    private fun logPreview(name: String, bitmap: Bitmap) {
        val maxWidth = 400
        val scale = maxWidth.toFloat() / bitmap.width
        val scaled = if (scale < 1f) {
            Bitmap.createScaledBitmap(bitmap, maxWidth, (bitmap.height * scale).toInt().coerceAtLeast(1), true)
        } else {
            bitmap
        }
        val out = ByteArrayOutputStream()
        scaled.compress(Bitmap.CompressFormat.JPEG, 82, out)
        val encoded = Base64.encodeToString(out.toByteArray(), Base64.NO_WRAP)

        Log.i(TAG, "===PREVIEW $name (${scaled.width}x${scaled.height}, ${out.size()} bytes jpeg)===")
        var index = 0
        var offset = 0
        val chunkSize = 2800
        while (offset < encoded.length) {
            val end = (offset + chunkSize).coerceAtMost(encoded.length)
            Log.i(TAG, "CHUNK $name $index ${encoded.substring(offset, end)}")
            offset = end
            index++
        }
        Log.i(TAG, "===END PREVIEW $name ($index chunks)===")
    }

    @Test
    fun capturesTheRealDashboardScreen() {
        val scenario = ActivityScenario.launch(MainActivity::class.java)
        Thread.sleep(1500) // let the first Compose layout/composition pass settle
        var bitmap: Bitmap? = null
        scenario.onActivity { activity -> bitmap = captureWindow(activity) }
        logPreview("dashboard", requireNotNull(bitmap) { "dashboard capture failed" })
        scenario.close()
    }

    /**
     * Opens one tool screen directly, the way a launcher shortcut does
     * (`ShortcutsManager`'s own ACTION_OPEN_TOOL intent), and captures it.
     */
    private fun captureTool(toolId: String) {
        val intent = Intent(ApplicationProvider.getApplicationContext(), MainActivity::class.java)
            .setAction(ACTION_OPEN_TOOL)
            .putExtra(EXTRA_TOOL_ID, toolId)
        val scenario = ActivityScenario.launch<MainActivity>(intent)
        Thread.sleep(1800) // first composition + the navigate-to-tool hop
        var bitmap: Bitmap? = null
        scenario.onActivity { activity -> bitmap = captureWindow(activity) }
        logPreview(toolId, requireNotNull(bitmap) { "$toolId capture failed" })
        scenario.close()
    }

    /**
     * The screens the form/layout pass changed, captured on a real device
     * so the claims about them can be checked rather than asserted:
     *
     * - `page-numbers` had six position options in a non-wrapping Row, four
     *   of them off the right edge; `crop-resize` had its "Custom" paper
     *   size (and so its whole custom-dimensions feature) off the edge the
     *   same way. Both should now wrap onto as many lines as they need.
     * - `merge` and `fill-form` never got the redesign at all: default
     *   Material buttons, no spacing between controls, and no scrolling.
     *
     * One test rather than four so the emulator boots once.
     */
    @Test
    fun capturesTheScreensTheFormPassChanged() {
        for (toolId in listOf("page-numbers", "crop-resize", "merge", "fill-form")) {
            captureTool(toolId)
        }
    }
}
