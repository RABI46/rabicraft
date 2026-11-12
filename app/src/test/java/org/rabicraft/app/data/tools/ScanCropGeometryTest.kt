package org.rabicraft.app.data.tools

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import org.rabicraft.app.model.ScanCorners

class ScanCropGeometryTest {

    private val sheet = ScanCorners(
        topLeftX = 0.1f,
        topLeftY = 0.2f,
        topRightX = 0.9f,
        topRightY = 0.2f,
        bottomRightX = 0.9f,
        bottomRightY = 0.8f,
        bottomLeftX = 0.1f,
        bottomLeftY = 0.8f
    )

    private val view = ScanCropGeometry.viewportFor(1000, 1000, 1000, 1000)

    @Test
    fun `the picture is fitted and centred`() {
        // A portrait photo in a landscape view keeps its ratio and gains side margins.
        val box = ScanCropGeometry.fit(1000, 500, 300, 600)

        assertEquals(250f, box.width, 0.01f)
        assertEquals(500f, box.height, 0.01f)
        assertEquals(375f, box.left, 0.01f)
        assertEquals(0f, box.top, 0.01f)
        assertEquals(0f, box.ratioX(box.left), 0.001f)
        assertEquals(1f, box.ratioX(box.left + box.width), 0.001f)
        assertEquals(0.5f, box.ratioX(box.x(0.5f)), 0.001f)
    }

    @Test
    fun `a degenerate view still yields a usable box`() {
        val box = ScanCropGeometry.fit(0, 0, 0, 0)

        assertEquals(1f, box.width, 0.001f)
        assertEquals(1f, box.height, 0.001f)
        assertEquals(0f, box.ratioX(0f), 0.001f)
        assertEquals(1f, box.ratioX(500f), 0.001f)
    }

    @Test
    fun `the picture cannot be pushed aside at rest`() {
        val pushed = view.panned(200f, -400f)

        assertEquals(1f, pushed.zoom, 0.0001f)
        assertEquals(0f, pushed.panX, 0.0001f)
        assertEquals(0f, pushed.panY, 0.0001f)
    }

    @Test
    fun `panning while zoomed stops at the edge of the picture`() {
        val zoomed = view.zoomed(2f)
        assertEquals(2000f, zoomed.contentWidth, 0.01f)

        val farRight = zoomed.panned(5000f, 5000f)
        assertEquals(0f, farRight.panX, 0.0001f)
        assertEquals(0f, farRight.panY, 0.0001f)

        val farLeft = zoomed.panned(-5000f, -5000f)
        assertEquals(-1000f, farLeft.panX, 0.0001f)
        assertEquals(-1000f, farLeft.panY, 0.0001f)
    }

    @Test
    fun `zooming keeps the focus point where it is`() {
        val zoomed = view.zoomed(2f, focusX = 500f, focusY = 250f)

        assertEquals(2f, zoomed.zoom, 0.0001f)
        assertEquals(500f, zoomed.x(0.5f), 0.5f)
        assertEquals(250f, zoomed.y(0.25f), 0.5f)
        assertEquals(0.5f, zoomed.ratioX(500f), 0.001f)
        assertEquals(0.25f, zoomed.ratioY(250f), 0.001f)
    }

    @Test
    fun `zoom stays inside its range`() {
        assertEquals(CropViewport.MAX_ZOOM, view.zoomed(50f).zoom, 0.0001f)
        assertEquals(CropViewport.MIN_ZOOM, view.zoomed(0.1f).zoom, 0.0001f)
    }

    @Test
    fun `dragging a corner moves only that corner`() {
        val moved = ScanCropGeometry.moveCorner(sheet, 0, 0.05f, -0.05f)

        assertEquals(0.15f, moved.topLeftX, 0.0001f)
        assertEquals(0.15f, moved.topLeftY, 0.0001f)
        assertEquals(0.9f, moved.topRightX, 0.0001f)
        assertEquals(0.8f, moved.bottomLeftY, 0.0001f)
    }

    @Test
    fun `a corner cannot be dragged off the picture`() {
        val moved = ScanCropGeometry.moveCorner(sheet, 2, 5f, 5f)

        assertEquals(1f, moved.bottomRightX, 0.0001f)
        assertEquals(1f, moved.bottomRightY, 0.0001f)

        val pulled = ScanCropGeometry.moveCorner(sheet, 2, -5f, -5f)
        assertEquals(0f, pulled.bottomRightX, 0.0001f)
        assertEquals(0f, pulled.bottomRightY, 0.0001f)
    }

    @Test
    fun `an unknown corner index changes nothing`() {
        assertEquals(sheet, ScanCropGeometry.moveCorner(sheet, 9, 0.1f, 0.1f))
    }

    @Test
    fun `the touch grabs the nearest corner within reach`() {
        assertEquals(0, ScanCropGeometry.cornerAt(sheet, view, 110f, 210f, 40f))
        assertEquals(3, ScanCropGeometry.cornerAt(sheet, view, 120f, 790f, 40f))
        assertNull(ScanCropGeometry.cornerAt(sheet, view, 500f, 500f, 40f))
        // The same reach is measured on screen, so it holds when the picture is zoomed in.
        val zoomed = view.zoomed(3f)
        assertEquals(0, ScanCropGeometry.cornerAt(sheet, zoomed, zoomed.x(0.1f) + 20f, zoomed.y(0.2f), 40f))
    }

    @Test
    fun `corners survive the trip through an array`() {
        assertEquals(sheet, ScanCropGeometry.fromArray(ScanCropGeometry.toArray(sheet)))
        assertEquals(4, ScanCropGeometry.CORNER_COUNT)
        assertEquals(0.02f, ScanCropGeometry.fullFrame.topLeftX, 0.0001f)
    }
}
