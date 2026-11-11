package org.rabicraft.app.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.Done
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog

@Composable
fun SignaturePadDialog(
    onDismiss: () -> Unit,
    onSaveSignature: (String) -> Unit
) {
    var selectedTab by remember { mutableIntStateOf(0) }
    var typedName by remember { mutableStateOf("") }
    val drawPoints = remember { mutableStateListOf<Offset?>() }

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
            ) {
                Text(
                    text = "Add Signature",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold
                )
                Spacer(modifier = Modifier.height(12.dp))

                TabRow(selectedTabIndex = selectedTab) {
                    Tab(
                        selected = selectedTab == 0,
                        onClick = { selectedTab = 0 },
                        text = { Text("Draw") }
                    )
                    Tab(
                        selected = selectedTab == 1,
                        onClick = { selectedTab = 1 },
                        text = { Text("Type") }
                    )
                }

                Spacer(modifier = Modifier.height(16.dp))

                if (selectedTab == 0) {
                    // Drawing Pad
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(180.dp)
                            .background(Color(0xFFFAFAFA), RoundedCornerShape(8.dp))
                            .border(1.dp, Color(0xFFCBD5E1), RoundedCornerShape(8.dp))
                    ) {
                        Canvas(
                            modifier = Modifier
                                .matchParentSize()
                                .pointerInput(Unit) {
                                    detectDragGestures(
                                        onDragStart = { offset ->
                                            drawPoints.add(offset)
                                        },
                                        onDrag = { change, _ ->
                                            change.consume()
                                            drawPoints.add(change.position)
                                        },
                                        onDragEnd = {
                                            drawPoints.add(null)
                                        }
                                    )
                                }
                        ) {
                            val path = Path()
                            var isDrawing = false
                            for (point in drawPoints) {
                                if (point != null) {
                                    if (!isDrawing) {
                                        path.moveTo(point.x, point.y)
                                        isDrawing = true
                                    } else {
                                        path.lineTo(point.x, point.y)
                                    }
                                } else {
                                    isDrawing = false
                                }
                            }
                            drawPath(
                                path = path,
                                color = Color(0xFF1E3A8A),
                                style = Stroke(
                                    width = 4f,
                                    cap = StrokeCap.Round,
                                    join = StrokeJoin.Round
                                )
                            )
                        }

                        if (drawPoints.isEmpty()) {
                            Text(
                                text = "Sign here with your finger or stylus",
                                color = Color(0xFF94A3B8),
                                fontSize = 13.sp,
                                modifier = Modifier
                                    .align(Alignment.Center)
                            )
                        }

                        // Baseline rule
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(1.dp)
                                .padding(horizontal = 24.dp)
                                .align(Alignment.BottomCenter)
                                .padding(bottom = 32.dp)
                                .background(Color(0xFFE2E8F0))
                        )
                    }

                    Spacer(modifier = Modifier.height(8.dp))
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                        OutlinedButton(
                            onClick = { drawPoints.clear() },
                            modifier = Modifier.testTag("clear_signature_button")
                        ) {
                            Icon(Icons.Default.Clear, contentDescription = null, modifier = Modifier.padding(end = 4.dp))
                            Text("Clear")
                        }
                    }
                } else {
                    // Type Signature
                    OutlinedTextField(
                        value = typedName,
                        onValueChange = { typedName = it },
                        label = { Text("Your Name") },
                        placeholder = { Text("e.g. Alexandre Dumas") },
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("type_signature_input")
                    )

                    Spacer(modifier = Modifier.height(16.dp))

                    if (typedName.isNotBlank()) {
                        Text(
                            text = "Signature Preview:",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Spacer(modifier = Modifier.height(6.dp))
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(90.dp)
                                .background(Color(0xFFF8FAFC), RoundedCornerShape(8.dp))
                                .border(1.dp, Color(0xFFCBD5E1), RoundedCornerShape(8.dp)),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = typedName,
                                fontSize = 28.sp,
                                fontStyle = FontStyle.Italic,
                                fontWeight = FontWeight.Normal,
                                color = Color(0xFF1E3A8A)
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(20.dp))

                // Bottom Buttons
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End
                ) {
                    OutlinedButton(onClick = onDismiss) {
                        Text("Cancel")
                    }
                    Spacer(modifier = Modifier.width(8.dp))
                    Button(
                        onClick = {
                            val sig = if (selectedTab == 0) {
                                if (drawPoints.isNotEmpty()) "Digital Signature" else "Signed"
                            } else {
                                typedName.ifBlank { "Signed" }
                            }
                            onSaveSignature(sig)
                            onDismiss()
                        },
                        colors = ButtonDefaults.buttonColors(
                            containerColor = MaterialTheme.colorScheme.primary
                        ),
                        modifier = Modifier.testTag("confirm_signature_button")
                    ) {
                        Icon(Icons.Default.Done, contentDescription = null, modifier = Modifier.padding(end = 4.dp))
                        Text("Apply Signature")
                    }
                }
            }
        }
    }
}
