package org.rabicraft.app.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.BrandingWatermark
import androidx.compose.material.icons.automirrored.filled.CallSplit
import androidx.compose.material.icons.automirrored.filled.MenuBook
import androidx.compose.material.icons.automirrored.filled.MergeType
import androidx.compose.material.icons.filled.Build
import androidx.compose.material.icons.filled.Compress
import androidx.compose.material.icons.filled.ContentCut
import androidx.compose.material.icons.filled.Crop
import androidx.compose.material.icons.filled.DeleteSweep
import androidx.compose.material.icons.filled.DocumentScanner
import androidx.compose.material.icons.filled.FormatListNumbered
import androidx.compose.material.icons.filled.GridView
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.Palette
import androidx.compose.material.icons.filled.ViewHeadline
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import org.rabicraft.app.model.BlankPageSettings
import org.rabicraft.app.model.ColourEffect
import org.rabicraft.app.model.CompressSettings
import org.rabicraft.app.model.CropSettings
import org.rabicraft.app.model.DocumentTool
import org.rabicraft.app.model.HeaderFooterSettings
import org.rabicraft.app.model.ImageExportSettings
import org.rabicraft.app.model.ImageFormat
import org.rabicraft.app.model.NUpSettings
import org.rabicraft.app.model.PageNumberSettings
import org.rabicraft.app.model.SplitSettings
import org.rabicraft.app.model.TextAnchor
import org.rabicraft.app.model.ToolConfig
import org.rabicraft.app.model.ToolText
import org.rabicraft.app.model.WatermarkSettings
import org.rabicraft.app.ui.i18n.AppStrings
import org.rabicraft.app.ui.i18n.LocalStrings

private data class ToolEntry(
    val tool: DocumentTool,
    val icon: ImageVector,
    val title: (AppStrings) -> String,
    val hint: (AppStrings) -> String
)

private val tools = listOf(
    ToolEntry(DocumentTool.PAGE_NUMBERS, Icons.Default.FormatListNumbered, { it.pageNumbers }, { it.pageNumbersHint }),
    ToolEntry(DocumentTool.WATERMARK, Icons.AutoMirrored.Filled.BrandingWatermark, { it.watermark }, { it.watermarkHint }),
    ToolEntry(DocumentTool.HEADER_FOOTER, Icons.Default.ViewHeadline, { it.headerFooter }, { it.headerFooterHint }),
    ToolEntry(DocumentTool.N_UP, Icons.Default.GridView, { it.nUp }, { it.nUpHint }),
    ToolEntry(DocumentTool.BOOKLET, Icons.AutoMirrored.Filled.MenuBook, { it.booklet }, { it.bookletHint }),
    ToolEntry(DocumentTool.CROP, Icons.Default.Crop, { it.crop }, { it.cropHint }),
    ToolEntry(DocumentTool.COLOUR, Icons.Default.Palette, { it.colours }, { it.coloursHint }),
    ToolEntry(DocumentTool.COMPRESS, Icons.Default.Compress, { it.compress }, { it.compressHint }),
    ToolEntry(DocumentTool.BLANK_PAGES, Icons.Default.DeleteSweep, { it.blankPages }, { it.blankPagesHint }),
    ToolEntry(DocumentTool.SPLIT, Icons.AutoMirrored.Filled.CallSplit, { it.split }, { it.splitHint }),
    ToolEntry(DocumentTool.EXTRACT, Icons.Default.ContentCut, { it.extract }, { it.extractHint }),
    ToolEntry(DocumentTool.TO_IMAGES, Icons.Default.Image, { it.toImages }, { it.toImagesHint }),
    ToolEntry(DocumentTool.OCR, Icons.Default.DocumentScanner, { it.ocr }, { it.ocrHint }),
    ToolEntry(DocumentTool.MERGE, Icons.AutoMirrored.Filled.MergeType, { it.merge }, { it.mergeHint })
)

/**
 * The Tools hub. Each entry maps to a real export-path transformation (see `ToolPlanner`), never a
 * preview-only effect, so what the user applies is what lands in the produced file.
 */
