package com.claristatus.app.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.claristatus.app.data.model.SplitSegment
import com.claristatus.app.data.model.TrimRange
import com.claristatus.app.ui.theme.*

@Composable
fun PureStatusEditorCard(
    splitSegments: List<SplitSegment>,
    selectedPartIndex: Int,
    qualityOption: String,
    splitDurationMs: Long,
    onSelectPart: (SplitSegment) -> Unit,
    onQualitySelected: (String) -> Unit,
    onSplitDurationSelected: (Long) -> Unit,
    onToggleTrimEditor: () -> Unit,
    onCompress: () -> Unit,
    modifier: Modifier = Modifier
) {
    // Tip Carousel States
    val tips = remember {
        listOf(
            "✨ 1080p Quality: Forward to My Status or use PC Companion for pure 1080p with zero quality loss.",
            "⚡ Hardware Accelerated: Encodes in seconds with zero battery drain.",
            "🎨 Rec.709 Color: Eliminates washed-out hues and keeps colors vibrant.",
            "✂️ Auto-Splitter: Slices your video into consecutive seamless story parts."
        )
    }
    var currentTipIndex by remember { mutableIntStateOf(0) }

    Column(
        modifier = modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        // 1. Floating Tip Capsule (PureStatus style)
        Surface(
            modifier = Modifier
                .fillMaxWidth(0.92f)
                .clip(RoundedCornerShape(24.dp)),
            color = Color(0xFFE2E8F0).copy(alpha = 0.95f),
            shadowElevation = 4.dp
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 10.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                IconButton(
                    onClick = {
                        currentTipIndex = if (currentTipIndex > 0) currentTipIndex - 1 else tips.size - 1
                    },
                    modifier = Modifier.size(28.dp)
                ) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                        contentDescription = "Previous Tip",
                        tint = Color(0xFF1E293B),
                        modifier = Modifier.size(16.dp)
                    )
                }

                Text(
                    text = tips[currentTipIndex],
                    style = MaterialTheme.typography.bodyMedium,
                    color = Color(0xFF1E293B),
                    fontWeight = FontWeight.Medium,
                    textAlign = TextAlign.Center,
                    fontSize = 11.sp,
                    lineHeight = 15.sp,
                    modifier = Modifier
                        .weight(1f)
                        .padding(horizontal = 6.dp)
                )

                IconButton(
                    onClick = {
                        currentTipIndex = if (currentTipIndex < tips.size - 1) currentTipIndex + 1 else 0
                    },
                    modifier = Modifier.size(28.dp)
                ) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.ArrowForward,
                        contentDescription = "Next Tip",
                        tint = Color(0xFF1E293B),
                        modifier = Modifier.size(16.dp)
                    )
                }
            }
        }

        // 2. Main Floating Rounded Card (PureStatus style)
        Surface(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(topStart = 32.dp, topEnd = 32.dp),
            color = Color(0xFFF1F5F9).copy(alpha = 0.98f),
            shadowElevation = 16.dp
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 18.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                // A. Horizontal Video Part Thumbnails
                if (splitSegments.isNotEmpty()) {
                    LazyRow(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.Center,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        items(splitSegments) { segment ->
                            val isSelected = segment.partIndex == selectedPartIndex
                            val borderColor = if (isSelected) Color(0xFF2563EB) else Color.Transparent

                            Surface(
                                modifier = Modifier
                                    .padding(horizontal = 5.dp)
                                    .size(width = 76.dp, height = 76.dp)
                                    .clip(RoundedCornerShape(16.dp))
                                    .clickable { onSelectPart(segment) },
                                shape = RoundedCornerShape(16.dp),
                                border = BorderStroke(if (isSelected) 3.dp else 1.dp, if (isSelected) borderColor else Color(0xFFCBD5E1)),
                                color = Color(0xFF0F172A)
                            ) {
                                Box(modifier = Modifier.fillMaxSize()) {
                                    if (segment.thumbnail != null) {
                                        Image(
                                            bitmap = segment.thumbnail.asImageBitmap(),
                                            contentDescription = "Part ${segment.partIndex}",
                                            contentScale = ContentScale.Crop,
                                            modifier = Modifier.fillMaxSize()
                                        )
                                    } else {
                                        Box(
                                            modifier = Modifier.fillMaxSize(),
                                            contentAlignment = Alignment.Center
                                        ) {
                                            Icon(
                                                imageVector = Icons.Default.PlayCircle,
                                                contentDescription = null,
                                                tint = Color.White.copy(alpha = 0.6f),
                                                modifier = Modifier.size(24.dp)
                                            )
                                        }
                                    }

                                    // Duration Badge pill on bottom-left
                                    Surface(
                                        modifier = Modifier
                                            .align(Alignment.BottomStart)
                                            .padding(5.dp),
                                        shape = RoundedCornerShape(6.dp),
                                        color = Color.Black.copy(alpha = 0.75f)
                                    ) {
                                        Text(
                                            text = formatShortDuration(segment.trimRange),
                                            color = Color.White,
                                            fontSize = 10.sp,
                                            fontWeight = FontWeight.Bold,
                                            modifier = Modifier.padding(horizontal = 5.dp, vertical = 2.dp)
                                        )
                                    }
                                }
                            }
                        }
                    }
                }

                // B. "Trim Video ✂️" Pill Button
                Surface(
                    modifier = Modifier
                        .clip(RoundedCornerShape(20.dp))
                        .clickable { onToggleTrimEditor() },
                    shape = RoundedCornerShape(20.dp),
                    color = Color.White,
                    shadowElevation = 2.dp,
                    border = BorderStroke(1.dp, Color(0xFFE2E8F0))
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 18.dp, vertical = 7.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.ContentCut,
                            contentDescription = null,
                            tint = Color(0xFF0F172A),
                            modifier = Modifier.size(16.dp)
                        )
                        Text(
                            text = "Trim Video",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = Color(0xFF0F172A),
                            fontSize = 14.sp
                        )
                    }
                }

                // C. Two Segmented Toggles Side-by-Side: Quality & Split Time
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // Left Toggle: Quality
                    Column(
                        modifier = Modifier.weight(1f),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        Text(
                            text = "Quality",
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.Bold,
                            color = Color(0xFF334155),
                            fontSize = 12.sp
                        )

                        Surface(
                            shape = RoundedCornerShape(20.dp),
                            color = Color.White,
                            border = BorderStroke(1.dp, Color(0xFFCBD5E1)),
                            modifier = Modifier.fillMaxWidth(0.95f)
                        ) {
                            Row(
                                modifier = Modifier.padding(3.dp),
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                // Full HD Pill (Disabled when 60s is selected)
                                val is1080pDisabled = splitDurationMs > 30_000L
                                val isFhd = qualityOption == "1080p" && !is1080pDisabled

                                Surface(
                                    modifier = Modifier
                                        .weight(1f)
                                        .clip(RoundedCornerShape(16.dp))
                                        .clickable(enabled = !is1080pDisabled) { onQualitySelected("1080p") },
                                    shape = RoundedCornerShape(16.dp),
                                    color = when {
                                        is1080pDisabled -> Color(0xFFE2E8F0).copy(alpha = 0.4f)
                                        isFhd -> Color(0xFF94A3B8).copy(alpha = 0.35f)
                                        else -> Color.Transparent
                                    }
                                ) {
                                    Column(
                                        modifier = Modifier.padding(vertical = 4.dp),
                                        horizontalAlignment = Alignment.CenterHorizontally
                                    ) {
                                        Text(
                                            text = "Full HD",
                                            fontSize = 11.sp,
                                            fontWeight = FontWeight.Bold,
                                            color = when {
                                                is1080pDisabled -> Color(0xFF94A3B8).copy(alpha = 0.6f)
                                                isFhd -> Color(0xFF0F172A)
                                                else -> Color(0xFF64748B)
                                            }
                                        )
                                        Surface(
                                            color = if (is1080pDisabled) Color(0xFF94A3B8) else Color(0xFFEF4444),
                                            shape = RoundedCornerShape(6.dp)
                                        ) {
                                            Text(
                                                text = if (is1080pDisabled) "30s only" else "1080p",
                                                color = Color.White,
                                                fontSize = 9.sp,
                                                fontWeight = FontWeight.Bold,
                                                modifier = Modifier.padding(horizontal = 4.dp, vertical = 1.dp)
                                            )
                                        }
                                    }
                                }

                                // HD Pill
                                val isHd = qualityOption == "720p"
                                Surface(
                                    modifier = Modifier
                                        .weight(1f)
                                        .clip(RoundedCornerShape(16.dp))
                                        .clickable { onQualitySelected("720p") },
                                    shape = RoundedCornerShape(16.dp),
                                    color = if (isHd) Color(0xFF94A3B8).copy(alpha = 0.35f) else Color.Transparent
                                ) {
                                    Column(
                                        modifier = Modifier.padding(vertical = 4.dp),
                                        horizontalAlignment = Alignment.CenterHorizontally
                                    ) {
                                        Text(
                                            text = "HD",
                                            fontSize = 11.sp,
                                            fontWeight = FontWeight.Bold,
                                            color = if (isHd) Color(0xFF0F172A) else Color(0xFF64748B)
                                        )
                                        Surface(
                                            color = Color(0xFFEF4444),
                                            shape = RoundedCornerShape(6.dp)
                                        ) {
                                            Text(
                                                text = "720p",
                                                color = Color.White,
                                                fontSize = 9.sp,
                                                fontWeight = FontWeight.Bold,
                                                modifier = Modifier.padding(horizontal = 4.dp, vertical = 1.dp)
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }

                    Spacer(modifier = Modifier.width(10.dp))

                    // Right Toggle: Split Time
                    Column(
                        modifier = Modifier.weight(1f),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        Text(
                            text = "Split Time",
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.Bold,
                            color = Color(0xFF334155),
                            fontSize = 12.sp
                        )

                        Surface(
                            shape = RoundedCornerShape(20.dp),
                            color = Color.White,
                            border = BorderStroke(1.dp, Color(0xFFCBD5E1)),
                            modifier = Modifier.fillMaxWidth(0.95f)
                        ) {
                            Row(
                                modifier = Modifier.padding(3.dp),
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                val is30s = splitDurationMs == 30_000L
                                Surface(
                                    modifier = Modifier
                                        .weight(1f)
                                        .clip(RoundedCornerShape(16.dp))
                                        .clickable { onSplitDurationSelected(30_000L) },
                                    shape = RoundedCornerShape(16.dp),
                                    color = if (is30s) Color(0xFF94A3B8).copy(alpha = 0.45f) else Color.Transparent
                                ) {
                                    Text(
                                        text = "30s",
                                        fontSize = 13.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = if (is30s) Color(0xFF0F172A) else Color(0xFF64748B),
                                        textAlign = TextAlign.Center,
                                        modifier = Modifier.padding(vertical = 8.dp)
                                    )
                                }

                                val is60s = splitDurationMs == 60_000L
                                Surface(
                                    modifier = Modifier
                                        .weight(1f)
                                        .clip(RoundedCornerShape(16.dp))
                                        .clickable { onSplitDurationSelected(60_000L) },
                                    shape = RoundedCornerShape(16.dp),
                                    color = if (is60s) Color(0xFF94A3B8).copy(alpha = 0.45f) else Color.Transparent
                                ) {
                                    Text(
                                        text = "60s",
                                        fontSize = 13.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = if (is60s) Color(0xFF0F172A) else Color(0xFF64748B),
                                        textAlign = TextAlign.Center,
                                        modifier = Modifier.padding(vertical = 8.dp)
                                    )
                                }
                            }
                        }
                    }
                }

                // Primary Action Button (Single Clean 1080p CTA)
                val partCount = splitSegments.size.coerceAtLeast(1)
                val isSinglePart = partCount == 1

                Button(
                    onClick = onCompress,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(56.dp),
                    shape = RoundedCornerShape(20.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = EmeraldPrimary,
                        contentColor = ObsidianBackground
                    ),
                    elevation = ButtonDefaults.buttonElevation(defaultElevation = 3.dp)
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.Bolt,
                            contentDescription = null,
                            modifier = Modifier.size(20.dp),
                            tint = ObsidianBackground
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Text(
                                text = if (isSinglePart) "Compress in $qualityOption" else "Compress All $partCount Stories in $qualityOption",
                                fontWeight = FontWeight.Bold,
                                fontSize = 15.sp,
                                color = ObsidianBackground
                            )
                            Text(
                                text = "1152×1920 • Rec.709 • 4.1 Mbps",
                                fontSize = 11.sp,
                                color = ObsidianBackground.copy(alpha = 0.8f),
                                fontWeight = FontWeight.Medium
                            )
                        }
                    }
                }
            }
        }
    }
}

private fun formatShortDuration(trimRange: TrimRange): String {
    val totalSeconds = trimRange.durationMs / 1000
    val minutes = totalSeconds / 60
    val seconds = totalSeconds % 60
    return String.format("%d:%02d", minutes, seconds)
}
