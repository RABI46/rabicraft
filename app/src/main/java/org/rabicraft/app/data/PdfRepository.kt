package org.rabicraft.app.data

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Matrix
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import android.graphics.Typeface
import android.graphics.pdf.PdfDocument
import android.graphics.pdf.PdfRenderer
import android.net.Uri
import android.os.ParcelFileDescriptor
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.rabicraft.app.data.export.OverlayOp
import org.rabicraft.app.data.export.PageExportPlan
import org.rabicraft.app.model.AnnotationItem
import org.rabicraft.app.model.BookmarkItem
import org.rabicraft.app.model.DocumentPermissions
import org.rabicraft.app.model.FormFieldItem
import org.rabicraft.app.model.PageEdit
import org.rabicraft.app.model.PageItem
import org.rabicraft.app.model.PdfDocumentInfo
import java.io.File
import java.io.FileOutputStream
import java.util.UUID

class PdfRepository(private val context: Context) {

    private var currentFileDescriptor: ParcelFileDescriptor? = null
    private var currentRenderer: PdfRenderer? = null
    private var activePdfFile: File? = null

    fun getActivePdfFile(): File? = activePdfFile

    val sampleBookmarks = listOf(
        BookmarkItem(
            id = "bm-1",
            title = "Cover & Specimen Overview",
            pageIndex = 0,
            level = 0
        ),
        BookmarkItem(
            id = "bm-2",
            title = "Scripts of the World",
            pageIndex = 1,
            level = 0,
            children = listOf(
                BookmarkItem("bm-2-1", "Latin & Kerning", 1, 1),
                BookmarkItem("bm-2-2", "Arabic & Hebrew BiDi", 1, 1),
                BookmarkItem("bm-2-3", "East Asian Glyphs", 1, 1)
            )
        ),
        BookmarkItem(
            id = "bm-3",
            title = "Architecture & Security",
            pageIndex = 2,
            level = 0
        ),
        BookmarkItem(
            id = "bm-4",
            title = "Interactive Forms & Sign",
            pageIndex = 3,
            level = 0
        ),
        BookmarkItem(
            id = "bm-5",
            title = "Vector Standards & Colors",
            pageIndex = 4,
            level = 0
        )
    )

    suspend fun loadShowcase(): Pair<PdfDocumentInfo, List<PageItem>> = withContext(Dispatchers.IO) {
        val file = ShowcaseGenerator.generateShowcasePdf(context)
        openFile(file, isSample = true)
    }

    suspend fun loadFromUri(uri: Uri): Pair<PdfDocumentInfo, List<PageItem>> = withContext(Dispatchers.IO) {
        // Copy to app cache to ensure reliable random-access ParcelFileDescriptor
        val tempFile = File(context.cacheDir, "imported_${System.currentTimeMillis()}.pdf")
        context.contentResolver.openInputStream(uri)?.use { input ->
            FileOutputStream(tempFile).use { output ->
                input.copyTo(output)
            }
        } ?: throw IllegalStateException("Could not read document from URI")

        val fileName = getFileNameFromUri(uri) ?: "Document.pdf"
        openFile(tempFile, displayName = fileName, isSample = false, uriString = uri.toString())
    }

    private fun openFile(
        file: File,
        displayName: String = file.name,
        isSample: Boolean = false,
        uriString: String? = null
    ): Pair<PdfDocumentInfo, List<PageItem>> {
        closeCurrentRenderer()
        activePdfFile = file

        var pfd: ParcelFileDescriptor? = null
        var renderer: PdfRenderer? = null
        try {
            pfd = ParcelFileDescriptor.open(file, ParcelFileDescriptor.MODE_READ_ONLY)
            renderer = PdfRenderer(pfd)
            currentFileDescriptor = pfd
            currentRenderer = renderer
        } catch (e: Throwable) {
            try { renderer?.close() } catch (_: Throwable) {}
            try { pfd?.close() } catch (_: Throwable) {}
            currentRenderer = null
            currentFileDescriptor = null
            throw IllegalStateException("Failed to initialize PDF renderer: ${e.message}", e)
        }

        val pageCount = renderer.pageCount
        val pages = mutableListOf<PageItem>()

        val defaultTexts = listOf(
            "FranPdf Document Specimen & Feature Showcase ISO 32000-2",
            "Scripts of the World Typography Latin Arabic Hebrew Greek Cyrillic Japanese Korean Devanagari",
            "Native Architecture & Security crates/engine/src/document.rs open_document parse_pdf_header",
            "Interactive Forms & Fill & Sign Alexandre Dumas Full Name Email Address Digital Signature",
            "Vector Graphics & Color Precision Rabi Wine Crimson Charcoal Gold Leaf Transparency"
        )

        for (i in 0 until pageCount) {
            var w = 595
            var h = 842
            var page: PdfRenderer.Page? = null
            try {
                page = renderer.openPage(i)
                w = page.width
                h = page.height
            } catch (e: Throwable) {
                e.printStackTrace()
            } finally {
                try {
                    page?.close()
                } catch (_: Throwable) {}
            }

            val label = if (i == 0) "Cover" else "$i"
            val textContent = if (i < defaultTexts.size) defaultTexts[i] else "Page $i content"

            pages.add(
                PageItem(
                    pageIndex = i,
                    originalPageIndex = i,
                    label = label,
                    rotationDegrees = 0,
                    width = w,
                    height = h,
                    textContent = textContent
                )
            )
        }

        val docInfo = PdfDocumentInfo(
            id = UUID.randomUUID().toString(),
            name = displayName,
            uriString = uriString,
            pageCount = pageCount,
            fileSizeBytes = file.length(),
            isSample = isSample,
            title = displayName.removeSuffix(".pdf"),
            pdfVersion = "PDF 1.7",
            author = if (isSample) "FranPdf Team" else "Document Author",
            creator = "FranPdf PDF Workbench",
            permissions = DocumentPermissions()
        )

        return Pair(docInfo, pages)
    }

