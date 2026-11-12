package org.rabicraft.app.data.tools

/** The kinds of block the plain-text body of "create a PDF" understands. */
enum class TextBlockKind { TITLE, HEADING, PARAGRAPH, BULLET }

data class TextBlock(val kind: TextBlockKind, val text: String)

/** One rendered line: wrapping has already happened, the renderer only draws it. */
data class TextLine(val text: String, val kind: TextBlockKind)

/**
 * Parses, wraps and paginates the body of a document written in the "create a PDF" tool.
 *
 * Text is measured through a callback so the layout is unit-tested without a graphics device: the
 * exporter passes a `Paint.measureText` wrapper, the tests pass a fake one.
 *
 * Body syntax is deliberately small: `# ` starts a heading, `- ` or `* ` a bullet, a blank line
 * separates paragraphs.
 */
object TextLayout {

    const val LINE_SPACING = 1.45f
    const val TITLE_FACTOR = 1.9f
    const val HEADING_FACTOR = 1.35f
    const val BULLET_PREFIX = "• "

    fun sizeFactor(kind: TextBlockKind): Float = when (kind) {
        TextBlockKind.TITLE -> TITLE_FACTOR
        TextBlockKind.HEADING -> HEADING_FACTOR
        TextBlockKind.PARAGRAPH, TextBlockKind.BULLET -> 1f
    }

    fun lineHeight(baseSizePt: Float, kind: TextBlockKind): Float =
        baseSizePt.coerceAtLeast(1f) * sizeFactor(kind) * LINE_SPACING

    fun parse(body: String): List<TextBlock> {
        val blocks = mutableListOf<TextBlock>()
        for (raw in body.replace("\r\n", "\n").split('\n')) {
            val line = raw.trim()
            when {
                line.isEmpty() -> Unit
                line.startsWith("#") -> {
                    val text = line.trimStart('#').trim()
                    if (text.isNotEmpty()) blocks.add(TextBlock(TextBlockKind.HEADING, text))
                }
                line.startsWith("- ") || line.startsWith("* ") -> {
                    val text = line.substring(2).trim()
                    if (text.isNotEmpty()) blocks.add(TextBlock(TextBlockKind.BULLET, text))
                }
                else -> {
                    val previous = blocks.lastOrNull()
                    if (previous != null && previous.kind == TextBlockKind.PARAGRAPH) {
                        blocks[blocks.lastIndex] = previous.copy(text = previous.text + " " + line)
                    } else {
                        blocks.add(TextBlock(TextBlockKind.PARAGRAPH, line))
                    }
                }
            }
        }
        return blocks
    }

    /** Wraps one block to [maxWidthPt]; words wider than the column are split by characters. */
    fun wrap(
        block: TextBlock,
        baseSizePt: Float,
        maxWidthPt: Float,
        measure: (String, Float) -> Float
    ): List<TextLine> {
        val size = baseSizePt.coerceAtLeast(1f) * sizeFactor(block.kind)
        val width = maxWidthPt.coerceAtLeast(1f)
        val prefix = if (block.kind == TextBlockKind.BULLET) BULLET_PREFIX else ""
        val pieces = mutableListOf<String>()
        for (word in block.text.split(' ').filter { it.isNotBlank() }) {
            pieces.addAll(chunk(word, size, width, measure))
        }
        if (pieces.isEmpty()) return emptyList()

        val lines = mutableListOf<String>()
        val current = StringBuilder(prefix)
        var hasWord = false
        for (piece in pieces) {
            val candidate = if (hasWord) current.toString() + " " + piece else current.toString() + piece
            if (hasWord && measure(candidate, size) > width) {
                lines.add(current.toString())
                current.setLength(0)
                current.append(piece)
            } else {
                if (hasWord) current.append(' ')
                current.append(piece)
            }
            hasWord = true
        }
        if (hasWord) lines.add(current.toString())
        return lines.map { TextLine(it, block.kind) }
    }

    fun wrapAll(
        blocks: List<TextBlock>,
        baseSizePt: Float,
        maxWidthPt: Float,
        measure: (String, Float) -> Float
    ): List<TextLine> = blocks.flatMap { wrap(it, baseSizePt, maxWidthPt, measure) }

    /** Splits [lines] into pages, breaking before a line that would not fit above the margin. */
    fun paginate(
        lines: List<TextLine>,
        baseSizePt: Float,
        pageHeightPt: Float,
        topMarginPt: Float,
        bottomMarginPt: Float
    ): List<List<TextLine>> {
        if (lines.isEmpty()) return emptyList()
        val top = topMarginPt.coerceAtLeast(0f)
        val limit = (pageHeightPt - bottomMarginPt).coerceAtLeast(top + 1f)
        val pages = mutableListOf<MutableList<TextLine>>()
        var page = mutableListOf<TextLine>()
        var y = top
        for (line in lines) {
            val height = lineHeight(baseSizePt, line.kind)
            if (page.isNotEmpty() && y + height > limit) {
                pages.add(page)
                page = mutableListOf()
                y = top
            }
            page.add(line)
            y += height
        }
        if (page.isNotEmpty()) pages.add(page)
        return pages
    }

    private fun chunk(
        word: String,
        size: Float,
        maxWidthPt: Float,
        measure: (String, Float) -> Float
    ): List<String> {
        if (measure(word, size) <= maxWidthPt) return listOf(word)
        val chunks = mutableListOf<String>()
        val current = StringBuilder()
        for (char in word) {
            current.append(char)
            if (current.length > 1 && measure(current.toString(), size) > maxWidthPt) {
                current.setLength(current.length - 1)
                chunks.add(current.toString())
                current.setLength(0)
                current.append(char)
            }
        }
        if (current.isNotEmpty()) chunks.add(current.toString())
        return chunks
    }
}
