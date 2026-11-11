package org.rabicraft.app.ui.components

import androidx.compose.foundation.layout.RowScope
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AutoAwesomeMotion
import androidx.compose.material.icons.filled.BorderColor
import androidx.compose.material.icons.filled.Draw
import androidx.compose.material.icons.filled.Edit
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
import androidx.compose.ui.unit.sp
import org.rabicraft.app.model.WorkbenchMode
import org.rabicraft.app.ui.i18n.LocalStrings

@Composable
fun ToolRail(
    activeMode: WorkbenchMode,
    onSelectMode: (WorkbenchMode) -> Unit
) {
    val strings = LocalStrings.current
    NavigationBar(
        containerColor = MaterialTheme.colorScheme.surface,
        contentColor = MaterialTheme.colorScheme.onSurface
    ) {
        RailItem(
            mode = WorkbenchMode.LIBRARY,
            activeMode = activeMode,
            icon = { Icon(Icons.Default.FolderOpen, contentDescription = strings.files) },
            label = strings.files,
            tag = "mode_library",
            onSelectMode = onSelectMode
        )
        RailItem(
            mode = WorkbenchMode.READ,
            activeMode = activeMode,
            icon = { Icon(Icons.Default.MenuBook, contentDescription = strings.read) },
            label = strings.read,
            tag = "mode_read",
            onSelectMode = onSelectMode
        )
        RailItem(
            mode = WorkbenchMode.ORGANIZE,
            activeMode = activeMode,
            icon = { Icon(Icons.Default.AutoAwesomeMotion, contentDescription = strings.organize) },
            label = strings.organize,
            tag = "mode_organize",
            onSelectMode = onSelectMode
        )
        RailItem(
            mode = WorkbenchMode.ANNOTATE,
            activeMode = activeMode,
            icon = { Icon(Icons.Default.Draw, contentDescription = strings.annotate) },
            label = strings.annotate,
            tag = "mode_annotate",
            onSelectMode = onSelectMode
        )
        RailItem(
            mode = WorkbenchMode.EDIT,
            activeMode = activeMode,
            icon = { Icon(Icons.Default.Edit, contentDescription = strings.edit) },
            label = strings.edit,
            tag = "mode_edit",
            onSelectMode = onSelectMode
        )
        RailItem(
            mode = WorkbenchMode.FILL_SIGN,
            activeMode = activeMode,
            icon = { Icon(Icons.Default.BorderColor, contentDescription = strings.fillSign) },
            label = strings.fillSign,
            tag = "mode_fill_sign",
            onSelectMode = onSelectMode
        )
        RailItem(
            mode = WorkbenchMode.PROTECT,
            activeMode = activeMode,
            icon = { Icon(Icons.Default.Security, contentDescription = strings.protect) },
            label = strings.protect,
            tag = "mode_protect",
            onSelectMode = onSelectMode
        )
    }
}

@Composable
private fun RowScope.RailItem(
    mode: WorkbenchMode,
    activeMode: WorkbenchMode,
    icon: @Composable () -> Unit,
    label: String,
    tag: String,
    onSelectMode: (WorkbenchMode) -> Unit
) {
    NavigationBarItem(
        selected = activeMode == mode,
        onClick = { onSelectMode(mode) },
        icon = icon,
        label = { Text(label, fontWeight = FontWeight.Medium, fontSize = 10.sp) },
        modifier = Modifier.testTag(tag),
        colors = NavigationBarItemDefaults.colors(
            indicatorColor = MaterialTheme.colorScheme.primaryContainer
        )
    )
}
