package com.claristatus.app.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Computer
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Smartphone
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import com.claristatus.app.service.TranscodeState
import com.claristatus.app.ui.theme.*

@Composable
fun ExportSuccessDialog(
    successState: TranscodeState.Success,
    originalSizeBytes: Long,
    defaultServerIp: String,
    defaultPhoneNumber: String,
    onShare: () -> Unit,
    onSendViaCompanion: (ip: String, phone: String, onResult: (Result<String>) -> Unit) -> Unit,
    onDone: () -> Unit
) {
    val originalSizeMb = originalSizeBytes / (1024.0f * 1024.0f)
    val compressedSizeMb = successState.outputSizeMb
    val savedPercentage = if (originalSizeMb > 0) {
        (((originalSizeMb - compressedSizeMb) / originalSizeMb) * 100).toInt().coerceAtLeast(0)
    } else 0

    val is1080pAvailable = successState.durationMs <= 30_000L && 
        (successState.preset.maxEdge >= 1920 || successState.preset.badgeLabel.contains("1080p"))

    var showCompanionDialog by remember { mutableStateOf(false) }
    var show720pPrompt by remember { mutableStateOf(false) }

    if (showCompanionDialog) {
        CompanionSendDialog(
            file = successState.outputFile,
            initialIp = defaultServerIp,
            initialPhone = defaultPhoneNumber,
            onSend = onSendViaCompanion,
            onDismiss = { showCompanionDialog = false }
        )
    }

    if (show720pPrompt) {
        AlertDialog(
            onDismissRequest = { show720pPrompt = false },
            icon = {
                Icon(
                    imageVector = Icons.Default.Smartphone,
                    contentDescription = null,
                    tint = EmeraldPrimary,
                    modifier = Modifier.size(32.dp)
                )
            },
            title = {
                Text(
                    text = "Share in 720p HD",
                    fontWeight = FontWeight.Bold,
                    color = TextPrimary
                )
            },
            text = {
                Text(
                    text = "Share to your own chat ('Message yourself') in HD first, then forward to My Status to get 720p!",
                    color = TextSecondary,
                    fontSize = 13.sp,
                    lineHeight = 18.sp
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        show720pPrompt = false
                        onShare()
                    },
                    colors = ButtonDefaults.buttonColors(
                        containerColor = EmeraldPrimary,
                        contentColor = ObsidianBackground
                    )
                ) {
                    Text("Open WhatsApp", fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = { show720pPrompt = false }) {
                    Text("Cancel", color = TextSecondary)
                }
            },
            containerColor = ObsidianSurface
        )
    }

    Dialog(onDismissRequest = onDone) {
        Surface(
            shape = RoundedCornerShape(24.dp),
            color = ObsidianSurface,
            border = BorderStroke(1.dp, ObsidianBorder),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(22.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                // Header
                Icon(
                    imageVector = Icons.Default.CheckCircle,
                    contentDescription = null,
                    tint = SuccessGreen,
                    modifier = Modifier.size(46.dp)
                )

                Text(
                    text = "Compression Complete!",
                    style = MaterialTheme.typography.titleLarge,
                    color = TextPrimary,
                    fontWeight = FontWeight.Bold
                )

                // Comparison Box
                Surface(
                    color = ObsidianCard,
                    shape = RoundedCornerShape(16.dp),
                    border = BorderStroke(1.dp, ObsidianBorder),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(14.dp),
                        horizontalArrangement = Arrangement.SpaceAround,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Text(
                                text = "Original",
                                style = MaterialTheme.typography.bodyMedium,
                                color = TextSecondary,
                                fontSize = 12.sp
                            )
                            Text(
                                text = "${"%.1f".format(originalSizeMb)} MB",
                                style = MaterialTheme.typography.titleMedium,
                                color = TextPrimary,
                                fontWeight = FontWeight.Bold
                            )
                        }

                        Surface(
                            color = SuccessGreen.copy(alpha = 0.15f),
                            shape = RoundedCornerShape(8.dp)
                        ) {
                            Text(
                                text = "-$savedPercentage%",
                                style = MaterialTheme.typography.labelLarge,
                                color = SuccessGreen,
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                                fontWeight = FontWeight.Bold
                            )
                        }

                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Text(
                                text = "Compressed",
                                style = MaterialTheme.typography.bodyMedium,
                                color = TextSecondary,
                                fontSize = 12.sp
                            )
                            Text(
                                text = "${"%.1f".format(compressedSizeMb)} MB",
                                style = MaterialTheme.typography.titleMedium,
                                color = EmeraldPrimary,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                }

                // WhatsApp Guidance
                Surface(
                    color = EmeraldPrimary.copy(alpha = 0.08f),
                    shape = RoundedCornerShape(16.dp),
                    border = BorderStroke(1.dp, EmeraldPrimary.copy(alpha = 0.3f)),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(
                        modifier = Modifier.padding(12.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Text(text = "✨", fontSize = 13.sp)
                            Text(
                                text = "Posting to Status Without Quality Loss",
                                fontWeight = FontWeight.Bold,
                                color = EmeraldLight,
                                fontSize = 12.sp
                            )
                        }

                        Text(
                            text = if (is1080pAvailable) {
                                "• Tap '1080p' to send via PC Companion for pure 1080p.\n• Tap '720p' to send to your own chat in HD."
                            } else {
                                "• Tap '720p' -> send to your own chat in HD -> forward to My Status."
                            },
                            color = TextSecondary,
                            fontSize = 11.sp,
                            lineHeight = 15.sp
                        )
                    }
                }

                // Action Buttons: 1080p (PC) & 720p (Phone)
                if (is1080pAvailable) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Button(
                            onClick = { showCompanionDialog = true },
                            modifier = Modifier
                                .weight(1f)
                                .height(48.dp),
                            shape = RoundedCornerShape(14.dp),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = EmeraldPrimary,
                                contentColor = ObsidianBackground
                            )
                        ) {
                            Icon(imageVector = Icons.Default.Computer, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(text = "1080p", fontWeight = FontWeight.Bold, fontSize = 14.sp)
                        }

                        Button(
                            onClick = { show720pPrompt = true },
                            modifier = Modifier
                                .weight(1f)
                                .height(48.dp),
                            shape = RoundedCornerShape(14.dp),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = Color(0xFF2563EB),
                                contentColor = Color.White
                            )
                        ) {
                            Icon(imageVector = Icons.Default.Smartphone, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(text = "720p", fontWeight = FontWeight.Bold, fontSize = 14.sp)
                        }
                    }
                } else {
                    // Only 720p (1080p removed when 60s is selected)
                    Button(
                        onClick = { show720pPrompt = true },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(48.dp),
                        shape = RoundedCornerShape(14.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = EmeraldPrimary,
                            contentColor = ObsidianBackground
                        )
                    ) {
                        Icon(imageVector = Icons.Default.Smartphone, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(text = "720p", fontWeight = FontWeight.Bold, fontSize = 14.sp)
                    }
                }

                // Compress Another Button
                TextButton(
                    onClick = onDone,
                    colors = ButtonDefaults.textButtonColors(contentColor = TextSecondary)
                ) {
                    Icon(imageVector = Icons.Default.Refresh, contentDescription = null, modifier = Modifier.size(15.dp))
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(text = "Compress Another Video", fontSize = 12.sp)
                }
            }
        }
    }
}
