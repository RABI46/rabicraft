package org.rabicraft.app.ui.edit

import androidx.compose.runtime.staticCompositionLocalOf
import org.rabicraft.app.model.PageEdit

/**
 * Edit-mode state handed to the page renderer. It is provided only while the workbench is in
 * Edit mode, so reading mode and annotation gestures are untouched.
 */
data class EditLayer(
    val edits: Map<Int, List<PageEdit>>,
    val selectedEditId: String?,
    val onSelect: (String?) -> Unit,
    val onMove: (String, Float, Float) -> Unit
)

val LocalEditLayer = staticCompositionLocalOf<EditLayer?> { null }
