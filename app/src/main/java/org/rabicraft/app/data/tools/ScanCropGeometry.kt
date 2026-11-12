package org.rabicraft.app.data.tools

import org.rabicraft.app.model.ScanCorners
import kotlin.math.min
import kotlin.math.sqrt

/** Where the picture is drawn inside the crop view, in view pixels. */
data class ImageBox(val left: Float, val top: Float, val width: Float, val height: Float) {
    fun x(normalizedX: Float): Float = left + normalizedX * width

    fun y(normalizedY: Float): Float = top + normalizedY * height

    fun ratioX(pixels: Float): Float =
        if (width <= 0f) 0f else ((pixels - left) / width).coerceIn(0f, 1f)

    fun ratioY(pixels: Float): Float =
        if (height <= 0f) 0f else ((pixels - top) / height).coerceIn(0f, 1f)
}

/**
 * What the crop view shows: the fitted picture, how far it is zoomed in and how far it has been
 * pushed aside.
 *
 * Panning is bounded so the photo always covers the fitted rectangle — the sheet can never be
 * dragged out of view with a corner lost off-screen. At zoom 1 the bounds are zero, so the picture
 * simply cannot move.
 */
data class CropViewport(
    val box: ImageBox,
    val zoom: Float = MIN_ZOOM,
    val panX: Float = 0f,
    val panY: Float = 0f
) {
    val contentWidth: Float get() = box.width * zoom

    val contentHeight: Float get() = box.height * zoom

    val centreX: Float get() = box.left + box.width / 2f

    val centreY: Float get() = box.top + box.height / 2f

    fun x(normalizedX: Float): Float = box.left + panX + normalizedX * contentWidth

    fun y(normalizedY: Float): Float = box.top + panY + normalizedY * contentHeight

    /** Picture coordinate under a view pixel, clamped to the sheet. */
    fun ratioX(pixels: Float): Float =
        if (contentWidth <= 0f) 0f else ((pixels - box.left - panX) / contentWidth).coerceIn(0f, 1f)

    fun ratioY(pixels: Float): Float =
        if (contentHeight <= 0f) 0f else ((pixels - box.top - panY) / contentHeight).coerceIn(0f, 1f)

    /** Zoom and pan brought back inside the allowed range. */
    fun clamped(): CropViewport {
        val boundedZoom = zoom.coerceIn(MIN_ZOOM, MAX_ZOOM)
        val maxX = box.width * (boundedZoom - 1f)
        val maxY = box.height * (boundedZoom - 1f)
        return copy(
            zoom = boundedZoom,
            panX = panX.coerceIn(-maxX, 0f),
            panY = panY.coerceIn(-maxY, 0f)
        )
    }

    fun panned(deltaX: Float, deltaY: Float): CropViewport =
        copy(panX = panX + deltaX, panY = panY + deltaY).clamped()

    /** Zooms while keeping the picture point under ([focusX], [focusY]) where it is. */
    fun zoomed(targetZoom: Float, focusX: Float = centreX, focusY: Float = centreY): CropViewport {
        val focusRatioX = ratioX(focusX)
        val focusRatioY = ratioY(focusY)
        val boundedZoom = targetZoom.coerceIn(MIN_ZOOM, MAX_ZOOM)
        return CropViewport(
            box = box,
            zoom = boundedZoom,
            panX = focusX - box.left - focusRatioX * box.width * boundedZoom,
            panY = focusY - box.top - focusRatioY * box.height * boundedZoom
        ).clamped()
    }

    companion object {
        const val MIN_ZOOM = 1f
        const val MAX_ZOOM = 5f
    }
}

/**
 * Maths of the corner adjustment screen: fitting the photo into the view, zooming and panning it,
 * dragging one corner and finding the corner under a finger.
 *
 * The screen itself is Compose and cannot be unit-tested, so everything that can be wrong in the
 * interaction — where the picture sits, how far it may be pushed, whether a drag stays on the
 * photo, which corner a touch grabs — lives here instead.
 */
object ScanCropGeometry {

    const val CORNER_COUNT = 4

    private const val FULL_FRAME_MARGIN = 0.02f

