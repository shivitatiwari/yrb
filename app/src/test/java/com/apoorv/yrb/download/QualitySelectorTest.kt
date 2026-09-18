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
        assertEquals("0 B", FileSizeFormatter.format(0L))
        assertEquals("Size unavailable", FileSizeFormatter.format(null))
    }

    @Test
    fun progressParserReadsBinarySpeed() {
        val speed = ProgressLineParser.speedBytesPerSecond(
            "[download] 42.0% of 100.0MiB at 2.50MiB/s ETA 00:12"
        )
        assertEquals((2.5 * 1024 * 1024).toLong(), speed)
    }

    @Test
    fun progressParserReadsModernPercentAndEta() {
        val line = "[download]  37.6% of ~ 480.0MiB at 8.20MiB/s ETA 01:24"
        assertEquals(37.6f, ProgressLineParser.percent(line))
        assertEquals(84L, ProgressLineParser.etaSeconds(line))
    }

    @Test
    fun progressParserReadsHourEta() {
        assertEquals(
            3723L,
            ProgressLineParser.etaSeconds(
                "[download] 4.2% of 4.0GiB at 1.0MiB/s ETA 01:02:03"
            )
        )
    }

    @Test
    fun progressParserIgnoresLinesWithoutSpeed() {
        assertNull(ProgressLineParser.speedBytesPerSecond("[Merger] Merging formats"))
        assertTrue(ProgressLineParser.isMerging("[Merger] Merging formats into x.mp4"))
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
