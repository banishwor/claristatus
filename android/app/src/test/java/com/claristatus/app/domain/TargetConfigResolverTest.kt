package com.claristatus.app.domain

import android.net.Uri
import com.claristatus.app.data.model.VideoMetadata
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class TargetConfigResolverTest {

    @Test
    fun `when duration is exactly 30 seconds, resolves 1080p FHD preset`() {
        val preset = TargetConfigResolver.resolvePreset(durationMs = 30_000L)
        assertEquals(1920, preset.maxEdge)
        assertEquals(3_900_000, preset.targetBitrateBps)
        assertEquals(4_200_000, preset.maxBitrateBps)
        assertEquals("1080p FHD", preset.badgeLabel)
    }

    @Test
    fun `when duration is 29 seconds, resolves 1080p FHD preset`() {
        val preset = TargetConfigResolver.resolvePreset(durationMs = 29_000L)
        assertEquals(1920, preset.maxEdge)
        assertEquals(3_900_000, preset.targetBitrateBps)
    }

    @Test
    fun `when duration exceeds 30 seconds by 1 ms, resolves 720p HD preset`() {
        val preset = TargetConfigResolver.resolvePreset(durationMs = 30_001L)
        assertEquals(1280, preset.maxEdge)
        assertEquals(2_400_000, preset.targetBitrateBps)
        assertEquals("720p HD", preset.badgeLabel)
    }

    @Test
    fun `when duration is 60 seconds, resolves 720p HD preset`() {
        val preset = TargetConfigResolver.resolvePreset(durationMs = 60_000L)
        assertEquals(1280, preset.maxEdge)
        assertEquals(2_400_000, preset.targetBitrateBps)
        assertEquals(2_800_000, preset.maxBitrateBps)
    }

    @Test
    fun `when source video is 720p and duration under 30s, does not upscale`() {
        val metadata = VideoMetadata(
            uri = null,
            durationMs = 15_000L,
            rawWidth = 1280,
            rawHeight = 720
        )
        val preset = TargetConfigResolver.resolvePreset(durationMs = 15_000L, sourceMetadata = metadata)
        assertEquals(1280, preset.maxEdge) // Preserves 1280 without upscaling to 1920
    }

    @Test
    fun `makeEven forces odd numbers to preceding even integer`() {
        assertEquals(1080, TargetConfigResolver.makeEven(1080))
        assertEquals(1078, TargetConfigResolver.makeEven(1079))
        assertEquals(720, TargetConfigResolver.makeEven(721))
        assertEquals(2, TargetConfigResolver.makeEven(1))
    }

    @Test
    fun `calculateTargetDimensions correctly formats portrait to 1152x1920 status canvas`() {
        // 4K portrait: 2160x3840 -> PureStatus WhatsApp Status canvas (1152x1920)
        val (w, h) = TargetConfigResolver.calculateTargetDimensions(
            srcWidth = 2160,
            srcHeight = 3840,
            maxEdge = 1920
        )
        assertEquals(1152, w)
        assertEquals(1920, h)
        assertTrue(w % 2 == 0)
        assertTrue(h % 2 == 0)
    }

    @Test
    fun `calculateTargetDimensions correctly formats landscape to HD 1280x768`() {
        // 4K landscape: 3840x2160 -> max edge 1280
        val (w, h) = TargetConfigResolver.calculateTargetDimensions(
            srcWidth = 3840,
            srcHeight = 2160,
            maxEdge = 1280
        )
        assertEquals(1280, w)
        assertEquals(768, h)
        assertTrue(w % 2 == 0)
        assertTrue(h % 2 == 0)
    }
}