    suspend fun renderPageBitmap(
        pageIndex: Int,
        rotationDegrees: Int = 0,
        scale: Float = 1.0f
    ): Bitmap? = withContext(Dispatchers.IO) {
        val renderer = currentRenderer ?: return@withContext null
        if (pageIndex < 0 || pageIndex >= renderer.pageCount) return@withContext null

        synchronized(renderer) {
            var page: PdfRenderer.Page? = null
            try {
                page = renderer.openPage(pageIndex)
                val targetW = (page.width * scale).toInt().coerceIn(80, 2048)
                val targetH = (page.height * scale).toInt().coerceIn(80, 2048)

                val bitmap = Bitmap.createBitmap(targetW, targetH, Bitmap.Config.ARGB_8888)
                bitmap.eraseColor(Color.WHITE)

                page.render(bitmap, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)

                if (rotationDegrees % 360 != 0) {
                    val matrix = Matrix().apply { postRotate(rotationDegrees.toFloat()) }
                    val rotated = Bitmap.createBitmap(bitmap, 0, 0, bitmap.width, bitmap.height, matrix, true)
                    bitmap.recycle()
                    rotated
                } else {
                    bitmap
                }
            } catch (e: Throwable) {
                e.printStackTrace()
                null
            } finally {
                try {
                    page?.close()
                } catch (_: Throwable) {}
            }
        }
    }

    suspend fun exportCurrentDocument(
        pages: List<PageItem>,
        annotations: Map<Int, List<AnnotationItem>>,
        edits: Map<Int, List<PageEdit>>,
        formFields: List<FormFieldItem>,
        targetFile: File
    ): Boolean = withContext(Dispatchers.IO) {
        val renderer = currentRenderer ?: return@withContext false
        val scale = 2.0f

        try {
            val newPdf = PdfDocument()

            for ((newIdx, pageItem) in pages.withIndex()) {
                val origIdx = pageItem.originalPageIndex
                if (origIdx >= renderer.pageCount) continue

                val bitmap = renderPageBitmap(origIdx, pageItem.rotationDegrees, scale = scale) ?: continue
                val pageWidth = bitmap.width.toFloat()
                val pageHeight = bitmap.height.toFloat()

                // Flatten annotations, form values and edit overlays into the page bitmap.
                val overlay = Canvas(bitmap)
                for (op in PageExportPlan.overlayOps(
                    pageWidth = pageWidth,
                    pageHeight = pageHeight,
                    scale = scale,
                    annotations = annotations[pageItem.pageIndex].orEmpty(),
                    formFields = formFields.filter { it.pageIndex == pageItem.pageIndex },
                    edits = edits[pageItem.pageIndex].orEmpty()
                )) {
                    drawOverlayOp(overlay, op)
                }

                // PageInfo is measured in PDF points; the bitmap is rendered at 2x.
                val (pageWidthPt, pageHeightPt) =
                    PageExportPlan.pageSizeInPoints(bitmap.width, bitmap.height, scale)
                val pageInfo = PdfDocument.PageInfo.Builder(pageWidthPt, pageHeightPt, newIdx + 1).create()
                val page = newPdf.startPage(pageInfo)
                page.canvas.drawBitmap(
                    bitmap,
                    null,
                    RectF(0f, 0f, pageWidthPt.toFloat(), pageHeightPt.toFloat()),
                    null
                )
                newPdf.finishPage(page)
                bitmap.recycle()
            }

            FileOutputStream(targetFile).use { out ->
                newPdf.writeTo(out)
            }
            newPdf.close()
            true
        } catch (e: Exception) {
            e.printStackTrace()
            false
        }
    }

