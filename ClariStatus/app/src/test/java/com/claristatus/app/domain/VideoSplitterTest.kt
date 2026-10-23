package com.claristatus.app.domain

import com.claristatus.app.data.model.TrimRange
import org.junit.Assert.*
import org.junit.Test

class VideoSplitterTest {

    @Test
    fun `isDurationSupported accepts up to 4 minutes`() {
        assertTrue(VideoSplitter.isDurationSupported(30_000L))
        assertTrue(VideoSplitter.isDurationSupported(240_000L)) // 4 minutes exact
        assertFalse(VideoSplitter.isDurationSupported(240_001L)) // Exceeds 4 minutes
        assertFalse(VideoSplitter.isDurationSupported(0L))
    }

    @Test
    fun `calculateSplits returns single segment for clips under 30s`() {
        val splits = VideoSplitter.calculateSplits(25_000L)
        assertEquals(1, splits.size)
        assertEquals(0L, splits[0].trimRange.startMs)
        assertEquals(25_000L, splits[0].trimRange.endMs)
        assertEquals(1, splits[0].partIndex)
        assertEquals(1, splits[0].totalParts)
    }

    @Test
    fun `calculateSplits correctly divides 75s video into 3 consecutive 30s parts`() {
        val splits = VideoSplitter.calculateSplits(75_000L)
        assertEquals(3, splits.size)

        // Part 1: 0 - 30s
        assertEquals(0L, splits[0].trimRange.startMs)
        assertEquals(30_000L, splits[0].trimRange.endMs)
        assertEquals(1, splits[0].partIndex)
        assertEquals(3, splits[0].totalParts)

        // Part 2: 30 - 60s
        assertEquals(30_000L, splits[1].trimRange.startMs)
        assertEquals(60_000L, splits[1].trimRange.endMs)
        assertEquals(2, splits[1].partIndex)

        // Part 3: 60 - 75s
        assertEquals(60_000L, splits[2].trimRange.startMs)
        assertEquals(75_000L, splits[2].trimRange.endMs)
        assertEquals(3, splits[2].partIndex)
    }

    @Test
    fun `calculateSplitsForRange trims custom window into parts`() {
        // User trimmed whole video from 10s to 75s (65s total)
        val trimmed = TrimRange(10_000L, 75_000L)
        val splits = VideoSplitter.calculateSplitsForRange(trimmed, 30_000L)
        assertEquals(3, splits.size)
        // Part 1: 10..40s
        assertEquals(10_000L, splits[0].trimRange.startMs)
        assertEquals(40_000L, splits[0].trimRange.endMs)
        // Part 2: 40..70s
        assertEquals(40_000L, splits[1].trimRange.startMs)
        assertEquals(70_000L, splits[1].trimRange.endMs)
        // Part 3: 70..75s
        assertEquals(70_000L, splits[2].trimRange.startMs)
        assertEquals(75_000L, splits[2].trimRange.endMs)
    }

    @Test
    fun `calculateSplits clamps to 4 minutes maximum`() {
        val splits = VideoSplitter.calculateSplits(300_000L) // 5 minutes input
        assertEquals(8, splits.size) // 240s / 30s = 8 parts
        assertEquals(240_000L, splits.last().trimRange.endMs)
    }
}
