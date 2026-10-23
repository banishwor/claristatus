package com.claristatus.app

import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.core.content.ContextCompat
import androidx.lifecycle.viewmodel.compose.viewModel
import com.claristatus.app.data.model.CompressionPreset
import com.claristatus.app.data.model.SplitSegment
import com.claristatus.app.data.model.TrimRange
import com.claristatus.app.data.model.VideoMetadata
import com.claristatus.app.service.TranscodeState
import com.claristatus.app.ui.MainViewModel
import com.claristatus.app.ui.components.*
import com.claristatus.app.ui.theme.*

class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (ContextCompat.checkSelfPermission(this, android.Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
                requestPermissions(arrayOf(android.Manifest.permission.POST_NOTIFICATIONS), 101)
            }
        }

        setContent {
            ClariStatusTheme {
                val viewModel: MainViewModel = viewModel()
                val context = LocalContext.current

                val selectedUri by viewModel.selectedUri.collectAsState()
                val metadata by viewModel.metadata.collectAsState()
                val bgFrameBitmap by viewModel.bgFrameBitmap.collectAsState()
                val splitSegments by viewModel.splitSegments.collectAsState()
                val selectedPartIndex by viewModel.selectedPartIndex.collectAsState()
                val splitDurationMs by viewModel.splitDurationMs.collectAsState()
                val qualityOption by viewModel.qualityOption.collectAsState()
                val overallTrimRange by viewModel.overallTrimRange.collectAsState()
                val transcodeState by viewModel.transcodeState.collectAsState()
                val isLoadingMetadata by viewModel.isLoadingMetadata.collectAsState()
                val durationError by viewModel.durationError.collectAsState()
                val showTrimEditor by viewModel.showTrimEditor.collectAsState()

                val photoPickerLauncher = rememberLauncherForActivityResult(
                    contract = ActivityResultContracts.PickVisualMedia()
                ) { uri ->
                    if (uri != null) {
                        viewModel.onVideoPicked(uri)
                    }
                }

                val activity = context as? ComponentActivity
                var lastBackPressTime by remember { mutableLongStateOf(0L) }

                BackHandler {
                    if (selectedUri != null) {
                        viewModel.reset()
                    } else {
                        val currentTime = System.currentTimeMillis()
                        if (currentTime - lastBackPressTime < 2000L) {
                            activity?.finish()
                        } else {
                            lastBackPressTime = currentTime
                            Toast.makeText(context, "Press back again to exit", Toast.LENGTH_SHORT).show()
                        }
                    }
                }

                // 4-Minute Limit Alert Dialog
                if (durationError != null) {
                    AlertDialog(
                        onDismissRequest = { viewModel.dismissDurationError() },
                        icon = { Icon(imageVector = Icons.Default.Warning, contentDescription = null, tint = ErrorRed) },
                        title = { Text("Video Exceeds Limit", fontWeight = FontWeight.Bold) },
                        text = { Text(durationError ?: "") },
                        confirmButton = {
                            Button(
                                onClick = {
                                    viewModel.dismissDurationError()
                                    photoPickerLauncher.launch(
                                        PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.VideoOnly)
                                    )
                                },
                                colors = ButtonDefaults.buttonColors(containerColor = EmeraldPrimary, contentColor = ObsidianBackground)
                            ) {
                                Text("Choose Another Video", fontWeight = FontWeight.Bold)
                            }
                        },
                        dismissButton = {
                            TextButton(onClick = { viewModel.dismissDurationError() }) {
                                Text("Dismiss", color = TextSecondary)
                            }
                        }
                    )
                }

                Scaffold(
                    modifier = Modifier.fillMaxSize(),
                    containerColor = ObsidianBackground
                ) { innerPadding ->
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(innerPadding)
                    ) {
                        if (selectedUri == null) {
                            // 1. Initial State: Empty / Picker Screen
                            EmptyPickerScreen(
                                onPickVideo = {
                                    photoPickerLauncher.launch(
                                        PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.VideoOnly)
                                    )
                                }
                            )
                        } else {
                            // 2. Video Selected: Blurred Static Background Frame + PureStatus Bottom Card
                            Box(modifier = Modifier.fillMaxSize()) {
                                // Static blurred frame backdrop (No playing video)
                                if (bgFrameBitmap != null) {
                                    Image(
                                        bitmap = bgFrameBitmap!!.asImageBitmap(),
                                        contentDescription = null,
                                        contentScale = ContentScale.Crop,
                                        modifier = Modifier
                                            .fillMaxSize()
                                            .blur(radius = 28.dp)
                                    )
                                    // Subtle dark vignette/scrim overlay
                                    Box(
                                        modifier = Modifier
                                            .fillMaxSize()
                                            .background(Color.Black.copy(alpha = 0.40f))
                                    )
                                } else {
                                    Box(
                                        modifier = Modifier
                                            .fillMaxSize()
                                            .background(ObsidianBackground)
                                    )
                                }

                                // Top Controls: Status Quality Pill & Red Close (X)
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .statusBarsPadding()
                                        .padding(horizontal = 16.dp, vertical = 12.dp),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Surface(
                                        color = ObsidianCard,
                                        shape = RoundedCornerShape(16.dp),
                                        border = BorderStroke(1.dp, ObsidianBorder)
                                    ) {
                                        Row(
                                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                                            verticalAlignment = Alignment.CenterVertically,
                                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                                        ) {
                                            Icon(
                                                imageVector = Icons.Default.AutoAwesome,
                                                contentDescription = null,
                                                tint = EmeraldPrimary,
                                                modifier = Modifier.size(16.dp)
                                            )
                                            Text(
                                                text = "1080p Ultra HD",
                                                style = MaterialTheme.typography.labelLarge,
                                                color = TextPrimary
                                            )
                                        }
                                    }

                                    // Red circular Close (X) button
                                    Surface(
                                        modifier = Modifier
                                            .size(38.dp)
                                            .clip(CircleShape)
                                            .clickable { viewModel.reset() },
                                        color = Color(0xFFEF4444),
                                        shadowElevation = 6.dp
                                    ) {
                                        Box(contentAlignment = Alignment.Center) {
                                            Icon(
                                                imageVector = Icons.Default.Close,
                                                contentDescription = "Close",
                                                tint = Color.White,
                                                modifier = Modifier.size(22.dp)
                                            )
                                        }
                                    }
                                }

                                // Floating Bottom Sheet
                                PureStatusEditorCard(
                                    splitSegments = splitSegments,
                                    selectedPartIndex = selectedPartIndex,
                                    qualityOption = qualityOption,
                                    splitDurationMs = splitDurationMs,
                                    onSelectPart = { segment -> viewModel.selectPart(segment) },
                                    onQualitySelected = { q -> viewModel.onQualityOptionSelected(q) },
                                    onSplitDurationSelected = { ms -> viewModel.onSplitDurationSelected(ms) },
                                    onToggleTrimEditor = { viewModel.toggleTrimEditor(true) },
                                    onCompress = {
                                        viewModel.startBatchCompression(context)
                                    },
                                    modifier = Modifier
                                        .align(Alignment.BottomCenter)
                                        .navigationBarsPadding()
                                )
                            }
                        }

                        // Manual "Edit Video" Trimmer Dialog (Across whole video)
                        if (showTrimEditor && metadata != null) {
                            Dialog(onDismissRequest = { viewModel.toggleTrimEditor(false) }) {
                                Surface(
                                    shape = RoundedCornerShape(24.dp),
                                    color = ObsidianSurface,
                                    border = BorderStroke(1.dp, ObsidianBorder),
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Column(
                                        modifier = Modifier.padding(20.dp),
                                        verticalArrangement = Arrangement.spacedBy(16.dp),
                                        horizontalAlignment = Alignment.CenterHorizontally
                                    ) {
                                         Text(
                                            text = "Trim Video",
                                            style = MaterialTheme.typography.titleLarge,
                                            fontWeight = FontWeight.Bold,
                                            color = TextPrimary
                                         )

                                        val selectedSec = overallTrimRange.durationSeconds
                                        val partsCount = splitSegments.size
                                        Text(
                                            text = "Selected: ${"%.1f".format(selectedSec)}s • Auto-splits into $partsCount ${if (partsCount == 1) "Story" else "Stories"}",
                                            style = MaterialTheme.typography.bodyMedium,
                                            color = EmeraldPrimary,
                                            fontWeight = FontWeight.SemiBold
                                        )

                                        TrimSlider(
                                            totalDurationMs = metadata?.durationMs ?: 30_000L,
                                            trimRange = overallTrimRange,
                                            onTrimChange = { newRange -> viewModel.onOverallTrimRangeChanged(newRange) }
                                        )

                                        Button(
                                            onClick = { viewModel.toggleTrimEditor(false) },
                                            modifier = Modifier.fillMaxWidth(),
                                            colors = ButtonDefaults.buttonColors(containerColor = EmeraldPrimary, contentColor = ObsidianBackground)
                                        ) {
                                            Text("Done", fontWeight = FontWeight.Bold)
                                        }
                                    }
                                }
                            }
                        }

                        // Transcode States Dialogs
                        when (val state = transcodeState) {
                            is TranscodeState.Processing -> {
                                TranscodeProgressDialog(
                                    progressPercentage = state.progressPercentage,
                                    currentPart = state.currentPart,
                                    totalParts = state.totalParts,
                                    onCancel = { viewModel.cancelCompression() }
                                )
                            }
                            is TranscodeState.BatchSuccess -> {
                                val savedIp = com.claristatus.app.domain.CompanionClient.getServerIp(context)
                                val savedPhone = com.claristatus.app.domain.CompanionClient.getPhoneNumber(context)
                                BatchExportSuccessDialog(
                                    batchState = state,
                                    defaultServerIp = savedIp,
                                    defaultPhoneNumber = savedPhone,
                                    onShareSinglePart = { file -> viewModel.shareSinglePart(context, file) },
                                    onSendSingleViaCompanion = { file, ip, phone, onResult ->
                                        viewModel.sendViaCompanion(context, file, ip, phone, onResult)
                                    },
                                    onDone = { viewModel.reset() }
                                )
                            }
                            is TranscodeState.Success -> {
                                val savedIp = com.claristatus.app.domain.CompanionClient.getServerIp(context)
                                val savedPhone = com.claristatus.app.domain.CompanionClient.getPhoneNumber(context)
                                ExportSuccessDialog(
                                    successState = state,
                                    originalSizeBytes = metadata?.fileSizeBytes ?: 0L,
                                    defaultServerIp = savedIp,
                                    defaultPhoneNumber = savedPhone,
                                    onShare = { viewModel.shareSinglePart(context, state.outputFile) },
                                    onSendViaCompanion = { ip, phone, onResult ->
                                        viewModel.sendViaCompanion(context, state.outputFile, ip, phone, onResult)
                                    },
                                    onDone = { viewModel.reset() }
                                )
                            }
                            is TranscodeState.Error -> {
                                AlertDialog(
                                    onDismissRequest = { viewModel.cancelCompression() },
                                    title = { Text("Compression Failed") },
                                    text = { Text(state.message) },
                                    confirmButton = {
                                        Button(onClick = { viewModel.cancelCompression() }) {
                                            Text("OK")
                                        }
                                    }
                                )
                            }
                            else -> { /* Idle or Cancelled */ }
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun EmptyPickerScreen(
    onPickVideo: () -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.SpaceBetween
    ) {
        // Top Bar
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = stringResource(R.string.app_name),
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.primary
            )

            Surface(
                color = ObsidianCard,
                shape = RoundedCornerShape(16.dp),
                border = BorderStroke(1.dp, ObsidianBorder)
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.AutoAwesome,
                        contentDescription = null,
                        tint = EmeraldPrimary,
                        modifier = Modifier.size(16.dp)
                    )
                    Text(
                        text = "1080p Ultra HD",
                        style = MaterialTheme.typography.labelLarge,
                        color = TextPrimary
                    )
                }
            }
        }

        // Hero Center Section
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(18.dp)
        ) {
            Surface(
                modifier = Modifier.size(100.dp),
                shape = RoundedCornerShape(30.dp),
                color = ObsidianCard,
                border = BorderStroke(1.dp, ObsidianBorder)
            ) {
                Box(
                    contentAlignment = Alignment.Center,
                    modifier = Modifier.fillMaxSize()
                ) {
                    Icon(
                        imageVector = Icons.Default.VideoLibrary,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(48.dp)
                    )
                }
            }

            Text(
                text = "Crystal-Clear Status",
                style = MaterialTheme.typography.headlineLarge,
                color = TextPrimary,
                textAlign = TextAlign.Center
            )

            Text(
                text = "Compress without blur. Auto-splits your video into consecutive 1080p FHD story parts ready for WhatsApp Status.",
                style = MaterialTheme.typography.bodyLarge,
                color = TextSecondary,
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(horizontal = 16.dp)
            )

            // Feature Badges
            Row(
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                FeaturePill(icon = Icons.Default.ElectricBolt, label = "Hardware Fast")
                FeaturePill(icon = Icons.Default.Lock, label = "100% Offline")
                FeaturePill(icon = Icons.Default.AutoAwesome, label = "Story Splitter")
            }
        }

        // Primary Select Button
        Button(
            onClick = onPickVideo,
            modifier = Modifier
                .fillMaxWidth()
                .height(56.dp),
            colors = ButtonDefaults.buttonColors(
                containerColor = MaterialTheme.colorScheme.primary,
                contentColor = ObsidianBackground
            ),
            shape = RoundedCornerShape(16.dp)
        ) {
            Icon(imageVector = Icons.Default.Add, contentDescription = null, modifier = Modifier.size(20.dp))
            Spacer(modifier = Modifier.width(8.dp))
            Text(
                text = "Select Video from Gallery",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold
            )
        }
    }
}

@Composable
fun FeaturePill(icon: androidx.compose.ui.graphics.vector.ImageVector, label: String) {
    Surface(
        color = ObsidianCard,
        shape = RoundedCornerShape(12.dp),
        border = BorderStroke(1.dp, ObsidianBorder)
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = EmeraldPrimary,
                modifier = Modifier.size(14.dp)
            )
            Text(
                text = label,
                style = MaterialTheme.typography.bodyMedium,
                color = TextSecondary,
                fontSize = 11.sp
            )
        }
    }
}
