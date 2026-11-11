package org.rabicraft.app.ui.components

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Brightness4
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.ViewCarousel
import androidx.compose.material.icons.filled.ZoomIn
import androidx.compose.material.icons.filled.ZoomOut
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
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
    onShare: () -> Unit
) {
    TopAppBar(
        colors = TopAppBarDefaults.topAppBarColors(
            containerColor = MaterialTheme.colorScheme.surface,
            titleContentColor = MaterialTheme.colorScheme.onSurface
        ),
        title = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Image(
                    painter = painterResource(R.drawable.ic_rabicraft_logo),
                    contentDescription = "RabiCraft Logo",
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
                onClick = onZoomOut,
                modifier = Modifier.testTag("zoom_out_button")
            ) {
                Icon(Icons.Default.ZoomOut, contentDescription = "Zoom Out")
            }

            IconButton(
                onClick = onZoomIn,
                modifier = Modifier.testTag("zoom_in_button")
            ) {
                Icon(Icons.Default.ZoomIn, contentDescription = "Zoom In")
            }

            IconButton(
                onClick = onToggleLayoutMode,
                modifier = Modifier.testTag("layout_mode_button")
            ) {
                Icon(Icons.Default.ViewCarousel, contentDescription = "Layout: $layoutMode")
            }

            IconButton(
                onClick = onCycleReadingTheme,
                modifier = Modifier.testTag("reading_theme_button")
            ) {
                Icon(Icons.Default.Brightness4, contentDescription = "Theme: $readingTheme")
            }

            IconButton(
                onClick = onOpenSearch,
                modifier = Modifier.testTag("search_button")
            ) {
                Icon(Icons.Default.Search, contentDescription = "Search text")
            }

            IconButton(
                onClick = onOpenInspector,
                modifier = Modifier.testTag("inspector_button")
            ) {
                Icon(Icons.Default.Info, contentDescription = "Document Info")
            }

            IconButton(
                onClick = onShare,
                modifier = Modifier.testTag("share_button")
            ) {
                Icon(Icons.Default.Share, contentDescription = "Export & Share")
            }
        }
    )
}
