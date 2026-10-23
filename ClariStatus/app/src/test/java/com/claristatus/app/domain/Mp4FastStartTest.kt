package com.claristatus.app.domain

import org.junit.Assert.*
import org.junit.Test
import java.io.File

class Mp4FastStartTest {

    @Test
    fun `process successfully converts MP4 with moov at end to faststart`() {
        val testSource = File("d:\\Project\\Reverse Engineer\\Story_Part1.mp4")
        if (!testSource.exists()) return

        // Make a working copy for test
        val copy = File("d:\\Project\\Reverse Engineer\\Story_Part1_faststart_test.mp4")
        testSource.copyTo(copy, overwrite = true)

        val success = Mp4FastStart.process(copy)
        assertTrue(success)
        assertTrue(copy.exists())
        assertEquals(testSource.length(), copy.length())
    }
}
