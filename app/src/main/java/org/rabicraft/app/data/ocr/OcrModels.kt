package org.rabicraft.app.data.ocr

/** One recognised word, with its box normalised to the page (0..1 on both axes). */
data class OcrWord(
    val text: String,
    val left: Float,
    val top: Float,
    val right: Float,
    val bottom: Float
)

data class OcrPageResult(
    val pageIndex: Int,
    val text: String,
    val words: List<OcrWord>
)
