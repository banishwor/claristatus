package com.claristatus.app.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.Computer
import androidx.compose.material.icons.filled.Wifi
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import com.claristatus.app.domain.CompanionClient
import com.claristatus.app.domain.DiscoveredCompanion
import com.claristatus.app.ui.theme.*
import java.io.File

@Composable
fun CompanionSendDialog(
    file: File,
    initialIp: String,
    initialPhone: String,
    onSend: (ip: String, phone: String, onResult: (Result<String>) -> Unit) -> Unit,
    onDismiss: () -> Unit
) {
    var serverIp by remember { mutableStateOf(initialIp) }
    var phoneNumber by remember { mutableStateOf(initialPhone) }
    var isSending by remember { mutableStateOf(false) }
    var statusMessage by remember { mutableStateOf<String?>(null) }
    var isError by remember { mutableStateOf(false) }

    var isSearchingCompanion by remember { mutableStateOf(true) }
    var discoveredPc by remember { mutableStateOf<DiscoveredCompanion?>(null) }
    var showManualIp by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        val found = CompanionClient.discoverCompanionOnLan(timeoutMs = 1800)
        isSearchingCompanion = false
        if (found != null) {
            discoveredPc = found
            serverIp = found.ip
        }
    }

    Dialog(onDismissRequest = { if (!isSending) onDismiss() }) {
        Surface(
            shape = RoundedCornerShape(22.dp),
            color = ObsidianSurface,
            border = BorderStroke(1.dp, ObsidianBorder),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(22.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                // Header
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Computer,
                        contentDescription = null,
                        tint = EmeraldPrimary,
                        modifier = Modifier.size(28.dp)
                    )
                    Column {
                        Text(
                            text = "Send 1080p via PC Companion",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = TextPrimary
                        )
                        Text(
                            text = "Transfers uncompressed Full HD to WhatsApp CDN",
                            style = MaterialTheme.typography.bodySmall,
                            color = TextSecondary,
                            fontSize = 11.sp
                        )
                    }
                }

                HorizontalDivider(color = ObsidianBorder)

                // Auto-Discovery Card
                if (discoveredPc != null) {
                    Surface(
                        color = SuccessGreen.copy(alpha = 0.12f),
                        shape = RoundedCornerShape(14.dp),
                        border = BorderStroke(1.dp, SuccessGreen.copy(alpha = 0.35f)),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            modifier = Modifier.padding(12.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            Text("🟢", fontSize = 16.sp)
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = "Connected to: ${discoveredPc?.name}",
                                    fontWeight = FontWeight.Bold,
                                    color = SuccessGreen,
                                    fontSize = 13.sp
                                )
                                Text(
                                    text = "Local Wi-Fi IP: ${discoveredPc?.ip}:3000",
                                    color = TextSecondary,
                                    fontSize = 11.sp
                                )
                            }
                            Text(
                                text = "Auto",
                                style = MaterialTheme.typography.labelSmall,
                                color = SuccessGreen,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                } else if (isSearchingCompanion) {
                    Surface(
                        color = ObsidianCard,
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            modifier = Modifier.padding(12.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(16.dp),
                                strokeWidth = 2.dp,
                                color = EmeraldPrimary
                            )
                            Text(
                                text = "Searching for PC Companion on Wi-Fi...",
                                color = TextSecondary,
                                fontSize = 12.sp
                            )
                        }
                    }
                }

                // Phone Number Field
                OutlinedTextField(
                    value = phoneNumber,
                    onValueChange = { phoneNumber = it },
                    label = { Text("Your WhatsApp Number (with country code)") },
                    placeholder = { Text("e.g. 919876543210") },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedTextColor = TextPrimary,
                        unfocusedTextColor = TextPrimary,
                        focusedBorderColor = EmeraldPrimary,
                        unfocusedBorderColor = ObsidianBorder
                    ),
                    modifier = Modifier.fillMaxWidth()
                )

                // Manual IP Toggle (if auto-discovered, hidden by default to keep it simple)
                if (discoveredPc != null) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { showManualIp = !showManualIp },
                        horizontalArrangement = Arrangement.End
                    ) {
                        Text(
                            text = if (showManualIp) "Hide Manual IP" else "⚙️ Change IP manually",
                            color = TextSecondary,
                            fontSize = 11.sp
                        )
                    }
                }

                // PC IP Field (shown if no discovery or toggled manually)
                AnimatedVisibility(visible = discoveredPc == null || showManualIp) {
                    OutlinedTextField(
                        value = serverIp,
                        onValueChange = { serverIp = it },
                        label = { Text("PC Local Wi-Fi IP") },
                        placeholder = { Text("e.g. 192.168.1.5") },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedTextColor = TextPrimary,
                            unfocusedTextColor = TextPrimary,
                            focusedBorderColor = EmeraldPrimary,
                            unfocusedBorderColor = ObsidianBorder
                        ),
                        modifier = Modifier.fillMaxWidth()
                    )
                }

                if (statusMessage != null) {
                    Surface(
                        color = if (isError) ErrorRed.copy(alpha = 0.15f) else SuccessGreen.copy(alpha = 0.15f),
                        shape = RoundedCornerShape(10.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(
                            text = statusMessage.orEmpty(),
                            color = if (isError) ErrorRed else SuccessGreen,
                            fontSize = 12.sp,
                            modifier = Modifier.padding(10.dp)
                        )
                    }
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    TextButton(
                        onClick = onDismiss,
                        enabled = !isSending,
                        colors = ButtonDefaults.textButtonColors(contentColor = TextSecondary)
                    ) {
                        Text("Cancel")
                    }

                    Spacer(modifier = Modifier.width(8.dp))

                    Button(
                        onClick = {
                            isSending = true
                            isError = false
                            statusMessage = "Uploading 1080p video to PC..."
                            onSend(serverIp, phoneNumber) { result ->
                                isSending = false
                                result.fold(
                                    onSuccess = { msg ->
                                        isError = false
                                        statusMessage = msg
                                    },
                                    onFailure = { err ->
                                        isError = true
                                        statusMessage = err.message ?: "Failed to connect to PC Companion"
                                    }
                                )
                            }
                        },
                        enabled = !isSending && phoneNumber.isNotBlank(),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = EmeraldPrimary,
                            contentColor = ObsidianBackground
                        ),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        if (isSending) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(16.dp),
                                color = ObsidianBackground,
                                strokeWidth = 2.dp
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("Sending...")
                        } else {
                            Icon(imageVector = Icons.AutoMirrored.Filled.Send, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Send to Status", fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }
        }
    }
}
