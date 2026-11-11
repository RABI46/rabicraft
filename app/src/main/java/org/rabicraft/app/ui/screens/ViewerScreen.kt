package org.rabicraft.app.ui.screens

import android.graphics.Bitmap
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
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
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Brush
import androidx.compose.material.icons.filled.ChatBubbleOutline
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.DeleteSweep
import androidx.compose.material.icons.filled.Draw
import androidx.compose.material.icons.filled.FormatColorFill
import androidx.compose.material.icons.filled.Gesture
import androidx.compose.material.icons.filled.Highlight
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.TextFields
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CheckboxDefaults
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.ColorMatrix
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import org.rabicraft.app.model.AnnotationItem
import org.rabicraft.app.model.AnnotationType
import org.rabicraft.app.model.CommentItem
import org.rabicraft.app.model.FormFieldItem
import org.rabicraft.app.model.NormalizedPoint
import org.rabicraft.app.model.PageItem
import org.rabicraft.app.model.PageLayoutMode
import org.rabicraft.app.model.ReadingTheme
import org.rabicraft.app.model.WorkbenchMode
import org.rabicraft.app.ui.theme.SepiaBackground
import org.rabicraft.app.ui.theme.SepiaPage
import java.util.UUID
import kotlin.math.roundToInt

@Composable
fun ViewerScreen(
    pages: List<PageItem>,
    currentPageIndex: Int,
    pageBitmaps: Map<Int, Bitmap>,
    workbenchMode: WorkbenchMode,
    layoutMode: PageLayoutMode,
    readingTheme: ReadingTheme,
    zoomLevel: Float,
    annotations: Map<Int, List<AnnotationItem>>,
    formFields: List<FormFieldItem>,
    comments: List<CommentItem>,
    activeAnnotType: AnnotationType,
    penColor: Long,
    strokeWidth: Float,
    onSelectPage: (Int) -> Unit,
    onAddAnnotation: (Int, AnnotationItem) -> Unit,
    onClearAnnotations: (Int) -> Unit,
    onSelectAnnotType: (AnnotationType) -> Unit,
    onSelectPenColor: (Long) -> Unit,
    onOpenComments: () -> Unit,
    onOpenSignatureDialog: () -> Unit,
    onUpdateFormField: (String, String) -> Unit,
    onToggleFormFieldChecked: (String) -> Unit
) {
    if (pages.isEmpty()) {
        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Text("No document loaded", style = MaterialTheme.typography.titleMedium)
        }
        return
    }

    val activePage = pages.getOrElse(currentPageIndex) { pages.first() }
    val activeBitmap = pageBitmaps[activePage.pageIndex]

    // Pan & Zoom state
    var scale by remember { mutableFloatStateOf(1f) }
    var offsetX by remember { mutableFloatStateOf(0f) }
    var offsetY by remember { mutableFloatStateOf(0f) }

    // Synchronize with external zoom controls
    val currentZoom = zoomLevel * scale

    // In-progress drawing path for annotations
    val currentStroke = remember { mutableStateListOf<NormalizedPoint>() }

    // Color filter for reading themes
    val colorFilter = when (readingTheme) {
        ReadingTheme.DARK -> {
            val cm = ColorMatrix(
                floatArrayOf(
                    -0.85f, 0f, 0f, 0f, 240f,
                    0f, -0.85f, 0f, 0f, 240f,
                    0f, 0f, -0.85f, 0f, 240f,
                    0f, 0f, 0f, 1f, 0f
                )
            )
            ColorFilter.colorMatrix(cm)
        }
        ReadingTheme.SEPIA -> {
            val cm = ColorMatrix().apply {
                setToSaturation(0.6f)
            }
            ColorFilter.colorMatrix(cm)
        }
        ReadingTheme.INVERTED -> {
            val cm = ColorMatrix(
                floatArrayOf(
                    -1f, 0f, 0f, 0f, 255f,
                    0f, -1f, 0f, 0f, 255f,
                    0f, 0f, -1f, 0f, 255f,
                    0f, 0f, 0f, 1f, 0f
                )
            )
            ColorFilter.colorMatrix(cm)
        }
        ReadingTheme.DEFAULT -> null
    }

    val backgroundColor = when (readingTheme) {
        ReadingTheme.SEPIA -> SepiaBackground
        ReadingTheme.DARK -> Color(0xFF131316)
        else -> MaterialTheme.colorScheme.background
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(backgroundColor)
    ) {
        // Annotation Toolbar (Only visible when in ANNOTATE mode)
        if (workbenchMode == WorkbenchMode.ANNOTATE) {
            AnnotationToolbar(
                activeType = activeAnnotType,
                penColor = penColor,
                onSelectType = onSelectAnnotType,
                onSelectColor = onSelectPenColor,
                onClear = { onClearAnnotations(currentPageIndex) }
            )
        }

        // Fill & Sign Action Bar (Only visible when in FILL_SIGN mode)
        if (workbenchMode == WorkbenchMode.FILL_SIGN) {
            FillSignToolbar(
                onOpenSignaturePad = onOpenSignatureDialog
            )
        }

        // Main Document Canvas
        BoxWithConstraints(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
                .pointerInput(workbenchMode) {
                    if (workbenchMode == WorkbenchMode.READ) {
                        detectTransformGestures { _, pan, zoom, _ ->
                            scale = (scale * zoom).coerceIn(0.8f, 3.5f)
                            offsetX += pan.x
                            offsetY += pan.y
                        }
                    }
                },
            contentAlignment = Alignment.Center
        ) {
            val containerWidth = maxWidth
            val containerHeight = maxHeight

            // Two-up layout check
            val showTwoUp = layoutMode == PageLayoutMode.TWO_UP && currentPageIndex + 1 < pages.size

            Row(
                modifier = Modifier
                    .graphicsLayer(
                        scaleX = currentZoom,
                        scaleY = currentZoom,
                        translationX = offsetX,
                        translationY = offsetY
                    )
                    .padding(16.dp),
                horizontalArrangement = Arrangement.Center,
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Page 1
                SinglePageCard(
                    page = activePage,
                    bitmap = activeBitmap,
                    colorFilter = colorFilter,
                    readingTheme = readingTheme,
                    workbenchMode = workbenchMode,
                    activeAnnotType = activeAnnotType,
                    penColor = penColor,
                    strokeWidth = strokeWidth,
                    annotations = annotations[activePage.pageIndex] ?: emptyList(),
                    formFields = formFields.filter { it.pageIndex == activePage.pageIndex },
                    comments = comments.filter { it.pageIndex == activePage.pageIndex },
                    currentStroke = currentStroke,
                    onAddAnnotation = { annot -> onAddAnnotation(activePage.pageIndex, annot) },
                    onUpdateFormField = onUpdateFormField,
                    onToggleFormFieldChecked = onToggleFormFieldChecked,
                    modifier = Modifier
                        .fillMaxWidth(if (showTwoUp) 0.48f else 0.92f)
                        .aspectRatio(595f / 842f)
                )

                // Page 2 if Two-Up mode
                if (showTwoUp) {
                    Spacer(modifier = Modifier.width(12.dp))
                    val secondPage = pages[currentPageIndex + 1]
                    val secondBitmap = pageBitmaps[secondPage.pageIndex]
                    SinglePageCard(
                        page = secondPage,
                        bitmap = secondBitmap,
                        colorFilter = colorFilter,
                        readingTheme = readingTheme,
                        workbenchMode = WorkbenchMode.READ, // primary interactions on left page
                        activeAnnotType = activeAnnotType,
                        penColor = penColor,
                        strokeWidth = strokeWidth,
                        annotations = annotations[secondPage.pageIndex] ?: emptyList(),
                        formFields = formFields.filter { it.pageIndex == secondPage.pageIndex },
                        comments = comments.filter { it.pageIndex == secondPage.pageIndex },
                        currentStroke = remember { mutableStateListOf() },
                        onAddAnnotation = { annot -> onAddAnnotation(secondPage.pageIndex, annot) },
                        onUpdateFormField = onUpdateFormField,
                        onToggleFormFieldChecked = onToggleFormFieldChecked,
                        modifier = Modifier
                            .fillMaxWidth(0.48f)
                            .aspectRatio(595f / 842f)
                    )
                }
            }

            // Floating Comments Button
            Surface(
                shape = CircleShape,
                color = MaterialTheme.colorScheme.primaryContainer,
                shadowElevation = 6.dp,
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .padding(20.dp)
                    .size(48.dp)
                    .clickable { onOpenComments() }
                    .testTag("comments_floating_button")
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        Icons.Default.ChatBubbleOutline,
                        contentDescription = "Comments",
                        tint = MaterialTheme.colorScheme.onPrimaryContainer,
                        modifier = Modifier.size(24.dp)
                    )
                }
            }
        }

        // Bottom Page Thumbnails Carousel
        Surface(
            color = MaterialTheme.colorScheme.surface,
            tonalElevation = 2.dp,
            modifier = Modifier
                .fillMaxWidth()
                .height(68.dp)
        ) {
            LazyRow(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = 12.dp, vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                items(pages, key = { it.pageIndex }) { page ->
                    val isSelected = page.pageIndex == currentPageIndex
                    val bmp = pageBitmaps[page.pageIndex]

                    Surface(
                        shape = RoundedCornerShape(4.dp),
                        color = Color.White,
                        shadowElevation = if (isSelected) 4.dp else 1.dp,
                        modifier = Modifier
                            .height(54.dp)
                            .aspectRatio(595f / 842f)
                            .border(
                                width = if (isSelected) 2.dp else 1.dp,
                                color = if (isSelected) MaterialTheme.colorScheme.primary else Color(0xFFE4E4E7),
                                shape = RoundedCornerShape(4.dp)
                            )
                            .clickable { onSelectPage(page.pageIndex) }
                            .testTag("thumbnail_page_${page.pageIndex}")
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            if (bmp != null) {
                                Image(
                                    bitmap = bmp.asImageBitmap(),
                                    contentDescription = "Thumbnail ${page.pageIndex + 1}",
                                    modifier = Modifier.fillMaxSize()
                                )
                            } else {
                                Text(
                                    text = "${page.pageIndex + 1}",
                                    fontSize = 11.sp,
                                    color = Color.Gray
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun SinglePageCard(
    page: PageItem,
    bitmap: Bitmap?,
    colorFilter: ColorFilter?,
    readingTheme: ReadingTheme,
    workbenchMode: WorkbenchMode,
    activeAnnotType: AnnotationType,
    penColor: Long,
    strokeWidth: Float,
    annotations: List<AnnotationItem>,
    formFields: List<FormFieldItem>,
    comments: List<CommentItem>,
    currentStroke: MutableList<NormalizedPoint>,
    onAddAnnotation: (AnnotationItem) -> Unit,
    onUpdateFormField: (String, String) -> Unit,
    onToggleFormFieldChecked: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    val pageColor = when (readingTheme) {
        ReadingTheme.SEPIA -> SepiaPage
        ReadingTheme.DARK -> Color(0xFF222226)
        else -> Color.White
    }

    BoxWithConstraints(
        modifier = modifier
            .shadow(6.dp, RoundedCornerShape(4.dp))
            .background(pageColor, RoundedCornerShape(4.dp))
            .clip(RoundedCornerShape(4.dp))
    ) {
        val widthPx = constraints.maxWidth.toFloat()
        val heightPx = constraints.maxHeight.toFloat()

        // 1. Rendered PDF Page Bitmap
        if (bitmap != null) {
            Image(
                bitmap = bitmap.asImageBitmap(),
                contentDescription = "Page ${page.pageIndex + 1}",
                colorFilter = colorFilter,
                modifier = Modifier.fillMaxSize()
            )
        } else {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text(
                    text = "Rendering Page ${page.pageIndex + 1}…",
                    color = Color.Gray,
                    style = MaterialTheme.typography.bodyMedium
                )
            }
        }

        // 2. Saved Annotations Canvas
        Canvas(modifier = Modifier.fillMaxSize()) {
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
                                color = color.copy(alpha = 0.45f),
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

            // Current Drawing In Progress
            if (currentStroke.isNotEmpty()) {
                val path = Path()
                path.moveTo(currentStroke[0].x * size.width, currentStroke[0].y * size.height)
                for (i in 1 until currentStroke.size) {
                    path.lineTo(currentStroke[i].x * size.width, currentStroke[i].y * size.height)
                }
                val liveColor = Color(penColor)
                val alpha = if (activeAnnotType == AnnotationType.HIGHLIGHTER) 0.45f else 1f
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

        // 3. Interactive Touch Input for Annotations
        if (workbenchMode == WorkbenchMode.ANNOTATE) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .pointerInput(Unit) {
                        detectDragGestures(
                            onDragStart = { offset ->
                                currentStroke.clear()
                                currentStroke.add(NormalizedPoint(offset.x / widthPx, offset.y / heightPx))
                            },
                            onDrag = { change, _ ->
                                change.consume()
                                currentStroke.add(NormalizedPoint(change.position.x / widthPx, change.position.y / heightPx))
                            },
                            onDragEnd = {
                                if (currentStroke.isNotEmpty()) {
                                    onAddAnnotation(
                                        AnnotationItem(
                                            id = UUID.randomUUID().toString(),
                                            pageIndex = page.pageIndex,
                                            type = activeAnnotType,
                                            colorArgb = penColor,
                                            strokeWidth = strokeWidth,
                                            points = currentStroke.toList()
                                        )
                                    )
                                    currentStroke.clear()
                                }
                            }
                        )
                    }
            )
        }

        // 4. Form Fields Overlay (Fill & Sign)
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
                        (field.widthRatio * widthPx).dp.coerceAtLeast(40.dp),
                        (field.heightRatio * heightPx).dp.coerceAtLeast(30.dp)
                    )
            ) {
                when (field.type) {
                    org.rabicraft.app.model.FieldType.CHECKBOX -> {
                        Checkbox(
                            checked = field.isChecked,
                            onCheckedChange = { onToggleFormFieldChecked(field.id) },
                            colors = CheckboxDefaults.colors(
                                checkedColor = MaterialTheme.colorScheme.primary
                            ),
                            modifier = Modifier.testTag("checkbox_${field.id}")
                        )
                    }
                    org.rabicraft.app.model.FieldType.TEXT -> {
                        if (workbenchMode == WorkbenchMode.FILL_SIGN) {
                            OutlinedTextField(
                                value = field.value,
                                onValueChange = { onUpdateFormField(field.id, it) },
                                modifier = Modifier
                                    .fillMaxSize()
                                    .testTag("input_${field.id}"),
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
                            color = Color(0xFFF1F5F9).copy(alpha = 0.85f),
                            border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFF94A3B8)),
                            modifier = Modifier.fillMaxSize()
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Text(
                                    text = field.value.ifBlank { "Click to Sign" },
                                    color = Color(0xFF1E3A8A),
                                    fontSize = 16.sp,
                                    fontStyle = androidx.compose.ui.text.font.FontStyle.Italic
                                )
                            }
                        }
                    }
                    else -> {}
                }
            }
        }
    }
}

