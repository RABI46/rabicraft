package org.rabicraft.app.data.tools

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class TextLayoutTest {

    /** Deterministic stand-in for Paint.measureText: half the point size per character. */
    private val measure: (String, Float) -> Float = { text, size -> text.length * size * 0.5f }

    @Test
    fun `parse reads headings, bullets and merged paragraphs`() {
        val blocks = TextLayout.parse("# Rapport\n\nBonjour le monde\nsur deux lignes\n- premier\n* second")

        assertEquals(4, blocks.size)
        assertEquals(TextBlockKind.HEADING, blocks[0].kind)
        assertEquals("Rapport", blocks[0].text)
        assertEquals(TextBlockKind.PARAGRAPH, blocks[1].kind)
        assertEquals("Bonjour le monde sur deux lignes", blocks[1].text)
        assertEquals(TextBlockKind.BULLET, blocks[2].kind)
        assertEquals("premier", blocks[2].text)
        assertEquals("second", blocks[3].text)
    }

    @Test
    fun `wrap keeps words inside the column`() {
        val lines = TextLayout.wrap(
            TextBlock(TextBlockKind.PARAGRAPH, "aaaa bbbb cccc dddd"),
            baseSizePt = 10f,
            maxWidthPt = 45f,
            measure = measure
        )

        assertEquals(2, lines.size)
        assertEquals("aaaa bbbb", lines[0].text)
        assertEquals("cccc dddd", lines[1].text)
        assertTrue(lines.all { measure(it.text, 10f) <= 45f })
    }

    @Test
    fun `bullets keep their marker`() {
        val lines = TextLayout.wrap(TextBlock(TextBlockKind.BULLET, "item"), 10f, 200f, measure)
        assertEquals("• item", lines.single().text)
    }

    @Test
    fun `a word wider than the column is split`() {
        val lines = TextLayout.wrap(
            TextBlock(TextBlockKind.PARAGRAPH, "supercalifragilistic"),
            10f,
            30f,
            measure
        )

        assertTrue(lines.size > 1)
        assertTrue(lines.all { measure(it.text, 10f) <= 30f })
        assertEquals("supercalifragilistic", lines.joinToString("") { it.text })
    }

    @Test
    fun `headings are larger than body text`() {
        assertTrue(
            TextLayout.lineHeight(10f, TextBlockKind.HEADING) >
                TextLayout.lineHeight(10f, TextBlockKind.PARAGRAPH)
        )
        assertTrue(
            TextLayout.lineHeight(10f, TextBlockKind.TITLE) >
                TextLayout.lineHeight(10f, TextBlockKind.HEADING)
        )
    }

    @Test
    fun `pagination breaks before a line that would overflow`() {
        val lines = (0 until 20).map { TextLine("ligne $it", TextBlockKind.PARAGRAPH) }
        val pages = TextLayout.paginate(lines, 10f, 100f, topMarginPt = 10f, bottomMarginPt = 10f)

        assertEquals(4, pages.size)
        assertEquals(5, pages[0].size)
        assertEquals(20, pages.sumOf { it.size })
    }

    @Test
    fun `empty input writes nothing`() {
        assertTrue(TextLayout.paginate(emptyList(), 10f, 100f, 10f, 10f).isEmpty())
        assertTrue(TextLayout.parse("   \n\n").isEmpty())
        assertTrue(
            TextLayout.wrap(TextBlock(TextBlockKind.PARAGRAPH, "   "), 10f, 100f, measure).isEmpty()
        )
    }
}
