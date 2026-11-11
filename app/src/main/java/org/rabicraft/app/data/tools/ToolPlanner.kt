package org.rabicraft.app.data.tools

import org.rabicraft.app.model.BlankPageSettings
import org.rabicraft.app.model.ColourEffect
import org.rabicraft.app.model.CropSettings
import org.rabicraft.app.model.HeaderFooterSettings
import org.rabicraft.app.model.NUpSettings
import org.rabicraft.app.model.PageItem
import org.rabicraft.app.model.PageNumberSettings
import org.rabicraft.app.model.TextAnchor
import org.rabicraft.app.model.ToolText
import org.rabicraft.app.model.WatermarkSettings
import java.time.LocalDate
import kotlin.math.max
import kotlin.math.min

/** Position and size of a source page on an output sheet, as fractions of the sheet. */
data class SlotRect(
    val left: Float,
    val top: Float,
    val width: Float,
    val height: Float
)

/** A text drawn inside a slot; sizes are PDF points so scaling the sheet does not resize text. */
data class TextOverlay(
    val text: String,
    val anchor: TextAnchor,
    val fontSizePt: Float,
    val colorArgb: Long,
    val bold: Boolean,
    val opacity: Float,
    val insetPt: Float,
    val rotationDegrees: Float
)

/** A box in PDF points, used to place the OCR text layer. */
data class TextBoxPt(
    val left: Float,
    val top: Float,
    val right: Float,
    val bottom: Float
)

data class PageSlot(
    val sourcePageIndex: Int,
    val rect: SlotRect,
    val crop: CropSettings = CropSettings(),
    val effect: ColourEffect = ColourEffect.NONE,
    val overlays: List<TextOverlay> = emptyList()
)

data class OutputSheet(val slots: List<PageSlot>)

/**
 * Everything the exporter needs to write a treated PDF: which source page lands where, how it is
 * cropped and tinted, and the text drawn on top of it. Pure data, so the geometry is unit-tested.
 */
data class ToolExportPlan(
    val sheets: List<OutputSheet>,
    val sheetWidthPt: Int,
    val sheetHeightPt: Int,
    val renderDpi: Int = 144,
    val jpegQuality: Int? = null,
    val borders: Boolean = false
)

/**
 * Builds export plans for the document tools. The geometry (N-up grids, saddle-stitch booklet
 * order, page selection, page chunking, blank detection) lives here so it can be checked without a
 * rasterizer.
 */
object ToolPlanner {

    const val FULL_SLOT = 1f

    /** Replaces `{page}`, `{total}`, `{title}` and `{date}`; unknown tokens are left untouched. */
    fun resolveTemplate(
        template: String,
        page: Int,
        total: Int,
        title: String = "",
        date: String = LocalDate.now().toString()
    ): String = template
        .replace("{page}", page.toString())
        .replace("{total}", total.toString())
        .replace("{title}", title)
        .replace("{date}", date)

    fun overlay(template: String, text: ToolText): TextOverlay = TextOverlay(
        text = template,
        anchor = text.anchor,
        fontSizePt = text.fontSizePt,
        colorArgb = text.colorArgb,
        bold = text.bold,
        opacity = text.opacity,
        insetPt = text.insetPt,
        rotationDegrees = text.rotationDegrees
    )

    /**
     * Parses a page selection such as `1-3,5` into 0-based indices.
     *
     * Returns null when [spec] is blank (every page), an empty set when nothing matched, and the
     * selected indices otherwise. Ranges may be written either way round (`5-3`).
     */
    fun parsePageSelection(spec: String, total: Int): Set<Int>? {
        if (total <= 0) return emptySet()
        val tokens = spec.split(',', ';', ' ', '\n')
            .map { it.trim() }
            .filter { it.isNotEmpty() }
        if (tokens.isEmpty()) return null
        val selected = linkedSetOf<Int>()
        for (token in tokens) {
            val bounds = token.split('-').map { it.trim() }
            when {
                bounds.size == 1 -> {
                    val page = bounds[0].toIntOrNull() ?: continue
                    if (page in 1..total) selected.add(page - 1)
                }
                bounds.size == 2 -> {
                    val first = bounds[0].toIntOrNull() ?: continue
                    val second = bounds[1].toIntOrNull() ?: continue
                    val from = min(first, second).coerceIn(1, total)
                    val to = max(first, second).coerceIn(1, total)
                    for (page in from..to) selected.add(page - 1)
                }
            }
        }
        return selected
    }

