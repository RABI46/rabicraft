package org.rabicraft.app.data.tools

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.rabicraft.app.model.PageItem
import org.rabicraft.app.model.StampPreset
import org.rabicraft.app.model.StampSettings

class ToolPlannerStampTest {

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
    fun `stamp covers every page with the preset wording and colour`() {
        val plan = ToolPlanner.stampPlan(pages(3), StampSettings())

        assertEquals(3, plan.sheets.size)
        val overlay = plan.sheets.last().slots.single().overlays.single()
        assertEquals("CONFIDENTIEL", overlay.text)
        assertEquals(StampPreset.CONFIDENTIEL.colorArgb, overlay.colorArgb)
        assertEquals(0.25f, overlay.opacity, 0.001f)
        assertEquals(-45f, overlay.rotationDegrees, 0.001f)
    }

    @Test
    fun `first page only stamps the cover`() {
        val plan = ToolPlanner.stampPlan(
            pages(4),
            StampSettings(preset = StampPreset.APPROVED, firstPageOnly = true)
        )

        assertEquals(4, plan.sheets.size)
        assertEquals(
            "APPROUVÉ",
            plan.sheets[0].slots.single().overlays.single().text
        )
        assertTrue(plan.sheets.drop(1).all { it.slots.single().overlays.isEmpty() })
    }

    @Test
    fun `blank stamp text writes nothing`() {
        assertTrue(ToolPlanner.stampPlan(pages(2), StampSettings(text = "   ")).sheets.isEmpty())
        assertTrue(ToolPlanner.stampPlan(emptyList(), StampSettings()).sheets.isEmpty())
    }

    /** Slot sizes are fractions of the sheet, so the aspect ratio is only visible in points. */
    private fun aspectInPoints(slot: SlotRect): Float =
        (slot.width * 595f) / (slot.height * 842f)

    @Test
    fun `image fit keeps the aspect ratio and centres the picture`() {
        val wide = ToolPlanner.fitSlot(600, 300, 595, 842, 18f)
        assertEquals(2f, aspectInPoints(wide), 0.01f)
        assertEquals(1f, wide.left * 2f + wide.width, 0.01f)
        assertEquals(1f, wide.top * 2f + wide.height, 0.01f)

        val tall = ToolPlanner.fitSlot(300, 600, 595, 842, 18f)
        assertEquals(0.5f, aspectInPoints(tall), 0.01f)
        assertEquals(1f, tall.left * 2f + tall.width, 0.01f)
        assertEquals(1f, tall.top * 2f + tall.height, 0.01f)
    }

    @Test
    fun `image margins hold on every edge`() {
        val slot = ToolPlanner.fitSlot(1200, 800, 595, 842, 18f)

        assertEquals(18f / 595f, slot.left, 0.001f)
        assertEquals(18f / 595f, 1f - slot.left - slot.width, 0.001f)
    }
}
