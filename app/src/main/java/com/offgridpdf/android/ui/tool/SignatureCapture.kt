package com.offgridpdf.android.ui.tool

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Typeface
import androidx.compose.ui.geometry.Offset
import java.io.ByteArrayOutputStream

/**
 * Android-`Bitmap`-based helpers backing the Type/Draw signature modes —
 * genuinely Android framework code (not PDF logic), so it lives here
 * alongside `SignatureScreen.kt` rather than in the `pdf` package.
 *
 * Both rasterize to PNG bytes, which then go through the exact same
 * [com.offgridpdf.android.pdf.addSignature] embed-as-image path as an
 * uploaded signature image — matching the web tool's own design (type,
 * draw, and upload all converge to image bytes before
 * `placeSignatureImage` ever runs).
 */

private const val SIGNATURE_CANVAS_WIDTH = 400
private const val SIGNATURE_CANVAS_HEIGHT = 150

/** Renders [text] onto a transparent canvas in a cursive-style face, matching the web tool's `48px cursive`. */
fun renderTypedSignature(text: String): ByteArray {
    val bitmap = Bitmap.createBitmap(SIGNATURE_CANVAS_WIDTH, SIGNATURE_CANVAS_HEIGHT, Bitmap.Config.ARGB_8888)
    val canvas = Canvas(bitmap)
    val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.BLACK
        textSize = 48f
        typeface = Typeface.create(Typeface.SERIF, Typeface.ITALIC)
    }
    canvas.drawText(text, 10f, SIGNATURE_CANVAS_HEIGHT / 2f, paint)
    return bitmap.toPngBytes()
}

/** Renders freehand [strokes] (each a list of points forming one continuous drag) onto a transparent canvas. */
fun renderDrawnSignature(strokes: List<List<Offset>>): ByteArray {
    val bitmap = Bitmap.createBitmap(SIGNATURE_CANVAS_WIDTH, SIGNATURE_CANVAS_HEIGHT, Bitmap.Config.ARGB_8888)
    val canvas = Canvas(bitmap)
    val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.BLACK
        strokeWidth = 3f
        strokeCap = Paint.Cap.ROUND
        style = Paint.Style.STROKE
    }
    for (stroke in strokes) {
        for (i in 0 until stroke.size - 1) {
            val start = stroke[i]
            val end = stroke[i + 1]
            canvas.drawLine(start.x, start.y, end.x, end.y, paint)
        }
    }
    return bitmap.toPngBytes()
}

private fun Bitmap.toPngBytes(): ByteArray {
    val out = ByteArrayOutputStream()
    compress(Bitmap.CompressFormat.PNG, 100, out)
    return out.toByteArray()
}
