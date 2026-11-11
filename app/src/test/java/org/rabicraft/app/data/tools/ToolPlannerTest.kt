package org.rabicraft.app.data.tools

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
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

class ToolPlannerTest {

    private fun pages(count: Int): List<PageItem> = (0 until count).map { index ->
        PageItem(
            pageIndex = index,
            originalPageIndex = index,
            label = "#${index + 1}",
            width = 595,
            height = 842
        )
    }

    @Test
    fun `template tokens are replaced per page`() {
        assertEquals(
            "Page 3 of 10 — Rapport",
            ToolPlanner.resolveTemplate("Page {page} of {total} — {title}", 3, 10, "Rapport")
        )
        assertEquals("Page 2", ToolPlanner.resolveTemplate("Page {page}", 2, 4, "x"))
    }

    @Test
    fun `unknown tokens survive untouched`() {
        assertEquals("{unknown} 1", ToolPlanner.resolveTemplate("{unknown} {page}", 1, 1, ""))
    }

    @Test
    fun `page numbers honour the start number`() {
        val settings = PageNumberSettings(startNumber = 7)
        val plan = ToolPlanner.pageNumberPlan(pages(3), settings, "Doc")

        assertEquals(3, plan.sheets.size)
        val first = plan.sheets[0].slots.single().overlays.single()
        val last = plan.sheets[2].slots.single().overlays.single()
        assertEquals("Page 7 / 3", first.text)
        assertEquals("Page 9 / 3", last.text)
        assertEquals(TextAnchor.BOTTOM_CENTRE, first.anchor)
    }

    @Test
    fun `blank templates produce no overlay`() {
        val plan = ToolPlanner.pageNumberPlan(
            pages(2),
            PageNumberSettings(text = ToolText(template = "")),
            "Doc"
        )
        assertTrue(plan.sheets.all { it.slots.single().overlays.isEmpty() })
    }

    @Test
    fun `watermark is a centred rotated stamp`() {
        val plan = ToolPlanner.watermarkPlan(
            pages(1),
            WatermarkSettings(text = "BROUILLON", diagonal = true, opacity = 0.2f)
        )
        val overlay = plan.sheets.single().slots.single().overlays.single()
        assertEquals("BROUILLON", overlay.text)
        assertEquals(TextAnchor.CENTRE, overlay.anchor)
        assertEquals(-35f, overlay.rotationDegrees, 0.001f)
        assertEquals(0.2f, overlay.opacity, 0.001f)
    }

    @Test
    fun `header and footer both land on every page`() {
        val settings = HeaderFooterSettings(
            header = ToolText("Rapport — {title}", TextAnchor.TOP_RIGHT),
            footer = ToolText("Page {page} / {total}", TextAnchor.BOTTOM_CENTRE)
        )
        val plan = ToolPlanner.headerFooterPlan(pages(2), settings, "Budget")
        val overlays = plan.sheets[1].slots.single().overlays
        assertEquals(2, overlays.size)
        assertEquals("Rapport — Budget", overlays[0].text)
        assertEquals("Page 2 / 2", overlays[1].text)
    }

    @Test
    fun `empty header is skipped`() {
        val settings = HeaderFooterSettings(
            header = ToolText("   "),
            footer = ToolText("Page {page}")
        )
        val plan = ToolPlanner.headerFooterPlan(pages(1), settings, "Doc")
        assertEquals(1, plan.sheets.single().slots.single().overlays.size)
    }

    @Test
    fun `colour and crop plans keep one slot per page`() {
        val colour = ToolPlanner.colourPlan(pages(3), ColourEffect.INVERT)
        val crop = ToolPlanner.cropPlan(pages(3), CropSettings(topPct = 0.1f, bottomPct = 0.1f))

        assertEquals(3, colour.sheets.size)
        assertTrue(colour.sheets.all { it.slots.single().effect == ColourEffect.INVERT })
        assertTrue(crop.sheets.all { it.slots.single().crop.topPct == 0.1f })
    }

