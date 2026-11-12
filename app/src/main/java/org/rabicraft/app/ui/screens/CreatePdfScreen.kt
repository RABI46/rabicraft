package org.rabicraft.app.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import org.rabicraft.app.ui.i18n.LocalStrings

/**
 * Writes a document from plain text: `# ` starts a heading, `- ` a bullet, blank lines separate
 * paragraphs. The page size and text size chosen here are handed to the writer unchanged.
 */
@Composable
fun CreatePdfScreen(
    onClose: () -> Unit,
    onCreate: (
        title: String,
        body: String,
        pageWidthPt: Int,
        pageHeightPt: Int,
        baseSizePt: Float
    ) -> Unit
) {
    val strings = LocalStrings.current
    var title by remember { mutableStateOf("") }
    var body by remember { mutableStateOf("") }
    var letter by remember { mutableStateOf(false) }
    var size by remember { mutableFloatStateOf(11f) }

    Surface(color = MaterialTheme.colorScheme.background, modifier = Modifier.fillMaxSize()) {
        Column(modifier = Modifier.fillMaxSize()) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 8.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(onClick = onClose, modifier = Modifier.testTag("create_pdf_close")) {
                    Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = strings.close)
                }
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = strings.createDocument,
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = strings.createDocumentHint,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState())
                    .padding(16.dp)
            ) {
                OutlinedTextField(
                    value = title,
                    onValueChange = { title = it },
                    label = { Text(strings.documentTitle) },
                    singleLine = true,
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("create_pdf_title")
                )
                Spacer(modifier = Modifier.height(12.dp))
                OutlinedTextField(
                    value = body,
                    onValueChange = { body = it },
                    label = { Text(strings.documentBody) },
                    supportingText = { Text(strings.documentBodyHint, fontSize = 11.sp) },
                    minLines = 10,
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("create_pdf_body")
                )
                Spacer(modifier = Modifier.height(14.dp))
                Text(strings.paperSize, fontWeight = FontWeight.Medium)
                Spacer(modifier = Modifier.height(6.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    FilterChip(
                        selected = !letter,
                        onClick = { letter = false },
                        label = { Text("A4") }
                    )
                    FilterChip(
                        selected = letter,
                        onClick = { letter = true },
                        label = { Text("Letter") }
                    )
                }
                Spacer(modifier = Modifier.height(10.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = strings.fontSize,
                        modifier = Modifier.weight(1f),
                        fontWeight = FontWeight.Medium
                    )
                    Text(
                        text = size.toInt().toString(),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Slider(value = size, onValueChange = { size = it }, valueRange = 9f..16f)
                Spacer(modifier = Modifier.height(16.dp))
                Button(
                    onClick = {
                        onCreate(
                            title,
                            body,
                            if (letter) 612 else 595,
                            if (letter) 792 else 842,
                            size
                        )
                    },
                    enabled = title.isNotBlank() || body.isNotBlank(),
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("create_pdf_generate")
                ) {
                    Text(strings.generatePdf)
                }
            }
        }
    }
}