    private fun drawOverlayOp(canvas: Canvas, op: OverlayOp) {
        when (op) {
            is OverlayOp.Ink -> {
                val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                    color = op.colorArgb.toInt()
                    style = Paint.Style.STROKE
                    strokeCap = Paint.Cap.ROUND
                    strokeJoin = Paint.Join.ROUND
                    strokeWidth = op.strokeWidth
                }
                if (op.highlighter) paint.alpha = 102
                if (op.points.isNotEmpty()) {
                    val path = Path()
                    path.moveTo(op.points[0].x, op.points[0].y)
                    for (i in 1 until op.points.size) {
                        path.lineTo(op.points[i].x, op.points[i].y)
                    }
                    canvas.drawPath(path, paint)
                }
            }
            is OverlayOp.StrokeRect -> canvas.drawRect(
                op.left,
                op.top,
                op.right,
                op.bottom,
                Paint(Paint.ANTI_ALIAS_FLAG).apply {
                    color = op.colorArgb.toInt()
                    style = Paint.Style.STROKE
                    strokeWidth = op.strokeWidth
                }
            )
            is OverlayOp.FillRect -> canvas.drawRect(
                op.left,
                op.top,
                op.right,
                op.bottom,
                Paint().apply { color = op.colorArgb.toInt() }
            )
            is OverlayOp.Text -> drawTextBlock(
                canvas,
                op.content,
                op.x,
                op.top,
                op.size,
                op.colorArgb.toInt(),
                op.bold
            )
            is OverlayOp.Image -> {
                val bitmap = op.bitmap
                if (bitmap != null) {
                    canvas.drawBitmap(
                        bitmap,
                        null,
                        RectF(op.left, op.top, op.right, op.bottom),
                        Paint(Paint.FILTER_BITMAP_FLAG)
                    )
                }
            }
        }
    }

    private fun drawTextBlock(
        canvas: Canvas,
        value: String,
        x: Float,
        top: Float,
        size: Float,
        color: Int,
        bold: Boolean = false
    ) {
        val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            textSize = size
            this.color = color
            typeface = Typeface.create(
                Typeface.SANS_SERIF,
                if (bold) Typeface.BOLD else Typeface.NORMAL
            )
        }
        var lineY = top + size
        for (line in value.split('\n')) {
            canvas.drawText(line, x, lineY, paint)
            lineY += size * 1.25f
        }
    }

    suspend fun combineDocuments(
        firstPages: List<PageItem>,
        secondFile: File,
        targetFile: File
    ): Boolean = withContext(Dispatchers.IO) {
        val renderer1 = currentRenderer ?: return@withContext false
        try {
            val pfd2 = ParcelFileDescriptor.open(secondFile, ParcelFileDescriptor.MODE_READ_ONLY)
            val renderer2 = PdfRenderer(pfd2)

            val combinedPdf = PdfDocument()
            var pageCounter = 1

            // Append pages from doc 1
            for (p in firstPages) {
                val bmp = renderPageBitmap(p.originalPageIndex, p.rotationDegrees, scale = 1.5f) ?: continue
                val pageInfo = PdfDocument.PageInfo.Builder(bmp.width, bmp.height, pageCounter++).create()
                val page = combinedPdf.startPage(pageInfo)
                page.canvas.drawBitmap(bmp, 0f, 0f, null)
                combinedPdf.finishPage(page)
                bmp.recycle()
            }

            // Append pages from doc 2
            for (i in 0 until renderer2.pageCount) {
                var page: PdfRenderer.Page? = null
                var bmp: Bitmap? = null
                try {
                    page = renderer2.openPage(i)
                    val w = (page.width * 1.5f).toInt().coerceIn(80, 2048)
                    val h = (page.height * 1.5f).toInt().coerceIn(80, 2048)
                    bmp = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
                    page.render(bmp, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
                } catch (e: Throwable) {
                    e.printStackTrace()
                } finally {
                    try { page?.close() } catch (_: Throwable) {}
                }

                if (bmp != null) {
                    val pageInfo = PdfDocument.PageInfo.Builder(bmp.width, bmp.height, pageCounter++).create()
                    val p = combinedPdf.startPage(pageInfo)
                    p.canvas.drawBitmap(bmp, 0f, 0f, null)
                    combinedPdf.finishPage(p)
                    bmp.recycle()
                }
            }

            try { renderer2.close() } catch (_: Throwable) {}
            try { pfd2.close() } catch (_: Throwable) {}

            FileOutputStream(targetFile).use { out ->
                combinedPdf.writeTo(out)
            }
            combinedPdf.close()
            true
        } catch (e: Exception) {
            e.printStackTrace()
            false
        }
    }

    private fun getFileNameFromUri(uri: Uri): String? {
        var name: String? = null
        val cursor = context.contentResolver.query(uri, null, null, null, null)
        cursor?.use {
            if (it.moveToFirst()) {
                val nameIdx = it.getColumnIndex(android.provider.OpenableColumns.DISPLAY_NAME)
                if (nameIdx != -1) {
                    name = it.getString(nameIdx)
                }
            }
        }
        return name ?: uri.lastPathSegment
    }

    fun closeCurrentRenderer() {
        try {
            currentRenderer?.close()
        } catch (_: Exception) {}
        currentRenderer = null

        try {
            currentFileDescriptor?.close()
        } catch (_: Exception) {}
        currentFileDescriptor = null
    }
}