@Composable
fun ToolsScreen(
    totalPages: Int,
    isBusy: Boolean,
    onClose: () -> Unit,
    onApply: (ToolConfig) -> Unit,
    onPickMergeFiles: () -> Unit
) {
    val strings = LocalStrings.current
    var activeTool by remember { mutableStateOf<DocumentTool?>(null) }

    Surface(color = MaterialTheme.colorScheme.background, modifier = Modifier.fillMaxSize()) {
        Column(modifier = Modifier.fillMaxSize()) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 8.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(onClick = onClose, modifier = Modifier.testTag("tools_close")) {
                    Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = strings.close)
                }
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = strings.toolsTitle,
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = strings.toolsSubtitle,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Icon(
                    Icons.Default.Build,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary
                )
            }

            Text(
                text = strings.pagesCount.format(totalPages),
                modifier = Modifier.padding(horizontal = 16.dp),
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            LazyVerticalGrid(
                columns = GridCells.Adaptive(minSize = 160.dp),
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                modifier = Modifier.fillMaxSize()
            ) {
                items(tools) { entry ->
                    Card(
                        shape = RoundedCornerShape(14.dp),
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.surface
                        ),
                        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable(enabled = !isBusy) {
                                when (entry.tool) {
                                    DocumentTool.MERGE -> onPickMergeFiles()
                                    DocumentTool.BOOKLET -> onApply(ToolConfig.Booklet)
                                    else -> activeTool = entry.tool
                                }
                            }
                            .testTag("tool_${entry.tool.name.lowercase()}")
                    ) {
                        Column(modifier = Modifier.padding(14.dp)) {
                            Icon(
                                entry.icon,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(26.dp)
                            )
                            Spacer(modifier = Modifier.height(8.dp))
                            Text(
                                text = entry.title(strings),
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.SemiBold
                            )
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                text = entry.hint(strings),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                fontSize = 11.sp
                            )
                        }
                    }
                }
            }
        }
    }

    activeTool?.let { tool ->
        ToolConfigDialog(
            tool = tool,
            totalPages = totalPages,
            onDismiss = { activeTool = null },
            onApply = { config ->
                activeTool = null
                onApply(config)
            }
        )
    }
}