    /** Corners of the whole photo, used when nothing was detected. */
    val fullFrame: ScanCorners = ScanCorners(
        topLeftX = FULL_FRAME_MARGIN,
        topLeftY = FULL_FRAME_MARGIN,
        topRightX = 1f - FULL_FRAME_MARGIN,
        topRightY = FULL_FRAME_MARGIN,
        bottomRightX = 1f - FULL_FRAME_MARGIN,
        bottomRightY = 1f - FULL_FRAME_MARGIN,
        bottomLeftX = FULL_FRAME_MARGIN,
        bottomLeftY = 1f - FULL_FRAME_MARGIN
    )

    /** Largest centred rectangle with the picture's aspect ratio inside [containerWidth] x [containerHeight]. */
    fun fit(
        containerWidth: Int,
        containerHeight: Int,
        imageWidth: Int,
        imageHeight: Int
    ): ImageBox {
        val containerW = containerWidth.toFloat().coerceAtLeast(1f)
        val containerH = containerHeight.toFloat().coerceAtLeast(1f)
        val imageW = imageWidth.coerceAtLeast(1).toFloat()
        val imageH = imageHeight.coerceAtLeast(1).toFloat()
        val scale = min(containerW / imageW, containerH / imageH)
        val width = imageW * scale
        val height = imageH * scale
        return ImageBox(
            left = (containerW - width) / 2f,
            top = (containerH - height) / 2f,
            width = width,
            height = height
        )
    }

    fun toArray(corners: ScanCorners): FloatArray = floatArrayOf(
        corners.topLeftX, corners.topLeftY,
        corners.topRightX, corners.topRightY,
        corners.bottomRightX, corners.bottomRightY,
        corners.bottomLeftX, corners.bottomLeftY
    )

    fun fromArray(points: FloatArray): ScanCorners = ScanCorners(
        topLeftX = points[0].coerceIn(0f, 1f),
        topLeftY = points[1].coerceIn(0f, 1f),
        topRightX = points[2].coerceIn(0f, 1f),
        topRightY = points[3].coerceIn(0f, 1f),
        bottomRightX = points[4].coerceIn(0f, 1f),
        bottomRightY = points[5].coerceIn(0f, 1f),
        bottomLeftX = points[6].coerceIn(0f, 1f),
        bottomLeftY = points[7].coerceIn(0f, 1f)
    )

    /**
     * Moves corner [index] (0 top-left, then clockwise) by a fraction of the picture.
     *
     * The corner stops at the edge of the photo: dragging further simply does nothing, so a fast
     * swipe cannot send a handle out of sight.
     */
    fun moveCorner(
        corners: ScanCorners,
        index: Int,
        deltaX: Float,
        deltaY: Float
    ): ScanCorners {
        if (index !in 0 until CORNER_COUNT) return corners
        val points = toArray(corners)
        points[index * 2] = (points[index * 2] + deltaX).coerceIn(0f, 1f)
        points[index * 2 + 1] = (points[index * 2 + 1] + deltaY).coerceIn(0f, 1f)
        return fromArray(points)
    }

    /** The fitted picture at rest, as the screen starts. */
    fun viewportFor(
        containerWidth: Int,
        containerHeight: Int,
        imageWidth: Int,
        imageHeight: Int
    ): CropViewport = CropViewport(fit(containerWidth, containerHeight, imageWidth, imageHeight))

    /**
     * Index of the corner within [tolerancePixels] of the touch, or null when the touch is
     * elsewhere. Measured on screen, so the reach feels the same however far the picture is zoomed.
     */
    fun cornerAt(
        corners: ScanCorners,
        viewport: CropViewport,
        x: Float,
        y: Float,
        tolerancePixels: Float
    ): Int? {
        val points = toArray(corners)
        var best: Int? = null
        var bestDistance = tolerancePixels.coerceAtLeast(0f)
        for (index in 0 until CORNER_COUNT) {
            val dx = viewport.x(points[index * 2]) - x
            val dy = viewport.y(points[index * 2 + 1]) - y
            val distance = sqrt(dx * dx + dy * dy)
            if (distance <= bestDistance) {
                bestDistance = distance
                best = index
            }
        }
        return best
    }
}
