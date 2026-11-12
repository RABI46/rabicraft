package org.rabicraft.app.data.tools

import org.rabicraft.app.data.ocr.OcrWord
import org.rabicraft.app.model.DetectedTextBlock

/**
 * Turns the words the OCR returns into the lines a reader sees.
 *
 * ML Kit hands back words in no guaranteed reading order, so words are grouped by vertical overlap
 * and then sorted left to right. A block is then something the editor can offer the user to
 * replace or delete.
 */
object TextBlockPlanner {

    fun groupLines(words: List<OcrWord>): List<DetectedTextBlock> {
        val valid = words.filter {
            it.text.isNotBlank() && it.right > it.left && it.bottom > it.top
        }
        if (valid.isEmpty()) return emptyList()

        val lines = mutableListOf<MutableList<OcrWord>>()
        for (word in valid.sortedBy { it.top }) {
            val current = lines.lastOrNull()
            if (current != null && overlapsVertically(current, word)) {
                current.add(word)
            } else {
                lines.add(mutableListOf(word))
            }
        }

        return lines.mapIndexed { index, lineWords ->
            val ordered = lineWords.sortedBy { it.left }
            DetectedTextBlock(
                id = "block-$index",
                text = ordered.joinToString(" ") { it.text.trim() },
                left = ordered.minOf { it.left },
                top = ordered.minOf { it.top },
                right = ordered.maxOf { it.right },
                bottom = ordered.maxOf { it.bottom }
            )
        }.filter { it.text.isNotBlank() }
    }

    /** Words on neighbouring baselines still share most of their height; superscripts do not. */
    private fun overlapsVertically(line: List<OcrWord>, word: OcrWord): Boolean {
        val lineTop = line.minOf { it.top }
        val lineBottom = line.maxOf { it.bottom }
        val overlap = minOf(lineBottom, word.bottom) - maxOf(lineTop, word.top)
        val shortest = minOf(lineBottom - lineTop, word.bottom - word.top)
        return shortest > 0f && overlap / shortest >= 0.5f
    }
}
