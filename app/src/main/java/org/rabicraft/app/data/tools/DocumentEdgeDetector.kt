package org.rabicraft.app.data.tools

import org.rabicraft.app.model.ScanCorners
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.sqrt

/**
 * Finds the sheet of paper inside a photograph so the scanner can straighten it.
 *
 * The paper is the bright region against a darker desk, so the picture is reduced to a luminance
 * mask with an Otsu threshold, and the mask's four extreme points — smallest and largest `x + y`
 * and `x - y` — are the corners of the page. That holds for a page lying at any angle, which is
 * exactly the case a hand-held photo produces.
 *
 * The detector refuses to guess: a frame with no convincing background, or a sheet that leaves
 * almost no border, returns null and the caller keeps the original picture.
 */
object DocumentEdgeDetector {

    /** Working width of the mask; larger pictures are sampled, not resized. */
    private const val SAMPLE_TARGET_WIDTH = 220

    private const val MIN_COVERAGE = 0.15f
    private const val MAX_COVERAGE = 0.97f
    private const val MIN_AREA_FRACTION = 0.25f
    private const val MIN_EDGE_FRACTION = 0.25f

    fun detect(pixels: IntArray, width: Int, height: Int): ScanCorners? {
        if (width < 16 || height < 16 || pixels.size < width * height) return null
        val step = max(1, width / SAMPLE_TARGET_WIDTH)
        val cols = width / step
        val rows = height / step
        if (cols < 8 || rows < 8) return null

        val luma = IntArray(cols * rows)
        val histogram = IntArray(256)
        for (row in 0 until rows) {
            val y = row * step
            for (col in 0 until cols) {
                val value = lumaOf(pixels[y * width + col * step])
                luma[row * cols + col] = value
                histogram[value]++
            }
        }
        val threshold = otsu(histogram, cols * rows)
        var bright = 0
        val mask = BooleanArray(cols * rows)
        for (index in luma.indices) {
            if (luma[index] > threshold) {
                mask[index] = true
                bright++
            }
        }
        val coverage = bright.toFloat() / (cols * rows).toFloat()
        if (coverage < MIN_COVERAGE || coverage > MAX_COVERAGE) return null

        var topLeft = -1
        var topRight = -1
        var bottomRight = -1
        var bottomLeft = -1
        var topLeftScore = Int.MAX_VALUE
        var topRightScore = Int.MIN_VALUE
        var bottomRightScore = Int.MIN_VALUE
        var bottomLeftScore = Int.MAX_VALUE
        for (row in 0 until rows) {
            for (col in 0 until cols) {
                val index = row * cols + col
                if (!mask[index]) continue
                val sum = col + row
                val difference = col - row
                if (sum < topLeftScore) {
                    topLeftScore = sum
                    topLeft = index
                }
                if (sum > bottomRightScore) {
                    bottomRightScore = sum
                    bottomRight = index
                }
                if (difference > topRightScore) {
                    topRightScore = difference
                    topRight = index
                }
                if (difference < bottomLeftScore) {
                    bottomLeftScore = difference
                    bottomLeft = index
                }
            }
        }
        if (topLeft < 0 || topRight < 0 || bottomRight < 0 || bottomLeft < 0) return null

        val corners = floatArrayOf(
            (topLeft % cols).toFloat() * step,
            (topLeft / cols).toFloat() * step,
            (topRight % cols).toFloat() * step,
            (topRight / cols).toFloat() * step,
            (bottomRight % cols).toFloat() * step,
            (bottomRight / cols).toFloat() * step,
            (bottomLeft % cols).toFloat() * step,
            (bottomLeft / cols).toFloat() * step
        )
        if (!isPlausibleSheet(corners, width.toFloat(), height.toFloat())) return null

        return ScanCorners(
            topLeftX = corners[0] / width,
            topLeftY = corners[1] / height,
            topRightX = corners[2] / width,
            topRightY = corners[3] / height,
            bottomRightX = corners[4] / width,
            bottomRightY = corners[5] / height,
            bottomLeftX = corners[6] / width,
            bottomLeftY = corners[7] / height
        )
    }

    /** A real sheet covers a good share of the frame and has four long, distinct edges. */
    private fun isPlausibleSheet(corners: FloatArray, width: Float, height: Float): Boolean {
        val area = abs(shoelace(corners))
        if (area / (width * height) < MIN_AREA_FRACTION) return false
        val diagonal = sqrt(width * width + height * height)
        val shortestEdge = minOf(
            distance(corners, 0, 2),
            distance(corners, 2, 4),
            distance(corners, 4, 6),
            distance(corners, 6, 0)
        )
        return shortestEdge >= diagonal * MIN_EDGE_FRACTION
    }

    private fun shoelace(points: FloatArray): Float {
        var total = 0f
        for (index in 0 until 4) {
            val next = (index + 1) % 4
            val x1 = points[index * 2]
            val y1 = points[index * 2 + 1]
            val x2 = points[next * 2]
            val y2 = points[next * 2 + 1]
            total += x1 * y2 - x2 * y1
        }
        return total / 2f
    }

    private fun distance(points: FloatArray, first: Int, second: Int): Float {
        val dx = points[first] - points[second]
        val dy = points[first + 1] - points[second + 1]
        return sqrt(dx * dx + dy * dy)
    }

    private fun lumaOf(pixel: Int): Int {
        val red = (pixel shr 16) and 0xFF
        val green = (pixel shr 8) and 0xFF
        val blue = pixel and 0xFF
        return (299 * red + 587 * green + 114 * blue) / 1000
    }

    /** Otsu's threshold: the luminance that best separates paper from background. */
    private fun otsu(histogram: IntArray, total: Int): Int {
        var sum = 0.0
        for (value in histogram.indices) sum += value.toDouble() * histogram[value]
        var sumBackground = 0.0
        var weightBackground = 0
        var best = 0.0
        var threshold = 127
        for (value in histogram.indices) {
            weightBackground += histogram[value]
            if (weightBackground == 0) continue
            val weightForeground = total - weightBackground
            if (weightForeground == 0) break
            sumBackground += value.toDouble() * histogram[value]
            val meanBackground = sumBackground / weightBackground
            val meanForeground = (sum - sumBackground) / weightForeground
            val between = weightBackground.toDouble() * weightForeground *
                (meanBackground - meanForeground) * (meanBackground - meanForeground)
            if (between > best) {
                best = between
                threshold = value
            }
        }
        return threshold
    }
}
