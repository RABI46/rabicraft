package org.rabicraft.app.model

/**
 * A line of text found on the current page by the offline OCR, in page-relative coordinates.
 *
 * Replacing text on a page that is drawn as a picture means covering the old glyphs and drawing
 * the new ones on top, so every detected line carries the box it occupies.
 */
data class DetectedTextBlock(
    val id: String,
    val text: String,
    val left: Float,
    val top: Float,
    val right: Float,
    val bottom: Float
) {
    val width: Float get() = (right - left).coerceAtLeast(0f)
    val height: Float get() = (bottom - top).coerceAtLeast(0f)
}

/**
 * The four corners of the photographed page, normalised to the picture (0..1 on both axes) and
 * ordered clockwise from the top-left, ready to be straightened into a rectangle.
 */
data class ScanCorners(
    val topLeftX: Float,
    val topLeftY: Float,
    val topRightX: Float,
    val topRightY: Float,
    val bottomRightX: Float,
    val bottomRightY: Float,
    val bottomLeftX: Float,
    val bottomLeftY: Float
)

/** One page captured by the scanner, with the corners of the sheet when they were adjusted. */
data class ScannedPage(
    val file: java.io.File,
    val corners: ScanCorners? = null
)

/** A fill-in area found on a page, in page-relative coordinates. */
data class DetectedFieldRect(
    val left: Float,
    val top: Float,
    val right: Float,
    val bottom: Float
) {
    val width: Float get() = (right - left).coerceAtLeast(0f)
    val height: Float get() = (bottom - top).coerceAtLeast(0f)
}
