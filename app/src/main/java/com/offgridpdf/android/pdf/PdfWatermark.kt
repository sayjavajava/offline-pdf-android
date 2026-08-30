package com.offgridpdf.android.pdf

import com.tom_roush.pdfbox.pdmodel.PDDocument
import com.tom_roush.pdfbox.pdmodel.PDPageContentStream
import com.tom_roush.pdfbox.pdmodel.font.PDType1Font
import com.tom_roush.pdfbox.pdmodel.graphics.state.PDExtendedGraphicsState
import com.tom_roush.pdfbox.util.Matrix
import java.io.ByteArrayOutputStream
import kotlin.math.cos
import kotlin.math.sin

/** RGB, each channel 0-1 — same convention as the web app's `WatermarkOptions.color`. */
data class WatermarkColor(val red: Float, val green: Float, val blue: Float)

/** Web reference: `WatermarkOptions` (`pdf-ops.ts`). */
data class WatermarkOptions(
    val fontSize: Float,
    val color: WatermarkColor,
    val opacity: Float,
    /** Degrees counter-clockwise. 45 gives the conventional diagonal stamp. */
    val rotation: Float = 0f,
    /** Repeat the text across the whole page instead of stamping it once. */
    val tile: Boolean = false,
)

/**
 * Web reference: `addWatermark` (`pdf-ops.ts`). Stamps [text] onto every
 * page of [document] using PDFBox's standard "append an overlay content
 * stream" recipe (`PDPageContentStream.AppendMode.APPEND` — adds on top
 * of the page's existing content, doesn't replace it).
 *
 * Opacity needs `PDExtendedGraphicsState` — `PDPageContentStream` alone
 * has no opacity concept, since transparency is a graphics-state
 * property, not a drawing-operator argument, in the PDF content-stream
 * model.
 */
fun addWatermark(document: PDDocument, text: String, options: WatermarkOptions): ByteArray {
    if (text.isBlank()) {
        throw IllegalArgumentException("Enter watermark text.")
    }
    if (!options.opacity.isFinite() || options.opacity < 0f || options.opacity > 1f) {
        throw IllegalArgumentException("Opacity must be between 0 and 1.")
    }
    if (!options.fontSize.isFinite() || options.fontSize <= 0f || options.fontSize > 300f) {
        throw IllegalArgumentException("Font size must be between 1 and 300.")
    }
    if (!options.rotation.isFinite() || options.rotation < -360f || options.rotation > 360f) {
        throw IllegalArgumentException("Rotation must be between -360 and 360 degrees.")
    }
    val color = options.color
    if (!color.red.isFinite() || color.red < 0f || color.red > 1f ||
        !color.green.isFinite() || color.green < 0f || color.green > 1f ||
        !color.blue.isFinite() || color.blue < 0f || color.blue > 1f
    ) {
        throw IllegalArgumentException("Watermark colour channels must each be between 0 and 1.")
    }

    val font = PDType1Font.HELVETICA_BOLD
    val textWidth = font.getStringWidth(text) / 1000f * options.fontSize
    val radians = Math.toRadians(options.rotation.toDouble())

    for (page in document.pages) {
        val mediaBox = page.mediaBox
        val width = mediaBox.width
        val height = mediaBox.height

        PDPageContentStream(document, page, PDPageContentStream.AppendMode.APPEND, true, true).use { stream ->
            val graphicsState = PDExtendedGraphicsState()
            graphicsState.setNonStrokingAlphaConstant(options.opacity)
            stream.setGraphicsStateParameters(graphicsState)
            stream.setNonStrokingColor(color.red, color.green, color.blue)
            stream.setFont(font, options.fontSize)

            stream.beginText()
            if (options.tile) {
                // Step by the text's own footprint so stamps do not
                // overlap, with a gutter proportional to the font size --
                // same formula as the web version.
                val stepX = maxOf(textWidth, options.fontSize) + options.fontSize * 2
                val stepY = options.fontSize * 4
                var y = 0f
                while (y < height + stepY) {
                    var x = 0f
                    while (x < width + stepX) {
                        stream.setTextMatrix(Matrix.getRotateInstance(radians, x, y))
                        stream.showText(text)
                        x += stepX
                    }
                    y += stepY
                }
            } else {
                // A rotated stamp's text matrix rotates about its own
                // origin, so centering it means walking back half the
                // text's *rotated* footprint along each axis, not simply
                // halving the page dimensions -- same formula as the web
                // version's addWatermark.
                val x = width / 2f - (textWidth / 2f) * cos(radians).toFloat()
                val y = height / 2f - (textWidth / 2f) * sin(radians).toFloat()
                stream.setTextMatrix(Matrix.getRotateInstance(radians, x, y))
                stream.showText(text)
            }
            stream.endText()
        }
    }

    val out = ByteArrayOutputStream()
    document.save(out)
    return out.toByteArray()
}
