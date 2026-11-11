package org.rabicraft.app.ui.components

import android.graphics.Bitmap
import android.graphics.Matrix
import androidx.compose.ui.graphics.ColorFilter
import android.graphics.pdf.PdfRenderer
import android.os.ParcelFileDescriptor
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ChatBubble
import androidx.compose.material.icons.filled.ErrorOutline
import androidx.compose.material.icons.filled.FitScreen
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.rabicraft.app.model.AnnotationItem
import org.rabicraft.app.model.AnnotationType
import org.rabicraft.app.model.CommentItem
import org.rabicraft.app.model.FormFieldItem
import org.rabicraft.app.model.NormalizedPoint
import org.rabicraft.app.model.PageItem
import org.rabicraft.app.model.PageLayoutMode
import org.rabicraft.app.model.ReadingTheme
import org.rabicraft.app.model.WorkbenchMode
import org.rabicraft.app.model.EditKind
import org.rabicraft.app.ui.edit.LocalEditLayer
import org.rabicraft.app.ui.theme.SepiaPage
import java.io.File
import kotlin.math.roundToInt

/**
 * High-performance Jetpack Compose PDF Rendering Component using Android's native [PdfRenderer].
 *
 * Supports:
 * - Direct asynchronous rendering of pages from a [File] via [PdfRenderer].
 * - Single-page, continuous scroll, and two-up facing pages modes.
 * - Double-tap to zoom, pinch-to-zoom, pan drag.
 * - Dynamic high-DPI scaling.
 * - Interactive layers for annotations, AcroForm fields, comments, and search highlights.
 */
