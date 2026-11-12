package org.rabicraft.app.data.tools

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class FormFieldDetectorTest {

    private val white = 0xFFFFFFFF.toInt()
    private val black = 0xFF000000.toInt()

    private fun page(width: Int, height: Int): IntArray = IntArray(width * height) { white }

    private fun rule(pixels: IntArray, width: Int, row: Int, from: Int, to: Int, thickness: Int = 1) {
        for (y in row until row + thickness) {
            for (x in from until to) pixels[y * width + x] = black
        }
    }

    @Test
    fun `an underline becomes one field around it`() {
        val width = 400
        val height = 600
        val pixels = page(width, height)
        rule(pixels, width, row = 300, from = 80, to = 320)

        val fields = FormFieldDetector.detect(pixels, width, height)

        assertEquals(1, fields.size)
        assertEquals(0.2f, fields[0].left, 0.02f)
        assertEquals(0.8f, fields[0].right, 0.02f)
        assertTrue("the box reaches above the rule", fields[0].top < 0.5f)
        assertTrue("the box covers the rule", fields[0].bottom > 0.5f)
    }

    @Test
    fun `a filled bar is not a field`() {
        val width = 400
        val height = 600
        val pixels = page(width, height)
        rule(pixels, width, row = 200, from = 40, to = 360, thickness = 30)

        assertTrue(FormFieldDetector.detect(pixels, width, height).isEmpty())
    }

    @Test
    fun `short marks are ignored`() {
        val width = 400
        val height = 600
        val pixels = page(width, height)
        rule(pixels, width, row = 300, from = 100, to = 120)

        assertTrue(FormFieldDetector.detect(pixels, width, height).isEmpty())
    }

    @Test
    fun `two separate rules stay two fields`() {
        val width = 400
        val height = 600
        val pixels = page(width, height)
        rule(pixels, width, row = 200, from = 80, to = 320)
        rule(pixels, width, row = 400, from = 80, to = 320)

        assertEquals(2, FormFieldDetector.detect(pixels, width, height).size)
    }

    @Test
    fun `a dark streak inside a picture is not a field`() {
        val width = 400
        val height = 600
        val pixels = page(width, height)
        // Alternating dark rows: each run is long enough, but the gap rows are inked too.
        for (row in 100..140) {
            if (row % 2 == 0) rule(pixels, width, row = row, from = 100, to = 250)
        }

        assertTrue(FormFieldDetector.detect(pixels, width, height).isEmpty())
    }

    @Test
    fun `a blank page and bad input have no field`() {
        assertTrue(FormFieldDetector.detect(page(100, 100), 100, 100).isEmpty())
        assertTrue(FormFieldDetector.detect(IntArray(0), 0, 0).isEmpty())
        assertTrue(FormFieldDetector.detect(IntArray(10), 400, 600).isEmpty())
    }
}
