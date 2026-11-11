package org.rabicraft.app.data.tools

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.ColorMatrix
import android.graphics.ColorMatrixColorFilter
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Typeface
import org.rabicraft.app.data.ocr.OcrWord
import org.rabicraft.app.model.ColourEffect
import org.rabicraft.app.model.CropSettings
import org.rabicraft.app.model.ImageFormat
import org.rabicraft.app.model.TextAnchor
import java.io.ByteArrayOutputStream
import kotlin.math.min

/**
 * Drawing half of the document tools. All coordinates are PDF points, the unit of a
 * [android.graphics.pdf.PdfDocument] page canvas, so a 10pt label stays 10pt on every sheet size.
 */
object ToolRenderer {

    fun applyEffect(source: Bitmap, effect: ColourEffect): Bitmap {
        val matrix = when (effect) {
            ColourEffect.NONE -> return source
            ColourEffect.GRAYSCALE -> ColorMatrix().apply { setSaturation(0f) }
            ColourEffect.INVERT -> ColorMatrix(
                floatArrayOf(
                    -1f, 0f, 0f, 0f, 255f,
                    0f, -1f, 0f, 0f, 255f,
                    0f, 0f, -1f, 0f, 255f,
                    0f, 0f, 0f, 1f, 0f
                )
            )
            ColourEffect.HIGH_CONTRAST -> ColorMatrix().apply {
                setSaturation(0f)
                postConcat(
                    ColorMatrix(
                        floatArrayOf(
                            1.8f, 0f, 0f, 0f, -96f,
                            0f, 1.8f, 0f, 0f, -96f,
                            0f, 0f, 1.8f, 0f, -96f,
                            0f, 0f, 0f, 1f, 0f
                        )
                    )
                )
            }
        }
        val output = Bitmap.createBitmap(source.width, source.height, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(output)
        canvas.drawBitmap(
            source,
            0f,
            0f,
            Paint(Paint.FILTER_BITMAP_FLAG).apply { colorFilter = ColorMatrixColorFilter(matrix) }
        )
        return output
    }

    fun crop(source: Bitmap, crop: CropSettings): Bitmap {
        if (crop.isEmpty) return source
        val left = (crop.leftPct.coerceIn(0f, 0.9f) * source.width).toInt()
        val top = (crop.topPct.coerceIn(0f, 0.9f) * source.height).toInt()
        val width = (source.width - left - crop.rightPct.coerceIn(0f, 0.9f) * source.width).toInt()
        val height = (source.height - top - crop.bottomPct.coerceIn(0f, 0.9f) * source.height).toInt()
        if (width <= 0 || height <= 0 || left < 0 || top < 0) return source
        if (left + width > source.width || top + height > source.height) return source
        return Bitmap.createBitmap(source, left, top, width, height)
    }

    /** Re-encodes through JPEG so the file really shrinks, as the compress tool promises. */
    fun jpegRoundTrip(source: Bitmap, quality: Int): Bitmap {
        val stream = ByteArrayOutputStream()
        source.compress(Bitmap.CompressFormat.JPEG, quality.coerceIn(20, 100), stream)
        val bytes = stream.toByteArray()
        return BitmapFactory.decodeByteArray(bytes, 0, bytes.size) ?: source
    }

    /** Draws a page bitmap into [rect], keeping its aspect ratio inside the slot. */
    fun drawPage(canvas: Canvas, bitmap: Bitmap, rect: RectF) {
        if (bitmap.width <= 0 || bitmap.height <= 0 || rect.width() <= 0f || rect.height() <= 0f) return
        val scale = min(rect.width() / bitmap.width, rect.height() / bitmap.height)
        val width = bitmap.width * scale
        val height = bitmap.height * scale
        val left = rect.left + (rect.width() - width) / 2f
        val top = rect.top + (rect.height() - height) / 2f
        canvas.drawBitmap(
            bitmap,
            null,
            RectF(left, top, left + width, top + height),
            Paint(Paint.FILTER_BITMAP_FLAG)
        )
    }

    fun drawOverlay(canvas: Canvas, overlay: TextOverlay, rect: RectF) {
        if (overlay.text.isBlank() || rect.width() <= 0f || rect.height() <= 0f) return
        val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            textSize = overlay.fontSizePt.coerceAtLeast(1f)
            color = overlay.colorArgb.toInt()
            typeface = Typeface.create(
                Typeface.SANS_SERIF,
                if (overlay.bold) Typeface.BOLD else Typeface.NORMAL
            )
            alpha = (overlay.opacity.coerceIn(0f, 1f) * 255f).toInt()
        }
        val inset = overlay.insetPt.coerceAtLeast(0f)
        val metrics = paint.fontMetrics

        val x: Float
        when (overlay.anchor) {
            TextAnchor.TOP_LEFT, TextAnchor.BOTTOM_LEFT -> {
                paint.textAlign = Paint.Align.LEFT
                x = rect.left + inset
            }
            TextAnchor.TOP_RIGHT, TextAnchor.BOTTOM_RIGHT -> {
                paint.textAlign = Paint.Align.RIGHT
                x = rect.right - inset
            }
            else -> {
                paint.textAlign = Paint.Align.CENTER
                x = rect.centerX()
            }
        }

        val y = when (overlay.anchor) {
            TextAnchor.TOP_LEFT, TextAnchor.TOP_CENTRE, TextAnchor.TOP_RIGHT ->
                rect.top + inset - metrics.ascent
            TextAnchor.CENTRE -> rect.centerY() - (metrics.ascent + metrics.descent) / 2f
            else -> rect.bottom - inset - metrics.descent
        }

        val needsRotation = overlay.rotationDegrees % 360f != 0f
        if (needsRotation) {
            canvas.save()
            canvas.rotate(overlay.rotationDegrees, x, y)
        }
        canvas.drawText(overlay.text, x, y, paint)
        if (needsRotation) {
            canvas.restore()
        }
    }