@Composable
private fun ToolConfigDialog(
    tool: DocumentTool,
    totalPages: Int,
    onDismiss: () -> Unit,
    onApply: (ToolConfig) -> Unit
) {
    val strings = LocalStrings.current
    val entry = tools.first { it.tool == tool }

    var text by remember(tool) { mutableStateOf("Page {page} / {total}") }
    var watermarkText by remember(tool) { mutableStateOf("CONFIDENTIEL") }
    var headerText by remember(tool) { mutableStateOf("") }
    var footerText by remember(tool) { mutableStateOf("Page {page} / {total}") }
    var startNumber by remember(tool) { mutableIntStateOf(1) }
    var fontSize by remember(tool) { mutableFloatStateOf(12f) }
    var watermarkSize by remember(tool) { mutableFloatStateOf(64f) }
    var opacity by remember(tool) { mutableFloatStateOf(0.18f) }
    var diagonal by remember(tool) { mutableStateOf(true) }
    var anchor by remember(tool) { mutableStateOf(TextAnchor.BOTTOM_CENTRE) }
    var columns by remember(tool) { mutableIntStateOf(2) }
    var rows by remember(tool) { mutableIntStateOf(1) }
    var landscape by remember(tool) { mutableStateOf(false) }
    var borders by remember(tool) { mutableStateOf(false) }
    var cropTop by remember(tool) { mutableFloatStateOf(0f) }
    var cropBottom by remember(tool) { mutableFloatStateOf(0f) }
    var cropLeft by remember(tool) { mutableFloatStateOf(0f) }
    var cropRight by remember(tool) { mutableFloatStateOf(0f) }
    var effect by remember(tool) { mutableStateOf(ColourEffect.GRAYSCALE) }
    var quality by remember(tool) { mutableFloatStateOf(70f) }
    var dpi by remember(tool) { mutableIntStateOf(150) }
    var threshold by remember(tool) { mutableFloatStateOf(0.004f) }
    var pagesPerFile by remember(tool) { mutableIntStateOf(1) }
    var splitRanges by remember(tool) { mutableStateOf("") }
    var extractRanges by remember(tool) { mutableStateOf("1-" + totalPages.coerceAtLeast(1)) }
    var pagesSpec by remember(tool) { mutableStateOf("") }
    var format by remember(tool) { mutableStateOf(ImageFormat.PNG) }

    Dialog(onDismissRequest = onDismiss) {
        Surface(
            shape = RoundedCornerShape(16.dp),
            color = MaterialTheme.colorScheme.surface,
            tonalElevation = 6.dp,
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = 560.dp)
                    .verticalScroll(rememberScrollState())
                    .padding(20.dp)
            ) {
                Text(
                    text = entry.title(strings),
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = entry.hint(strings),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(modifier = Modifier.height(16.dp))

                when (tool) {
                    DocumentTool.PAGE_NUMBERS -> {
                        OutlinedTextField(
                            value = text,
                            onValueChange = { text = it },
                            label = { Text(strings.template) },
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("tool_page_numbers_template"),
                            singleLine = true
                        )
                        Spacer(modifier = Modifier.height(10.dp))
                        OutlinedTextField(
                            value = startNumber.toString(),
                            onValueChange = { startNumber = it.toIntOrNull()?.coerceAtLeast(0) ?: startNumber },
                            label = { Text(strings.startNumber) },
                            modifier = Modifier.fillMaxWidth(),
                            singleLine = true
                        )
                        Spacer(modifier = Modifier.height(10.dp))
                        Label(strings.position)
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            Chip(strings.bottomCentre, anchor == TextAnchor.BOTTOM_CENTRE) {
                                anchor = TextAnchor.BOTTOM_CENTRE
                            }
                            Chip(strings.topRight, anchor == TextAnchor.TOP_RIGHT) {
                                anchor = TextAnchor.TOP_RIGHT
                            }
                        }
                        Spacer(modifier = Modifier.height(10.dp))
                        SliderRow(strings.fontSize, fontSize, 8f, 28f) { fontSize = it }
                        SelectionField(strings, pagesSpec) { pagesSpec = it }
                    }

                    DocumentTool.WATERMARK -> {
                        OutlinedTextField(
                            value = watermarkText,
                            onValueChange = { watermarkText = it },
                            label = { Text(strings.template) },
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("tool_watermark_text"),
                            singleLine = true
                        )
                        Spacer(modifier = Modifier.height(10.dp))
                        SliderRow(strings.fontSize, watermarkSize, 24f, 140f) { watermarkSize = it }
                        SliderRow(strings.opacity, opacity * 100f, 5f, 80f) { opacity = it / 100f }
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(strings.diagonal, modifier = Modifier.weight(1f))
                            Switch(checked = diagonal, onCheckedChange = { diagonal = it })
                        }
                        SelectionField(strings, pagesSpec) { pagesSpec = it }
                    }

                    DocumentTool.HEADER_FOOTER -> {
                        OutlinedTextField(
                            value = headerText,
                            onValueChange = { headerText = it },
                            label = { Text(strings.headerText) },
                            modifier = Modifier.fillMaxWidth(),
                            singleLine = true
                        )
                        Spacer(modifier = Modifier.height(10.dp))
                        OutlinedTextField(
                            value = footerText,
                            onValueChange = { footerText = it },
                            label = { Text(strings.footerText) },
                            modifier = Modifier.fillMaxWidth(),
                            singleLine = true
                        )
                        Spacer(modifier = Modifier.height(10.dp))
                        Text(
                            "{page} · {total} · {title} · {date}",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Spacer(modifier = Modifier.height(6.dp))
                        SliderRow(strings.fontSize, fontSize, 8f, 20f) { fontSize = it }
                        SelectionField(strings, pagesSpec) { pagesSpec = it }
                    }

                    DocumentTool.N_UP -> {
                        Text(strings.pagesPerSheet, fontWeight = FontWeight.Medium)
                        Spacer(modifier = Modifier.height(6.dp))
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            listOf(2 to (2 to 1), 4 to (2 to 2), 6 to (2 to 3), 9 to (3 to 3))
                                .forEach { (count, grid) ->
                                    Chip(count.toString(), columns == grid.first && rows == grid.second) {
                                        columns = grid.first
                                        rows = grid.second
                                    }
                                }
                        }
                        Spacer(modifier = Modifier.height(8.dp))
                        ToggleRow(strings.landscape, landscape) { landscape = it }
                        ToggleRow(strings.borders, borders) { borders = it }
                    }

                    DocumentTool.CROP -> {
                        SliderRow(strings.top, cropTop * 100f, 0f, 25f) { cropTop = it / 100f }
                        SliderRow(strings.bottom, cropBottom * 100f, 0f, 25f) { cropBottom = it / 100f }
                        SliderRow(strings.left, cropLeft * 100f, 0f, 25f) { cropLeft = it / 100f }
                        SliderRow(strings.right, cropRight * 100f, 0f, 25f) { cropRight = it / 100f }
                        SelectionField(strings, pagesSpec) { pagesSpec = it }
                    }

                    DocumentTool.COLOUR -> {
                        Text(strings.colours, fontWeight = FontWeight.Medium)
                        Spacer(modifier = Modifier.height(6.dp))
                        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            Chip(strings.invert, effect == ColourEffect.INVERT) { effect = ColourEffect.INVERT }
                            Chip(strings.grayscale, effect == ColourEffect.GRAYSCALE) { effect = ColourEffect.GRAYSCALE }
                            Chip(strings.highContrast, effect == ColourEffect.HIGH_CONTRAST) {
                                effect = ColourEffect.HIGH_CONTRAST
                            }
                        }
                        SelectionField(strings, pagesSpec) { pagesSpec = it }
                    }

                    DocumentTool.COMPRESS -> {
                        SliderRow(strings.quality, quality, 30f, 95f) { quality = it }
                        Text(strings.resolution, fontWeight = FontWeight.Medium)
                        Spacer(modifier = Modifier.height(6.dp))
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            listOf(96, 150, 200).forEach { value ->
                                Chip("$value dpi", dpi == value) { dpi = value }
                            }
                        }
                        SelectionField(strings, pagesSpec) { pagesSpec = it }
                    }

                    DocumentTool.BLANK_PAGES -> {
                        Text(strings.sensitivity, fontWeight = FontWeight.Medium)
                        Spacer(modifier = Modifier.height(6.dp))
                        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            Chip(strings.low, threshold == 0.001f) { threshold = 0.001f }
                            Chip(strings.medium, threshold == 0.004f) { threshold = 0.004f }
                            Chip(strings.high, threshold == 0.02f) { threshold = 0.02f }
                        }
                        Spacer(modifier = Modifier.height(6.dp))
                        Text(
                            if (threshold == 0.001f) "≤ 0,1 %" else if (threshold == 0.02f) "≤ 2 %" else "≤ 0,4 %",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }

                    DocumentTool.SPLIT -> {
                        Text(strings.pagesPerFile, fontWeight = FontWeight.Medium)
                        Spacer(modifier = Modifier.height(6.dp))
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            listOf(1, 2, 5, 10).forEach { value ->
                                Chip(value.toString(), pagesPerFile == value && splitRanges.isBlank()) {
                                    pagesPerFile = value
                                    splitRanges = ""
                                }
                            }
                        }
                        Spacer(modifier = Modifier.height(10.dp))
                        OutlinedTextField(
                            value = splitRanges,
                            onValueChange = { splitRanges = it },
                            label = { Text(strings.splitRanges) },
                            placeholder = { Text(strings.rangeHint) },
                            modifier = Modifier.fillMaxWidth(),
                            singleLine = true
                        )
                        Spacer(modifier = Modifier.height(6.dp))
                        Text(
                            strings.pagesCount.format(totalPages),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }

                    DocumentTool.EXTRACT -> {
                        OutlinedTextField(
                            value = extractRanges,
                            onValueChange = { extractRanges = it },
                            label = { Text(strings.pages) },
                            placeholder = { Text(strings.rangeHint) },
                            modifier = Modifier.fillMaxWidth(),
                            singleLine = true
                        )
                        Spacer(modifier = Modifier.height(6.dp))
                        Text(
                            strings.pagesHint,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }

                    DocumentTool.TO_IMAGES -> {
                        Text(strings.imageFormat, fontWeight = FontWeight.Medium)
                        Spacer(modifier = Modifier.height(6.dp))
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            Chip("PNG", format == ImageFormat.PNG) { format = ImageFormat.PNG }
                            Chip("JPEG", format == ImageFormat.JPG) { format = ImageFormat.JPG }
                        }
                    }

                    DocumentTool.OCR -> {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                Icons.Default.DocumentScanner,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary
                            )
                            Spacer(modifier = Modifier.width(10.dp))
                            Column {
                                Text(strings.ocrModel, fontWeight = FontWeight.Medium)
                                Text(
                                    strings.ocrEmbedded,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                        Spacer(modifier = Modifier.height(10.dp))
                        Text(
                            strings.ocrOfflineNote,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        SelectionField(strings, pagesSpec) { pagesSpec = it }
                    }

                    DocumentTool.BOOKLET, DocumentTool.MERGE -> Unit
                }

                Spacer(modifier = Modifier.height(20.dp))

                Row(modifier = Modifier.fillMaxWidth()) {
                    OutlinedButton(onClick = onDismiss, modifier = Modifier.weight(1f)) {
                        Text(strings.cancel)
                    }
                    Spacer(modifier = Modifier.width(10.dp))
                    Button(
                        onClick = {
                            onApply(
                                when (tool) {
                                    DocumentTool.PAGE_NUMBERS -> ToolConfig.PageNumbers(
                                        PageNumberSettings(
                                            text = ToolText(
                                                template = text,
                                                anchor = anchor,
                                                fontSizePt = fontSize
                                            ),
                                            startNumber = startNumber,
                                            selection = pagesSpec
                                        )
                                    )
                                    DocumentTool.WATERMARK -> ToolConfig.Watermark(
                                        WatermarkSettings(
                                            text = watermarkText,
                                            fontSizePt = watermarkSize,
                                            opacity = opacity,
                                            diagonal = diagonal,
                                            selection = pagesSpec
                                        )
                                    )
                                    DocumentTool.HEADER_FOOTER -> ToolConfig.HeaderFooter(
                                        HeaderFooterSettings(
                                            header = ToolText(
                                                template = headerText,
                                                anchor = TextAnchor.TOP_CENTRE,
                                                fontSizePt = fontSize
                                            ),
                                            footer = ToolText(
                                                template = footerText,
                                                anchor = TextAnchor.BOTTOM_CENTRE,
                                                fontSizePt = fontSize
                                            ),
                                            selection = pagesSpec
                                        )
                                    )
                                    DocumentTool.N_UP -> ToolConfig.NUp(
                                        NUpSettings(
                                            columns = columns,
                                            rows = rows,
                                            landscape = landscape,
                                            borders = borders
                                        )
                                    )
                                    DocumentTool.CROP -> ToolConfig.Crop(
                                        CropSettings(
                                            topPct = cropTop,
                                            bottomPct = cropBottom,
                                            leftPct = cropLeft,
                                            rightPct = cropRight
                                        ),
                                        selection = pagesSpec
                                    )
                                    DocumentTool.COLOUR -> ToolConfig.Colour(effect, pagesSpec)
                                    DocumentTool.COMPRESS -> ToolConfig.Compress(
                                        CompressSettings(
                                            maxDpi = dpi,
                                            jpegQuality = quality.toInt(),
                                            selection = pagesSpec
                                        )
                                    )
                                    DocumentTool.BLANK_PAGES -> ToolConfig.BlankPages(
                                        BlankPageSettings(inkThreshold = threshold)
                                    )
                                    DocumentTool.SPLIT -> ToolConfig.Split(
                                        SplitSettings(
                                            pagesPerFile = pagesPerFile,
                                            ranges = splitRanges
                                        )
                                    )
                                    DocumentTool.EXTRACT -> ToolConfig.Extract(extractRanges)
                                    DocumentTool.TO_IMAGES -> ToolConfig.ToImages(
                                        ImageExportSettings(
                                            format = format,
                                            jpegQuality = 90
                                        )
                                    )
                                    DocumentTool.OCR -> ToolConfig.Ocr(pagesSpec)
                                    DocumentTool.BOOKLET -> ToolConfig.Booklet
                                    // Merge is launched straight from its card: the user picks files
                                    // in the system picker, so this dialog never opens for it.
                                    DocumentTool.MERGE -> ToolConfig.Booklet
                                }
                            )
                        },
                        modifier = Modifier
                            .weight(1f)
                            .testTag("tool_apply")
                    ) {
                        Text(strings.apply)
                    }
                }
            }
        }
    }
}

