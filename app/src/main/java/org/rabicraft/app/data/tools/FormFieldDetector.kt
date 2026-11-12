package org.rabicraft.app.data.tools

import org.rabicraft.app.model.DetectedFieldRect

/**
 * Finds the places a reader is meant to write in: the underlines and rules that stand in for form
 * fields on a printed or scanned page.
 *
 * The page is passed as raw ARGB pixels so the detection can be unit-tested. Only horizontal rules
 * are treated as fields — text is a dense block of dark pixels, a rule is one, thin, long run of
 * them — and each rule becomes a field whose box reaches up to where the answer is written.
 */
object FormFieldDetector {

    /** Below this luma a pixel counts as ink. */
    private const val INK_LUMA = 170

    /** A rule must span this share of the page width. */
    private const val MIN_RUN_FRACTION = 0.08f

    /** Rows of a single rule; anything thicker is a picture or a filled bar. */
    private const val MAX_RULE_THICKNESS = 4

    /** Two rules closer than this are the same rule. */
    private const val MERGE_ROWS = 3

    /** How far above the rule the answer box reaches, as a multiple of the rule thickness. */
    private const val BOX_HEIGHT_FACTOR = 3f

    /** Rows checked above and below a rule to tell a form line from a streak in a picture. */
    private const val QUIET_ROWS = 3
    private const val QUIET_MAX_INK = 0.2f

    fun detect(pixels: IntArray, width: Int, height: Int): List<DetectedFieldRect> {
        if (width <= 0 || height <= 0 || pixels.size < width * height) return emptyList()
        val minRun = (width * MIN_RUN_FRACTION).toInt().coerceAtLeast(8)
        val runs = mutableListOf<InkRun>()
        var y = 0
        while (y < height) {
            var x = 0
            while (x < width) {
                if (!isInk(pixels[y * width + x])) {
                    x++
                    continue
                }
                val start = x
                while (x < width && isInk(pixels[y * width + x])) x++
                if (x - start >= minRun) runs.add(InkRun(y, start, x))
            }
            y += 2
        }
        if (runs.isEmpty()) return emptyList()

        val rules = groupIntoRules(runs)
        val fields = mutableListOf<DetectedFieldRect>()
        for (rule in rules) {
            val top = rule.minOf { it.y }.toFloat()
            val bottom = (rule.maxOf { it.y } + 1).toFloat()
            if (bottom - top > MAX_RULE_THICKNESS) continue
            val left = rule.minOf { it.start }
            val right = rule.maxOf { it.end }
            // A rule on a form has white paper around it; a dark streak inside a picture does not.
            if (!isQuiet(pixels, width, height, top.toInt(), bottom.toInt(), left, right)) continue
            val answerHeight = (bottom - top).coerceAtLeast(1f) * BOX_HEIGHT_FACTOR
            fields.add(
                DetectedFieldRect(
                    left = (left.toFloat() / width).coerceIn(0f, 1f),
                    top = ((top - answerHeight * 2f) / height).coerceIn(0f, 1f),
                    right = (right.toFloat() / width).coerceIn(0f, 1f),
                    bottom = ((bottom + answerHeight) / height).coerceIn(0f, 1f)
                )
            )
        }
        return withoutOverlaps(fields)
    }

    private fun isInk(pixel: Int): Boolean {
        val alpha = (pixel ushr 24) and 0xFF
        if (alpha < 16) return false
        val luma = (
            299 * ((pixel shr 16) and 0xFF) +
                587 * ((pixel shr 8) and 0xFF) +
                114 * (pixel and 0xFF)
            ) / 1000
        return luma < INK_LUMA
    }

    private class InkRun(val y: Int, val start: Int, val end: Int)

    /** True when the few rows above and below the rule are mostly blank. */
    private fun isQuiet(
        pixels: IntArray,
        width: Int,
        height: Int,
        top: Int,
        bottom: Int,
        left: Int,
        right: Int
    ): Boolean {
        val rows = mutableListOf<Int>()
        for (row in (top - QUIET_ROWS) until top) if (row in 0 until height) rows.add(row)
        for (row in (bottom + 1) until (bottom + 1 + QUIET_ROWS)) {
            if (row in 0 until height) rows.add(row)
        }
        if (rows.isEmpty()) return true
        var ink = 0
        var sampled = 0
        for (row in rows) {
            for (x in left until right) {
                if (isInk(pixels[row * width + x])) ink++
                sampled++
            }
        }
        return sampled == 0 || ink.toFloat() / sampled.toFloat() <= QUIET_MAX_INK
    }

    /** Stacks runs that sit on neighbouring rows with the same span into one rule. */
    private fun groupIntoRules(runs: List<InkRun>): List<MutableList<InkRun>> {
        val rules = mutableListOf<MutableList<InkRun>>()
        for (run in runs.sortedBy { it.y }) {
            val rule = rules.lastOrNull()
            if (rule != null) {
                val last = rule.last()
                if (run.y - last.y <= MERGE_ROWS && horizontalOverlap(last, run) >= 0.7f) {
                    rule.add(run)
                    continue
                }
            }
            rules.add(mutableListOf(run))
        }
        return rules
    }

    private fun horizontalOverlap(left: InkRun, right: InkRun): Float {
        val overlap = minOf(left.end, right.end) - maxOf(left.start, right.start)
        val shortest = minOf(left.end - left.start, right.end - right.start)
        return if (shortest <= 0) 0f else overlap.toFloat() / shortest.toFloat()
    }

    private fun withoutOverlaps(fields: List<DetectedFieldRect>): List<DetectedFieldRect> {
        val kept = mutableListOf<DetectedFieldRect>()
        for (field in fields.sortedBy { it.top }) {
            val duplicate = kept.any { existing ->
                val horizontal = minOf(existing.right, field.right) - maxOf(existing.left, field.left)
                val vertical = minOf(existing.bottom, field.bottom) - maxOf(existing.top, field.top)
                horizontal > 0.5f * minOf(existing.width, field.width).coerceAtLeast(0.01f) &&
                    vertical > 0f
            }
            if (!duplicate) kept.add(field)
        }
        return kept
    }
}
