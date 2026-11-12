package org.rabicraft.app.data.tools

import org.rabicraft.app.model.DiffKind
import org.rabicraft.app.model.DiffResult
import kotlin.math.abs

/**
 * Pixel comparison behind the "compare two PDFs" tool. Both sides are sampled into same-size
 * thumbnails, so this is a like-for-like look at the page rather than a byte diff of the files.
 *
 * Rendering the same page twice never produces bit-identical rasters (anti-aliasing and hinting
 * move a few edge pixels), so [IDENTICAL_THRESHOLD] ignores noise under 0.05 % of the page.
 */
object PageDiff {

    const val IDENTICAL_THRESHOLD = 0.0005f

    /** Luma difference, out of 255, below which two pixels count as the same shade. */
    private const val LUMA_TOLERANCE = 24

    fun compare(left: IntArray?, right: IntArray?): DiffResult {
        if (left == null || right == null) return DiffResult(DiffKind.MISSING, 0f)
        if (left.isEmpty() || left.size != right.size) {
            return DiffResult(DiffKind.DIMENSION_MISMATCH, 0f)
        }
        val ratio = differingRatio(left, right)
        val kind = if (ratio <= IDENTICAL_THRESHOLD) DiffKind.IDENTICAL else DiffKind.DIFFERENT
        return DiffResult(kind, ratio)
    }

    fun differingRatio(left: IntArray, right: IntArray): Float {
        val size = minOf(left.size, right.size)
        if (size == 0) return 0f
        var different = 0
        for (index in 0 until size) {
            if (abs(luma(left[index]) - luma(right[index])) > LUMA_TOLERANCE) different++
        }
        return different.toFloat() / size.toFloat()
    }

    private fun luma(pixel: Int): Int {
        val red = (pixel shr 16) and 0xFF
        val green = (pixel shr 8) and 0xFF
        val blue = pixel and 0xFF
        return (299 * red + 587 * green + 114 * blue) / 1000
    }
}
