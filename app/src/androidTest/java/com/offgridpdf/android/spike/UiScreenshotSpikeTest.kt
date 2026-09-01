package com.offgridpdf.android.spike

import android.app.Activity
import android.graphics.Bitmap
import android.os.Handler
import android.os.Looper
import android.util.Base64
import android.util.Log
import android.view.PixelCopy
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.offgridpdf.android.MainActivity
import org.junit.Test
import org.junit.runner.RunWith
import java.io.ByteArrayOutputStream
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

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
 * Captures via `PixelCopy` (real Android framework API, no new test
 * dependency needed) rather than Compose's own `captureToImage()`, since
 * this project doesn't have `androidx.compose.ui:ui-test-junit4` wired in
 * and adding a new dependency for a one-off screenshot isn't warranted.
 */
@RunWith(AndroidJUnit4::class)
class UiScreenshotSpikeTest {

    companion object {
        private const val TAG = "UiScreenshot"
    }

    private fun captureWindow(activity: Activity): Bitmap {
        val decorView = activity.window.decorView
        val bitmap = Bitmap.createBitmap(decorView.width, decorView.height, Bitmap.Config.ARGB_8888)
        val latch = CountDownLatch(1)
        var success = false
        PixelCopy.request(
            activity.window,
            bitmap,
            { result ->
                success = result == PixelCopy.SUCCESS
                latch.countDown()
            },
            Handler(Looper.getMainLooper()),
        )
        latch.await(10, TimeUnit.SECONDS)
        check(success) { "PixelCopy.request did not report SUCCESS" }
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
}
