package org.rabicraft.app.data.tools

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.ByteArrayOutputStream

class EmbeddedImageScannerTest {

    /** A marker segment: FF marker, two-byte length (payload + 2), payload of zero bytes. */
    private fun segment(marker: Int, payloadSize: Int): ByteArray {
        val length = payloadSize + 2
        val segment = ByteArray(4 + payloadSize)
        segment[0] = 0xFF.toByte()
        segment[1] = marker.toByte()
        segment[2] = (length shr 8).toByte()
        segment[3] = length.toByte()
        return segment
    }

    private fun jpeg(): ByteArray {
        val out = ByteArrayOutputStream()
        out.write(byteArrayOf(0xFF.toByte(), 0xD8.toByte()))
        out.write(segment(0xE0, 14))
        // The frame header carries the bulk of the bytes so the stream passes the size floor.
        out.write(segment(0xC0, 130))
        out.write(byteArrayOf(0xFF.toByte(), 0xD9.toByte()))
        return out.toByteArray()
    }

    private fun png(): ByteArray {
        val out = ByteArrayOutputStream()
        out.write(
            byteArrayOf(
                0x89.toByte(), 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A
            )
        )
        out.write(ByteArray(120) { 0x11 })
        out.write(byteArrayOf(0x49, 0x45, 0x4E, 0x44, 0, 0, 0, 0))
        return out.toByteArray()
    }

    @Test
    fun `finds jpeg and png streams among document bytes`() {
        val document = ByteArrayOutputStream()
        document.write("%PDF-1.7 stream".toByteArray())
        document.write(jpeg())
        document.write(ByteArray(40) { 0x2A })
        document.write(png())
        document.write("endstream".toByteArray())

        val found = EmbeddedImageScanner.scan(document.toByteArray())

        assertEquals(2, found.size)
        assertEquals("jpg", found[0].extension)
        assertEquals("png", found[1].extension)
        assertEquals(jpeg().size, found[0].bytes.size)
        assertEquals(png().size, found[1].bytes.size)
    }

    @Test
    fun `rejects a jpeg-shaped stream without a frame header`() {
        val document = ByteArrayOutputStream()
        document.write(byteArrayOf(0xFF.toByte(), 0xD8.toByte()))
        document.write(segment(0xE0, 200))
        document.write(byteArrayOf(0xFF.toByte(), 0xD9.toByte()))

        assertTrue(EmbeddedImageScanner.scan(document.toByteArray()).isEmpty())
    }

    @Test
    fun `ignores streams below the minimum size`() {
        val tiny = ByteArrayOutputStream()
        tiny.write(byteArrayOf(0xFF.toByte(), 0xD8.toByte()))
        tiny.write(segment(0xC0, 8))
        tiny.write(byteArrayOf(0xFF.toByte(), 0xD9.toByte()))

        assertTrue(EmbeddedImageScanner.scan(tiny.toByteArray()).isEmpty())
    }

    @Test
    fun `plain bytes contain no image`() {
        assertTrue(EmbeddedImageScanner.scan("just some text".toByteArray()).isEmpty())
        assertTrue(EmbeddedImageScanner.scan(ByteArray(0)).isEmpty())
    }
}