@Composable
fun PdfRendererComponent(
    pdfFile: File?,
    pages: List<PageItem>,
    currentPageIndex: Int,
    layoutMode: PageLayoutMode,
    readingTheme: ReadingTheme,
    workbenchMode: WorkbenchMode,
    zoomLevel: Float,
    annotations: Map<Int, List<AnnotationItem>>,
    formFields: List<FormFieldItem>,
    comments: List<CommentItem>,
    colorFilter: ColorFilter?,
    activeAnnotType: AnnotationType,
    penColor: Long,
    strokeWidth: Float,
    onPageChanged: (Int) -> Unit,
    onAddAnnotation: (Int, AnnotationItem) -> Unit,
    onUpdateFormField: (String, String) -> Unit,
    onToggleFormFieldChecked: (String) -> Unit,
    onCommentClick: (CommentItem) -> Unit,
    modifier: Modifier = Modifier
) {
    if (pdfFile == null || !pdfFile.exists() || pages.isEmpty()) {
        Box(
            modifier = modifier.fillMaxSize(),
            contentAlignment = Alignment.Center
        ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                CircularProgressIndicator(
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(36.dp)
                )
                Spacer(modifier = Modifier.height(12.dp))
                Text(
                    text = "Loading PDF document…",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
        return
    }

    // Manage PdfRenderer lifecycle safely
    var pdfRenderer by remember(pdfFile) { mutableStateOf<PdfRenderer?>(null) }
    var fileDescriptor by remember(pdfFile) { mutableStateOf<ParcelFileDescriptor?>(null) }

    DisposableEffect(pdfFile) {
        var pfd: ParcelFileDescriptor? = null
        var renderer: PdfRenderer? = null
        try {
            if (pdfFile != null && pdfFile.exists() && pdfFile.length() > 0) {
                pfd = ParcelFileDescriptor.open(pdfFile, ParcelFileDescriptor.MODE_READ_ONLY)
                renderer = PdfRenderer(pfd)
                fileDescriptor = pfd
                pdfRenderer = renderer
            }
        } catch (e: Throwable) {
            e.printStackTrace()
            try { renderer?.close() } catch (_: Throwable) {}
            try { pfd?.close() } catch (_: Throwable) {}
            fileDescriptor = null
            pdfRenderer = null
        }

        onDispose {
            try {
                pdfRenderer?.close()
            } catch (_: Throwable) {}
            pdfRenderer = null
            try {
                fileDescriptor?.close()
            } catch (_: Throwable) {}
            fileDescriptor = null
        }
    }

    val renderer = pdfRenderer

    if (renderer == null) {
        Box(
            modifier = modifier.fillMaxSize(),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = "Initializing native PDF engine…",
                style = MaterialTheme.typography.bodyMedium
            )
        }
        return
    }

    // In-memory LRU page bitmap cache
    val pageBitmapCache = remember(renderer) { mutableStateMapOf<String, Bitmap>() }

    when (layoutMode) {
        PageLayoutMode.CONTINUOUS -> {
            ContinuousPdfView(
                renderer = renderer,
                pages = pages,
                initialPageIndex = currentPageIndex,
                pageBitmapCache = pageBitmapCache,
                readingTheme = readingTheme,
                workbenchMode = workbenchMode,
                zoomLevel = zoomLevel,
                annotations = annotations,
                formFields = formFields,
                comments = comments,
                colorFilter = colorFilter,
                activeAnnotType = activeAnnotType,
                penColor = penColor,
                strokeWidth = strokeWidth,
                onPageVisible = onPageChanged,
                onAddAnnotation = onAddAnnotation,
                onUpdateFormField = onUpdateFormField,
                onToggleFormFieldChecked = onToggleFormFieldChecked,
                onCommentClick = onCommentClick,
                modifier = modifier
            )
        }

        PageLayoutMode.SINGLE_PAGE -> {
            SinglePagePdfView(
                renderer = renderer,
                page = pages.getOrElse(currentPageIndex) { pages.first() },
                pageBitmapCache = pageBitmapCache,
                readingTheme = readingTheme,
                workbenchMode = workbenchMode,
                zoomLevel = zoomLevel,
                annotations = annotations[currentPageIndex] ?: emptyList(),
                formFields = formFields.filter { it.pageIndex == currentPageIndex },
                comments = comments.filter { it.pageIndex == currentPageIndex },
                colorFilter = colorFilter,
                activeAnnotType = activeAnnotType,
                penColor = penColor,
                strokeWidth = strokeWidth,
                onAddAnnotation = { onAddAnnotation(currentPageIndex, it) },
                onUpdateFormField = onUpdateFormField,
                onToggleFormFieldChecked = onToggleFormFieldChecked,
                onCommentClick = onCommentClick,
                modifier = modifier
            )
        }

        PageLayoutMode.TWO_UP -> {
            TwoUpPdfView(
                renderer = renderer,
                pages = pages,
                firstPageIndex = currentPageIndex,
                pageBitmapCache = pageBitmapCache,
                readingTheme = readingTheme,
                workbenchMode = workbenchMode,
                zoomLevel = zoomLevel,
                annotations = annotations,
                formFields = formFields,
                comments = comments,
                colorFilter = colorFilter,
                activeAnnotType = activeAnnotType,
                penColor = penColor,
                strokeWidth = strokeWidth,
                onAddAnnotation = onAddAnnotation,
                onUpdateFormField = onUpdateFormField,
                onToggleFormFieldChecked = onToggleFormFieldChecked,
                onCommentClick = onCommentClick,
                modifier = modifier
            )
        }
    }
}

/**
 * Continuous vertical scrolling PDF view using LazyColumn.
 */
@Composable
private fun ContinuousPdfView(
    renderer: PdfRenderer,
    pages: List<PageItem>,
    initialPageIndex: Int,
    pageBitmapCache: MutableMap<String, Bitmap>,
    readingTheme: ReadingTheme,
    workbenchMode: WorkbenchMode,
    zoomLevel: Float,
    annotations: Map<Int, List<AnnotationItem>>,
    formFields: List<FormFieldItem>,
    comments: List<CommentItem>,
    colorFilter: ColorFilter?,
    activeAnnotType: AnnotationType,
    penColor: Long,
    strokeWidth: Float,
    onPageVisible: (Int) -> Unit,
    onAddAnnotation: (Int, AnnotationItem) -> Unit,
    onUpdateFormField: (String, String) -> Unit,
    onToggleFormFieldChecked: (String) -> Unit,
    onCommentClick: (CommentItem) -> Unit,
    modifier: Modifier = Modifier
) {
    val listState = rememberLazyListState(initialFirstVisibleItemIndex = initialPageIndex)

    LaunchedEffect(listState) {
        snapshotFlow { listState.firstVisibleItemIndex }
            .distinctUntilChanged()
            .collect { visibleIndex ->
                if (visibleIndex in pages.indices) {
                    onPageVisible(visibleIndex)
                }
            }
    }

    LazyColumn(
        state = listState,
        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 20.dp),
        verticalArrangement = Arrangement.spacedBy(20.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = modifier
            .fillMaxSize()
            .testTag("continuous_pdf_scroll_view")
    ) {
        itemsIndexed(pages, key = { _, item -> item.pageIndex }) { index, page ->
            Box(
                modifier = Modifier
                    .fillMaxWidth(0.96f)
                    .aspectRatio(595f / 842f)
            ) {
                PdfPageRendererCanvas(
                    renderer = renderer,
                    page = page,
                    scaleMultiplier = zoomLevel,
                    pageBitmapCache = pageBitmapCache,
                    readingTheme = readingTheme,
                    workbenchMode = workbenchMode,
                    annotations = annotations[page.pageIndex] ?: emptyList(),
                    formFields = formFields.filter { it.pageIndex == page.pageIndex },
                    comments = comments.filter { it.pageIndex == page.pageIndex },
                    colorFilter = colorFilter,
                    activeAnnotType = activeAnnotType,
                    penColor = penColor,
                    strokeWidth = strokeWidth,
                    onAddAnnotation = { onAddAnnotation(page.pageIndex, it) },
                    onUpdateFormField = onUpdateFormField,
                    onToggleFormFieldChecked = onToggleFormFieldChecked,
                    onCommentClick = onCommentClick,
                    modifier = Modifier.fillMaxSize()
                )
            }
        }
    }
}

/**
 * Single page view with pinch-to-zoom, double-tap zoom, and pan gestures.
 */
@Composable
private fun SinglePagePdfView(
    renderer: PdfRenderer,
    page: PageItem,
    pageBitmapCache: MutableMap<String, Bitmap>,
    readingTheme: ReadingTheme,
    workbenchMode: WorkbenchMode,
    zoomLevel: Float,
    annotations: List<AnnotationItem>,
    formFields: List<FormFieldItem>,
    comments: List<CommentItem>,
    colorFilter: ColorFilter?,
    activeAnnotType: AnnotationType,
    penColor: Long,
    strokeWidth: Float,
    onAddAnnotation: (AnnotationItem) -> Unit,
    onUpdateFormField: (String, String) -> Unit,
    onToggleFormFieldChecked: (String) -> Unit,
    onCommentClick: (CommentItem) -> Unit,
    modifier: Modifier = Modifier
) {
    var scale by remember { mutableFloatStateOf(1f) }
    var offsetX by remember { mutableFloatStateOf(0f) }
    var offsetY by remember { mutableFloatStateOf(0f) }

    val combinedScale = zoomLevel * scale

    Box(
        modifier = modifier
            .fillMaxSize()
            .pointerInput(workbenchMode) {
                if (workbenchMode == WorkbenchMode.READ) {
                    detectTransformGestures { _, pan, zoom, _ ->
                        scale = (scale * zoom).coerceIn(0.8f, 4.0f)
                        offsetX += pan.x
                        offsetY += pan.y
                    }
                }
            }
            .pointerInput(workbenchMode) {
                if (workbenchMode == WorkbenchMode.READ) {
                    detectTapGestures(
                        onDoubleTap = {
                            if (scale > 1.2f) {
                                scale = 1f
                                offsetX = 0f
                                offsetY = 0f
                            } else {
                                scale = 2.0f
                            }
                        }
                    )
                }
            },
        contentAlignment = Alignment.Center
    ) {
        Box(
            modifier = Modifier
                .graphicsLayer(
                    scaleX = combinedScale,
                    scaleY = combinedScale,
                    translationX = offsetX,
                    translationY = offsetY
                )
                .fillMaxWidth(0.92f)
                .aspectRatio(595f / 842f)
                .padding(8.dp)
        ) {
            PdfPageRendererCanvas(
                renderer = renderer,
                page = page,
                scaleMultiplier = combinedScale,
                pageBitmapCache = pageBitmapCache,
                readingTheme = readingTheme,
                workbenchMode = workbenchMode,
                annotations = annotations,
                formFields = formFields,
                comments = comments,
                colorFilter = colorFilter,
                activeAnnotType = activeAnnotType,
                penColor = penColor,
                strokeWidth = strokeWidth,
                onAddAnnotation = onAddAnnotation,
                onUpdateFormField = onUpdateFormField,
                onToggleFormFieldChecked = onToggleFormFieldChecked,
                onCommentClick = onCommentClick,
                modifier = Modifier.fillMaxSize()
            )
        }

        // Reset Zoom quick button if zoomed in
        if (scale > 1.1f) {
            Surface(
                shape = CircleShape,
                color = MaterialTheme.colorScheme.surface.copy(alpha = 0.9f),
                shadowElevation = 4.dp,
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(16.dp)
                    .size(40.dp)
                    .clickable {
                        scale = 1f
                        offsetX = 0f
                        offsetY = 0f
                    }
                    .testTag("reset_zoom_button")
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        Icons.Default.FitScreen,
                        contentDescription = "Fit Page",
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(20.dp)
                    )
                }
            }
        }
    }
}

