package org.rabicraft.app.ui.components

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AutoAwesomeMotion
import androidx.compose.material.icons.filled.BorderColor
import androidx.compose.material.icons.filled.Draw
import androidx.compose.material.icons.filled.FolderOpen
import androidx.compose.material.icons.filled.MenuBook
import androidx.compose.material.icons.filled.Security
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import org.rabicraft.app.model.WorkbenchMode

@Composable
fun ToolRail(
    activeMode: WorkbenchMode,
    onSelectMode: (WorkbenchMode) -> Unit
) {
    NavigationBar(
        containerColor = MaterialTheme.colorScheme.surface,
        contentColor = MaterialTheme.colorScheme.onSurface
    ) {
        NavigationBarItem(
            selected = activeMode == WorkbenchMode.LIBRARY,
            onClick = { onSelectMode(WorkbenchMode.LIBRARY) },
            icon = { Icon(Icons.Default.FolderOpen, contentDescription = "Files") },
            label = { Text("Files", fontWeight = FontWeight.Medium) },
            modifier = Modifier.testTag("mode_library"),
            colors = NavigationBarItemDefaults.colors(
                indicatorColor = MaterialTheme.colorScheme.primaryContainer
            )
        )

        NavigationBarItem(
            selected = activeMode == WorkbenchMode.READ,
            onClick = { onSelectMode(WorkbenchMode.READ) },
            icon = { Icon(Icons.Default.MenuBook, contentDescription = "Read") },
            label = { Text("Read", fontWeight = FontWeight.Medium) },
            modifier = Modifier.testTag("mode_read"),
            colors = NavigationBarItemDefaults.colors(
                indicatorColor = MaterialTheme.colorScheme.primaryContainer
            )
        )

        NavigationBarItem(
            selected = activeMode == WorkbenchMode.ORGANIZE,
            onClick = { onSelectMode(WorkbenchMode.ORGANIZE) },
            icon = { Icon(Icons.Default.AutoAwesomeMotion, contentDescription = "Organize") },
            label = { Text("Organize", fontWeight = FontWeight.Medium) },
            modifier = Modifier.testTag("mode_organize"),
            colors = NavigationBarItemDefaults.colors(
                indicatorColor = MaterialTheme.colorScheme.primaryContainer
            )
        )

        NavigationBarItem(
            selected = activeMode == WorkbenchMode.ANNOTATE,
            onClick = { onSelectMode(WorkbenchMode.ANNOTATE) },
            icon = { Icon(Icons.Default.Draw, contentDescription = "Annotate") },
            label = { Text("Annotate", fontWeight = FontWeight.Medium) },
            modifier = Modifier.testTag("mode_annotate"),
            colors = NavigationBarItemDefaults.colors(
                indicatorColor = MaterialTheme.colorScheme.primaryContainer
            )
        )

        NavigationBarItem(
            selected = activeMode == WorkbenchMode.FILL_SIGN,
            onClick = { onSelectMode(WorkbenchMode.FILL_SIGN) },
            icon = { Icon(Icons.Default.BorderColor, contentDescription = "Fill & Sign") },
            label = { Text("Fill & Sign", fontWeight = FontWeight.Medium) },
            modifier = Modifier.testTag("mode_fill_sign"),
            colors = NavigationBarItemDefaults.colors(
                indicatorColor = MaterialTheme.colorScheme.primaryContainer
            )
        )

        NavigationBarItem(
            selected = activeMode == WorkbenchMode.PROTECT,
            onClick = { onSelectMode(WorkbenchMode.PROTECT) },
            icon = { Icon(Icons.Default.Security, contentDescription = "Protect") },
            label = { Text("Protect", fontWeight = FontWeight.Medium) },
            modifier = Modifier.testTag("mode_protect"),
            colors = NavigationBarItemDefaults.colors(
                indicatorColor = MaterialTheme.colorScheme.primaryContainer
            )
        )
    }
}
