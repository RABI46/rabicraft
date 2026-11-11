package org.rabicraft.app.data

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Color
import android.graphics.Matrix
import android.graphics.pdf.PdfDocument
import android.graphics.pdf.PdfRenderer
import android.net.Uri
import android.os.ParcelFileDescriptor
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.rabicraft.app.model.BookmarkItem
import org.rabicraft.app.model.DocumentPermissions
import org.rabicraft.app.model.PageItem
import org.rabicraft.app.model.PdfDocumentInfo
import java.io.File
import java.io.FileOutputStream
import java.util.UUID

class PdfRepository(private val context: Context) {

    private var currentFileDescriptor: ParcelFileDescriptor? = null
    private var currentRenderer: PdfRenderer? = null
    private var activePdfFile: File? = null

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

        val pfd = ParcelFileDescriptor.open(file, ParcelFileDescriptor.MODE_READ_ONLY)
        currentFileDescriptor = pfd
        val renderer = PdfRenderer(pfd)
        currentRenderer = renderer

        val pageCount = renderer.pageCount
        val pages = mutableListOf<PageItem>()

        val defaultTexts = listOf(
            "RabiCraft The Open-Source PDF Workbench Document Specimen & Feature Showcase ISO 32000-2",
            "Scripts of the World Typography Latin Arabic Hebrew Greek Cyrillic Japanese Korean Devanagari",
            "Native Architecture & Security crates/engine/src/document.rs open_document parse_pdf_header",
            "Interactive Forms & Fill & Sign Alexandre Dumas Full Name Email Address Digital Signature",
            "Vector Graphics & Color Precision Rabi Wine Crimson Charcoal Gold Leaf Transparency"
        )

        for (i in 0 until pageCount) {
            val page = renderer.openPage(i)
            val w = page.width
            val h = page.height
            page.close()

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
            author = if (isSample) "RabiCraft Team" else "Document Author",
            creator = "RabiCraft PDF Workbench",
            permissions = DocumentPermissions()
        )

        return Pair(docInfo, pages)
    }

    suspend fun renderPageBitmap(
        pageIndex: Int,
        rotationDegrees: Int = 0,
        scale: Float = 1.5f
    ): Bitmap? = withContext(Dispatchers.IO) {
        val renderer = currentRenderer ?: return@withContext null
        if (pageIndex < 0 || pageIndex >= renderer.pageCount) return@withContext null

        synchronized(renderer) {
            try {
                val page = renderer.openPage(pageIndex)
                val targetW = (page.width * scale).toInt().coerceAtLeast(100)
                val targetH = (page.height * scale).toInt().coerceAtLeast(100)

                val bitmap = Bitmap.createBitmap(targetW, targetH, Bitmap.Config.ARGB_8888)
                bitmap.eraseColor(Color.WHITE)

                page.render(bitmap, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
                page.close()

                if (rotationDegrees % 360 != 0) {
                    val matrix = Matrix().apply { postRotate(rotationDegrees.toFloat()) }
                    val rotated = Bitmap.createBitmap(bitmap, 0, 0, bitmap.width, bitmap.height, matrix, true)
                    bitmap.recycle()
                    rotated
                } else {
                    bitmap
                }
            } catch (e: Exception) {
                e.printStackTrace()
                null
            }
        }
    }

    suspend fun exportCurrentDocument(
        pages: List<PageItem>,
        targetFile: File
    ): Boolean = withContext(Dispatchers.IO) {
        val renderer = currentRenderer ?: return@withContext false
        try {
            val newPdf = PdfDocument()

            for ((newIdx, pageItem) in pages.withIndex()) {
                val origIdx = pageItem.originalPageIndex
                if (origIdx >= renderer.pageCount) continue

                val bitmap = renderPageBitmap(origIdx, pageItem.rotationDegrees, scale = 2.0f) ?: continue
                val pageInfo = PdfDocument.PageInfo.Builder(bitmap.width, bitmap.height, newIdx + 1).create()
                val page = newPdf.startPage(pageInfo)
                page.canvas.drawBitmap(bitmap, 0f, 0f, null)
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
                val page = renderer2.openPage(i)
                val w = (page.width * 1.5f).toInt()
                val h = (page.height * 1.5f).toInt()
                val bmp = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
                page.render(bmp, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
                page.close()

                val pageInfo = PdfDocument.PageInfo.Builder(w, h, pageCounter++).create()
                val p = combinedPdf.startPage(pageInfo)
                p.canvas.drawBitmap(bmp, 0f, 0f, null)
                combinedPdf.finishPage(p)
                bmp.recycle()
            }

            renderer2.close()
            pfd2.close()

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