/**
 * Two-up view showing facing pages side by side.
 */
@Composable
private fun TwoUpPdfView(
    renderer: PdfRenderer,
    pages: List<PageItem>,
    firstPageIndex: Int,
    pageBitmapCache: MutableMap<String, Bitmap>,
    readingTheme: ReadingTheme,
    workbenchMode: WorkbenchMode,
    zoomLevel: Float,
    annotations: Map<Int, List<AnnotationItem>>,
    formFields: List<FormFieldItem>,
    comments: List<CommentItem>,
    colorFilter: ColorFilter?,
    activeAnnotType: AnnotationType,
    penColor: Long,
    strokeWidth: Float,
    onAddAnnotation: (Int, AnnotationItem) -> Unit,
    onUpdateFormField: (String, String) -> Unit,
    onToggleFormFieldChecked: (String) -> Unit,
    onCommentClick: (CommentItem) -> Unit,
    modifier: Modifier = Modifier
) {
    val page1 = pages.getOrElse(firstPageIndex) { pages.first() }
    val page2 = if (firstPageIndex + 1 < pages.size) pages[firstPageIndex + 1] else null

    Box(
        modifier = modifier.fillMaxSize(),
        contentAlignment = Alignment.Center
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth(0.96f)
                .graphicsLayer(scaleX = zoomLevel, scaleY = zoomLevel)
                .padding(horizontal = 8.dp),
            horizontalArrangement = Arrangement.Center,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .weight(1f)
                    .aspectRatio(595f / 842f)
            ) {
                PdfPageRendererCanvas(
                    renderer = renderer,
                    page = page1,
                    scaleMultiplier = zoomLevel,
                    pageBitmapCache = pageBitmapCache,
                    readingTheme = readingTheme,
                    workbenchMode = workbenchMode,
                    annotations = annotations[page1.pageIndex] ?: emptyList(),
                    formFields = formFields.filter { it.pageIndex == page1.pageIndex },
                    comments = comments.filter { it.pageIndex == page1.pageIndex },
                    colorFilter = colorFilter,
                    activeAnnotType = activeAnnotType,
                    penColor = penColor,
                    strokeWidth = strokeWidth,
                    onAddAnnotation = { onAddAnnotation(page1.pageIndex, it) },
                    onUpdateFormField = onUpdateFormField,
                    onToggleFormFieldChecked = onToggleFormFieldChecked,
                    onCommentClick = onCommentClick,
                    modifier = Modifier.fillMaxSize()
                )
            }

            if (page2 != null) {
                Spacer(modifier = Modifier.width(12.dp))
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .aspectRatio(595f / 842f)
                ) {
                    PdfPageRendererCanvas(
                        renderer = renderer,
                        page = page2,
                        scaleMultiplier = zoomLevel,
                        pageBitmapCache = pageBitmapCache,
                        readingTheme = readingTheme,
                        workbenchMode = WorkbenchMode.READ,
                        annotations = annotations[page2.pageIndex] ?: emptyList(),
                        formFields = formFields.filter { it.pageIndex == page2.pageIndex },
                        comments = comments.filter { it.pageIndex == page2.pageIndex },
                        colorFilter = colorFilter,
                        activeAnnotType = activeAnnotType,
                        penColor = penColor,
                        strokeWidth = strokeWidth,
                        onAddAnnotation = { onAddAnnotation(page2.pageIndex, it) },
                        onUpdateFormField = onUpdateFormField,
                        onToggleFormFieldChecked = onToggleFormFieldChecked,
                        onCommentClick = onCommentClick,
                        modifier = Modifier.fillMaxSize()
                    )
                }
            }
        }
    }
}

