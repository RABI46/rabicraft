package org.rabicraft.app.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import org.rabicraft.app.model.SearchMatch

@Composable
fun SearchOverlay(
    searchQuery: String,
    matches: List<SearchMatch>,
    currentMatchIndex: Int,
    onQueryChange: (String) -> Unit,
    onNextMatch: () -> Unit,
    onPrevMatch: () -> Unit,
    onClose: () -> Unit
) {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 6.dp),
        shape = RoundedCornerShape(12.dp),
        shadowElevation = 8.dp,
        color = MaterialTheme.colorScheme.surface
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 10.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                Icons.Default.Search,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary
            )
            Spacer(modifier = Modifier.width(8.dp))
            TextField(
                value = searchQuery,
                onValueChange = onQueryChange,
                placeholder = { Text("Find text in document…", fontSize = 14.sp) },
                singleLine = true,
                modifier = Modifier
                    .weight(1f)
                    .testTag("search_text_input"),
                colors = TextFieldDefaults.colors(
                    focusedContainerColor = Color.Transparent,
                    unfocusedContainerColor = Color.Transparent,
                    disabledContainerColor = Color.Transparent,
                    focusedIndicatorColor = Color.Transparent,
                    unfocusedIndicatorColor = Color.Transparent
                )
            )

            if (searchQuery.isNotBlank()) {
                val matchText = if (matches.isNotEmpty()) {
                    "${currentMatchIndex + 1} of ${matches.size}"
                } else {
                    "0 results"
                }
                Text(
                    text = matchText,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(horizontal = 6.dp)
                )

                IconButton(
                    onClick = onPrevMatch,
                    enabled = matches.isNotEmpty(),
                    modifier = Modifier.testTag("search_prev_button")
                ) {
                    Icon(Icons.Default.KeyboardArrowUp, contentDescription = "Previous match")
                }

                IconButton(
                    onClick = onNextMatch,
                    enabled = matches.isNotEmpty(),
                    modifier = Modifier.testTag("search_next_button")
                ) {
                    Icon(Icons.Default.KeyboardArrowDown, contentDescription = "Next match")
                }
            }

            IconButton(
                onClick = onClose,
                modifier = Modifier.testTag("search_close_button")
            ) {
                Icon(Icons.Default.Close, contentDescription = "Close search")
            }
        }
    }
}
