package org.rabicraft.app.model

import android.graphics.Bitmap

data class PdfDocumentInfo(
    val id: String,
    val name: String,
    val uriString: String? = null,
    val pageCount: Int,
    val fileSizeBytes: Long = 0L,
    val lastOpenedTimestamp: Long = System.currentTimeMillis(),
    val isSample: Boolean = false,
    val pdfVersion: String = "PDF 1.7",
    val title: String = name,
    val author: String = "FranPdf User",
    val creator: String = "FranPdf Engine 1.0",
    val producer: String = "FranPdf Android Core",
    val isEncrypted: Boolean = false,
    val permissions: DocumentPermissions = DocumentPermissions()
)

data class DocumentPermissions(
    val allowPrinting: Boolean = true,
    val allowHighQualityPrinting: Boolean = true,
    val allowModifying: Boolean = true,
    val allowCopying: Boolean = true,
    val allowAnnotating: Boolean = true,
    val allowFillingForms: Boolean = true,
    val allowScreenReaders: Boolean = true,
    val allowAssembly: Boolean = true
)

data class PageItem(
    val pageIndex: Int,
    val originalPageIndex: Int,
    val label: String,
    val rotationDegrees: Int = 0,
    val width: Int = 595,
    val height: Int = 842,
    val textContent: String = "",
    val cachedBitmap: Bitmap? = null
)

enum class AnnotationType {
    INK_PEN,
    HIGHLIGHTER,
    TEXT_NOTE,
    SIGNATURE,
    STAMP,
    RECTANGLE
}

data class NormalizedPoint(
    val x: Float, // 0.0 to 1.0 relative to page width
    val y: Float  // 0.0 to 1.0 relative to page height
)

data class AnnotationItem(
    val id: String,
    val pageIndex: Int,
    val type: AnnotationType,
    val colorArgb: Long,
    val strokeWidth: Float,
    val points: List<NormalizedPoint> = emptyList(),
    val text: String = "",
    val boundsX: Float = 0f,
    val boundsY: Float = 0f,
    val boundsWidth: Float = 0f,
    val boundsHeight: Float = 0f,
    val timestamp: Long = System.currentTimeMillis()
)

data class CommentItem(
    val id: String,
    val pageIndex: Int,
    val author: String,
    val text: String,
    val timestamp: Long = System.currentTimeMillis(),
    val xRatio: Float = 0.5f,
    val yRatio: Float = 0.5f,
    val isResolved: Boolean = false,
    val replies: List<CommentReply> = emptyList()
)

data class CommentReply(
    val id: String,
    val author: String,
    val text: String,
    val timestamp: Long = System.currentTimeMillis()
)

enum class FieldType {
    TEXT,
    CHECKBOX,
    SIGNATURE,
    DATE
}

data class FormFieldItem(
    val id: String,
    val pageIndex: Int,
    val label: String,
    val type: FieldType,
    val value: String = "",
    val isChecked: Boolean = false,
    val xRatio: Float,
    val yRatio: Float,
    val widthRatio: Float,
    val heightRatio: Float
)

enum class WorkbenchMode {
    LIBRARY,
    READ,
    ORGANIZE,
    ANNOTATE,
    EDIT,
    FILL_SIGN,
    PROTECT
}

enum class PageLayoutMode {
    CONTINUOUS,
    SINGLE_PAGE,
    TWO_UP
}

enum class ReadingTheme {
    DEFAULT,
    DARK,
    SEPIA,
    INVERTED
}

data class SearchMatch(
    val pageIndex: Int,
    val snippet: String,
    val matchIndexInPage: Int
)

data class BookmarkItem(
    val id: String,
    val title: String,
    val pageIndex: Int,
    val level: Int = 0,
    val children: List<BookmarkItem> = emptyList()
)

/**
 * Objects placed on a page in "Edit PDF" mode. Positions and sizes are stored relative to the page
 * so they survive preview scaling and are baked into the exported PDF.
 */
enum class EditKind {
    TEXT,
    IMAGE,
    RECTANGLE,
    WHITEOUT
}

data class PageEdit(
    val id: String,
    val pageIndex: Int,
    val kind: EditKind,
    val xRatio: Float,
    val yRatio: Float,
    val widthRatio: Float,
    val heightRatio: Float,
    val text: String = "",
    val colorArgb: Long = 0xFF18181BL,
    val fontSizeRatio: Float = 0.024f,
    val image: Bitmap? = null
)
