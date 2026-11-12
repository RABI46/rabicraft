package org.rabicraft.app.ui.components

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import org.rabicraft.app.model.DetectedTextBlock
import org.rabicraft.app.ui.i18n.LocalStrings

/**
 * Lists the lines the OCR found on the current page. Picking one opens its text for rewriting;
 * erasing covers the line without writing anything back.
 */
@Composable
fun TextEditDialog(
    blocks: List<DetectedTextBlock>,
    onDismiss: () -> Unit,
    onReplace: (DetectedTextBlock, String) -> Unit,
    onDelete: (DetectedTextBlock) -> Unit
) {
    val strings = LocalStrings.current
    var editing by remember { mutableStateOf<DetectedTextBlock?>(null) }
    var draft by remember { mutableStateOf("") }

    val target = editing
    if (target == null) {
        AlertDialog(
            onDismissRequest = onDismiss,
            title = { Text(strings.textEditTitle) },
            text = {
                if (blocks.isEmpty()) {
                    Text(strings.textEditEmpty)
                } else {
                    Column(
                        modifier = Modifier
                            .heightIn(max = 340.dp)
                            .verticalScroll(rememberScrollState())
                    ) {
                        blocks.forEach { block ->
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 2.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = block.text,
                                    modifier = Modifier
                                        .weight(1f)
                                        .padding(end = 8.dp),
                                    style = MaterialTheme.typography.bodyMedium,
                                    maxLines = 2
                                )
                                TextButton(
                                    onClick = {
                                        editing = block
                                        draft = block.text
                                    },
                                    modifier = Modifier.testTag("text_edit_pick")
                                ) {
                                    Text(strings.replaceText)
                                }
                                TextButton(onClick = { onDelete(block) }) {
                                    Text(strings.deleteText)
                                }
                            }
                            HorizontalDivider()
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = onDismiss) {
                    Text(strings.close)
                }
            }
        )
        return
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(strings.replaceText) },
        text = {
            Column {
                Text(
                    text = target.text,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(modifier = Modifier.height(8.dp))
                OutlinedTextField(
                    value = draft,
                    onValueChange = { draft = it },
                    label = { Text(strings.textHint) },
                    minLines = 2,
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("text_edit_input")
                )
            }
        },
        confirmButton = {
            TextButton(
                onClick = {
                    onReplace(target, draft)
                    onDismiss()
                },
                modifier = Modifier.testTag("text_edit_confirm")
            ) {
                Text(strings.apply)
            }
        },
        dismissButton = {
            TextButton(onClick = { editing = null }) {
                Text(strings.cancel)
            }
        }
    )
}