@Composable
private fun AnnotationToolbar(
    activeType: AnnotationType,
    penColor: Long,
    onSelectType: (AnnotationType) -> Unit,
    onSelectColor: (Long) -> Unit,
    onClear: () -> Unit
) {
    Surface(
        color = MaterialTheme.colorScheme.surfaceVariant,
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(
                onClick = { onSelectType(AnnotationType.INK_PEN) },
                modifier = Modifier
                    .background(
                        if (activeType == AnnotationType.INK_PEN) MaterialTheme.colorScheme.primary.copy(alpha = 0.2f) else Color.Transparent,
                        CircleShape
                    )
                    .testTag("tool_pen")
            ) {
                Icon(Icons.Default.Draw, contentDescription = "Pen", tint = MaterialTheme.colorScheme.primary)
            }

            IconButton(
                onClick = { onSelectType(AnnotationType.HIGHLIGHTER) },
                modifier = Modifier
                    .background(
                        if (activeType == AnnotationType.HIGHLIGHTER) MaterialTheme.colorScheme.primary.copy(alpha = 0.2f) else Color.Transparent,
                        CircleShape
                    )
                    .testTag("tool_highlighter")
            ) {
                Icon(Icons.Default.Highlight, contentDescription = "Highlighter", tint = Color(0xFFD97706))
            }

            Spacer(modifier = Modifier.width(12.dp))

            // Swatches
            val colors = listOf(
                0xFF8A011EL, // Wine
                0xFF1E3A8AL, // Blue
                0xFF059669L, // Green
                0xFFD97706L, // Gold/Orange
                0xFF18181BL  // Black
            )

            for (c in colors) {
                Box(
                    modifier = Modifier
                        .padding(horizontal = 4.dp)
                        .size(24.dp)
                        .clip(CircleShape)
                        .background(Color(c))
                        .border(
                            width = if (penColor == c) 2.dp else 0.dp,
                            color = MaterialTheme.colorScheme.onSurface,
                            shape = CircleShape
                        )
                        .clickable { onSelectColor(c) }
                )
            }

            Spacer(modifier = Modifier.weight(1f))

            IconButton(
                onClick = onClear,
                modifier = Modifier.testTag("clear_annotations_button")
            ) {
                Icon(Icons.Default.DeleteSweep, contentDescription = "Clear ink", tint = MaterialTheme.colorScheme.error)
            }
        }
    }
}

@Composable
private fun FillSignToolbar(
    onOpenSignaturePad: () -> Unit
) {
    Surface(
        color = MaterialTheme.colorScheme.surfaceVariant,
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "Fill form fields directly or insert signature:",
                style = MaterialTheme.typography.bodySmall,
                modifier = Modifier.weight(1f)
            )
            FilledTonalButton(
                onClick = onOpenSignaturePad,
                modifier = Modifier.testTag("create_signature_button")
            ) {
                Icon(Icons.Default.Gesture, contentDescription = null, modifier = Modifier.size(16.dp))
                Spacer(modifier = Modifier.width(6.dp))
                Text("Add Signature")
            }
        }
    }
}
