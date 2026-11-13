package org.rabicraft.app.data

import android.content.ContentResolver
import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
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
import androidx.exifinterface.media.ExifInterface
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.rabicraft.app.data.export.OverlayOp
import org.rabicraft.app.data.export.PageExportPlan
import org.rabicraft.app.data.ocr.OcrPageResult
import org.rabicraft.app.data.tools.DocumentEdgeDetector
import org.rabicraft.app.data.tools.EmbeddedImageScanner
import org.rabicraft.app.data.tools.FormFieldDetector
import org.rabicraft.app.data.tools.ImageOrientation
import org.rabicraft.app.data.tools.ImageTurn
import org.rabicraft.app.data.tools.PageDiff
import org.rabicraft.app.data.tools.TextBlock
import org.rabicraft.app.data.tools.TextBlockKind
import org.rabicraft.app.data.tools.TextLayout
import org.rabicraft.app.data.tools.TextOverlay
import org.rabicraft.app.data.tools.ToolExportPlan
import org.rabicraft.app.data.tools.ToolPlanner
import org.rabicraft.app.data.tools.ToolRenderer
import org.rabicraft.app.model.AnnotationItem
import org.rabicraft.app.model.BookmarkItem
import org.rabicraft.app.model.ColourEffect
import org.rabicraft.app.model.ComparisonLabels
import org.rabicraft.app.model.DiffKind
import org.rabicraft.app.model.DocumentPermissions
import org.rabicraft.app.model.FieldType
import org.rabicraft.app.model.FormFieldItem
import org.rabicraft.app.model.ImageExportSettings
import org.rabicraft.app.model.PageComparison
import org.rabicraft.app.model.PageEdit
import org.rabicraft.app.model.PageItem
import org.rabicraft.app.model.PdfDocumentInfo
import org.rabicraft.app.model.ScanCorners
import org.rabicraft.app.model.ScannedPage
import org.rabicraft.app.model.TextAnchor
import java.io.File
import java.io.FileOutputStream
import java.util.UUID
import kotlin.math.abs
import kotlin.math.sqrt

/** Comparison sheets are landscape A4: two pages side by side, captions underneath. */
private const val COMPARISON_SHEET_WIDTH = 842
private const val COMPARISON_SHEET_HEIGHT = 595
private const val COMPARISON_THUMB_SCALE = 0.35f
private const val COMPARISON_REPORT_SCALE = 0.9f
private const val COMPARISON_MAX_PAGES = 200

/** Image extraction reads the whole file, so it stays under a size a phone can hold twice. */
private const val MAX_EMBEDDED_SOURCE_BYTES = 200L * 1024L * 1024L

private const val TEXT_PDF_MARGIN_PT = 56f
private const val FIELDS_PER_PAGE = 12

