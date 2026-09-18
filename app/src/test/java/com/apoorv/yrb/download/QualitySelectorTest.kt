package com.apoorv.yrb.download

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class QualitySelectorTest {
    @Test
    fun supportedQualitiesAreDescendingAndComplete() {
        assertEquals(listOf(2160, 1440, 1080, 720, 480, 360), QualitySelector.supported)
    }

    @Test
    fun fileSizeFormatterProducesHumanReadableValues() {
        assertEquals("1.0 MB", FileSizeFormatter.format(1024L * 1024L))
        assertEquals("Size unavailable", FileSizeFormatter.format(null))
    }

    @Test
    fun progressParserReadsBinarySpeed() {
        val speed = ProgressLineParser.speedBytesPerSecond(
            "[download]  42.0% of 100.0MiB at 2.50MiB/s ETA 00:12"
        )
        assertEquals((2.5 * 1024 * 1024).toLong(), speed)
    }

    @Test
    fun progressParserIgnoresLinesWithoutSpeed() {
        assertNull(ProgressLineParser.speedBytesPerSecond("[Merger] Merging formats"))
    }

    @Test
    fun humanErrorPrefersErrorLineOverWarningNoise() {
        val text = """
            WARNING: Your yt-dlp version is old
            WARNING: another warning
            ERROR: Requested format is not available
        """.trimIndent()

        assertTrue(
            ProgressLineParser.humanError(text)
                .contains("Requested format is not available")
        )
    }
}
