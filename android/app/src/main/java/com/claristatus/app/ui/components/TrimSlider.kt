package com.claristatus.app.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.claristatus.app.data.model.TrimRange
import com.claristatus.app.ui.theme.*

@Composable
fun TrimSlider(
    totalDurationMs: Long,
    trimRange: TrimRange,
    onTrimChange: (TrimRange) -> Unit,
    modifier: Modifier = Modifier
) {
    val durationFloat = totalDurationMs.toFloat().coerceAtLeast(1.0f)
    val sliderValue = (trimRange.startMs.toFloat() / durationFloat)..(trimRange.endMs.toFloat() / durationFloat)

    Surface(
        modifier = modifier.fillMaxWidth(),
        color = ObsidianCard,
        shape = RoundedCornerShape(16.dp),
        border = BorderStroke(1.dp, ObsidianBorder)
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            // Header showing selected interval & duration
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Trim Selection",
                    style = MaterialTheme.typography.titleMedium,
                    color = TextPrimary,
                    fontWeight = FontWeight.SemiBold
                )

                Surface(
                    color = ObsidianSurface,
                    shape = RoundedCornerShape(8.dp)
                ) {
                    Text(
                        text = "Duration: ${"%.1f".format(trimRange.durationSeconds)}s",
                        style = MaterialTheme.typography.labelLarge,
                        color = if (trimRange.durationMs <= 30_000L) EmeraldPrimary else BadgeHd,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                        fontSize = 12.sp
                    )
                }
            }

            // Material3 RangeSlider
            RangeSlider(
                value = sliderValue,
                onValueChange = { range ->
                    val newStartMs = (range.start * durationFloat).toLong()
                    val newEndMs = (range.endInclusive * durationFloat).toLong()
                    // Minimum 1 second clip duration
                    if (newEndMs - newStartMs >= 1000L) {
                        onTrimChange(TrimRange(startMs = newStartMs, endMs = newEndMs))
                    }
                },
                colors = SliderDefaults.colors(
                    thumbColor = EmeraldPrimary,
                    activeTrackColor = EmeraldPrimary,
                    inactiveTrackColor = ObsidianBorder
                ),
                modifier = Modifier.fillMaxWidth()
            )

            // Bottom timestamp indicators
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    text = "Start: ${trimRange.formatTimestamp(trimRange.startMs)}",
                    style = MaterialTheme.typography.bodyMedium,
                    color = TextSecondary,
                    fontSize = 12.sp
                )
                Text(
                    text = "End: ${trimRange.formatTimestamp(trimRange.endMs)}",
                    style = MaterialTheme.typography.bodyMedium,
                    color = TextSecondary,
                    fontSize = 12.sp
                )
            }
        }
    }
}