private const val IMAGE_PDF_SHEET_WIDTH = 595
private const val IMAGE_PDF_SHEET_HEIGHT = 842
private const val IMAGE_PDF_MAX_PAGES = 100
private const val IMAGE_PDF_MAX_EDGE = 1600
private const val SCAN_PREVIEW_MAX_EDGE = 1400

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

    /**
     * One file per picture embedded in the open PDF, written into [targetDirectory].
     *
     * Pictures stored as Flate-compressed pixels cannot be recovered this way, so a fully scanned
     * PDF may yield nothing even though it visibly contains images.
     */
    suspend fun extractEmbeddedImages(targetDirectory: File): List<File> = withContext(Dispatchers.IO) {
        val source = activePdfFile ?: return@withContext emptyList()
        if (!source.exists()) return@withContext emptyList()
        if (source.length() > MAX_EMBEDDED_SOURCE_BYTES) return@withContext emptyList()
        val bytes = try {
            source.readBytes()
        } catch (e: Exception) {
            e.printStackTrace()
            return@withContext emptyList()
        }
        val images = EmbeddedImageScanner.scan(bytes)
        if (images.isEmpty()) return@withContext emptyList()
        targetDirectory.mkdirs()
        val written = mutableListOf<File>()
        for ((index, image) in images.withIndex()) {
            val file = File(
                targetDirectory,
                "FranPdf_image_${(index + 1).toString().padStart(3, '0')}.${image.extension}"
            )
            try {
                FileOutputStream(file).use { it.write(image.bytes) }
                written.add(file)
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
        written
    }

    /**
     * Compares the open document with [otherFile] page by page.
     *
     * Both sides are rendered small and sampled into thumbnails, then compared pixel by pixel (see
     * [PageDiff]). Pages whose aspect ratio differs by more than 2 % are reported as a size
     * mismatch instead of a pixel difference, because stretching them would compare nothing.
     */
    suspend fun compareDocuments(
        otherFile: File,
        pages: List<PageItem>,
        maxPages: Int = COMPARISON_MAX_PAGES
    ): List<PageComparison> = withContext(Dispatchers.IO) {
        val leftItems = pages.ifEmpty { defaultPageItems() }
        if (leftItems.isEmpty()) return@withContext emptyList()
        val opened = openRenderer(otherFile) ?: return@withContext emptyList()
        try {
            val total = minOf(
                maxOf(leftItems.size, opened.renderer.pageCount),
                maxPages.coerceAtLeast(1)
            )
            val results = mutableListOf<PageComparison>()
            for (index in 0 until total) {
                val item = leftItems.getOrNull(index)
                val left = item?.let {
                    renderPageBitmap(it.originalPageIndex, it.rotationDegrees, COMPARISON_THUMB_SCALE)
                }
                val right = if (index < opened.renderer.pageCount) {
                    renderPageFrom(opened.renderer, index, COMPARISON_THUMB_SCALE)
                } else {
                    null
                }
                results.add(comparePair(index + 1, left, right))
                left?.recycle()
                right?.recycle()
            }
            results
        } catch (e: Exception) {
            e.printStackTrace()
            emptyList()
        } finally {
            opened.close()
        }
    }

    /** Writes the comparison as a landscape A4 report: the two pages side by side, verdict above. */
    suspend fun exportComparisonReport(
        comparisons: List<PageComparison>,
        leftName: String,
        rightName: String,
        otherFile: File,
        labels: ComparisonLabels,
        pages: List<PageItem>,
        targetFile: File
    ): Boolean = withContext(Dispatchers.IO) {
        if (comparisons.isEmpty()) return@withContext false
        val leftItems = pages.ifEmpty { defaultPageItems() }
        val opened = openRenderer(otherFile) ?: return@withContext false
        try {
            val document = PdfDocument()
            val sheetWidth = COMPARISON_SHEET_WIDTH.toFloat()
            val sheetHeight = COMPARISON_SHEET_HEIGHT.toFloat()
            val slotWidth = (sheetWidth - 48f) / 2f
            val slotHeight = sheetHeight - 74f
            for ((index, comparison) in comparisons.withIndex()) {
                val info = PdfDocument.PageInfo.Builder(
                    COMPARISON_SHEET_WIDTH,
                    COMPARISON_SHEET_HEIGHT,
                    index + 1
                ).create()
                val page = document.startPage(info)
                val item = leftItems.getOrNull(index)
                val left = item?.let {
                    renderPageBitmap(it.originalPageIndex, it.rotationDegrees, COMPARISON_REPORT_SCALE)
                }
                val right = if (index < opened.renderer.pageCount) {
                    renderPageFrom(opened.renderer, index, COMPARISON_REPORT_SCALE)
                } else {
                    null
                }
                ToolRenderer.drawOverlay(
                    page.canvas,
                    TextOverlay(
                        text = comparisonLabel(comparison, labels),
                        anchor = TextAnchor.TOP_LEFT,
                        fontSizePt = 12f,
                        colorArgb = 0xFF111827L,
                        bold = true,
                        opacity = 1f,
                        insetPt = 14f,
                        rotationDegrees = 0f
                    ),
                    RectF(0f, 0f, sheetWidth, 34f)
                )
                val leftRect = RectF(16f, 40f, 16f + slotWidth, 40f + slotHeight)
                val rightRect = RectF(32f + slotWidth, 40f, 32f + slotWidth * 2f, 40f + slotHeight)
                left?.let { ToolRenderer.drawPage(page.canvas, it, leftRect) }
                right?.let { ToolRenderer.drawPage(page.canvas, it, rightRect) }
                val captionTop = 40f + slotHeight + 4f
                caption(page.canvas, leftName, RectF(16f, captionTop, 16f + slotWidth, sheetHeight - 2f))
                caption(
                    page.canvas,
                    rightName,
                    RectF(32f + slotWidth, captionTop, 32f + slotWidth * 2f, sheetHeight - 2f)
                )
                document.finishPage(page)
                left?.recycle()
                right?.recycle()
            }
            FileOutputStream(targetFile).use { document.writeTo(it) }
            document.close()
            true
        } catch (e: Exception) {
            e.printStackTrace()
            false
        } finally {
            opened.close()
        }
    }

    /**
     * One A4 page per picked picture, scaled to fit and centred. Returns the pages written.
     *
     * [effect] is how a photographed page is made readable: scans usually want the high-contrast
     * pass, while pictures should keep their colours.
     */
    suspend fun imagesToPdf(
        uris: List<Uri>,
        targetFile: File,
        effect: ColourEffect = ColourEffect.NONE
    ): Int = withContext(Dispatchers.IO) {
        val bitmaps = mutableListOf<Bitmap>()
        try {
            for (uri in uris.take(IMAGE_PDF_MAX_PAGES)) {
                val decoded = decodeImage(uri) ?: continue
                val treated = ToolRenderer.applyEffect(decoded, effect)
                if (treated !== decoded) decoded.recycle()
                bitmaps.add(treated)
            }
            writeImagePages(bitmaps, targetFile)
        } catch (e: Exception) {
            e.printStackTrace()
            0
        } finally {
            for (bitmap in bitmaps) bitmap.recycle()
        }
    }

    /**
     * Writes a paginated PDF from the "create a PDF" editor.
     *
     * The body is laid out by [TextLayout], which decides where lines wrap and where pages break;
     * this method only paints the lines it is given.
     */
    suspend fun createTextPdf(
        title: String,
        body: String,
        pageWidthPt: Int,
        pageHeightPt: Int,
        baseSizePt: Float,
        targetFile: File
    ): Boolean = withContext(Dispatchers.IO) {
        val width = pageWidthPt.coerceIn(200, 2000)
        val height = pageHeightPt.coerceIn(200, 3000)
        val margin = TEXT_PDF_MARGIN_PT
        val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.BLACK }
        val measure = { text: String, size: Float ->
            paint.textSize = size
            paint.measureText(text)
        }
        val blocks = buildList {
            if (title.isNotBlank()) add(TextBlock(TextBlockKind.TITLE, title.trim()))
            addAll(TextLayout.parse(body))
        }
        if (blocks.isEmpty()) return@withContext false
        val lines = TextLayout.wrapAll(blocks, baseSizePt, width - 2 * margin, measure)
        val pages = TextLayout.paginate(lines, baseSizePt, height.toFloat(), margin, margin)
        if (pages.isEmpty()) return@withContext false
        try {
            val document = PdfDocument()
            try {
                for ((index, pageLines) in pages.withIndex()) {
                    val info = PdfDocument.PageInfo.Builder(width, height, index + 1).create()
                    val page = document.startPage(info)
                    var top = margin
                    for (line in pageLines) {
                        val size = baseSizePt * TextLayout.sizeFactor(line.kind)
                        paint.textSize = size
                        paint.typeface = if (
                            line.kind == TextBlockKind.TITLE || line.kind == TextBlockKind.HEADING
                        ) {
                            Typeface.DEFAULT_BOLD
                        } else {
                            Typeface.DEFAULT
                        }
                        page.canvas.drawText(line.text, margin, top + size, paint)
                        top += TextLayout.lineHeight(baseSizePt, line.kind)
                    }
                    document.finishPage(page)
                }
                FileOutputStream(targetFile).use { document.writeTo(it) }
            } finally {
                document.close()
            }
            true
        } catch (e: Exception) {
            e.printStackTrace()
            false
        }
    }

    /**
     * Fillable areas found on the first [maxPages] pages, shaped like the document's own form
     * fields so fill & sign can type into them.
     */
    suspend fun detectFormFields(
        pages: List<PageItem>,
        maxPages: Int = 30
    ): List<FormFieldItem> = withContext(Dispatchers.IO) {
        val fields = mutableListOf<FormFieldItem>()
        for (page in pages.take(maxPages)) {
            val bitmap = renderPageBitmap(page.originalPageIndex, page.rotationDegrees, 1.2f)
                ?: continue
            val width = bitmap.width
            val height = bitmap.height
            val pixels = IntArray(width * height)
            try {
                bitmap.getPixels(pixels, 0, width, 0, 0, width, height)
            } finally {
                bitmap.recycle()
            }
            val detected = FormFieldDetector.detect(pixels, width, height)
            for (rect in detected.take(FIELDS_PER_PAGE)) {
                fields.add(
                    FormFieldItem(
                        id = "field-${page.pageIndex}-${fields.size}",
                        pageIndex = page.pageIndex,
                        label = "Zone ${fields.size + 1}",
                        type = if (rect.height > 0.05f) FieldType.SIGNATURE else FieldType.TEXT,
                        xRatio = rect.left,
                        yRatio = rect.top,
                        widthRatio = rect.width.coerceAtLeast(0.02f),
                        heightRatio = rect.height.coerceAtLeast(0.012f)
                    )
                )
            }
        }
        fields
    }

    /**
     * A preview of a captured page together with the corners found on it, for the adjustment
     * screen. The corners are null when no convincing sheet was recognised, and the screen then
     * starts from the edges of the photo.
     */
    suspend fun scanPreview(
        file: File,
        maxEdge: Int = SCAN_PREVIEW_MAX_EDGE
    ): Pair<Bitmap?, ScanCorners?> = withContext(Dispatchers.IO) {
        val bitmap = decodeImage(Uri.fromFile(file), maxEdge)
            ?: return@withContext Pair(null, null)
        val corners = try {
            val pixels = IntArray(bitmap.width * bitmap.height)
            bitmap.getPixels(pixels, 0, bitmap.width, 0, 0, bitmap.width, bitmap.height)
            DocumentEdgeDetector.detect(pixels, bitmap.width, bitmap.height)
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
        Pair(bitmap, corners)
    }

    /**
     * The scanner's pages as one PDF, each warped with the corners the user confirmed. A page the
     * user chose to keep as photographed has null corners and is drawn untouched.
     */
    suspend fun scannedPagesToPdf(
        pages: List<ScannedPage>,
        targetFile: File,
        effect: ColourEffect = ColourEffect.NONE
    ): Int = withContext(Dispatchers.IO) {
        val bitmaps = mutableListOf<Bitmap>()
        try {
            for (page in pages.take(IMAGE_PDF_MAX_PAGES)) {
                var decoded = decodeImage(Uri.fromFile(page.file)) ?: continue
                val corners = page.corners
                if (corners != null) decoded = warpToPage(decoded, corners)
                val treated = ToolRenderer.applyEffect(decoded, effect)
                if (treated !== decoded) decoded.recycle()
                bitmaps.add(treated)
            }
            writeImagePages(bitmaps, targetFile)
        } catch (e: Exception) {
            e.printStackTrace()
            0
        } finally {
            for (bitmap in bitmaps) bitmap.recycle()
        }
    }

    /** One A4 page per bitmap, scaled to fit and centred. The caller owns and recycles [bitmaps]. */
    private fun writeImagePages(bitmaps: List<Bitmap>, targetFile: File): Int {
        if (bitmaps.isEmpty()) return 0
        val document = PdfDocument()
        try {
            for ((index, bitmap) in bitmaps.withIndex()) {
                val info = PdfDocument.PageInfo.Builder(
                    IMAGE_PDF_SHEET_WIDTH,
                    IMAGE_PDF_SHEET_HEIGHT,
                    index + 1
                ).create()
                val page = document.startPage(info)
                val slot = ToolPlanner.fitSlot(
                    imageWidth = bitmap.width,
                    imageHeight = bitmap.height,
                    sheetWidthPt = IMAGE_PDF_SHEET_WIDTH,
                    sheetHeightPt = IMAGE_PDF_SHEET_HEIGHT,
                    marginPt = 18f
                )
                ToolRenderer.drawPage(
                    page.canvas,
                    bitmap,
                    ToolRenderer.slotRect(slot, IMAGE_PDF_SHEET_WIDTH, IMAGE_PDF_SHEET_HEIGHT)
                )
                document.finishPage(page)
            }
            FileOutputStream(targetFile).use { document.writeTo(it) }
        } finally {
            document.close()
        }
        return bitmaps.size
    }

    private fun warpToPage(bitmap: Bitmap, corners: ScanCorners): Bitmap {
        val width = bitmap.width.toFloat()
        val height = bitmap.height.toFloat()
        val source = floatArrayOf(
            corners.topLeftX * width, corners.topLeftY * height,
            corners.topRightX * width, corners.topRightY * height,
            corners.bottomRightX * width, corners.bottomRightY * height,
            corners.bottomLeftX * width, corners.bottomLeftY * height
        )
        val outputWidth = maxOf(distance(source, 0, 2), distance(source, 6, 4))
            .toInt()
            .coerceIn(64, 4096)
        val outputHeight = maxOf(distance(source, 0, 6), distance(source, 2, 4))
            .toInt()
            .coerceIn(64, 4096)
        val target = floatArrayOf(
            0f, 0f,
            outputWidth.toFloat(), 0f,
            outputWidth.toFloat(), outputHeight.toFloat(),
            0f, outputHeight.toFloat()
        )
        val matrix = Matrix()
        if (!matrix.setPolyToPoly(source, 0, target, 0, 4)) return bitmap
        val output = Bitmap.createBitmap(outputWidth, outputHeight, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(output)
        // Corners rarely land exactly on the picture edge; paper-coloured backing blends the seam.
        canvas.drawColor(Color.WHITE)
        canvas.drawBitmap(bitmap, matrix, Paint(Paint.FILTER_BITMAP_FLAG))
        if (output !== bitmap) bitmap.recycle()
        return output
    }

    private fun distance(points: FloatArray, first: Int, second: Int): Float {
        val dx = points[first] - points[second]
        val dy = points[first + 1] - points[second + 1]
        return sqrt(dx * dx + dy * dy)
    }

    /**
     * Decodes a picked image, downsampled so a 50 MP photo does not fill the heap, and turned the way
     * its EXIF orientation asks.
     *
     * BitmapFactory ignores that tag, so without the turn a photo taken in portrait arrived on the
     * page sideways — and the corner detection then looked for the sheet in a rotated picture.
     */
    private fun decodeImage(uri: Uri, maxEdge: Int = IMAGE_PDF_MAX_EDGE): Bitmap? = try {
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        context.contentResolver.openInputStream(uri)?.use {
            BitmapFactory.decodeStream(it, null, bounds)
        }
        if (bounds.outWidth <= 0 || bounds.outHeight <= 0) {
            null
        } else {
            var sample = 1
            while (maxOf(bounds.outWidth / sample, bounds.outHeight / sample) > maxEdge) sample *= 2
            val options = BitmapFactory.Options().apply { inSampleSize = sample }
            val decoded = context.contentResolver.openInputStream(uri)?.use {
                BitmapFactory.decodeStream(it, null, options)
            }
            if (decoded == null) null else applyExifOrientation(uri, decoded)
        }
    } catch (e: Exception) {
        e.printStackTrace()
        null
    }

    /**
     * Turns [bitmap] as its EXIF header asks. A picture that is already upright, or whose header
     * cannot be read, comes back untouched and is left for the caller to recycle.
     */
    private fun applyExifOrientation(uri: Uri, bitmap: Bitmap): Bitmap {
        val turn = readExifTurn(uri) ?: return bitmap
        if (turn == ImageTurn.NONE) return bitmap
        val matrix = Matrix()
        if (turn.mirrored) matrix.postScale(-1f, 1f)
        if (turn.degrees != 0) matrix.postRotate(turn.degrees.toFloat())
        return try {
            val turned = Bitmap.createBitmap(bitmap, 0, 0, bitmap.width, bitmap.height, matrix, true)
            if (turned !== bitmap) bitmap.recycle()
            turned
        } catch (e: Exception) {
            e.printStackTrace()
            bitmap
        }
    }

    /**
     * The EXIF orientation behind [uri], or null when it cannot be read.
     *
     * Document providers often hand a picture over as a content URI, which only the stream
     * constructor can open; a picked file can be read directly. Neither failing is an error — the
     * photo is then simply used as decoded.
     */
    @Suppress("DEPRECATION")
    private fun readExifTurn(uri: Uri): ImageTurn? = try {
        val exif = when (uri.scheme) {
            ContentResolver.SCHEME_FILE -> uri.path?.let { ExifInterface(File(it)) }
            ContentResolver.SCHEME_CONTENT ->
                context.contentResolver.openInputStream(uri)?.use { ExifInterface(it) }
            else -> null
        }
        exif?.let {
            ImageOrientation.fromExifTag(
                it.getAttributeInt(ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_NORMAL)
            )
        }
    } catch (e: Exception) {
        null
    }

    private fun defaultPageItems(): List<PageItem> {
        val count = currentRenderer?.pageCount ?: 0
        return (0 until count).map { index ->
            PageItem(pageIndex = index, originalPageIndex = index, label = "#${index + 1}")
        }
    }

    private class OpenRenderer(
        val renderer: PdfRenderer,
        private val descriptor: ParcelFileDescriptor
    ) {
        fun close() {
            try {
                renderer.close()
            } catch (_: Throwable) {
            }
            try {
                descriptor.close()
            } catch (_: Throwable) {
            }
        }
    }

    private fun openRenderer(file: File): OpenRenderer? {
        var descriptor: ParcelFileDescriptor? = null
        return try {
            descriptor = ParcelFileDescriptor.open(file, ParcelFileDescriptor.MODE_READ_ONLY)
            OpenRenderer(PdfRenderer(descriptor), descriptor)
        } catch (e: Throwable) {
            e.printStackTrace()
            try {
                descriptor?.close()
            } catch (_: Throwable) {
            }
            null
        }
    }

    private fun renderPageFrom(renderer: PdfRenderer, pageIndex: Int, scale: Float): Bitmap? {
        synchronized(renderer) {
            var page: PdfRenderer.Page? = null
            return try {
                page = renderer.openPage(pageIndex)
                val width = (page.width * scale).toInt().coerceIn(80, 2048)
                val height = (page.height * scale).toInt().coerceIn(80, 2048)
                val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
                bitmap.eraseColor(Color.WHITE)
                page.render(bitmap, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
                bitmap
            } catch (e: Throwable) {
                e.printStackTrace()
                null
            } finally {
                try {
                    page?.close()
                } catch (_: Throwable) {
                }
            }
        }
    }

    private fun comparePair(pageNumber: Int, left: Bitmap?, right: Bitmap?): PageComparison = when {
        left == null || right == null -> PageComparison(
            pageNumber = pageNumber,
            status = DiffKind.MISSING,
            ratio = 0f,
            hasLeft = left != null,
            hasRight = right != null
        )
        aspectDiffers(left, right) -> PageComparison(
            pageNumber = pageNumber,
            status = DiffKind.DIMENSION_MISMATCH,
            ratio = 0f,
            hasLeft = true,
            hasRight = true
        )
        else -> {
            val result = PageDiff.compare(thumbnail(left), thumbnail(right))
            PageComparison(
                pageNumber = pageNumber,
                status = result.kind,
                ratio = result.ratio,
                hasLeft = true,
                hasRight = true
            )
        }
    }

    private fun aspectDiffers(left: Bitmap, right: Bitmap): Boolean {
        val leftAspect = left.width.toFloat() / left.height.coerceAtLeast(1).toFloat()
        val rightAspect = right.width.toFloat() / right.height.coerceAtLeast(1).toFloat()
        return abs(leftAspect - rightAspect) > 0.02f
    }

    private fun thumbnail(bitmap: Bitmap): IntArray {
        val width = 96
        val height = 128
        val scaled = Bitmap.createScaledBitmap(bitmap, width, height, true)
        val pixels = IntArray(width * height)
        try {
            scaled.getPixels(pixels, 0, width, 0, 0, width, height)
        } finally {
            if (scaled !== bitmap) scaled.recycle()
        }
        return pixels
    }

    private fun comparisonLabel(comparison: PageComparison, labels: ComparisonLabels): String {
        val verdict = when (comparison.status) {
            DiffKind.IDENTICAL -> labels.identical
            DiffKind.DIFFERENT -> "${labels.different} · " + "%.2f %%".format(comparison.ratio * 100f)
            DiffKind.DIMENSION_MISMATCH -> labels.differentFormat
            DiffKind.MISSING -> labels.missing
        }
        return "Page ${comparison.pageNumber} — $verdict"
    }

    private fun caption(canvas: Canvas, name: String, rect: RectF) {
        ToolRenderer.drawOverlay(
            canvas,
            TextOverlay(
                text = name.take(48),
                anchor = TextAnchor.TOP_LEFT,
                fontSizePt = 9f,
                colorArgb = 0xFF6B7280L,
                bold = false,
                opacity = 1f,
                insetPt = 0f,
                rotationDegrees = 0f
            ),
            rect
        )
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