    @Test
    fun `two up packs two pages per sheet and keeps the sheet size`() {
        val plan = ToolPlanner.nUpPlan(pages(5), NUpSettings(columns = 2, rows = 1))

        assertEquals(3, plan.sheets.size)
        assertEquals(2, plan.sheets[0].slots.size)
        assertEquals(1, plan.sheets[2].slots.size)
        assertEquals(595, plan.sheetWidthPt)
        assertEquals(842, plan.sheetHeightPt)
        assertEquals(listOf(0, 1), plan.sheets[0].slots.map { it.sourcePageIndex })
    }

    @Test
    fun `four up places cells left to right then top to bottom`() {
        val plan = ToolPlanner.nUpPlan(pages(8), NUpSettings(columns = 2, rows = 2))
        assertEquals(2, plan.sheets.size)
        val slots = plan.sheets[0].slots
        assertEquals(4, slots.size)
        assertTrue(slots[0].rect.left < slots[1].rect.left)
        assertTrue(slots[0].rect.top < slots[2].rect.top)
        // 595pt wide sheet, 16pt margins and a 12pt gutter: (595 - 32 - 12) / 2 per cell.
        assertEquals((595f - 44f) / 2f / 595f, slots[0].rect.width, 0.0001f)
        assertEquals((842f - 44f) / 2f / 842f, slots[0].rect.height, 0.0001f)
    }

    @Test
    fun `n up slots leave the requested margin`() {
        val slots = ToolPlanner.nUpSlots(1, 1, marginPt = 10f, gapPt = 0f, 100, 200)
        val slot = slots.single()
        assertEquals(0.1f, slot.left, 0.0001f)
        assertEquals(0.05f, slot.top, 0.0001f)
        assertEquals(0.8f, slot.width, 0.0001f)
        assertEquals(0.9f, slot.height, 0.0001f)
    }

    @Test
    fun `booklet orders sides outside in and pads to a multiple of four`() {
        val sheets = ToolPlanner.bookletSheets(8)
        assertEquals(4, sheets.size)
        assertEquals(listOf<Int?>(7, 0), sheets[0])
        assertEquals(listOf<Int?>(1, 6), sheets[1])
        assertEquals(listOf<Int?>(5, 2), sheets[2])
        assertEquals(listOf<Int?>(3, 4), sheets[3])
        assertTrue(sheets.flatten().filterNotNull().distinct().size == 8)
    }

    @Test
    fun `booklet pads missing pages with nulls`() {
        val sheets = ToolPlanner.bookletSheets(5)
        assertEquals(4, sheets.size)
        assertEquals(5, sheets.flatten().filterNotNull().size)
        assertEquals(listOf<Int?>(null, 0), sheets[0])
        assertEquals(listOf<Int?>(3, 4), sheets.last())
        assertNull(sheets[0][0])
    }

    @Test
    fun `booklet plan skips padded cells`() {
        val plan = ToolPlanner.bookletPlan(pages(5))
        assertEquals(4, plan.sheets.size)
        // Page 1 is the right half of the cover; the padded left half is left empty.
        assertEquals(1, plan.sheets[0].slots.size)
        assertEquals(0.5f, plan.sheets[0].slots[0].rect.left, 0.0001f)
        assertEquals(0.5f, plan.sheets[0].slots[0].rect.width, 0.0001f)
        assertEquals(2, plan.sheets.last().slots.size)
    }

    @Test
    fun `chunks split every n pages and close the last range`() {
        assertEquals(listOf(0..0, 1..1, 2..2), ToolPlanner.chunks(3, 1))
        assertEquals(listOf(0..1, 2..3, 4..4), ToolPlanner.chunks(5, 2))
        assertTrue(ToolPlanner.chunks(0, 3).isEmpty())
    }

    @Test
    fun `blank detection follows the ink threshold`() {
        assertTrue(ToolPlanner.isBlank(0.0f, BlankPageSettings()))
        assertTrue(ToolPlanner.isBlank(0.003f, BlankPageSettings(inkThreshold = 0.004f)))
        assertFalse(ToolPlanner.isBlank(0.05f, BlankPageSettings()))
    }