/**
 * Individual page rendering canvas directly using [PdfRenderer] with cached bitmap management.
 */
@Composable
private fun PdfPageRendererCanvas(
    renderer: PdfRenderer,
    page: PageItem,
    scaleMultiplier: Float,
    pageBitmapCache: MutableMap<String, Bitmap>,
    readingTheme: ReadingTheme,
    workbenchMode: WorkbenchMode,
    annotations: List<AnnotationItem>,
    formFields: List<FormFieldItem>,
    comments: List<CommentItem>,
    colorFilter: ColorFilter?,
    activeAnnotType: AnnotationType,
    penColor: Long,
    strokeWidth: Float,
    onAddAnnotation: (AnnotationItem) -> Unit,
    onUpdateFormField: (String, String) -> Unit,
    onToggleFormFieldChecked: (String) -> Unit,
    onCommentClick: (CommentItem) -> Unit,
    modifier: Modifier = Modifier
) {
    val coroutineScope = rememberCoroutineScope()
    var isRendering by remember { mutableStateOf(false) }
    var renderError by remember { mutableStateOf<String?>(null) }

    val cacheKey = "p_${page.originalPageIndex}_r${page.rotationDegrees}_s${(scaleMultiplier * 10).toInt()}"
    var renderedBitmap by remember(cacheKey) { mutableStateOf<Bitmap?>(pageBitmapCache[cacheKey]) }

    // Touch drawing path in progress
    val currentStroke = remember { mutableListOf<NormalizedPoint>() }
    var strokeUpdateTrigger by remember { mutableStateOf(0) }

    // Asynchronously render using PdfRenderer
    LaunchedEffect(cacheKey, renderer) {
        if (renderedBitmap == null) {
            isRendering = true
            renderError = null
            try {
                val bmp = withContext(Dispatchers.IO) {
                    synchronized(renderer) {
                        if (page.originalPageIndex !in 0 until renderer.pageCount) {
                            return@withContext null
                        }
                        var pdfPage: PdfRenderer.Page? = null
                        try {
                            pdfPage = renderer.openPage(page.originalPageIndex)
                            val densityFactor = 1.4f * scaleMultiplier.coerceIn(1.0f, 2.0f)
                            val targetW = (pdfPage.width * densityFactor).toInt().coerceIn(80, 2048)
                            val targetH = (pdfPage.height * densityFactor).toInt().coerceIn(80, 2048)

                            val b = Bitmap.createBitmap(targetW, targetH, Bitmap.Config.ARGB_8888)
                            b.eraseColor(android.graphics.Color.WHITE)

                            pdfPage.render(b, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)

                            if (page.rotationDegrees % 360 != 0) {
                                val matrix = Matrix().apply { postRotate(page.rotationDegrees.toFloat()) }
                                val rotated = Bitmap.createBitmap(b, 0, 0, b.width, b.height, matrix, true)
                                b.recycle()
                                rotated
                            } else {
                                b
                            }
                        } finally {
                            try {
                                pdfPage?.close()
                            } catch (_: Throwable) {}
                        }
                    }
                }
                if (bmp != null) {
                    pageBitmapCache[cacheKey] = bmp
                    renderedBitmap = bmp
                }
            } catch (e: Throwable) {
                renderError = e.message ?: "Render error"
            } finally {
                isRendering = false
            }
        }
    }

    val pageSurfaceColor = when (readingTheme) {
        ReadingTheme.SEPIA -> SepiaPage
        ReadingTheme.DARK -> Color(0xFF222226)
        else -> Color.White
    }

    BoxWithConstraints(
        modifier = modifier
            .shadow(6.dp, RoundedCornerShape(4.dp))
            .background(pageSurfaceColor, RoundedCornerShape(4.dp))
            .clip(RoundedCornerShape(4.dp))
            .border(1.dp, Color(0xFFE2E8F0), RoundedCornerShape(4.dp))
            .testTag("pdf_page_renderer_${page.pageIndex}")
    ) {
        val widthPx = constraints.maxWidth.toFloat()
        val heightPx = constraints.maxHeight.toFloat()

        // 1. Display Rendered PDF Bitmap
        if (renderedBitmap != null) {
            Image(
                bitmap = renderedBitmap!!.asImageBitmap(),
                contentDescription = "PDF Page ${page.pageIndex + 1}",
                colorFilter = colorFilter,
                modifier = Modifier.fillMaxSize()
            )
        } else if (isRendering) {
            // Skeleton shimmer placeholder
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color(0xFFF8FAFC)),
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    CircularProgressIndicator(
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(28.dp),
                        strokeWidth = 2.5.dp
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = "Rendering page ${page.pageIndex + 1}…",
                        style = MaterialTheme.typography.bodySmall,
                        color = Color(0xFF94A3B8)
                    )
                }
            }
        } else if (renderError != null) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color(0xFFFEF2F2)),
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(
                        Icons.Default.ErrorOutline,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.error
                    )
                    Spacer(modifier = Modifier.height(6.dp))
                    Text(
                        text = "Unable to render page",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error
                    )
                }
            }
        }

        // 2. Vector Annotations Overlay Canvas
        Canvas(modifier = Modifier.fillMaxSize()) {
            val _trigger = strokeUpdateTrigger // re-compose on drag

            // Draw existing saved annotations
            for (annot in annotations) {
                val color = Color(annot.colorArgb)
                when (annot.type) {
                    AnnotationType.INK_PEN -> {
                        if (annot.points.isNotEmpty()) {
                            val path = Path()
                            path.moveTo(annot.points[0].x * size.width, annot.points[0].y * size.height)
                            for (i in 1 until annot.points.size) {
                                path.lineTo(annot.points[i].x * size.width, annot.points[i].y * size.height)
                            }
                            drawPath(
                                path = path,
                                color = color,
                                style = Stroke(
                                    width = annot.strokeWidth,
                                    cap = StrokeCap.Round,
                                    join = StrokeJoin.Round
                                )
                            )
                        }
                    }
                    AnnotationType.HIGHLIGHTER -> {
                        if (annot.points.isNotEmpty()) {
                            val path = Path()
                            path.moveTo(annot.points[0].x * size.width, annot.points[0].y * size.height)
                            for (i in 1 until annot.points.size) {
                                path.lineTo(annot.points[i].x * size.width, annot.points[i].y * size.height)
                            }
                            drawPath(
                                path = path,
                                color = color.copy(alpha = 0.40f),
                                style = Stroke(
                                    width = annot.strokeWidth * 3.5f,
                                    cap = StrokeCap.Square
                                )
                            )
                        }
                    }
                    AnnotationType.RECTANGLE -> {
                        drawRect(
                            color = color,
                            topLeft = Offset(annot.boundsX * size.width, annot.boundsY * size.height),
                            size = androidx.compose.ui.geometry.Size(
                                annot.boundsWidth * size.width,
                                annot.boundsHeight * size.height
                            ),
                            style = Stroke(width = annot.strokeWidth)
                        )
                    }
                    else -> {}
                }
            }

            // Draw live drawing stroke
            if (currentStroke.isNotEmpty()) {
                val path = Path()
                path.moveTo(currentStroke[0].x * size.width, currentStroke[0].y * size.height)
                for (i in 1 until currentStroke.size) {
                    path.lineTo(currentStroke[i].x * size.width, currentStroke[i].y * size.height)
                }
                val liveColor = Color(penColor)
                val alpha = if (activeAnnotType == AnnotationType.HIGHLIGHTER) 0.40f else 1f
                val width = if (activeAnnotType == AnnotationType.HIGHLIGHTER) strokeWidth * 3.5f else strokeWidth
                drawPath(
                    path = path,
                    color = liveColor.copy(alpha = alpha),
                    style = Stroke(
                        width = width,
                        cap = StrokeCap.Round,
                        join = StrokeJoin.Round
                    )
                )
            }
        }

        // 2b. Objects placed in Edit PDF mode (text, images, shapes, whiteout)
        val editLayer = LocalEditLayer.current
        val pageEdits = editLayer?.edits?.get(page.pageIndex).orEmpty()
        if (editLayer != null && pageEdits.isNotEmpty()) {
            val density = LocalDensity.current
            for (edit in pageEdits) {
                val editWidth = (edit.widthRatio * widthPx).coerceAtLeast(24f)
                val editHeight = (edit.heightRatio * heightPx).coerceAtLeast(18f)
                val isSelected = edit.id == editLayer.selectedEditId
                Box(
                    modifier = Modifier
                        .offset {
                            IntOffset(
                                (edit.xRatio * widthPx).roundToInt(),
                                (edit.yRatio * heightPx).roundToInt()
                            )
                        }
                        .size(
                            with(density) { editWidth.toDp() },
                            with(density) { editHeight.toDp() }
                        )
                        .then(
                            if (isSelected) {
                                Modifier.border(1.5.dp, MaterialTheme.colorScheme.primary)
                            } else {
                                Modifier
                            }
                        )
                        .pointerInput(edit.id) {
                            detectDragGestures(
                                onDragStart = { editLayer.onSelect(edit.id) },
                                onDrag = { change, dragAmount ->
                                    change.consume()
                                    editLayer.onMove(
                                        edit.id,
                                        dragAmount.x / widthPx,
                                        dragAmount.y / heightPx
                                    )
                                }
                            )
                        }
                        .clickable { editLayer.onSelect(edit.id) }
                        .testTag("page_edit_${edit.id}")
                ) {
                    when (edit.kind) {
                        EditKind.TEXT -> Text(
                            text = edit.text,
                            color = Color(edit.colorArgb),
                            fontSize = with(density) { (edit.fontSizeRatio * heightPx).toSp() },
                            modifier = Modifier.fillMaxSize()
                        )
                        EditKind.IMAGE -> {
                            val bitmap = edit.image
                            if (bitmap != null) {
                                Image(
                                    bitmap = bitmap.asImageBitmap(),
                                    contentDescription = null,
                                    contentScale = ContentScale.Fit,
                                    modifier = Modifier.fillMaxSize()
                                )
                            }
                        }
                        EditKind.RECTANGLE -> Box(
                            modifier = Modifier
                                .fillMaxSize()
                                .border(2.dp, Color(edit.colorArgb))
                        )
                        EditKind.WHITEOUT -> Box(
                            modifier = Modifier
                                .fillMaxSize()
                                .background(Color.White)
                        )
                    }
                }
            }
        }

        // 3. Annotation Touch Input Interceptor (when in ANNOTATE mode)
        if (workbenchMode == WorkbenchMode.ANNOTATE) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .pointerInput(Unit) {
                        detectDragGestures(
                            onDragStart = { offset ->
                                currentStroke.clear()
                                currentStroke.add(NormalizedPoint(offset.x / widthPx, offset.y / heightPx))
                                strokeUpdateTrigger++
                            },
                            onDrag = { change, _ ->
                                change.consume()
                                currentStroke.add(NormalizedPoint(change.position.x / widthPx, change.position.y / heightPx))
                                strokeUpdateTrigger++
                            },
                            onDragEnd = {
                                if (currentStroke.isNotEmpty()) {
                                    onAddAnnotation(
                                        AnnotationItem(
                                            id = java.util.UUID.randomUUID().toString(),
                                            pageIndex = page.pageIndex,
                                            type = activeAnnotType,
                                            colorArgb = penColor,
                                            strokeWidth = strokeWidth,
                                            points = currentStroke.toList()
                                        )
                                    )
                                    currentStroke.clear()
                                    strokeUpdateTrigger++
                                }
                            }
                        )
                    }
            )
        }

        // 4. Form Fields Interactive Layer (Fill & Sign)
        for (field in formFields) {
            Box(
                modifier = Modifier
                    .offset {
                        IntOffset(
                            (field.xRatio * widthPx).roundToInt(),
                            (field.yRatio * heightPx).roundToInt()
                        )
                    }
                    .size(
                        (field.widthRatio * widthPx).dp.coerceAtLeast(36.dp),
                        (field.heightRatio * heightPx).dp.coerceAtLeast(26.dp)
                    )
            ) {
                when (field.type) {
                    org.rabicraft.app.model.FieldType.CHECKBOX -> {
                        androidx.compose.material3.Checkbox(
                            checked = field.isChecked,
                            onCheckedChange = { onToggleFormFieldChecked(field.id) },
                            colors = androidx.compose.material3.CheckboxDefaults.colors(
                                checkedColor = MaterialTheme.colorScheme.primary
                            ),
                            modifier = Modifier.testTag("checkbox_${field.id}")
                        )
                    }
                    org.rabicraft.app.model.FieldType.TEXT -> {
                        if (workbenchMode == WorkbenchMode.FILL_SIGN) {
                            androidx.compose.material3.OutlinedTextField(
                                value = field.value,
                                onValueChange = { onUpdateFormField(field.id, it) },
                                modifier = Modifier
                                    .fillMaxSize()
                                    .testTag("field_text_${field.id}"),
                                singleLine = true
                            )
                        } else {
                            Text(
                                text = field.value,
                                style = MaterialTheme.typography.bodySmall,
                                color = Color.Black
                            )
                        }
                    }
                    org.rabicraft.app.model.FieldType.SIGNATURE -> {
                        Surface(
                            shape = RoundedCornerShape(4.dp),
                            color = Color(0xFFF1F5F9).copy(alpha = 0.9f),
                            border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFF94A3B8)),
                            modifier = Modifier.fillMaxSize()
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Text(
                                    text = field.value.ifBlank { "Tap to Sign" },
                                    color = Color(0xFF1E3A8A),
                                    fontSize = 15.sp,
                                    fontStyle = androidx.compose.ui.text.font.FontStyle.Italic
                                )
                            }
                        }
                    }
                    else -> {}
                }
            }
        }

        // 5. Threaded Comments Pins
        for (comment in comments) {
            Box(
                modifier = Modifier
                    .offset {
                        IntOffset(
                            (comment.xRatio * widthPx).roundToInt(),
                            (comment.yRatio * heightPx).roundToInt()
                        )
                    }
                    .size(24.dp)
                    .clip(CircleShape)
                    .background(if (comment.isResolved) Color(0xFF16A34A) else MaterialTheme.colorScheme.primary)
                    .clickable { onCommentClick(comment) }
                    .testTag("comment_pin_${comment.id}"),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    Icons.Default.ChatBubble,
                    contentDescription = "Comment by ${comment.author}",
                    tint = Color.White,
                    modifier = Modifier.size(14.dp)
                )
            }
        }
    }
}
