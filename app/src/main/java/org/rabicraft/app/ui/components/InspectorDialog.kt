package org.rabicraft.app.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material3.Divider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import org.rabicraft.app.model.PdfDocumentInfo

@Composable
fun InspectorDialog(
    docInfo: PdfDocumentInfo,
    onDismiss: () -> Unit
) {
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
                    .padding(20.dp)
                    .verticalScroll(rememberScrollState())
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "Document Properties",
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.weight(1f)
                    )
                    IconButton(
                        onClick = onDismiss,
                        modifier = Modifier.testTag("close_inspector_button")
                    ) {
                        Icon(Icons.Default.Close, contentDescription = "Close")
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                PropertySection(title = "Description") {
                    PropertyRow("File Name", docInfo.name)
                    PropertyRow("Title", docInfo.title)
                    PropertyRow("Author", docInfo.author)
                    PropertyRow("Application", docInfo.creator)
                    PropertyRow("PDF Producer", docInfo.producer)
                    PropertyRow("PDF Version", docInfo.pdfVersion)
                    PropertyRow("Page Count", "${docInfo.pageCount} pages")
                    val kb = docInfo.fileSizeBytes / 1024
                    PropertyRow("File Size", if (kb > 0) "$kb KB" else "In Memory")
                }

                Spacer(modifier = Modifier.height(16.dp))

                PropertySection(title = "Security & Permissions") {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            if (docInfo.isEncrypted) Icons.Default.Lock else Icons.Default.CheckCircle,
                            contentDescription = null,
                            tint = if (docInfo.isEncrypted) Color(0xFFD97706) else Color(0xFF16A34A),
                            modifier = Modifier.padding(end = 6.dp)
                        )
                        Text(
                            text = if (docInfo.isEncrypted) "Password Protected" else "No Security (Open Access)",
                            fontWeight = FontWeight.Medium,
                            fontSize = 13.sp
                        )
                    }
                    Spacer(modifier = Modifier.height(8.dp))
                    PermissionItem("Printing", docInfo.permissions.allowPrinting)
                    PermissionItem("High Resolution Printing", docInfo.permissions.allowHighQualityPrinting)
                    PermissionItem("Content Copying", docInfo.permissions.allowCopying)
                    PermissionItem("Document Assembly", docInfo.permissions.allowAssembly)
                    PermissionItem("Form Filling & Signatures", docInfo.permissions.allowFillingForms)
                    PermissionItem("Screen Readers & Accessibility", docInfo.permissions.allowScreenReaders)
                }

                Spacer(modifier = Modifier.height(16.dp))

                PropertySection(title = "Page Geometry & Media Boxes") {
                    PropertyRow("MediaBox", "0 0 595 842 pt (A4 Portrait)")
                    PropertyRow("CropBox", "0 0 595 842 pt")
                    PropertyRow("BleedBox", "0 0 595 842 pt")
                    PropertyRow("Color Space", "DeviceRGB, DeviceCMYK calibrated")
                }
            }
        }
    }
}

@Composable
private fun PropertySection(title: String, content: @Composable () -> Unit) {
    Text(
        text = title,
        style = MaterialTheme.typography.titleSmall,
        color = MaterialTheme.colorScheme.primary,
        fontWeight = FontWeight.SemiBold
    )
    Spacer(modifier = Modifier.height(6.dp))
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f), RoundedCornerShape(8.dp))
            .padding(12.dp)
    ) {
        content()
    }
}

@Composable
private fun PropertyRow(label: String, value: String) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp)
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.width(110.dp)
        )
        Text(
            text = value,
            style = MaterialTheme.typography.bodySmall,
            fontWeight = FontWeight.Medium,
            modifier = Modifier.weight(1f)
        )
    }
}

@Composable
private fun PermissionItem(name: String, allowed: Boolean) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 2.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = if (allowed) "✓ Allowed: " else "✕ Disallowed: ",
            color = if (allowed) Color(0xFF16A34A) else Color(0xFFDC2626),
            fontSize = 11.sp,
            fontWeight = FontWeight.Bold
        )
        Text(
            text = name,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurface
        )
    }
}
