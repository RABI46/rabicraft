package org.rabicraft.app.data.export

import android.graphics.Bitmap
import org.rabicraft.app.model.AnnotationItem
import org.rabicraft.app.model.AnnotationType
import org.rabicraft.app.model.EditKind
import org.rabicraft.app.model.FieldType
import org.rabicraft.app.model.FormFieldItem
import org.rabicraft.app.model.PageEdit
import kotlin.math.roundToInt

/**
 * A drawing operation the exporter applies to a page bitmap before that page is written to the
 * exported PDF. Coordinates are in pixels of the flattened bitmap.
 */
sealed interface OverlayOp {
    data class Ink(
        val points: List<OpPoint>,
        val colorArgb: Long,
        val strokeWidth: Float,
        val highlighter: Boolean
    ) : OverlayOp

    data class StrokeRect(
        val left: Float,
        val top: Float,
        val right: Float,
        val bottom: Float,
        val colorArgb: Long,
        val strokeWidth: Float
    ) : OverlayOp

    data class FillRect(
        val left: Float,
        val top: Float,
        val right: Float,
        val bottom: Float,
        val colorArgb: Long
    ) : OverlayOp

    data class Text(
        val content: String,
        val x: Float,
        val top: Float,
        val size: Float,
        val colorArgb: Long,
        val bold: Boolean
    ) : OverlayOp

    data class Image(
        val bitmap: Bitmap?,
        val left: Float,
        val top: Float,
        val right: Float,
        val bottom: Float
    ) : OverlayOp
}

data class OpPoint(val x: Float, val y: Float)

/**
 * Turns annotations, filled form fields and Edit-PDF objects into drawing operations. Keeping this
 * pure means the export path can be unit-tested without an Android graphics device.
 */
object PageExportPlan {

    private const val BLACK = 0xFF000000L
    private const val WHITE = 0xFFFFFFFFL

    /** PDF page size in points for a page rendered at [scale] pixels per point. */
    fun pageSizeInPoints(bitmapWidth: Int, bitmapHeight: Int, scale: Float): Pair<Int, Int> {
        val safeScale = if (scale <= 0f) 1f else scale
        return Pair(
            (bitmapWidth / safeScale).roundToInt().coerceAtLeast(1),
            (bitmapHeight / safeScale).roundToInt().coerceAtLeast(1)
        )
    }

    fun overlayOps(
        pageWidth: Float,
        pageHeight: Float,
        scale: Float,
        annotations: List<AnnotationItem>,
        formFields: List<FormFieldItem>,
        edits: List<PageEdit>
    ): List<OverlayOp> {
        val ops = mutableListOf<OverlayOp>()

        for (annot in annotations) {
            when (annot.type) {
                AnnotationType.INK_PEN, AnnotationType.HIGHLIGHTER -> ops.add(
                    OverlayOp.Ink(
                        points = annot.points.map {
                            OpPoint(it.x * pageWidth, it.y * pageHeight)
                        },
                        colorArgb = annot.colorArgb,
                        strokeWidth = annot.strokeWidth * scale,
                        highlighter = annot.type == AnnotationType.HIGHLIGHTER
                    )
                )
                AnnotationType.RECTANGLE -> ops.add(
                    OverlayOp.StrokeRect(
                        left = annot.boundsX * pageWidth,
                        top = annot.boundsY * pageHeight,
                        right = (annot.boundsX + annot.boundsWidth) * pageWidth,
                        bottom = (annot.boundsY + annot.boundsHeight) * pageHeight,
                        colorArgb = annot.colorArgb,
                        strokeWidth = annot.strokeWidth * scale
                    )
                )
                else -> Unit
            }
        }

        for (field in formFields) {
            val left = field.xRatio * pageWidth
            val top = field.yRatio * pageHeight
            when (field.type) {
                FieldType.CHECKBOX -> if (field.isChecked) {
                    ops.add(OverlayOp.Text("✓", left, top, 14f * scale, BLACK, bold = true))
                }
                FieldType.SIGNATURE -> if (field.value.isNotBlank()) {
                    ops.add(OverlayOp.Text(field.value, left, top, 16f * scale, BLACK, bold = false))
                }
                else -> if (field.value.isNotBlank()) {
                    ops.add(OverlayOp.Text(field.value, left, top, 12f * scale, BLACK, bold = false))
                }
            }
        }

        for (edit in edits) {
            val left = edit.xRatio * pageWidth
            val top = edit.yRatio * pageHeight
            val right = left + edit.widthRatio * pageWidth
            val bottom = top + edit.heightRatio * pageHeight
            when (edit.kind) {
                EditKind.TEXT -> ops.add(
                    OverlayOp.Text(
                        content = edit.text,
                        x = left,
                        top = top,
                        size = edit.fontSizeRatio * pageHeight,
                        colorArgb = edit.colorArgb,
                        bold = false
                    )
                )
                EditKind.IMAGE -> ops.add(OverlayOp.Image(edit.image, left, top, right, bottom))
                EditKind.RECTANGLE -> ops.add(
                    OverlayOp.StrokeRect(
                        left = left,
                        top = top,
                        right = right,
                        bottom = bottom,
                        colorArgb = edit.colorArgb,
                        strokeWidth = 2f * scale
                    )
                )
                EditKind.WHITEOUT -> ops.add(
                    OverlayOp.FillRect(left, top, right, bottom, WHITE)
                )
            }
        }

        return ops
    }
}
