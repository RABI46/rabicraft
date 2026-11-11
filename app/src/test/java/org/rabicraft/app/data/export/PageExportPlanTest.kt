package org.rabicraft.app.data.export

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.rabicraft.app.model.AnnotationItem
import org.rabicraft.app.model.AnnotationType
import org.rabicraft.app.model.EditKind
import org.rabicraft.app.model.FieldType
import org.rabicraft.app.model.FormFieldItem
import org.rabicraft.app.model.NormalizedPoint
import org.rabicraft.app.model.PageEdit

/**
 * The exporter draws these operations onto each page bitmap before writing it, so a complete plan
 * is what "flattened into the exported PDF" means here.
 */
class PageExportPlanTest {

    private val pageWidth = 1000f
    private val pageHeight = 2000f
    private val scale = 2f

    private fun plan(
        annotations: List<AnnotationItem> = emptyList(),
        formFields: List<FormFieldItem> = emptyList(),
        edits: List<PageEdit> = emptyList()
    ): List<OverlayOp> =
        PageExportPlan.overlayOps(pageWidth, pageHeight, scale, annotations, formFields, edits)

    @Test
    fun `page size is expressed in PDF points`() {
        val (width, height) = PageExportPlan.pageSizeInPoints(1190, 1684, 2f)

        assertEquals(595, width)
        assertEquals(842, height)
    }

    @Test
    fun `page size returns to points for other render scales`() {
        val (width, height) = PageExportPlan.pageSizeInPoints(893, 1263, 1.5f)

        assertEquals(595, width)
        assertEquals(842, height)
    }

    @Test
    fun `ink annotation is flattened with page-relative coordinates`() {
        val ink = AnnotationItem(
            id = "ink-1",
            pageIndex = 0,
            type = AnnotationType.INK_PEN,
            colorArgb = 0xFF112233L,
            strokeWidth = 4f,
            points = listOf(NormalizedPoint(0.1f, 0.2f), NormalizedPoint(0.5f, 0.6f))
        )

        val op = plan(annotations = listOf(ink)).single() as OverlayOp.Ink

        assertEquals(2, op.points.size)
        assertEquals(100f, op.points[0].x, 0.01f)
        assertEquals(400f, op.points[0].y, 0.01f)
        assertEquals(500f, op.points[1].x, 0.01f)
        assertEquals(1200f, op.points[1].y, 0.01f)
        assertEquals(0xFF112233L, op.colorArgb)
        assertEquals(8f, op.strokeWidth, 0.01f)
        assertEquals(false, op.highlighter)
    }

    @Test
    fun `highlighter annotation stays a highlighter`() {
        val marker = AnnotationItem(
            id = "hl-1",
            pageIndex = 0,
            type = AnnotationType.HIGHLIGHTER,
            colorArgb = 0xFFD97706L,
            strokeWidth = 3f,
            points = listOf(NormalizedPoint(0.2f, 0.2f), NormalizedPoint(0.8f, 0.2f))
        )

        val op = plan(annotations = listOf(marker)).single() as OverlayOp.Ink

        assertTrue(op.highlighter)
        assertEquals(6f, op.strokeWidth, 0.01f)
    }

    @Test
    fun `rectangle annotation is flattened with its bounds`() {
        val rectangle = AnnotationItem(
            id = "rect-1",
            pageIndex = 0,
            type = AnnotationType.RECTANGLE,
            colorArgb = 0xFF8A011EL,
            strokeWidth = 2f,
            boundsX = 0.1f,
            boundsY = 0.2f,
            boundsWidth = 0.3f,
            boundsHeight = 0.4f
        )

        val op = plan(annotations = listOf(rectangle)).single() as OverlayOp.StrokeRect

        assertEquals(100f, op.left, 0.01f)
        assertEquals(400f, op.top, 0.01f)
        assertEquals(400f, op.right, 0.01f)
        assertEquals(1200f, op.bottom, 0.01f)
        assertEquals(4f, op.strokeWidth, 0.01f)
    }

    @Test
    fun `text edit is flattened with its position and size`() {
        val edit = PageEdit(
            id = "text-1",
            pageIndex = 0,
            kind = EditKind.TEXT,
            xRatio = 0.1f,
            yRatio = 0.2f,
            widthRatio = 0.5f,
            heightRatio = 0.1f,
            text = "Bonjour",
            colorArgb = 0xFF8A011EL,
            fontSizeRatio = 0.05f
        )

        val op = plan(edits = listOf(edit)).single() as OverlayOp.Text

        assertEquals("Bonjour", op.content)
        assertEquals(100f, op.x, 0.01f)
        assertEquals(400f, op.top, 0.01f)
        assertEquals(100f, op.size, 0.01f)
        assertEquals(0xFF8A011EL, op.colorArgb)
    }

    @Test
    fun `whiteout edit is flattened as an opaque white rectangle`() {
        val edit = PageEdit(
            id = "whiteout-1",
            pageIndex = 0,
            kind = EditKind.WHITEOUT,
            xRatio = 0.2f,
            yRatio = 0.25f,
            widthRatio = 0.4f,
            heightRatio = 0.05f
        )

        val op = plan(edits = listOf(edit)).single() as OverlayOp.FillRect

        assertEquals(200f, op.left, 0.01f)
        assertEquals(500f, op.top, 0.01f)
        assertEquals(600f, op.right, 0.01f)
        assertEquals(600f, op.bottom, 0.01f)
        assertEquals(0xFFFFFFFFL, op.colorArgb)
    }

