package org.rabicraft.app.data.tools

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.rabicraft.app.data.ocr.OcrWord

class TextBlockPlannerTest {

    private fun word(text: String, left: Float, top: Float, right: Float, bottom: Float) =
        OcrWord(text = text, left = left, top = top, right = right, bottom = bottom)

    @Test
    fun `words on one baseline become a single line, left to right`() {
        val words = listOf(
            word("Prénom", 0.32f, 0.30f, 0.48f, 0.335f),
            word("Nom", 0.10f, 0.301f, 0.24f, 0.334f)
        )

        val blocks = TextBlockPlanner.groupLines(words)

        assertEquals(1, blocks.size)
        assertEquals("Nom Prénom", blocks[0].text)
        assertEquals(0.10f, blocks[0].left, 0.001f)
        assertEquals(0.48f, blocks[0].right, 0.001f)
    }

    @Test
    fun `separate baselines become separate blocks`() {
        val words = listOf(
            word("Ligne", 0.1f, 0.10f, 0.3f, 0.13f),
            word("un", 0.31f, 0.10f, 0.4f, 0.13f),
            word("Ligne", 0.1f, 0.20f, 0.3f, 0.23f)
        )

        val blocks = TextBlockPlanner.groupLines(words)

        assertEquals(2, blocks.size)
        assertEquals("Ligne un", blocks[0].text)
        assertEquals("Ligne", blocks[1].text)
    }

    @Test
    fun `blank and degenerate words are dropped`() {
        val words = listOf(
            word("   ", 0.1f, 0.1f, 0.2f, 0.12f),
            word("mot", 0.3f, 0.1f, 0.29f, 0.12f)
        )

        assertTrue(TextBlockPlanner.groupLines(words).isEmpty())
        assertTrue(TextBlockPlanner.groupLines(emptyList()).isEmpty())
    }
}