    /**
     * Parses `1-3,5` into the page ranges one output file each, clamped to the document. Returns
     * null when the spec is blank, so callers fall back to a fixed page count.
     */
    fun rangesFromSpec(spec: String, total: Int): List<IntRange>? {
        val selected = parsePageSelection(spec, total) ?: return null
        if (selected.isEmpty()) return emptyList()
        val ranges = mutableListOf<IntRange>()
        var start: Int? = null
        var previous: Int? = null
        for (page in selected.sorted()) {
            val currentStart = start
            val currentPrevious = previous
            if (currentStart == null || currentPrevious == null || page != currentPrevious + 1) {
                if (currentStart != null && currentPrevious != null) {
                    ranges.add(currentStart..currentPrevious)
                }
                start = page
            }
            previous = page
        }
        val lastStart = start
        val lastPrevious = previous
        if (lastStart != null && lastPrevious != null) ranges.add(lastStart..lastPrevious)
        return ranges
    }

    fun sheetSize(pages: List<PageItem>): Pair<Int, Int> {
        val first = pages.firstOrNull() ?: return Pair(595, 842)
        return Pair(
            first.width.coerceIn(72, 14400),
            first.height.coerceIn(72, 14400)
        )
    }

    /**
     * One output sheet per source page. [selection] limits which pages the effect, the crop and the
     * overlays touch; the other pages are copied unchanged.
     */
    fun sequentialPlan(
        pages: List<PageItem>,
        effect: ColourEffect = ColourEffect.NONE,
        crop: CropSettings = CropSettings(),
        renderDpi: Int = 144,
        jpegQuality: Int? = null,
        selection: Set<Int>? = null,
        overlaysFor: (pageIndex: Int, page: PageItem) -> List<TextOverlay> = { _, _ -> emptyList() }
    ): ToolExportPlan {
        val (width, height) = sheetSize(pages)
        val sheets = pages.mapIndexed { index, page ->
            val applies = selection == null || index in selection
            OutputSheet(
                listOf(
                    PageSlot(
                        sourcePageIndex = index,
                        rect = SlotRect(0f, 0f, FULL_SLOT, FULL_SLOT),
                        crop = if (applies) crop else CropSettings(),
                        effect = if (applies) effect else ColourEffect.NONE,
                        overlays = if (applies) overlaysFor(index, page) else emptyList()
                    )
                )
            )
        }
        return ToolExportPlan(
            sheets = sheets,
            sheetWidthPt = width,
            sheetHeightPt = height,
            renderDpi = renderDpi,
            jpegQuality = jpegQuality
        )
    }

    fun pageNumberPlan(
        pages: List<PageItem>,
        settings: PageNumberSettings,
        title: String = ""
    ): ToolExportPlan = sequentialPlan(
        pages = pages,
        selection = parsePageSelection(settings.selection, pages.size)
    ) { index, _ ->
        val number = settings.startNumber + index
        val resolved = resolveTemplate(settings.text.template, number, pages.size, title)
        if (resolved.isBlank()) emptyList() else listOf(overlay(resolved, settings.text))
    }