    @Test
    fun `shape edit is flattened as a stroked rectangle`() {
        val edit = PageEdit(
            id = "shape-1",
            pageIndex = 0,
            kind = EditKind.RECTANGLE,
            xRatio = 0.1f,
            yRatio = 0.1f,
            widthRatio = 0.2f,
            heightRatio = 0.1f,
            colorArgb = 0xFF1E3A8AL
        )

        val op = plan(edits = listOf(edit)).single() as OverlayOp.StrokeRect

        assertEquals(100f, op.left, 0.01f)
        assertEquals(200f, op.top, 0.01f)
        assertEquals(300f, op.right, 0.01f)
        assertEquals(400f, op.bottom, 0.01f)
        assertEquals(4f, op.strokeWidth, 0.01f)
    }

    @Test
    fun `image edit is flattened into its destination rectangle`() {
        val edit = PageEdit(
            id = "image-1",
            pageIndex = 0,
            kind = EditKind.IMAGE,
            xRatio = 0.2f,
            yRatio = 0.3f,
            widthRatio = 0.5f,
            heightRatio = 0.2f
        )

        val op = plan(edits = listOf(edit)).single() as OverlayOp.Image

        assertEquals(null, op.bitmap)
        assertEquals(200f, op.left, 0.01f)
        assertEquals(600f, op.top, 0.01f)
        assertEquals(700f, op.right, 0.01f)
        assertEquals(1000f, op.bottom, 0.01f)
    }

    @Test
    fun `filled form fields are flattened with their values`() {
        val textField = FormFieldItem(
            id = "field-1",
            pageIndex = 0,
            label = "Name",
            type = FieldType.TEXT,
            value = "Ada",
            xRatio = 0.1f,
            yRatio = 0.1f,
            widthRatio = 0.4f,
            heightRatio = 0.05f
        )
        val checkedBox = FormFieldItem(
            id = "field-2",
            pageIndex = 0,
            label = "Agree",
            type = FieldType.CHECKBOX,
            isChecked = true,
            xRatio = 0.1f,
            yRatio = 0.5f,
            widthRatio = 0.03f,
            heightRatio = 0.03f
        )

        val texts = plan(formFields = listOf(textField, checkedBox))
            .filterIsInstance<OverlayOp.Text>()

        assertEquals(2, texts.size)
        assertEquals("Ada", texts[0].content)
        assertEquals(24f, texts[0].size, 0.01f)
        assertEquals("✓", texts[1].content)
        assertTrue(texts[1].bold)
    }

    @Test
    fun `empty form fields add nothing`() {
        val emptyField = FormFieldItem(
            id = "field-3",
            pageIndex = 0,
            label = "Empty",
            type = FieldType.TEXT,
            value = "",
            xRatio = 0.1f,
            yRatio = 0.1f,
            widthRatio = 0.4f,
            heightRatio = 0.05f
        )

        assertTrue(plan(formFields = listOf(emptyField)).isEmpty())
    }

    @Test
    fun `every overlay kind is flattened on the same page`() {
        val ink = AnnotationItem(
            id = "ink-2",
            pageIndex = 0,
            type = AnnotationType.INK_PEN,
            colorArgb = 0xFF000000L,
            strokeWidth = 2f,
            points = listOf(NormalizedPoint(0.1f, 0.1f), NormalizedPoint(0.2f, 0.2f))
        )
        val field = FormFieldItem(
            id = "field-4",
            pageIndex = 0,
            label = "Date",
            type = FieldType.DATE,
            value = "11/11/2026",
            xRatio = 0.1f,
            yRatio = 0.3f,
            widthRatio = 0.3f,
            heightRatio = 0.04f
        )
        val textEdit = PageEdit(
            id = "text-2",
            pageIndex = 0,
            kind = EditKind.TEXT,
            xRatio = 0.1f,
            yRatio = 0.5f,
            widthRatio = 0.5f,
            heightRatio = 0.1f,
            text = "Ajout"
        )
        val whiteout = PageEdit(
            id = "whiteout-2",
            pageIndex = 0,
            kind = EditKind.WHITEOUT,
            xRatio = 0.1f,
            yRatio = 0.7f,
            widthRatio = 0.5f,
            heightRatio = 0.05f
        )

        val ops = plan(
            annotations = listOf(ink),
            formFields = listOf(field),
            edits = listOf(textEdit, whiteout)
        )

        assertEquals(4, ops.size)
        assertEquals(1, ops.filterIsInstance<OverlayOp.Ink>().size)
        assertEquals(2, ops.filterIsInstance<OverlayOp.Text>().size)
        assertEquals(1, ops.filterIsInstance<OverlayOp.FillRect>().size)
    }

    @Test
    fun `empty page produces no overlay operations`() {
        assertTrue(plan().isEmpty())
    }
}
