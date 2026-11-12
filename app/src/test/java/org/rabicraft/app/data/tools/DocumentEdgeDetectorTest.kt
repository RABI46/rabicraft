package org.rabicraft.app.data.tools

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test

class DocumentEdgeDetectorTest {

    private val desk = 0xFF202020.toInt()
    private val paper = 0xFFF0F0F0.toInt()

    private fun frame(width: Int, height: Int, fill: Int): IntArray =
        IntArray(width * height) { fill }

    private fun fillRect(
        pixels: IntArray,
        width: Int,
        left: Int,
        top: Int,
        right: Int,
        bottom: Int,
        colour: Int
    ) {
        for (y in top until bottom) {
            for (x in left until right) pixels[y * width + x] = colour
        }
    }

    @Test
    fun `a sheet on a dark desk yields its four corners`() {
        val width = 400
        val height = 600
        val pixels = frame(width, height, desk)
        fillRect(pixels, width, 40, 30, 360, 570, paper)

        val corners = DocumentEdgeDetector.detect(pixels, width, height)

        assertNotNull(corners)
        val found = corners!!
        assertEquals(0.1f, found.topLeftX, 0.03f)
        assertEquals(0.05f, found.topLeftY, 0.03f)
        assertEquals(0.9f, found.topRightX, 0.03f)
        assertEquals(0.05f, found.topRightY, 0.03f)
        assertEquals(0.9f, found.bottomRightX, 0.03f)
        assertEquals(0.95f, found.bottomRightY, 0.03f)
        assertEquals(0.1f, found.bottomLeftX, 0.03f)
        assertEquals(0.95f, found.bottomLeftY, 0.03f)
    }

    @Test
    fun `a tilted sheet still gives four corners`() {
        val width = 400
        val height = 600
        val pixels = frame(width, height, desk)
        // A parallelogram leaning right: the corners are no longer axis-aligned.
        for (y in 60 until 540) {
            val shift = (y - 60) / 8
            for (x in (60 + shift) until (340 + shift)) pixels[y * width + x] = paper
        }

        val corners = DocumentEdgeDetector.detect(pixels, width, height)

        assertNotNull(corners)
        val found = corners!!
        assertEquals(0.15f, found.topLeftX, 0.05f)
        assertEquals(0.10f, found.topLeftY, 0.05f)
        assertEquals(0.30f, found.bottomLeftX, 0.05f)
        assertEquals(0.90f, found.bottomRightY, 0.05f)
    }

    @Test
    fun `a frame with no background is not cropped`() {
        assertNull(DocumentEdgeDetector.detect(frame(400, 600, paper), 400, 600))
    }

    @Test
    fun `an empty frame is not cropped`() {
        assertNull(DocumentEdgeDetector.detect(frame(400, 600, desk), 400, 600))
    }

    @Test
    fun `a sheet covering too little of the frame is rejected`() {
        val width = 400
        val height = 600
        val pixels = frame(width, height, desk)
        fillRect(pixels, width, 180, 280, 230, 330, paper)

        assertNull(DocumentEdgeDetector.detect(pixels, width, height))
    }

    @Test
    fun `degenerate input has no corners`() {
        assertNull(DocumentEdgeDetector.detect(IntArray(0), 0, 0))
        assertNull(DocumentEdgeDetector.detect(IntArray(10), 400, 600))
        assertNull(DocumentEdgeDetector.detect(frame(10, 10, paper), 10, 10))
    }
}