@Composable
private fun Label(value: String) {
    Text(value, fontWeight = FontWeight.Medium)
}

@Composable
private fun Chip(label: String, selected: Boolean, onClick: () -> Unit) {
    FilterChip(selected = selected, onClick = onClick, label = { Text(label) })
}

@Composable
private fun ToggleRow(label: String, checked: Boolean, onChange: (Boolean) -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(label, modifier = Modifier.weight(1f))
        Switch(checked = checked, onCheckedChange = onChange)
    }
}

@Composable
private fun SelectionField(strings: AppStrings, value: String, onChange: (String) -> Unit) {
    Spacer(modifier = Modifier.height(10.dp))
    OutlinedTextField(
        value = value,
        onValueChange = onChange,
        label = { Text(strings.pages) },
        placeholder = { Text(strings.rangeHint) },
        supportingText = { Text(strings.pagesHint, fontSize = 11.sp) },
        modifier = Modifier
            .fillMaxWidth()
            .testTag("tool_pages_selection"),
        singleLine = true
    )
}

@Composable
private fun SliderRow(
    label: String,
    value: Float,
    min: Float,
    max: Float,
    onChange: (Float) -> Unit
) {
    Column(modifier = Modifier.fillMaxWidth()) {
        Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Text(label, modifier = Modifier.weight(1f), fontWeight = FontWeight.Medium)
            Text(
                if (max <= 30f) "%.1f".format(value) else value.toInt().toString(),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        Slider(
            value = value.coerceIn(min, max),
            onValueChange = onChange,
            valueRange = min..max
        )
    }
}
