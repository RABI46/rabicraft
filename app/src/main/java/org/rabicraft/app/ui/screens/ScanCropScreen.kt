package org.rabicraft.app.ui.screens

import android.graphics.Bitmap
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.calculateCentroid
import androidx.compose.foundation.gestures.calculatePan
import androidx.compose.foundation.gestures.calculateZoom
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.positionChanged
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import org.rabicraft.app.data.tools.CropViewport
import org.rabicraft.app.data.tools.ScanCropGeometry
import org.rabicraft.app.model.ScanCorners
import org.rabicraft.app.ui.i18n.LocalStrings
import kotlin.math.roundToInt

/** How close (in dp) a touch must be to a handle to grab it instead of panning the photo. */
private const val CORNER_TOUCH_DP = 26

/** Each press of the zoom buttons multiplies the zoom by this much. */
private const val ZOOM_STEP = 1.6f

/**
 * Lets the user put the four corners of the page where they belong before it is straightened.
 *
 * The handles start on the detected corners, so on a clear photo the page only needs a glance; when
 * detection failed they start on the edges of the photo and the user drags them. Dragging anywhere
 * else moves the picture, pinching zooms (and the two buttons do the same without a second finger),
 * and the zoomed area is what the corners are read on. Confirming with "keep the photo" hands back
 * null, which leaves that page untouched.
 */
