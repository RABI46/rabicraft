package org.rabicraft.app.data.tools

/** An image stream recovered from a PDF; [bytes] is the encoded file exactly as stored. */
class EmbeddedImage(val bytes: ByteArray, val extension: String)

/**
 * Recovers the JPEG and PNG pictures embedded in a PDF by scanning its bytes for image signatures.
 *
 * A PDF may also store a picture as Flate-compressed pixel data, which this scanner cannot recover
 * without decoding content streams; those pages simply yield no file. The caps keep a hostile
 * document (a thousand fake 50 MB "JPEGs") from exhausting memory or storage.
 */
object EmbeddedImageScanner {

    private const val MIN_IMAGE_BYTES = 128
    private const val MAX_IMAGES = 1000
    private const val MAX_TOTAL_BYTES = 500L * 1024L * 1024L

    fun scan(data: ByteArray): List<EmbeddedImage> {
        val found = mutableListOf<EmbeddedImage>()
        var total = 0L
        var index = 0
        while (index < data.size - 3 && found.size < MAX_IMAGES) {
            if (isJpegStart(data, index)) {
                val end = jpegEnd(data, index)
                if (end != null && isValidJpeg(data, index, end)) {
                    val size = end - index
                    if (size >= MIN_IMAGE_BYTES) {
                        if (total + size > MAX_TOTAL_BYTES) break
                        found.add(EmbeddedImage(data.copyOfRange(index, end), "jpg"))
                        total += size
                    }
                    index = end
                    continue
                }
                index++
                continue
            }
            if (isPngStart(data, index)) {
                val end = pngEnd(data, index)
                if (end != null && end - index >= MIN_IMAGE_BYTES) {
                    val size = end - index
                    if (total + size > MAX_TOTAL_BYTES) break
                    found.add(EmbeddedImage(data.copyOfRange(index, end), "png"))
                    total += size
                    index = end
                    continue
                }
            }
            index++
        }
        return found
    }

    private fun byteAt(data: ByteArray, index: Int): Int = data[index].toInt() and 0xFF

    private fun isJpegStart(data: ByteArray, index: Int): Boolean =
        index + 2 < data.size &&
            byteAt(data, index) == 0xFF &&
            byteAt(data, index + 1) == 0xD8 &&
            byteAt(data, index + 2) == 0xFF

    private fun isPngStart(data: ByteArray, index: Int): Boolean {
        if (index + 7 >= data.size) return false
        val signature = intArrayOf(0x89, 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A)
        for (offset in signature.indices) {
            if (byteAt(data, index + offset) != signature[offset]) return false
        }
        return true
    }

    /**
     * Finds the EOI (`FF D9`) closing a JPEG that starts at [start]. Inside the entropy-coded data
     * every `FF` is byte-stuffed with `00` or followed by an RSTn marker, so a bare `FF D9` is the
     * real end of image. Returns the index just past it, or null when it never appears.
     */
    private fun jpegEnd(data: ByteArray, start: Int): Int? {
        var position = start + 2
        while (position < data.size - 1) {
            if (byteAt(data, position) == 0xFF && byteAt(data, position + 1) == 0xD9) {
                return position + 2
            }
            position++
        }
        return null
    }

    /**
     * Walks the JPEG marker segments and requires a Start-Of-Frame before the scan data or the end
     * of image. Without one the bytes are a coincidence inside another stream, not a picture.
     */
    private fun isValidJpeg(data: ByteArray, start: Int, end: Int): Boolean {
        var position = start + 2
        var sawFrame = false
        while (position < end - 1) {
            if (byteAt(data, position) != 0xFF) return false
            while (position < end && byteAt(data, position) == 0xFF) position++
            if (position >= end) break
            val marker = byteAt(data, position)
            position++
            if (marker == 0xD9 || marker == 0xDA) break
            // TEM and the restart markers carry no length field.
            if (marker == 0x01 || marker in 0xD0..0xD7) continue
            if (position + 1 >= end) return false
            val segmentLength = (byteAt(data, position) shl 8) or byteAt(data, position + 1)
            if (segmentLength < 2) return false
            val isFrame = marker in 0xC0..0xCF &&
                marker != 0xC4 && marker != 0xC8 && marker != 0xCC
            if (isFrame) sawFrame = true
            position += segmentLength
        }
        return sawFrame
    }

    /** Finds the end of the PNG chunk stream: `IEND` followed by its four CRC bytes. */
    private fun pngEnd(data: ByteArray, start: Int): Int? {
        var position = start + 8
        while (position < data.size - 7) {
            if (byteAt(data, position) == 0x49 &&
                byteAt(data, position + 1) == 0x45 &&
                byteAt(data, position + 2) == 0x4E &&
                byteAt(data, position + 3) == 0x44
            ) {
                return (position + 8).coerceAtMost(data.size)
            }
            position++
        }
        return null
    }
}