    /**
     * Writes the OCR words onto the page in near-invisible white. PDF viewers keep them in the text
     * layer, so a scanned page becomes searchable and selectable without changing how it looks on
     * white paper; on a dark scan the glyphs can be faintly visible, which is the trade-off of an
     * image-only pipeline that cannot attach an invisible text object of its own.
     */
    fun drawSearchableText(
        canvas: Canvas,
        words: List<OcrWord>,
        pageWidthPt: Int,
        pageHeightPt: Int
    ) {
        if (words.isEmpty()) return
        val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = 0x1AFFFFFF
            textAlign = Paint.Align.LEFT
        }
        for (word in words) {
            if (word.text.isBlank()) continue
            val box = ToolPlanner.normalizedToPoints(
                word.left,
                word.top,
                word.right,
                word.bottom,
                pageWidthPt,
                pageHeightPt
            )
            val height = box.bottom - box.top
            if (height <= 0f) continue
            paint.textSize = (height * 0.92f).coerceIn(1f, 512f)
            canvas.drawText(word.text, box.left, box.bottom - height * 0.15f, paint)
        }
    }

    /** Hairline frame around an imposed page, so the cut lines are visible on the sheet. */
    fun drawFrame(canvas: Canvas, rect: RectF) {
        canvas.drawRect(
            rect,
            Paint(Paint.ANTI_ALIAS_FLAG).apply {
                style = Paint.Style.STROKE
                strokeWidth = 0.75f
                color = 0xFFBFC7D5.toInt()
            }
        )
    }

    fun slotRect(rect: SlotRect, sheetWidthPt: Int, sheetHeightPt: Int): RectF = RectF(
        rect.left * sheetWidthPt,
        rect.top * sheetHeightPt,
        (rect.left + rect.width) * sheetWidthPt,
        (rect.top + rect.height) * sheetHeightPt
    )

    fun encodeImage(bitmap: Bitmap, format: ImageFormat, quality: Int): ByteArray {
        val stream = ByteArrayOutputStream()
        if (format == ImageFormat.JPG) {
            bitmap.compress(Bitmap.CompressFormat.JPEG, quality.coerceIn(20, 100), stream)
        } else {
            bitmap.compress(Bitmap.CompressFormat.PNG, 100, stream)
        }
        return stream.toByteArray()
    }

    fun extensionFor(format: ImageFormat): String = if (format == ImageFormat.JPG) "jpg" else "png"
}
