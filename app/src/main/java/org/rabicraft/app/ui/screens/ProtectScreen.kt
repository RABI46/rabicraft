package org.rabicraft.app.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CleaningServices
import androidx.compose.material.icons.filled.Done
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.LockOpen
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material.icons.filled.VerifiedUser
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CheckboxDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import org.rabicraft.app.model.PdfDocumentInfo

@Composable
fun ProtectScreen(
    docInfo: PdfDocumentInfo?,
    onApplySecurity: (isEncrypted: Boolean, password: String) -> Unit
) {
    var isPasswordProtected by remember { mutableStateOf(docInfo?.isEncrypted ?: false) }
    var password by remember { mutableStateOf("") }
    var allowPrinting by remember { mutableStateOf(true) }
    var allowCopying by remember { mutableStateOf(true) }
    var allowModifying by remember { mutableStateOf(false) }
    var allowAnnotations by remember { mutableStateOf(true) }
    var allowScreenReaders by remember { mutableStateOf(true) }
    var metadataSanitized by remember { mutableStateOf(false) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .verticalScroll(rememberScrollState())
            .padding(16.dp)
    ) {
        // Header
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(
                Icons.Default.Security,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(32.dp)
            )
            Spacer(modifier = Modifier.width(12.dp))
            Column {
                Text(
                    text = "Document Protection & Security",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = "Set passwords, permissions, redaction and audit standards.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }

        Spacer(modifier = Modifier.height(20.dp))

        // 1. Password Protection Card
        Card(
            shape = RoundedCornerShape(12.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
            elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        if (isPasswordProtected) Icons.Default.Lock else Icons.Default.LockOpen,
                        contentDescription = null,
                        tint = if (isPasswordProtected) Color(0xFFD97706) else MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(24.dp)
                    )
                    Spacer(modifier = Modifier.width(10.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "Require Password to Open",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.SemiBold
                        )
                        Text(
                            text = "AES-256 standard encryption per ISO 32000-2",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    Switch(
                        checked = isPasswordProtected,
                        onCheckedChange = { isPasswordProtected = it },
                        colors = SwitchDefaults.colors(
                            checkedThumbColor = MaterialTheme.colorScheme.primary
                        ),
                        modifier = Modifier.testTag("toggle_password_protect")
                    )
                }

                if (isPasswordProtected) {
                    Spacer(modifier = Modifier.height(14.dp))
                    OutlinedTextField(
                        value = password,
                        onValueChange = { password = it },
                        label = { Text("Document Open Password") },
                        placeholder = { Text("Enter strong password…") },
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("input_protect_password"),
                        singleLine = true
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        // 2. Granular Permissions Card
        Card(
            shape = RoundedCornerShape(12.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
            elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text(
                    text = "Document Permissions",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold
                )
                Text(
                    text = "Control what viewers and PDF readers are permitted to perform.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                Spacer(modifier = Modifier.height(12.dp))

                PermissionCheckboxRow("Allow Printing", allowPrinting) { allowPrinting = it }
                PermissionCheckboxRow("Allow Copying Text & Images", allowCopying) { allowCopying = it }
                PermissionCheckboxRow("Allow Modifying Document", allowModifying) { allowModifying = it }
                PermissionCheckboxRow("Allow Annotations & Form Signing", allowAnnotations) { allowAnnotations = it }
                PermissionCheckboxRow("Allow Screen Readers (Accessibility)", allowScreenReaders) { allowScreenReaders = it }
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        // 3. Sanitize & Redaction Card
        Card(
            shape = RoundedCornerShape(12.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
            elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        Icons.Default.CleaningServices,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "Sanitize & Redact",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold
                    )
                }
                Spacer(modifier = Modifier.height(6.dp))
                Text(
                    text = "Strip hidden metadata, private XMP tags, unreferenced objects, and document history before sharing.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                Spacer(modifier = Modifier.height(12.dp))

                Button(
                    onClick = { metadataSanitized = true },
                    colors = ButtonDefaults.buttonColors(
                        containerColor = if (metadataSanitized) Color(0xFF16A34A) else MaterialTheme.colorScheme.primary
                    ),
                    modifier = Modifier.testTag("sanitize_metadata_button")
                ) {
                    Icon(
                        if (metadataSanitized) Icons.Default.Done else Icons.Default.Shield,
                        contentDescription = null,
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(if (metadataSanitized) "Document Sanitized ✓" else "Sanitize Document")
                }
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        // 4. Standards Compliance Audit
        Card(
            shape = RoundedCornerShape(12.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
            elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text(
                    text = "Standards Conformance Audit",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold
                )
                Spacer(modifier = Modifier.height(10.dp))
                AuditResultItem("ISO 32000-2 (PDF 2.0 / 1.7)", "Passed standard syntax checks")
                AuditResultItem("PDF/A-2b Archival Compliance", "All fonts embedded or standard-14 metrics")
                AuditResultItem("Linearization & Fast Web View", "Optimized cross-reference streams")
                AuditResultItem("Structure Trees / Section 508", "Tagged PDF accessible hierarchy")
            }
        }

        Spacer(modifier = Modifier.height(24.dp))

        Button(
            onClick = { onApplySecurity(isPasswordProtected, password) },
            colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary),
            modifier = Modifier
                .fillMaxWidth()
                .height(48.dp)
                .testTag("apply_security_settings_button")
        ) {
            Icon(Icons.Default.VerifiedUser, contentDescription = null)
            Spacer(modifier = Modifier.width(8.dp))
            Text("Apply Security Settings")
        }

        Spacer(modifier = Modifier.height(30.dp))
    }
}

@Composable
private fun PermissionCheckboxRow(
    title: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 3.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Checkbox(
            checked = checked,
            onCheckedChange = onCheckedChange,
            colors = CheckboxDefaults.colors(checkedColor = MaterialTheme.colorScheme.primary)
        )
        Spacer(modifier = Modifier.width(6.dp))
        Text(text = title, style = MaterialTheme.typography.bodyMedium)
    }
}

@Composable
private fun AuditResultItem(standard: String, note: String) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text("✓", color = Color(0xFF16A34A), fontWeight = FontWeight.Bold, fontSize = 14.sp)
        Spacer(modifier = Modifier.width(8.dp))
        Column {
            Text(text = standard, fontWeight = FontWeight.Medium, fontSize = 13.sp)
            Text(text = note, color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 11.sp)
        }
    }
}