    fun watermarkPlan(pages: List<PageItem>, settings: WatermarkSettings): ToolExportPlan = sequentialPlan(
        pages = pages,
        selection = parsePageSelection(settings.selection, pages.size)
    ) { _, _ ->
        if (settings.text.isBlank()) {
            emptyList()
        } else {
            listOf(
                TextOverlay(
                    text = settings.text,
                    anchor = TextAnchor.CENTRE,
                    fontSizePt = settings.fontSizePt,
                    colorArgb = settings.colorArgb,
                    bold = true,
                    opacity = settings.opacity,
                    insetPt = 0f,
                    rotationDegrees = if (settings.diagonal) -35f else 0f
                )
            )
        }
    }

    fun headerFooterPlan(
        pages: List<PageItem>,
        settings: HeaderFooterSettings,
        title: String = ""
    ): ToolExportPlan = sequentialPlan(
        pages = pages,
        selection = parsePageSelection(settings.selection, pages.size)
    ) { index, _ ->
        val page = index + 1
        buildList {
            addAll(stamp(settings.header, page, pages.size, title))
            addAll(stamp(settings.footer, page, pages.size, title))
        }
    }

    private fun stamp(text: ToolText, page: Int, total: Int, title: String): List<TextOverlay> {
        val resolved = resolveTemplate(text.template, page, total, title)
        return if (resolved.isBlank()) emptyList() else listOf(overlay(resolved, text))
    }

    fun colourPlan(
        pages: List<PageItem>,
        effect: ColourEffect,
        selection: String = ""
    ): ToolExportPlan = sequentialPlan(
        pages = pages,
        effect = effect,
        selection = parsePageSelection(selection, pages.size)
    )

    fun cropPlan(
        pages: List<PageItem>,
        crop: CropSettings,
        selection: String = ""
    ): ToolExportPlan = sequentialPlan(
        pages = pages,
        crop = crop,
        selection = parsePageSelection(selection, pages.size)
    )

    /**
     * Places [settings.columns] x [settings.rows] pages on each sheet. The last sheet keeps its
     * empty cells rather than stretching the remaining pages, matching how imposition works.
     */
    fun nUpPlan(pages: List<PageItem>, settings: NUpSettings): ToolExportPlan {
        val (portraitWidth, portraitHeight) = sheetSize(pages)
        val sheetWidth = if (settings.landscape) portraitHeight else portraitWidth
        val sheetHeight = if (settings.landscape) portraitWidth else portraitHeight
        val slots = nUpSlots(settings.columns, settings.rows, settings.marginPt, settings.gapPt, sheetWidth, sheetHeight)
        if (slots.isEmpty() || pages.isEmpty()) {
            return ToolExportPlan(emptyList(), sheetWidth, sheetHeight)
        }
        val sheets = mutableListOf<OutputSheet>()
        var index = 0
        while (index < pages.size) {
            val sheetSlots = mutableListOf<PageSlot>()
            for (slot in slots) {
                if (index >= pages.size) break
                sheetSlots.add(PageSlot(sourcePageIndex = index, rect = slot))
                index++
            }
            sheets.add(OutputSheet(sheetSlots))
        }
        return ToolExportPlan(
            sheets = sheets,
            sheetWidthPt = sheetWidth,
            sheetHeightPt = sheetHeight,
            borders = settings.borders
        )
    }

    /**
     * Saddle-stitch imposition: sheet sides are paired outside-in so the printed stack folds into a
     * booklet. Padding pages are null and the exporter leaves those cells empty.
     */
    fun bookletPlan(pages: List<PageItem>): ToolExportPlan {
        val (width, height) = sheetSize(pages)
        val sides = bookletSheets(pages.size)
        val sheets = sides.map { pair ->
            val slots = mutableListOf<PageSlot>()
            pair.forEachIndexed { column, source ->
                if (source != null && source in pages.indices) {
                    slots.add(
                        PageSlot(
                            sourcePageIndex = source,
                            rect = SlotRect(
                                left = column * 0.5f,
                                top = 0f,
                                width = 0.5f,
                                height = FULL_SLOT
                            )
                        )
                    )
                }
            }
            OutputSheet(slots)
        }
        return ToolExportPlan(sheets, width, height, borders = false)
    }

