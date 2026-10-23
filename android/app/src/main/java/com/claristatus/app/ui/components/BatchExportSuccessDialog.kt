package com.claristatus.app.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
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
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import com.claristatus.app.data.model.SplitSegment
import com.claristatus.app.service.TranscodeState
import com.claristatus.app.ui.theme.*
import java.io.File

@Composable
fun BatchExportSuccessDialog(
    batchState: TranscodeState.BatchSuccess,
    defaultServerIp: String,
    defaultPhoneNumber: String,
    onShareSinglePart: (File) -> Unit,
    onSendSingleViaCompanion: (File, String, String, (Result<String>) -> Unit) -> Unit,
    onDone: () -> Unit
) {
    val totalSizeMb = batchState.totalSizeMb
    val is1080pAvailable = batchState.completedParts.all { it.trimRange.durationMs <= 30_000L } && 
        (batchState.preset.maxEdge >= 1920 || batchState.preset.badgeLabel.contains("1080p"))

    var companionTargetFile by remember { mutableStateOf<File?>(null) }
    var targetFileFor720pPrompt by remember { mutableStateOf<File?>(null) }

    // PC Companion 1080p Dialog
    if (companionTargetFile != null) {
        CompanionSendDialog(
            file = companionTargetFile!!,
            initialIp = defaultServerIp,
            initialPhone = defaultPhoneNumber,
            onSend = { ip, phone, onResult ->
                onSendSingleViaCompanion(companionTargetFile!!, ip, phone, onResult)
            },
            onDismiss = { companionTargetFile = null }
        )
    }

    // 720p WhatsApp Chat Guidance Dialog
    if (targetFileFor720pPrompt != null) {
        AlertDialog(
            onDismissRequest = { targetFileFor720pPrompt = null },
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
                        val file = targetFileFor720pPrompt
                        targetFileFor720pPrompt = null
                        if (file != null) onShareSinglePart(file)
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
                TextButton(onClick = { targetFileFor720pPrompt = null }) {
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
            modifier = Modifier
                .fillMaxWidth()
                .fillMaxHeight(0.88f)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(20.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.SpaceBetween
            ) {
                // Header
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.CheckCircle,
                        contentDescription = null,
                        tint = SuccessGreen,
                        modifier = Modifier.size(44.dp)
                    )

                    Text(
                        text = "All Stories Ready!",
                        style = MaterialTheme.typography.titleLarge,
                        color = TextPrimary,
                        fontWeight = FontWeight.Bold
                    )

                    Text(
                        text = "${batchState.completedParts.size} parts • ${batchState.preset.badgeLabel} • ${"%.1f".format(totalSizeMb)} MB total",
                        style = MaterialTheme.typography.bodyMedium,
                        color = TextSecondary,
                        fontSize = 12.sp,
                        textAlign = TextAlign.Center
                    )
                }

                // WhatsApp Guidance Card
                Surface(
                    color = EmeraldPrimary.copy(alpha = 0.08f),
                    shape = RoundedCornerShape(14.dp),
                    border = BorderStroke(1.dp, EmeraldPrimary.copy(alpha = 0.25f)),
                    modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp)
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
                                "• Tap '1080p' for pure 1080p via PC Companion.\n• Tap '720p' to share via phone chat in HD."
                            } else {
                                "• Tap '720p' -> send to your own chat in HD -> forward to My Status."
                            },
                            color = TextSecondary,
                            fontSize = 11.sp,
                            lineHeight = 15.sp
                        )
                    }
                }

                // List of parts with individual 1080p (if <=30s) and 720p buttons
                LazyColumn(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f)
                        .padding(vertical = 6.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    items(batchState.completedParts) { segment ->
                        val file = segment.outputFile
                        val sizeMb = if (file != null) file.length() / (1024.0f * 1024.0f) else 0f

                        Surface(
                            shape = RoundedCornerShape(12.dp),
                            color = ObsidianCard,
                            border = BorderStroke(1.dp, ObsidianBorder),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 12.dp, vertical = 10.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = "Part ${segment.partIndex} of ${segment.totalParts}",
                                        color = TextPrimary,
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 13.sp
                                    )
                                    Text(
                                        text = "${"%.1f".format(segment.trimRange.durationSeconds)}s • ${"%.1f".format(sizeMb)} MB",
                                        color = TextSecondary,
                                        fontSize = 11.sp
                                    )
                                }

                                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                    // 1080p PC Companion Button (Only shown if 1080p is available / duration <= 30s)
                                    if (is1080pAvailable) {
                                        Button(
                                            onClick = { if (file != null) companionTargetFile = file },
                                            colors = ButtonDefaults.buttonColors(
                                                containerColor = EmeraldPrimary,
                                                contentColor = ObsidianBackground
                                            ),
                                            shape = RoundedCornerShape(10.dp),
                                            contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp),
                                            modifier = Modifier.height(34.dp)
                                        ) {
                                            Icon(imageVector = Icons.Default.Computer, contentDescription = null, modifier = Modifier.size(13.dp))
                                            Spacer(modifier = Modifier.width(3.dp))
                                            Text(text = "1080p", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                                        }
                                    }

                                    // 720p Phone Direct Button (with phone icon)
                                    Button(
                                        onClick = { if (file != null) targetFileFor720pPrompt = file },
                                        colors = ButtonDefaults.buttonColors(
                                            containerColor = Color(0xFF2563EB),
                                            contentColor = Color.White
                                        ),
                                        shape = RoundedCornerShape(10.dp),
                                        contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp),
                                        modifier = Modifier.height(34.dp)
                                    ) {
                                        Icon(imageVector = Icons.Default.Smartphone, contentDescription = null, modifier = Modifier.size(13.dp))
                                        Spacer(modifier = Modifier.width(3.dp))
                                        Text(text = "720p", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                                    }
                                }
                            }
                        }
                    }
                }

                // Actions Bottom (Share All removed per user request)
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    // Done / Start Over
                    TextButton(
                        onClick = onDone,
                        modifier = Modifier.fillMaxWidth(),
                        colors = ButtonDefaults.textButtonColors(contentColor = TextSecondary)
                    ) {
                        Icon(imageVector = Icons.Default.Refresh, contentDescription = null, modifier = Modifier.size(14.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(text = "Compress Another Video", fontSize = 12.sp)
                    }
                }
            }
        }
    }
}
