package org.rabicraft.app.model

/**
 * Document tools ported from Stirling-PDF's tool set that FranPdf can run entirely on-device.
 *
 * Every tool here rewrites the exported PDF through the same page pipeline as the rest of the app,
 * so applying one produces a real file rather than a UI-only preview.
 */
enum class DocumentTool {
    PAGE_NUMBERS,
    WATERMARK,
    HEADER_FOOTER,
    N_UP,
    BOOKLET,
    CROP,
    COLOUR,
    COMPRESS,
    BLANK_PAGES,
    SPLIT,
    EXTRACT,
    TO_IMAGES,
    OCR,
    MERGE,
    STAMP,
    EXTRACT_IMAGES,
    COMPARE,
    IMAGES_TO_PDF,
    SCAN_DOCUMENT,
    CREATE_PDF,
    DETECT_FIELDS
}

/** Where a text overlay sits inside its page. */
enum class TextAnchor {
    TOP_LEFT,
    TOP_CENTRE,
    TOP_RIGHT,
    BOTTOM_LEFT,
    BOTTOM_CENTRE,
    BOTTOM_RIGHT,
    CENTRE
}

/** Post-processing applied to each rendered page. */
enum class ColourEffect {
    NONE,
    INVERT,
    GRAYSCALE,
    HIGH_CONTRAST
}

enum class ImageFormat {
    PNG,
    JPG
}

/** A text stamp and its placement, shared by page numbers, headers, footers and watermarks. */
data class ToolText(
    val template: String,
    val anchor: TextAnchor = TextAnchor.BOTTOM_CENTRE,
    val fontSizePt: Float = 10f,
    val colorArgb: Long = 0xFF374151L,
    val bold: Boolean = false,
    val opacity: Float = 1f,
    val insetPt: Float = 16f,
    val rotationDegrees: Float = 0f
)

/**
 * Page numbering. `{page}`, `{total}`, `{title}` and `{date}` are replaced on every page. [selection]
 * holds page numbers and ranges such as `1-3,5`; blank means every page.
 */
data class PageNumberSettings(
    val text: ToolText = ToolText(
        template = "Page {page} / {total}",
        anchor = TextAnchor.BOTTOM_CENTRE
    ),
    val startNumber: Int = 1,
    val selection: String = ""
)

data class WatermarkSettings(
    val text: String = "CONFIDENTIEL",
    val fontSizePt: Float = 64f,
    val colorArgb: Long = 0xFF8A011EL,
    val opacity: Float = 0.18f,
    val diagonal: Boolean = true,
    val selection: String = ""
)

/**
 * The ready-made stamps PDF Tech offers (Apache-2.0), same wording and colours: a reviewer taps
 * "APPROUVÉ" or "BROUILLON" instead of typing the label and picking its colour.
 */
enum class StampPreset(val text: String, val colorArgb: Long) {
    CONFIDENTIEL("CONFIDENTIEL", 0xFFD32F2FL),
    APPROVED("APPROUVÉ", 0xFF388E3CL),
    COPY("COPIE", 0xFF1976D2L),
    DRAFT("BROUILLON", 0xFFF57C00L),
    CANCELLED("ANNULÉ", 0xFF7B1FA2L),
    URGENT("URGENT", 0xFFD32F2FL)
}

/** A diagonal stamp; [firstPageOnly] marks a cover page instead of every sheet. */
data class StampSettings(
    val preset: StampPreset = StampPreset.CONFIDENTIEL,
    val text: String = preset.text,
    val colorArgb: Long = preset.colorArgb,
    val opacity: Float = 0.25f,
    val fontSizePt: Float = 54f,
    val firstPageOnly: Boolean = false
)

data class HeaderFooterSettings(
    val header: ToolText = ToolText(
        template = "",
        anchor = TextAnchor.TOP_CENTRE
    ),
    val footer: ToolText = ToolText(
        template = "Page {page} / {total}",
        anchor = TextAnchor.BOTTOM_CENTRE
    ),
    val selection: String = ""
)

/** Places [columns] x [rows] source pages on one sheet, in reading order. */
data class NUpSettings(
    val columns: Int = 2,
    val rows: Int = 1,
    val marginPt: Float = 16f,
    val gapPt: Float = 12f,
    val landscape: Boolean = false,
    val borders: Boolean = false
)

/** Crop insets as a fraction (0..0.9) of the page, like Stirling's crop tool. */
data class CropSettings(
    val topPct: Float = 0f,
    val bottomPct: Float = 0f,
    val leftPct: Float = 0f,
    val rightPct: Float = 0f
) {
    val isEmpty: Boolean
        get() = topPct <= 0f && bottomPct <= 0f && leftPct <= 0f && rightPct <= 0f
}

data class CompressSettings(
    val maxDpi: Int = 150,
    val jpegQuality: Int = 70,
    val selection: String = ""
)

/** A page counts as blank when fewer than this fraction of its pixels carry ink. */
data class BlankPageSettings(
    val inkThreshold: Float = 0.004f
)

/** Either every [pagesPerFile] pages, or the explicit [ranges] such as `1-3,5`. */
data class SplitSettings(
    val pagesPerFile: Int = 1,
    val ranges: String = ""
)

data class ImageExportSettings(
    val format: ImageFormat = ImageFormat.PNG,
    val jpegQuality: Int = 90
)

/** What the Tools hub asks the view-model to do. */
sealed interface ToolConfig {
    data class PageNumbers(val settings: PageNumberSettings) : ToolConfig
    data class Watermark(val settings: WatermarkSettings) : ToolConfig
    data class HeaderFooter(val settings: HeaderFooterSettings) : ToolConfig
    data class NUp(val settings: NUpSettings) : ToolConfig
    data object Booklet : ToolConfig
    data class Crop(val settings: CropSettings, val selection: String = "") : ToolConfig
    data class Colour(val effect: ColourEffect, val selection: String = "") : ToolConfig
    data class Compress(val settings: CompressSettings) : ToolConfig
    data class BlankPages(val settings: BlankPageSettings) : ToolConfig
    data class Split(val settings: SplitSettings) : ToolConfig
    data class Extract(val ranges: String) : ToolConfig
    data class ToImages(val settings: ImageExportSettings) : ToolConfig
    data class Ocr(val selection: String = "") : ToolConfig
    data class Stamp(val settings: StampSettings) : ToolConfig
    data object ExtractImages : ToolConfig
}