    fun nUpSlots(
        columns: Int,
        rows: Int,
        marginPt: Float,
        gapPt: Float,
        sheetWidthPt: Int,
        sheetHeightPt: Int
    ): List<SlotRect> {
        val cols = columns.coerceIn(1, 6)
        val rws = rows.coerceIn(1, 6)
        val sheetWidth = sheetWidthPt.toFloat().coerceAtLeast(1f)
        val sheetHeight = sheetHeightPt.toFloat().coerceAtLeast(1f)
        val margin = marginPt.coerceIn(0f, min(sheetWidth, sheetHeight) / 4f)
        val gap = gapPt.coerceIn(0f, min(sheetWidth, sheetHeight) / 6f)
        val usableWidth = (sheetWidth - 2f * margin - (cols - 1) * gap).coerceAtLeast(1f)
        val usableHeight = (sheetHeight - 2f * margin - (rws - 1) * gap).coerceAtLeast(1f)
        val cellWidth = usableWidth / cols
        val cellHeight = usableHeight / rws
        val slots = mutableListOf<SlotRect>()
        for (row in 0 until rws) {
            for (col in 0 until cols) {
                slots.add(
                    SlotRect(
                        left = (margin + col * (cellWidth + gap)) / sheetWidth,
                        top = (margin + row * (cellHeight + gap)) / sheetHeight,
                        width = cellWidth / sheetWidth,
                        height = cellHeight / sheetHeight
                    )
                )
            }
        }
        return slots
    }

    /** Sides of the booklet, each holding a left and a right page index (0-based) or null padding. */
    fun bookletSheets(pageCount: Int): List<List<Int?>> {
        if (pageCount <= 0) return emptyList()
        val padded = ((pageCount + 3) / 4) * 4
        val sheets = mutableListOf<List<Int?>>()
        for (sheet in 0 until padded / 4) {
            val frontLeft = padded - 2 * sheet
            val frontRight = 1 + 2 * sheet
            val backLeft = 2 + 2 * sheet
            val backRight = padded - 1 - 2 * sheet
            sheets.add(listOf(indexOrNull(frontLeft, pageCount), indexOrNull(frontRight, pageCount)))
            sheets.add(listOf(indexOrNull(backLeft, pageCount), indexOrNull(backRight, pageCount)))
        }
        return sheets
    }

    private fun indexOrNull(oneBased: Int, pageCount: Int): Int? =
        if (oneBased in 1..pageCount) oneBased - 1 else null

    fun chunks(total: Int, pagesPerFile: Int): List<IntRange> {
        if (total <= 0) return emptyList()
        val step = pagesPerFile.coerceAtLeast(1)
        return (0 until total step step).map { start -> start..min(start + step - 1, total - 1) }
    }

    /** `inkRatio` is the share of sampled pixels that are not white. */
    fun isBlank(inkRatio: Float, settings: BlankPageSettings): Boolean =
        inkRatio.coerceIn(0f, 1f) <= settings.inkThreshold.coerceIn(0f, 1f)

    /** Maps a normalised OCR word box onto a page measured in PDF points. */
    fun normalizedToPoints(
        left: Float,
        top: Float,
        right: Float,
        bottom: Float,
        pageWidthPt: Int,
        pageHeightPt: Int
    ): TextBoxPt = TextBoxPt(
        left = left.coerceIn(0f, 1f) * pageWidthPt.coerceAtLeast(1),
        top = top.coerceIn(0f, 1f) * pageHeightPt.coerceAtLeast(1),
        right = right.coerceIn(0f, 1f) * pageWidthPt.coerceAtLeast(1),
        bottom = bottom.coerceIn(0f, 1f) * pageHeightPt.coerceAtLeast(1)
    )

    /** How many pages the chosen N-up grid puts on one sheet. */
    fun pagesPerSheet(settings: NUpSettings): Int =
        max(1, settings.columns.coerceIn(1, 6) * settings.rows.coerceIn(1, 6))
}
