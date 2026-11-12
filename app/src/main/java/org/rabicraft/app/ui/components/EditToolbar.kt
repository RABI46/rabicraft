package org.rabicraft.app.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AddPhotoAlternate
import androidx.compose.material.icons.filled.CheckBoxOutlineBlank
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.DeleteSweep
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.FormatColorReset
import androidx.compose.material.icons.filled.TextFields
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import org.rabicraft.app.ui.i18n.LocalStrings

/** Actions offered by the Edit PDF menu. */
enum class EditAction {
    ADD_TEXT,
    EDIT_TEXT,
    ADD_IMAGE,
    ADD_SHAPE,
    WHITEOUT,
    DELETE_SELECTED,
    CLEAR_PAGE
}

@Composable
fun EditToolbar(
    hasSelection: Boolean,
    onAction: (EditAction) -> Unit
) {
    val strings = LocalStrings.current
    Surface(
        color = MaterialTheme.colorScheme.surfaceVariant,
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState()),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(2.dp)
            ) {
                EditButton(Icons.Default.TextFields, strings.addText, "edit_add_text") {
                    onAction(EditAction.ADD_TEXT)
                }
                EditButton(Icons.Default.Edit, strings.editText, "edit_edit_text") {
                    onAction(EditAction.EDIT_TEXT)
                }
                EditButton(Icons.Default.AddPhotoAlternate, strings.addImage, "edit_add_image") {
                    onAction(EditAction.ADD_IMAGE)
                }
                EditButton(Icons.Default.CheckBoxOutlineBlank, strings.addShape, "edit_add_shape") {
                    onAction(EditAction.ADD_SHAPE)
                }
                EditButton(Icons.Default.FormatColorReset, strings.whiteout, "edit_whiteout") {
                    onAction(EditAction.WHITEOUT)
                }
                EditButton(
                    Icons.Default.Delete,
                    strings.deleteSelected,
                    "edit_delete_selected",
                    enabled = hasSelection
                ) {
                    onAction(EditAction.DELETE_SELECTED)
                }
                EditButton(Icons.Default.DeleteSweep, strings.clearPage, "edit_clear_page") {
                    onAction(EditAction.CLEAR_PAGE)
                }
            }
            Text(
                text = strings.editHint,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
            )
        }
    }
}

@Composable
private fun EditButton(
    icon: ImageVector,
    label: String,
    tag: String,
    enabled: Boolean = true,
    onClick: () -> Unit
) {
    TextButton(
        onClick = onClick,
        enabled = enabled,
        modifier = Modifier.testTag(tag)
    ) {
        Icon(icon, contentDescription = label, modifier = Modifier.size(18.dp))
        Spacer(modifier = Modifier.size(6.dp))
        Text(label, style = MaterialTheme.typography.labelMedium)
    }
}

@Composable
fun AddTextDialog(
    onDismiss: () -> Unit,
    onAdd: (String, Long) -> Unit
) {
    val strings = LocalStrings.current
    var text by remember { mutableStateOf("") }
    var color by remember { mutableLongStateOf(0xFF18181BL) }
    val swatches = listOf(0xFF18181BL, 0xFF8A011EL, 0xFF1E3A8AL, 0xFF059669L, 0xFFD97706L)

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(strings.newTextTitle) },
        text = {
            Column {
                OutlinedTextField(
                    value = text,
                    onValueChange = { text = it },
                    label = { Text(strings.textHint) },
                    minLines = 2,
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("edit_text_input")
                )
                Spacer(modifier = Modifier.height(12.dp))
                Text(strings.colorLabel, style = MaterialTheme.typography.labelMedium)
                Row(modifier = Modifier.padding(top = 6.dp)) {
                    for (swatch in swatches) {
                        Box(
                            modifier = Modifier
                                .padding(horizontal = 4.dp)
                                .size(28.dp)
                                .clip(CircleShape)
                                .background(Color(swatch))
                                .border(
                                    width = if (color == swatch) 2.dp else 0.dp,
                                    color = MaterialTheme.colorScheme.onSurface,
                                    shape = CircleShape
                                )
                                .clickable { color = swatch }
                        )
                    }
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = { onAdd(text, color) },
                enabled = text.isNotBlank(),
                modifier = Modifier.testTag("edit_text_confirm")
            ) {
                Text(strings.add)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(strings.cancel)
            }
        }
    )
}