@Composable
fun ScanCropScreen(
    preview: Bitmap,
    detected: ScanCorners?,
    onCancel: () -> Unit,
    onConfirm: (ScanCorners?) -> Unit
) {
    val strings = LocalStrings.current
    val starting = detected ?: ScanCropGeometry.fullFrame
    var corners by remember(preview, detected) { mutableStateOf(starting) }
    var canvasSize by remember { mutableStateOf(IntSize.Zero) }
    var zoom by remember(preview) { mutableStateOf(CropViewport.MIN_ZOOM) }
    var pan by remember(preview) { mutableStateOf(Offset.Zero) }
    val base = remember(canvasSize, preview) {
        ScanCropGeometry.viewportFor(canvasSize.width, canvasSize.height, preview.width, preview.height)
    }
    val viewport = base.copy(zoom = zoom, panX = pan.x, panY = pan.y).clamped()
    val accent = MaterialTheme.colorScheme.primary
    val image = remember(preview) { preview.asImageBitmap() }

    fun show(next: CropViewport) {
        zoom = next.zoom
        pan = Offset(next.panX, next.panY)
    }

    fun reset() {
        corners = detected ?: ScanCropGeometry.fullFrame
        zoom = CropViewport.MIN_ZOOM
        pan = Offset.Zero
    }

    Surface(color = MaterialTheme.colorScheme.background, modifier = Modifier.fillMaxSize()) {
        Column(modifier = Modifier.fillMaxSize()) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 8.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(onClick = onCancel, modifier = Modifier.testTag("scan_crop_cancel")) {
                    Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = strings.cancel)
                }
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = strings.cropTitle,
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = strings.cropScreenHint,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .clipToBounds()
                    .onSizeChanged { canvasSize = it }
                    .pointerInput(base, preview) {
                        val cornerTouch = CORNER_TOUCH_DP.dp.toPx()
                        awaitEachGesture {
                            val down = awaitFirstDown(requireUnconsumed = false)
                            val atDown = CropViewport(base.box, zoom, pan.x, pan.y).clamped()
                            var grabbed = ScanCropGeometry.cornerAt(
                                corners = corners,
                                viewport = atDown,
                                x = down.position.x,
                                y = down.position.y,
                                tolerancePixels = cornerTouch
                            )
                            var pinching = false
                            while (true) {
                                val event = awaitPointerEvent()
                                val pressed = event.changes.filter { it.pressed }
                                if (pressed.isEmpty()) break
                                if (pressed.size > 1) {
                                    pinching = true
                                    grabbed = null
                                    val live = CropViewport(base.box, zoom, pan.x, pan.y).clamped()
                                    val zoomChange = event.calculateZoom()
                                    val centroid = event.calculateCentroid(useCurrent = true)
                                    var next = live
                                    if (zoomChange != 1f) {
                                        next = next.zoomed(next.zoom * zoomChange, centroid.x, centroid.y)
                                    }
                                    val panChange = event.calculatePan()
                                    if (panChange.x != 0f || panChange.y != 0f) {
                                        next = next.panned(panChange.x, panChange.y)
                                    }
                                    show(next)
                                    event.changes.forEach { if (it.positionChanged()) it.consume() }
                                } else {
                                    val change = pressed.first()
                                    if (!pinching) {
                                        val delta = change.position - change.previousPosition
                                        val index = grabbed
                                        if (index != null) {
                                            val live = CropViewport(base.box, zoom, pan.x, pan.y).clamped()
                                            corners = ScanCropGeometry.moveCorner(
                                                corners = corners,
                                                index = index,
                                                deltaX = delta.x / live.contentWidth.coerceAtLeast(1f),
                                                deltaY = delta.y / live.contentHeight.coerceAtLeast(1f)
                                            )
                                        } else {
                                            val live = CropViewport(base.box, zoom, pan.x, pan.y).clamped()
                                            show(live.panned(delta.x, delta.y))
                                        }
                                    }
                                    change.consume()
                                }
                            }
                        }
                    }
                    .testTag("scan_crop_canvas")
            ) {
                Canvas(modifier = Modifier.fillMaxSize()) {
                    val contentLeft = viewport.box.left + viewport.panX
                    val contentTop = viewport.box.top + viewport.panY
                    drawImage(
                        image = image,
                        dstOffset = IntOffset(contentLeft.roundToInt(), contentTop.roundToInt()),
                        dstSize = IntSize(
                            viewport.contentWidth.roundToInt(),
                            viewport.contentHeight.roundToInt()
                        ),
                        filterQuality = FilterQuality.Medium
                    )
                    val points = ScanCropGeometry.toArray(corners)
                    val outline = Path().apply {
                        moveTo(viewport.x(points[0]), viewport.y(points[1]))
                        lineTo(viewport.x(points[2]), viewport.y(points[3]))
                        lineTo(viewport.x(points[4]), viewport.y(points[5]))
                        lineTo(viewport.x(points[6]), viewport.y(points[7]))
                        close()
                    }
                    drawPath(outline, color = accent, style = Stroke(width = 2.dp.toPx()))
                    for (index in 0 until ScanCropGeometry.CORNER_COUNT) {
                        val centre = Offset(
                            viewport.x(points[index * 2]),
                            viewport.y(points[index * 2 + 1])
                        )
                        drawCircle(Color.White, radius = 11.dp.toPx(), center = centre)
                        drawCircle(accent, radius = 6.dp.toPx(), center = centre)
                    }
                }

                Row(
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .padding(8.dp)
                        .background(
                            MaterialTheme.colorScheme.surface.copy(alpha = 0.9f),
                            RoundedCornerShape(24.dp)
                        )
                ) {
                    IconButton(
                        onClick = { show(viewport.zoomed(viewport.zoom / ZOOM_STEP)) },
                        enabled = viewport.zoom > CropViewport.MIN_ZOOM,
                        modifier = Modifier.testTag("scan_crop_zoom_out")
                    ) {
                        Icon(Icons.Default.Remove, contentDescription = strings.zoomOut)
                    }
                    IconButton(
                        onClick = { show(viewport.zoomed(viewport.zoom * ZOOM_STEP)) },
                        enabled = viewport.zoom < CropViewport.MAX_ZOOM,
                        modifier = Modifier.testTag("scan_crop_zoom_in")
                    ) {
                        Icon(Icons.Default.Add, contentDescription = strings.zoomIn)
                    }
                }
            }

            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Button(
                    onClick = { onConfirm(corners) },
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("scan_crop_confirm")
                ) {
                    Text(strings.straighten)
                }
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    OutlinedButton(
                        onClick = { reset() },
                        modifier = Modifier.weight(1f)
                    ) {
                        Text(strings.resetCorners)
                    }
                    OutlinedButton(
                        onClick = { onConfirm(null) },
                        modifier = Modifier
                            .weight(1f)
                            .testTag("scan_crop_keep")
                    ) {
                        Text(strings.keepPhoto)
                    }
                }
            }
        }
    }
}