    @Test
    fun `pages per sheet is the grid area`() {
        assertEquals(2, ToolPlanner.pagesPerSheet(NUpSettings(2, 1)))
        assertEquals(6, ToolPlanner.pagesPerSheet(NUpSettings(2, 3)))
        assertEquals(1, ToolPlanner.pagesPerSheet(NUpSettings(0, 0)))
    }

    @Test
    fun `ocr boxes map to page points`() {
        val box = ToolPlanner.normalizedToPoints(0.1f, 0.05f, 0.6f, 0.1f, 1000, 500)
        assertEquals(100f, box.left, 0.001f)
        assertEquals(25f, box.top, 0.001f)
        assertEquals(600f, box.right, 0.001f)
        assertEquals(50f, box.bottom, 0.001f)
    }

    @Test
    fun `page selection accepts lists and ranges`() {
        assertNull(ToolPlanner.parsePageSelection("", 10))
        assertEquals(setOf(0, 1, 2, 4), ToolPlanner.parsePageSelection("1-3,5", 10))
        assertEquals(setOf(0, 1, 2), ToolPlanner.parsePageSelection("3-1", 10))
        assertEquals(setOf(9), ToolPlanner.parsePageSelection("10", 10))
    }

    @Test
    fun `page selection ignores what the document does not have`() {
        assertTrue(ToolPlanner.parsePageSelection("99", 5).isNullOrEmpty())
        assertEquals(setOf(3, 4), ToolPlanner.parsePageSelection("4-99", 5))
    }

    @Test
    fun `a selection leaves the other pages untouched`() {
        val plan = ToolPlanner.sequentialPlan(
            pages = pages(4),
            effect = ColourEffect.INVERT,
            crop = CropSettings(leftPct = 0.2f),
            selection = setOf(1, 3)
        ) { _, _ -> emptyList() }

        assertEquals(ColourEffect.NONE, plan.sheets[0].slots.single().effect)
        assertEquals(ColourEffect.INVERT, plan.sheets[1].slots.single().effect)
        assertEquals(0f, plan.sheets[0].slots.single().crop.leftPct, 0.001f)
        assertEquals(0.2f, plan.sheets[1].slots.single().crop.leftPct, 0.001f)
    }

    @Test
    fun `page numbers honour a page selection`() {
        val plan = ToolPlanner.pageNumberPlan(
            pages(3),
            PageNumberSettings(selection = "2")
        )
        assertTrue(plan.sheets[0].slots.single().overlays.isEmpty())
        assertEquals("Page 2 / 3", plan.sheets[1].slots.single().overlays.single().text)
        assertTrue(plan.sheets[2].slots.single().overlays.isEmpty())
    }

    @Test
    fun `split ranges become the output chunks`() {
        val ranges = ToolPlanner.rangesFromSpec("1-3,5,7-8", 10)
        assertEquals(listOf(0..2, 4..4, 6..7), ranges)
    }

    @Test
    fun `blank split range falls back to a page count`() {
        assertNull(ToolPlanner.rangesFromSpec("", 10))
        assertTrue(ToolPlanner.rangesFromSpec("99", 5).isNullOrEmpty())
    }

    @Test
    fun `landscape n-up swaps the sheet and keeps the grid`() {
        val portrait = ToolPlanner.nUpPlan(pages(2), NUpSettings(columns = 2, rows = 1))
        val landscape = ToolPlanner.nUpPlan(
            pages(2),
            NUpSettings(columns = 2, rows = 1, landscape = true, borders = true)
        )
        assertEquals(595, portrait.sheetWidthPt)
        assertEquals(842, portrait.sheetHeightPt)
        assertEquals(842, landscape.sheetWidthPt)
        assertEquals(595, landscape.sheetHeightPt)
        assertTrue(landscape.borders)
        assertEquals(2, landscape.sheets.single().slots.size)
    }

    @Test
    fun `ocr boxes are clamped to the page`() {
        val box = ToolPlanner.normalizedToPoints(-0.2f, -1f, 1.4f, 2f, 595, 842)
        assertEquals(0f, box.left, 0.001f)
        assertEquals(0f, box.top, 0.001f)
        assertEquals(595f, box.right, 0.001f)
        assertEquals(842f, box.bottom, 0.001f)
    }
}
