package org.rabicraft.app.data.tools

/**
 * How a decoded picture has to be turned before it is used.
 *
 * Cameras write the orientation of a photo in its EXIF header and leave the pixels as the sensor
 * saw them, so a picture taken in portrait arrives 90° sideways. [degrees] is clockwise.
 */
enum class ImageTurn(val degrees: Int, val mirrored: Boolean) {
    NONE(0, false),
    ROTATE_90(90, false),
    ROTATE_180(180, false),
    ROTATE_270(270, false),
    MIRROR(0, true),
    MIRROR_ROTATE_90(90, true),
    MIRROR_ROTATE_180(180, true),
    MIRROR_ROTATE_270(270, true);

    /** True when the turn exchanges the width and the height of the picture. */
    val swapsSides: Boolean get() = degrees == 90 || degrees == 270
}

/**
 * The EXIF orientation values and what each one asks for.
 *
 * Kept out of the repository so the mapping — the part that goes wrong silently — is covered by
 * tests: a wrong turn does not crash, it just lays the page out sideways.
 */
object ImageOrientation {

    /** Turns the EXIF `TAG_ORIENTATION` value into what the picture needs. */
    fun fromExifTag(tag: Int): ImageTurn = when (tag) {
        2 -> ImageTurn.MIRROR
        3 -> ImageTurn.ROTATE_180
        4 -> ImageTurn.MIRROR_ROTATE_180
        5 -> ImageTurn.MIRROR_ROTATE_90
        6 -> ImageTurn.ROTATE_90
        7 -> ImageTurn.MIRROR_ROTATE_270
        8 -> ImageTurn.ROTATE_270
        // 0, 1 and anything unfamiliar mean "already upright".
        else -> ImageTurn.NONE
    }
}
