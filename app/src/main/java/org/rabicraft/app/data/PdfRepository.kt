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
import org.rabicraft.app.data.ocr.OcrPageResult
import org.rabicraft.app.data.tools.ToolExportPlan
import org.rabicraft.app.data.tools.ToolRenderer
import org.rabicraft.app.model.AnnotationItem
import org.rabicraft.app.model.BookmarkItem
import org.rabicraft.app.model.DocumentPermissions
import org.rabicraft.app.model.FormFieldItem
import org.rabicraft.app.model.ImageExportSettings
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

    /**
     * Writes a PDF from a tool plan: cropped, tinted, imposed and stamped source pages land on the
     * output sheets exactly where [ToolPlanner][org.rabicraft.app.data.tools.ToolPlanner] put them.
     */
    suspend fun exportWithPlan(
        pages: List<PageItem>,
        plan: ToolExportPlan,
        targetFile: File,
        annotations: Map<Int, List<AnnotationItem>> = emptyMap(),
        edits: Map<Int, List<PageEdit>> = emptyMap(),
        formFields: List<FormFieldItem> = emptyList()
    ): Boolean = withContext(Dispatchers.IO) {
        if (plan.sheets.isEmpty()) return@withContext false
        val scale = (plan.renderDpi.coerceIn(36, 600) / 72f)
        try {
            val document = PdfDocument()
            for ((sheetIndex, sheet) in plan.sheets.withIndex()) {
                val pageInfo = PdfDocument.PageInfo.Builder(
                    plan.sheetWidthPt.coerceAtLeast(1),
                    plan.sheetHeightPt.coerceAtLeast(1),
                    sheetIndex + 1
                ).create()
                val outPage = document.startPage(pageInfo)
                for (slot in sheet.slots) {
                    val item = pages.getOrNull(slot.sourcePageIndex) ?: continue
                    var bitmap = renderPageBitmap(
                        item.originalPageIndex,
                        item.rotationDegrees,
                        scale = scale
                    ) ?: continue
                    // Rotated pages come back immutable, and a Canvas needs a mutable bitmap.
                    if (!bitmap.isMutable) {
                        val mutable = bitmap.copy(Bitmap.Config.ARGB_8888, true)
                        if (mutable != null) {
                            bitmap.recycle()
                            bitmap = mutable
                        }
                    }
                    // Keep the user's markup: tools must not silently drop annotations or edits.
                    val markup = PageExportPlan.overlayOps(
                        pageWidth = bitmap.width.toFloat(),
                        pageHeight = bitmap.height.toFloat(),
                        scale = scale,
                        annotations = annotations[item.pageIndex].orEmpty(),
                        formFields = formFields.filter { it.pageIndex == item.pageIndex },
                        edits = edits[item.pageIndex].orEmpty()
                    )
                    if (markup.isNotEmpty()) {
                        val markupCanvas = Canvas(bitmap)
                        for (op in markup) drawOverlayOp(markupCanvas, op)
                    }
                    val cropped = ToolRenderer.crop(bitmap, slot.crop)
                    if (cropped !== bitmap) {
                        bitmap.recycle()
                        bitmap = cropped
                    }
                    val tinted = ToolRenderer.applyEffect(bitmap, slot.effect)
                    if (tinted !== bitmap) {
                        bitmap.recycle()
                        bitmap = tinted
                    }
                    if (plan.jpegQuality != null) {
                        val recompressed = ToolRenderer.jpegRoundTrip(bitmap, plan.jpegQuality)
                        bitmap.recycle()
                        bitmap = recompressed
                    }
                    val rect = ToolRenderer.slotRect(slot.rect, plan.sheetWidthPt, plan.sheetHeightPt)
                    ToolRenderer.drawPage(outPage.canvas, bitmap, rect)
                    for (overlay in slot.overlays) {
                        ToolRenderer.drawOverlay(outPage.canvas, overlay, rect)
                    }
                    if (plan.borders) ToolRenderer.drawFrame(outPage.canvas, rect)
                    bitmap.recycle()
                }
                document.finishPage(outPage)
            }
            FileOutputStream(targetFile).use { out -> document.writeTo(out) }
            document.close()
            true
        } catch (e: Exception) {
            e.printStackTrace()
            false
        }
    }

    /**
     * Share of non-white pixels per page, sampled on a small render. Used by "remove blank pages":
     * a scanned separator or a section break is almost entirely white.
     */
    suspend fun measureInkRatios(pages: List<PageItem>, scale: Float = 0.3f): List<Float> =
        withContext(Dispatchers.IO) {
            pages.map { page ->
                val bitmap = renderPageBitmap(page.originalPageIndex, page.rotationDegrees, scale)
                    ?: return@map 1f
                try {
                    var ink = 0
                    var sampled = 0
                    val step = 3
                    var y = 0
                    while (y < bitmap.height) {
                        var x = 0
                        while (x < bitmap.width) {
                            val pixel = bitmap.getPixel(x, y)
                            val alpha = (pixel ushr 24) and 0xFF
                            if (alpha > 8) {
                                val luma = 0.299f * ((pixel shr 16) and 0xFF) +
                                    0.587f * ((pixel shr 8) and 0xFF) +
                                    0.114f * (pixel and 0xFF)
                                if (luma < 240f) ink++
                                sampled++
                            }
                            x += step
                        }
                        y += step
                    }
                    if (sampled == 0) 1f else ink.toFloat() / sampled.toFloat()
                } finally {
                    bitmap.recycle()
                }
            }
        }

    /** One image file per page, in [targetDirectory]. */
    suspend fun exportPageImages(
        pages: List<PageItem>,
        settings: ImageExportSettings,
        targetDirectory: File
    ): List<File> = withContext(Dispatchers.IO) {
        val written = mutableListOf<File>()
        targetDirectory.mkdirs()
        for ((index, page) in pages.withIndex()) {
            val bitmap = renderPageBitmap(page.originalPageIndex, page.rotationDegrees, 2f) ?: continue
            try {
                val file = File(
                    targetDirectory,
                    "FranPdf_page_${(index + 1).toString().padStart(3, '0')}.${ToolRenderer.extensionFor(settings.format)}"
                )
                val bytes = ToolRenderer.encodeImage(bitmap, settings.format, settings.jpegQuality)
                FileOutputStream(file).use { it.write(bytes) }
                written.add(file)
            } catch (e: Exception) {
                e.printStackTrace()
            } finally {
                bitmap.recycle()
            }
        }
        written
    }

    /** Writes one PDF per page range, carrying the annotations and edits of those pages. */
    suspend fun splitIntoFiles(
        pages: List<PageItem>,
        chunks: List<IntRange>,
        annotations: Map<Int, List<AnnotationItem>>,
        edits: Map<Int, List<PageEdit>>,
        formFields: List<FormFieldItem>,
        targetDirectory: File,
        namePrefix: String
    ): List<File> = withContext(Dispatchers.IO) {
        val written = mutableListOf<File>()
        targetDirectory.mkdirs()
        for ((index, range) in chunks.withIndex()) {
            val subset = pages.subList(range.first, range.last + 1)
            if (subset.isEmpty()) continue
            val file = File(targetDirectory, "${namePrefix}_part${index + 1}.pdf")
            val ok = exportCurrentDocument(subset, annotations, edits, formFields, file)
            if (ok) written.add(file)
        }
        written
    }

    /** Renders the open document, then each extra file, into a single PDF. */
    suspend fun mergeFiles(
        pages: List<PageItem>,
        extraFiles: List<File>,
        targetFile: File
    ): Boolean = withContext(Dispatchers.IO) {
        try {
            val merged = PdfDocument()
            var pageNumber = 1

            for (page in pages) {
                val bitmap = renderPageBitmap(page.originalPageIndex, page.rotationDegrees, 1.5f)
                    ?: continue
                val (widthPt, heightPt) = PageExportPlan.pageSizeInPoints(bitmap.width, bitmap.height, 1.5f)
                val info = PdfDocument.PageInfo.Builder(widthPt, heightPt, pageNumber++).create()
                val outPage = merged.startPage(info)
                outPage.canvas.drawBitmap(
                    bitmap,
                    null,
                    RectF(0f, 0f, widthPt.toFloat(), heightPt.toFloat()),
                    null
                )
                merged.finishPage(outPage)
                bitmap.recycle()
            }

            for (extra in extraFiles) {
                var descriptor: ParcelFileDescriptor? = null
                var renderer: PdfRenderer? = null
                try {
                    descriptor = ParcelFileDescriptor.open(extra, ParcelFileDescriptor.MODE_READ_ONLY)
                    renderer = PdfRenderer(descriptor)
                    for (index in 0 until renderer.pageCount) {
                        var page: PdfRenderer.Page? = null
                        var bitmap: Bitmap? = null
                        try {
                            page = renderer.openPage(index)
                            val width = (page.width * 1.5f).toInt().coerceIn(80, 2048)
                            val height = (page.height * 1.5f).toInt().coerceIn(80, 2048)
                            bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
                            bitmap.eraseColor(Color.WHITE)
                            page.render(bitmap, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
                        } catch (e: Throwable) {
                            e.printStackTrace()
                        } finally {
                            try { page?.close() } catch (_: Throwable) {}
                        }
                        val rendered = bitmap ?: continue
                        val (widthPt, heightPt) = PageExportPlan.pageSizeInPoints(
                            rendered.width,
                            rendered.height,
                            1.5f
                        )
                        val info = PdfDocument.PageInfo.Builder(widthPt, heightPt, pageNumber++).create()
                        val outPage = merged.startPage(info)
                        outPage.canvas.drawBitmap(
                            rendered,
                            null,
                            RectF(0f, 0f, widthPt.toFloat(), heightPt.toFloat()),
                            null
                        )
                        merged.finishPage(outPage)
                        rendered.recycle()
                    }
                } catch (e: Exception) {
                    e.printStackTrace()
                } finally {
                    try { renderer?.close() } catch (_: Throwable) {}
                    try { descriptor?.close() } catch (_: Throwable) {}
                }
            }

            if (pageNumber <= 1) {
                merged.close()
                return@withContext false
            }
            FileOutputStream(targetFile).use { out -> merged.writeTo(out) }
            merged.close()
            true
        } catch (e: Exception) {
            e.printStackTrace()
            false
        }
    }

    /**
     * Writes the document with the OCR words baked in as a searchable text layer, so a scanned page
     * can be found with Ctrl+F in any reader afterwards.
     */
    suspend fun exportWithOcrLayer(
        pages: List<PageItem>,
        ocrPages: Map<Int, OcrPageResult>,
        targetFile: File
    ): Boolean = withContext(Dispatchers.IO) {
        val scale = 2.0f
        try {
            val document = PdfDocument()
            for ((index, pageItem) in pages.withIndex()) {
                val bitmap = renderPageBitmap(
                    pageItem.originalPageIndex,
                    pageItem.rotationDegrees,
                    scale
                ) ?: continue
                val (widthPt, heightPt) = PageExportPlan.pageSizeInPoints(bitmap.width, bitmap.height, scale)
                val info = PdfDocument.PageInfo.Builder(widthPt, heightPt, index + 1).create()
                val outPage = document.startPage(info)
                outPage.canvas.drawBitmap(
                    bitmap,
                    null,
                    RectF(0f, 0f, widthPt.toFloat(), heightPt.toFloat()),
                    null
                )
                ocrPages[pageItem.pageIndex]?.let { result ->
                    ToolRenderer.drawSearchableText(outPage.canvas, result.words, widthPt, heightPt)
                }
                document.finishPage(outPage)
                bitmap.recycle()
            }
            FileOutputStream(targetFile).use { out -> document.writeTo(out) }
            document.close()
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
