package org.rabicraft.app.data.tools

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.rabicraft.app.model.DiffKind

class PageDiffTest {

    private val white = 0xFFFFFFFF.toInt()
    private val black = 0xFF000000.toInt()

    @Test
    fun `same pixels are identical`() {
        val page = IntArray(1000) { white }
        val result = PageDiff.compare(page, page.copyOf())

        assertEquals(DiffKind.IDENTICAL, result.kind)
        assertEquals(0f, result.ratio, 0.0001f)
    }

    @Test
    fun `a changed block is reported as different with its share of the page`() {
        val left = IntArray(1000) { white }
        val right = IntArray(1000) { white }
        for (index in 0 until 100) right[index] = black

        val result = PageDiff.compare(left, right)

        assertEquals(DiffKind.DIFFERENT, result.kind)
        assertEquals(0.1f, result.ratio, 0.0001f)
    }

    @Test
    fun `anti-aliasing noise stays identical`() {
        val left = IntArray(10000) { white }
        val right = IntArray(10000) { white }
        right[0] = 0xFFF4F4F4.toInt()

        assertEquals(DiffKind.IDENTICAL, PageDiff.compare(left, right).kind)
    }

    @Test
    fun `a missing side and mismatched sizes are their own outcomes`() {
        assertEquals(DiffKind.MISSING, PageDiff.compare(null, IntArray(4)).kind)
        assertEquals(DiffKind.MISSING, PageDiff.compare(IntArray(4), null).kind)
        assertEquals(DiffKind.DIMENSION_MISMATCH, PageDiff.compare(IntArray(4), IntArray(8)).kind)
        assertEquals(DiffKind.DIMENSION_MISMATCH, PageDiff.compare(IntArray(0), IntArray(0)).kind)
    }

    @Test
    fun `ratio never exceeds one`() {
        val left = IntArray(500) { white }
        val right = IntArray(500) { black }
        val ratio = PageDiff.differingRatio(left, right)

        assertTrue(ratio <= 1f)
        assertEquals(1f, ratio, 0.0001f)
    }
}
