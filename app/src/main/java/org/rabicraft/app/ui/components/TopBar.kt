package org.rabicraft.app.ui.components

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Brightness4
import androidx.compose.material.icons.filled.Build
import androidx.compose.material.icons.filled.FolderOpen
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.Translate
import androidx.compose.material.icons.filled.ViewCarousel
import androidx.compose.material.icons.filled.ZoomIn
import androidx.compose.material.icons.filled.ZoomOut
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import org.rabicraft.app.R
import org.rabicraft.app.model.PageLayoutMode
import org.rabicraft.app.model.ReadingTheme
import org.rabicraft.app.ui.i18n.LocalStrings

/**
 * Top bar of the workbench.
 *
 * Only the actions people reach for constantly stay on the bar (open, search, tools, share); zoom,
 * layout, theme, document info and language live in the overflow menu. Earlier every action had its
 * own icon, and on a phone-width screen the last ones — tools and share — were pushed off the bar
 * and became unreachable.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TopBar(
    documentName: String,
    currentPage: Int,
    totalPages: Int,
    zoomLevel: Float,
    layoutMode: PageLayoutMode,
    readingTheme: ReadingTheme,
    onZoomIn: () -> Unit,
    onZoomOut: () -> Unit,
    onToggleLayoutMode: () -> Unit,
    onCycleReadingTheme: () -> Unit,
    onOpenSearch: () -> Unit,
    onOpenInspector: () -> Unit,
    onOpenDocument: () -> Unit,
    onOpenLanguage: () -> Unit,
    onOpenTools: () -> Unit,
    onShare: () -> Unit
) {
    val strings = LocalStrings.current
    var menuOpen by remember { mutableStateOf(false) }

    TopAppBar(
        colors = TopAppBarDefaults.topAppBarColors(
            containerColor = MaterialTheme.colorScheme.surface,
            titleContentColor = MaterialTheme.colorScheme.onSurface
        ),
        title = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Image(
                    painter = painterResource(R.drawable.ic_rabicraft_logo),
                    contentDescription = "FranPdf logo",
                    modifier = Modifier.size(28.dp)
                )
                Spacer(modifier = Modifier.width(10.dp))
                Row(verticalAlignment = Alignment.Bottom) {
                    Text(
                        text = documentName,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    if (totalPages > 0) {
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "(${currentPage + 1}/$totalPages)",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            fontSize = 12.sp
                        )
                    }
                }
            }
        },
        actions = {
            IconButton(
                onClick = onOpenDocument,
                modifier = Modifier.testTag("open_document_button")
            ) {
                Icon(Icons.Default.FolderOpen, contentDescription = "Open PDF")
            }

            IconButton(
                onClick = onOpenSearch,
                modifier = Modifier.testTag("search_button")
            ) {
                Icon(Icons.Default.Search, contentDescription = "Search text")
            }

            IconButton(
                onClick = onOpenTools,
                modifier = Modifier.testTag("tools_button")
            ) {
                Icon(Icons.Default.Build, contentDescription = strings.tools)
            }

            IconButton(
                onClick = onShare,
                modifier = Modifier.testTag("share_button")
            ) {
                Icon(Icons.Default.Share, contentDescription = "Export & Share")
            }

            Box {
                IconButton(
                    onClick = { menuOpen = true },
                    modifier = Modifier.testTag("more_button")
                ) {
                    Icon(Icons.Default.MoreVert, contentDescription = "More actions")
                }

                DropdownMenu(
                    expanded = menuOpen,
                    onDismissRequest = { menuOpen = false }
                ) {
                    DropdownMenuItem(
                        text = { Text("Zoom in") },
                        onClick = { menuOpen = false; onZoomIn() },
                        leadingIcon = { Icon(Icons.Default.ZoomIn, contentDescription = null) },
                        modifier = Modifier.testTag("zoom_in_button")
                    )
                    DropdownMenuItem(
                        text = { Text("Zoom out") },
                        onClick = { menuOpen = false; onZoomOut() },
                        leadingIcon = { Icon(Icons.Default.ZoomOut, contentDescription = null) },
                        modifier = Modifier.testTag("zoom_out_button")
                    )
                    DropdownMenuItem(
                        text = { Text("Layout: ${layoutMode.name.lowercase()}") },
                        onClick = { menuOpen = false; onToggleLayoutMode() },
                        leadingIcon = { Icon(Icons.Default.ViewCarousel, contentDescription = null) },
                        modifier = Modifier.testTag("layout_mode_button")
                    )
                    DropdownMenuItem(
                        text = { Text("Theme: ${readingTheme.name.lowercase()}") },
                        onClick = { menuOpen = false; onCycleReadingTheme() },
                        leadingIcon = { Icon(Icons.Default.Brightness4, contentDescription = null) },
                        modifier = Modifier.testTag("reading_theme_button")
                    )
                    DropdownMenuItem(
                        text = { Text("Document info") },
                        onClick = { menuOpen = false; onOpenInspector() },
                        leadingIcon = { Icon(Icons.Default.Info, contentDescription = null) },
                        modifier = Modifier.testTag("inspector_button")
                    )
                    DropdownMenuItem(
                        text = { Text(strings.language) },
                        onClick = { menuOpen = false; onOpenLanguage() },
                        leadingIcon = { Icon(Icons.Default.Translate, contentDescription = null) },
                        modifier = Modifier.testTag("language_button")
                    )
                }
            }
        }
    )
}
