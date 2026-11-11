package org.rabicraft.app.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Bookmark
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.DriveFileRenameOutline
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.FilterList
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.FolderOpen
import androidx.compose.material.icons.filled.Label
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.PictureAsPdf
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.StarBorder
import androidx.compose.material.icons.filled.UploadFile
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.InputChip
import androidx.compose.material3.InputChipDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import org.rabicraft.app.data.local.DocumentEntity
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@OptIn(ExperimentalLayoutApi::class, ExperimentalMaterial3Api::class)
@Composable
fun DocumentLibraryScreen(
    documents: List<DocumentEntity>,
    groups: List<String>,
    onOpenDocument: (DocumentEntity) -> Unit,
    onImportDocument: () -> Unit,
    onRenameDocument: (id: String, newName: String) -> Unit,
    onUpdateGroup: (id: String, newGroup: String) -> Unit,
    onUpdateTags: (id: String, tags: List<String>) -> Unit,
    onToggleStarred: (id: String, currentStarred: Boolean) -> Unit,
    onDeleteDocument: (id: String) -> Unit
) {
    var searchQuery by remember { mutableStateOf("") }
    var selectedGroup by remember { mutableStateOf<String?>(null) } // null = All
    var selectedTag by remember { mutableStateOf<String?>(null) }
    var filterStarredOnly by remember { mutableStateOf(false) }

    // Dialog States
    var documentToRename by remember { mutableStateOf<DocumentEntity?>(null) }
    var documentToGroup by remember { mutableStateOf<DocumentEntity?>(null) }
    var documentToTag by remember { mutableStateOf<DocumentEntity?>(null) }
    var documentToDelete by remember { mutableStateOf<DocumentEntity?>(null) }

    // Extract all distinct tags across documents
    val allTags = remember(documents) {
        documents.flatMap { it.getTagsList() }.distinct().sorted()
    }

    // Filter documents
    val filteredDocuments = documents.filter { doc ->
        val matchesQuery = searchQuery.isBlank() ||
                doc.name.contains(searchQuery, ignoreCase = true) ||
                doc.tagsCsv.contains(searchQuery, ignoreCase = true) ||
                doc.groupName.contains(searchQuery, ignoreCase = true)

        val matchesGroup = selectedGroup == null || doc.groupName.equals(selectedGroup, ignoreCase = true)
        val matchesTag = selectedTag == null || doc.getTagsList().any { it.equals(selectedTag, ignoreCase = true) }
        val matchesStarred = !filterStarredOnly || doc.isStarred

        matchesQuery && matchesGroup && matchesTag && matchesStarred
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
    ) {
        Column(modifier = Modifier.fillMaxSize()) {
            // Top Search & Filter Bar
            Surface(
                color = MaterialTheme.colorScheme.surface,
                tonalElevation = 2.dp,
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 10.dp)
                ) {
                    // Search text field
                    TextField(
                        value = searchQuery,
                        onValueChange = { searchQuery = it },
                        placeholder = { Text("Search by name, tag, or group…", fontSize = 14.sp) },
                        leadingIcon = {
                            Icon(Icons.Default.Search, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                        },
                        trailingIcon = {
                            if (searchQuery.isNotEmpty()) {
                                IconButton(onClick = { searchQuery = "" }) {
                                    Icon(Icons.Default.Close, contentDescription = "Clear search")
                                }
                            }
                        },
                        singleLine = true,
                        shape = RoundedCornerShape(12.dp),
                        colors = TextFieldDefaults.colors(
                            focusedContainerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                            unfocusedContainerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f),
                            focusedIndicatorColor = Color.Transparent,
                            unfocusedIndicatorColor = Color.Transparent
                        ),
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("library_search_input")
                    )

                    Spacer(modifier = Modifier.height(10.dp))

                    // Group Filter Chips Row
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        FilterChip(
                            selected = selectedGroup == null && !filterStarredOnly,
                            onClick = {
                                selectedGroup = null
                                filterStarredOnly = false
                            },
                            label = { Text("All (${documents.size})") },
                            colors = FilterChipDefaults.filterChipColors(
                                selectedContainerColor = MaterialTheme.colorScheme.primaryContainer
                            ),
                            modifier = Modifier.testTag("filter_group_all")
                        )

                        FilterChip(
                            selected = filterStarredOnly,
                            onClick = {
                                filterStarredOnly = !filterStarredOnly
                                if (filterStarredOnly) selectedGroup = null
                            },
                            label = { Text("★ Starred") },
                            leadingIcon = { Icon(Icons.Default.Star, contentDescription = null, modifier = Modifier.size(16.dp)) },
                            colors = FilterChipDefaults.filterChipColors(
                                selectedContainerColor = Color(0xFFFEF3C7)
                            ),
                            modifier = Modifier.testTag("filter_group_starred")
                        )

                        val allGroups = (listOf("General", "Specimens") + groups).distinct()
                        for (grp in allGroups) {
                            FilterChip(
                                selected = selectedGroup == grp && !filterStarredOnly,
                                onClick = {
                                    selectedGroup = if (selectedGroup == grp) null else grp
                                    filterStarredOnly = false
                                },
                                label = { Text(grp) },
                                leadingIcon = { Icon(Icons.Default.Folder, contentDescription = null, modifier = Modifier.size(16.dp)) },
                                modifier = Modifier.testTag("filter_group_$grp")
                            )
                        }
                    }

                    // Tag Filter Chips (if any exist)
                    if (allTags.isNotEmpty()) {
                        Spacer(modifier = Modifier.height(6.dp))
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .horizontalScroll(rememberScrollState()),
                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "Tags:",
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )

                            for (tag in allTags) {
                                val isSelected = selectedTag == tag
                                Surface(
                                    shape = RoundedCornerShape(12.dp),
                                    color = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceVariant,
                                    modifier = Modifier
                                        .clickable {
                                            selectedTag = if (selectedTag == tag) null else tag
                                        }
                                        .testTag("filter_tag_$tag")
                                ) {
                                    Text(
                                        text = "#$tag",
                                        fontSize = 11.sp,
                                        fontWeight = FontWeight.Medium,
                                        color = if (isSelected) Color.White else MaterialTheme.colorScheme.onSurfaceVariant,
                                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                                    )
                                }
                            }

                            if (selectedTag != null) {
                                TextButton(
                                    onClick = { selectedTag = null },
                                    contentPadding = PaddingValues(horizontal = 6.dp)
                                ) {
                                    Text("Clear", fontSize = 11.sp)
                                }
                            }
                        }
                    }
                }
            }

            // Document Cards List
            if (filteredDocuments.isEmpty()) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(32.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Icon(
                            Icons.Default.PictureAsPdf,
                            contentDescription = null,
                            tint = Color(0xFFCBD5E1),
                            modifier = Modifier.size(64.dp)
                        )
                        Spacer(modifier = Modifier.height(12.dp))
                        Text(
                            text = if (searchQuery.isNotEmpty() || selectedGroup != null || selectedTag != null) {
                                "No documents match the current filters"
                            } else {
                                "Your library is empty"
                            },
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.SemiBold
                        )
                        Spacer(modifier = Modifier.height(6.dp))
                        Text(
                            text = "Tap 'Import PDF' to add documents, or clear active filters.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Spacer(modifier = Modifier.height(16.dp))
                        OutlinedButton(onClick = onImportDocument) {
                            Icon(Icons.Default.UploadFile, contentDescription = null)
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Import PDF File")
                        }
                    }
                }
            } else {
                LazyColumn(
                    contentPadding = PaddingValues(horizontal = 16.dp, vertical = 16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                    modifier = Modifier.fillMaxSize()
                ) {
                    items(filteredDocuments, key = { it.id }) { doc ->
                        DocumentItemCard(
                            document = doc,
                            onClick = { onOpenDocument(doc) },
                            onToggleStar = { onToggleStarred(doc.id, doc.isStarred) },
                            onRename = { documentToRename = doc },
                            onMoveGroup = { documentToGroup = doc },
                            onEditTags = { documentToTag = doc },
                            onDelete = { documentToDelete = doc }
                        )
                    }
                }
            }
        }

        // Floating Action Button to Import Documents
        FloatingActionButton(
            onClick = onImportDocument,
            containerColor = MaterialTheme.colorScheme.primary,
            contentColor = Color.White,
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .padding(20.dp)
                .testTag("import_pdf_fab")
        ) {
            Row(
                modifier = Modifier.padding(horizontal = 16.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(Icons.Default.UploadFile, contentDescription = "Import PDF")
                Spacer(modifier = Modifier.width(8.dp))
                Text("Import PDF", fontWeight = FontWeight.SemiBold)
            }
        }
    }

    // Modal: Rename Dialog
    documentToRename?.let { doc ->
        RenameDocumentDialog(
            currentName = doc.name,
            onDismiss = { documentToRename = null },
            onConfirm = { newName ->
                onRenameDocument(doc.id, newName)
                documentToRename = null
            }
        )
    }

    // Modal: Change Group Dialog
    documentToGroup?.let { doc ->
        ChangeGroupDialog(
            currentGroup = doc.groupName,
            existingGroups = groups,
            onDismiss = { documentToGroup = null },
            onConfirm = { newGroup ->
                onUpdateGroup(doc.id, newGroup)
                documentToGroup = null
            }
        )
    }

    // Modal: Edit Tags Dialog
    documentToTag?.let { doc ->
        EditTagsDialog(
            currentTags = doc.getTagsList(),
            availableTags = allTags,
            onDismiss = { documentToTag = null },
            onConfirm = { newTags ->
                onUpdateTags(doc.id, newTags)
                documentToTag = null
            }
        )
    }

    // Modal: Confirm Delete Dialog
    documentToDelete?.let { doc ->
        AlertDialog(
            onDismissRequest = { documentToDelete = null },
            title = { Text("Delete Document") },
            text = { Text("Are you sure you want to remove '${doc.name}' from your library?") },
            confirmButton = {
                Button(
                    onClick = {
                        onDeleteDocument(doc.id)
                        documentToDelete = null
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
                ) {
                    Text("Delete")
                }
            },
            dismissButton = {
                TextButton(onClick = { documentToDelete = null }) {
                    Text("Cancel")
                }
            }
        )
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun DocumentItemCard(
    document: DocumentEntity,
    onClick: () -> Unit,
    onToggleStar: () -> Unit,
    onRename: () -> Unit,
    onMoveGroup: () -> Unit,
    onEditTags: () -> Unit,
    onDelete: () -> Unit
) {
    var menuExpanded by remember { mutableStateOf(false) }

    Card(
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onClick() }
            .testTag("doc_item_${document.id}")
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth()
            ) {
                // PDF Tile Icon
                Box(
                    modifier = Modifier
                        .size(44.dp)
                        .clip(RoundedCornerShape(8.dp))
                        .background(Color(0xFF8A011E)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        Icons.Default.PictureAsPdf,
                        contentDescription = null,
                        tint = Color.White,
                        modifier = Modifier.size(24.dp)
                    )
                }

                Spacer(modifier = Modifier.width(12.dp))

                // Title and Meta
                Column(modifier = Modifier.weight(1f)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            text = document.name,
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.SemiBold,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.weight(1f, fill = false)
                        )
                        if (document.isSample) {
                            Spacer(modifier = Modifier.width(6.dp))
                            Surface(
                                shape = RoundedCornerShape(4.dp),
                                color = MaterialTheme.colorScheme.primaryContainer
                            ) {
                                Text(
                                    text = "SAMPLE",
                                    fontSize = 9.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.onPrimaryContainer,
                                    modifier = Modifier.padding(horizontal = 4.dp, vertical = 2.dp)
                                )
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(2.dp))

                    Row(verticalAlignment = Alignment.CenterVertically) {
                        // Group badge
                        Surface(
                            shape = RoundedCornerShape(4.dp),
                            color = MaterialTheme.colorScheme.surfaceVariant,
                            modifier = Modifier.padding(end = 8.dp)
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                            ) {
                                Icon(Icons.Default.Folder, contentDescription = null, modifier = Modifier.size(10.dp))
                                Spacer(modifier = Modifier.width(4.dp))
                                Text(
                                    text = document.groupName,
                                    fontSize = 11.sp,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }

                        val sizeKb = document.fileSizeBytes / 1024
                        val sizeText = if (sizeKb > 1024) String.format(Locale.US, "%.1f MB", sizeKb / 1024f) else "$sizeKb KB"
                        Text(
                            text = "${document.pageCount} pages • $sizeText",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            fontSize = 12.sp
                        )
                    }
                }

                // Favorite Star
                IconButton(
                    onClick = onToggleStar,
                    modifier = Modifier.size(36.dp)
                ) {
                    Icon(
                        if (document.isStarred) Icons.Default.Star else Icons.Default.StarBorder,
                        contentDescription = "Star",
                        tint = if (document.isStarred) Color(0xFFEAB308) else MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                // Overflow Menu
                Box {
                    IconButton(
                        onClick = { menuExpanded = true },
                        modifier = Modifier.size(36.dp)
                    ) {
                        Icon(Icons.Default.MoreVert, contentDescription = "Options")
                    }

                    DropdownMenu(
                        expanded = menuExpanded,
                        onDismissRequest = { menuExpanded = false }
                    ) {
                        DropdownMenuItem(
                            text = { Text("Open in Workbench") },
                            leadingIcon = { Icon(Icons.Default.FolderOpen, contentDescription = null) },
                            onClick = {
                                menuExpanded = false
                                onClick()
                            }
                        )
                        DropdownMenuItem(
                            text = { Text("Rename") },
                            leadingIcon = { Icon(Icons.Default.DriveFileRenameOutline, contentDescription = null) },
                            onClick = {
                                menuExpanded = false
                                onRename()
                            }
                        )
                        DropdownMenuItem(
                            text = { Text("Change Group") },
                            leadingIcon = { Icon(Icons.Default.Folder, contentDescription = null) },
                            onClick = {
                                menuExpanded = false
                                onMoveGroup()
                            }
                        )
                        DropdownMenuItem(
                            text = { Text("Edit Tags") },
                            leadingIcon = { Icon(Icons.Default.Label, contentDescription = null) },
                            onClick = {
                                menuExpanded = false
                                onEditTags()
                            }
                        )
                        DropdownMenuItem(
                            text = { Text("Delete", color = MaterialTheme.colorScheme.error) },
                            leadingIcon = { Icon(Icons.Default.Delete, contentDescription = null, tint = MaterialTheme.colorScheme.error) },
                            onClick = {
                                menuExpanded = false
                                onDelete()
                            }
                        )
                    }
                }
            }

            // Tags row (if any)
            val tags = document.getTagsList()
            if (tags.isNotEmpty()) {
                Spacer(modifier = Modifier.height(8.dp))
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    for (tag in tags) {
                        Surface(
                            shape = RoundedCornerShape(12.dp),
                            color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.4f)
                        ) {
                            Text(
                                text = "#$tag",
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Medium,
                                color = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.padding(horizontal = 7.dp, vertical = 2.dp)
                            )
                        }
                    }
                }
            }
        }
    }
}

