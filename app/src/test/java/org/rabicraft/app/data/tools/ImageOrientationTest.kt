package org.rabicraft.app.data.tools

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ImageOrientationTest {

    @Test
    fun `an upright picture is left alone`() {
        assertEquals(ImageTurn.NONE, ImageOrientation.fromExifTag(1))
        assertEquals(ImageTurn.NONE, ImageOrientation.fromExifTag(0))
        assertEquals(ImageTurn.NONE, ImageOrientation.fromExifTag(99))
        assertEquals(ImageTurn.NONE, ImageOrientation.fromExifTag(-1))
    }

    @Test
    fun `the common rotations map to clockwise turns`() {
        assertEquals(ImageTurn.ROTATE_90, ImageOrientation.fromExifTag(6))
        assertEquals(ImageTurn.ROTATE_180, ImageOrientation.fromExifTag(3))
        assertEquals(ImageTurn.ROTATE_270, ImageOrientation.fromExifTag(8))
    }

    @Test
    fun `the mirrored values keep their mirror`() {
        assertEquals(ImageTurn.MIRROR, ImageOrientation.fromExifTag(2))
        assertEquals(ImageTurn.MIRROR_ROTATE_180, ImageOrientation.fromExifTag(4))
        assertEquals(ImageTurn.MIRROR_ROTATE_90, ImageOrientation.fromExifTag(5))
        assertEquals(ImageTurn.MIRROR_ROTATE_270, ImageOrientation.fromExifTag(7))
    }

    @Test
    fun `only quarter turns exchange the sides`() {
        assertTrue(ImageTurn.ROTATE_90.swapsSides)
        assertTrue(ImageTurn.MIRROR_ROTATE_270.swapsSides)
        assertFalse(ImageTurn.NONE.swapsSides)
        assertFalse(ImageTurn.ROTATE_180.swapsSides)
        assertFalse(ImageTurn.MIRROR.swapsSides)
        assertEquals(90, ImageTurn.ROTATE_90.degrees)
        assertEquals(270, ImageTurn.MIRROR_ROTATE_270.degrees)
        assertEquals(0, ImageTurn.MIRROR.degrees)
    }
}
