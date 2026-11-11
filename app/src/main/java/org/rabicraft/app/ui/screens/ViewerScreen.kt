package org.rabicraft.app.ui.screens

import android.graphics.Bitmap
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ChatBubbleOutline
import androidx.compose.material.icons.filled.DeleteSweep
import androidx.compose.material.icons.filled.Draw
import androidx.compose.material.icons.filled.Gesture
import androidx.compose.material.icons.filled.Highlight
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.ColorMatrix
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import org.rabicraft.app.model.AnnotationItem
import org.rabicraft.app.model.AnnotationType
import org.rabicraft.app.model.CommentItem
import org.rabicraft.app.model.FormFieldItem
import org.rabicraft.app.model.PageItem
import org.rabicraft.app.model.PageLayoutMode
import org.rabicraft.app.model.ReadingTheme
import org.rabicraft.app.model.WorkbenchMode
import org.rabicraft.app.ui.components.PdfRendererComponent
import org.rabicraft.app.ui.theme.SepiaBackground
import java.io.File

@Composable
fun ViewerScreen(
    pdfFile: File?,
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

        // Main PDF Canvas using the dedicated PdfRendererComponent
        Box(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
        ) {
            PdfRendererComponent(
                pdfFile = pdfFile,
                pages = pages,
                currentPageIndex = currentPageIndex,
                layoutMode = layoutMode,
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
                onPageChanged = onSelectPage,
                onAddAnnotation = onAddAnnotation,
                onUpdateFormField = onUpdateFormField,
                onToggleFormFieldChecked = onToggleFormFieldChecked,
                onCommentClick = { onOpenComments() },
                modifier = Modifier.fillMaxSize()
            )

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

        // Bottom Page Thumbnails Carousel for rapid jumping
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