// Dialog: Rename
@Composable
private fun RenameDocumentDialog(
    currentName: String,
    onDismiss: () -> Unit,
    onConfirm: (String) -> Unit
) {
    var newName by remember { mutableStateOf(currentName) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Rename Document") },
        text = {
            OutlinedTextField(
                value = newName,
                onValueChange = { newName = it },
                label = { Text("Document Name") },
                singleLine = true,
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("rename_input_field")
            )
        },
        confirmButton = {
            Button(
                onClick = {
                    if (newName.isNotBlank()) onConfirm(newName)
                },
                modifier = Modifier.testTag("confirm_rename_button")
            ) {
                Text("Save")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        }
    )
}

// Dialog: Change Group
@Composable
private fun ChangeGroupDialog(
    currentGroup: String,
    existingGroups: List<String>,
    onDismiss: () -> Unit,
    onConfirm: (String) -> Unit
) {
    val predefined = (listOf("General", "Personal", "Work", "Legal", "Invoices", "Research") + existingGroups).distinct()
    var selectedGroup by remember { mutableStateOf(currentGroup) }
    var customGroupName by remember { mutableStateOf("") }
    var isCustom by remember { mutableStateOf(false) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Move to Group / Folder") },
        text = {
            Column {
                Text("Select group:", style = MaterialTheme.typography.bodySmall)
                Spacer(modifier = Modifier.height(8.dp))
                for (grp in predefined) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable {
                                selectedGroup = grp
                                isCustom = false
                            }
                            .padding(vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            Icons.Default.Folder,
                            contentDescription = null,
                            tint = if (selectedGroup == grp && !isCustom) MaterialTheme.colorScheme.primary else Color.Gray,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.width(10.dp))
                        Text(
                            text = grp,
                            fontWeight = if (selectedGroup == grp && !isCustom) FontWeight.Bold else FontWeight.Normal
                        )
                    }
                }

                Spacer(modifier = Modifier.height(10.dp))
                OutlinedTextField(
                    value = customGroupName,
                    onValueChange = {
                        customGroupName = it
                        isCustom = true
                    },
                    label = { Text("Or create new group…") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    val finalGroup = if (isCustom && customGroupName.isNotBlank()) customGroupName.trim() else selectedGroup
                    onConfirm(finalGroup)
                }
            ) {
                Text("Set Group")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        }
    )
}

// Dialog: Edit Tags
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun EditTagsDialog(
    currentTags: List<String>,
    availableTags: List<String>,
    onDismiss: () -> Unit,
    onConfirm: (List<String>) -> Unit
) {
    val tagsList = remember { mutableStateListOf<String>().apply { addAll(currentTags) } }
    var newTagInput by remember { mutableStateOf("") }

    val suggestedTags = listOf("Urgent", "Reviewed", "Signed", "Contract", "Draft", "Tax", "Archive")

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Edit Document Tags") },
        text = {
            Column {
                Text("Current tags:", style = MaterialTheme.typography.bodySmall)
                Spacer(modifier = Modifier.height(6.dp))

                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    for (tag in tagsList) {
                        InputChip(
                            selected = true,
                            onClick = { tagsList.remove(tag) },
                            label = { Text("#$tag") },
                            trailingIcon = { Icon(Icons.Default.Close, contentDescription = "Remove tag", modifier = Modifier.size(14.dp)) }
                        )
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))

                Row(verticalAlignment = Alignment.CenterVertically) {
                    OutlinedTextField(
                        value = newTagInput,
                        onValueChange = { newTagInput = it },
                        placeholder = { Text("New tag name…") },
                        singleLine = true,
                        modifier = Modifier.weight(1f)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Button(
                        onClick = {
                            val clean = newTagInput.trim().removePrefix("#")
                            if (clean.isNotBlank() && !tagsList.contains(clean)) {
                                tagsList.add(clean)
                                newTagInput = ""
                            }
                        }
                    ) {
                        Text("Add")
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))
                Text("Suggestions:", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Spacer(modifier = Modifier.height(4.dp))
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    for (st in suggestedTags) {
                        if (!tagsList.contains(st)) {
                            Surface(
                                shape = RoundedCornerShape(12.dp),
                                color = MaterialTheme.colorScheme.surfaceVariant,
                                modifier = Modifier.clickable { tagsList.add(st) }
                            ) {
                                Text(
                                    text = "+ $st",
                                    fontSize = 11.sp,
                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                                )
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            Button(onClick = { onConfirm(tagsList.toList()) }) {
                Text("Save Tags")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        }
    )
}
