package com.offgridpdf.android.ui.common

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.unit.IntSize
import androidx.compose.foundation.Image
import androidx.compose.ui.input.pointer.pointerInput
import com.offgridpdf.android.pdf.PdfRect
import com.offgridpdf.android.pdf.PixelPoint
import com.offgridpdf.android.pdf.pixelToPdfRect
import com.offgridpdf.android.pdf.toPixelRect
import kotlin.math.abs

/**
 * How one rect should be painted over the page.
 *
 * [Filled] is for something that will really cover the page's content — a
 * redaction box. [Outlined] is for something that only marks a region — a
 * signature's placement, or what a crop will keep — where hiding what is
 * underneath would defeat the purpose.
 */
sealed interface PageOverlayStyle {
    data class Filled(val color: Color) : PageOverlayStyle
    data class Outlined(val color: Color, val strokeWidth: Float = 3f) : PageOverlayStyle
}

/** One rect to draw over the page, in PDF point-space, and how to draw it. */
data class PageOverlay(val rect: PdfRect, val style: PageOverlayStyle)

/**
 * A rendered PDF page the user can point at.
 *
 * Draws [image] at the page's own aspect ratio and paints [overlays] on top
 * of it, converting each from PDF point-space (bottom-left origin) to the
 * screen. When [onRectDragged] is non-null, dragging across the page reports
 * the dragged region back in point-space.
 *
 * The scaling here goes through two steps, and both are needed: the bitmap
 * is [scale] times the page's point size, and the view is some third size
 * again depending on the device and window. Touches arrive in view pixels,
 * so they are scaled to bitmap pixels before conversion, and overlays are
 * converted to bitmap pixels and then scaled down to the view.
 *
 * Extracted from `RedactScreen`, which was the only screen with a page
 * preview when it was written. Signature placement and crop margins are the
 * same interaction, so they share this rather than growing a second and
 * third copy of the coordinate handling — the part of this code most likely
 * to be subtly wrong.
 */
@Composable
fun PagePreview(
    image: ImageBitmap,
    bitmapWidth: Int,
    bitmapHeight: Int,
    pageHeightPts: Float,
    contentDescription: String,
    modifier: Modifier = Modifier,
    scale: Float,
    overlays: List<PageOverlay> = emptyList(),
    onRectDragged: ((PdfRect) -> Unit)? = null,
    dragIndicatorColor: Color = Color.Black,
    minDraggedSizePts: Float = 0f,
) {
    var displaySize by remember { mutableStateOf<IntSize?>(null) }

    // Keyed on the image so a page turn drops any half-finished drag. A drag
    // interrupted by moving to another page never gets its onDragEnd, so
    // without this its dashed outline would be left hanging over the new
    // page.
    var dragStart by remember(image) { mutableStateOf<Offset?>(null) }
    var dragCurrent by remember(image) { mutableStateOf<Offset?>(null) }

    val aspect = if (bitmapHeight > 0) bitmapWidth.toFloat() / bitmapHeight else 1f

    Box(
        modifier = modifier
            .fillMaxWidth()
            .aspectRatio(aspect)
            .onSizeChanged { displaySize = it }
            .then(
                if (onRectDragged == null) {
                    Modifier
                } else {
                    Modifier.pointerInput(image, pageHeightPts, scale) {
                        detectDragGestures(
                            onDragStart = { offset -> dragStart = offset; dragCurrent = offset },
                            onDrag = { change, _ -> dragCurrent = change.position },
                            onDragEnd = {
                                val start = dragStart
                                val current = dragCurrent
                                val size = displaySize
                                if (start != null && current != null && size != null &&
                                    size.width > 0 && size.height > 0
                                ) {
                                    val ratioX = bitmapWidth.toFloat() / size.width
                                    val ratioY = bitmapHeight.toFloat() / size.height
                                    val a = PixelPoint(start.x * ratioX, start.y * ratioY)
                                    val b = PixelPoint(current.x * ratioX, current.y * ratioY)
                                    val rect = pixelToPdfRect(a, b, pageHeightPts, scale)
                                    if (rect.width >= minDraggedSizePts && rect.height >= minDraggedSizePts) {
                                        onRectDragged(rect)
                                    }
                                }
                                dragStart = null
                                dragCurrent = null
                            },
                        )
                    }
                },
            ),
    ) {
        Image(
            bitmap = image,
            contentDescription = contentDescription,
            contentScale = ContentScale.FillBounds,
            modifier = Modifier.fillMaxWidth(),
        )

        val size = displaySize
        if (size != null && size.width > 0 && bitmapWidth > 0) {
            val displayScale = size.width.toFloat() / bitmapWidth
            Canvas(modifier = Modifier.fillMaxWidth().aspectRatio(aspect)) {
                for (overlay in overlays) {
                    val px = toPixelRect(overlay.rect, pageHeightPts, scale)
                    val topLeft = Offset(px.x * displayScale, px.y * displayScale)
                    val drawSize = Size(px.width * displayScale, px.height * displayScale)
                    when (val style = overlay.style) {
                        is PageOverlayStyle.Filled ->
                            drawRect(color = style.color, topLeft = topLeft, size = drawSize)
                        is PageOverlayStyle.Outlined ->
                            drawRect(
                                color = style.color,
                                topLeft = topLeft,
                                size = drawSize,
                                style = Stroke(width = style.strokeWidth),
                            )
                    }
                }

                // The in-progress drag, drawn in view pixels because that is
                // the space the finger is in — no conversion, so it tracks
                // the touch exactly.
                val start = dragStart
                val current = dragCurrent
                if (start != null && current != null) {
                    drawRect(
                        color = dragIndicatorColor,
                        topLeft = Offset(minOf(start.x, current.x), minOf(start.y, current.y)),
                        size = Size(abs(current.x - start.x), abs(current.y - start.y)),
                        style = Stroke(
                            width = 3f,
                            pathEffect = PathEffect.dashPathEffect(floatArrayOf(14f, 10f)),
                        ),
                    )
                }
            }
        }
    }
}
