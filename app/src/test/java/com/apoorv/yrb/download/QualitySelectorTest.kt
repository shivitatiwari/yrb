package com.apoorv.yrb.download

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assert.assertThrows
import org.junit.Test

class QualitySelectorTest {
    @Test
    fun supportedQualitiesAreDescendingAndComplete() {
        assertEquals(listOf(2160, 1440, 1080, 720, 480, 360), QualitySelector.supported)
    }

    @Test
    fun anonymousFallbackOrderIsStable() {
        assertEquals(
            listOf("default", "ipv4", "web_safari_hls", "web_embedded"),
            YtDlpClient.anonymousFallbackNamesForTest()
        )
    }

    @Test
    fun authenticatedYouTubeClientAvoidsBrokenLoggedInDefault() {
        assertEquals(
            "youtube:player_client=default,web_embedded",
            YtDlpClient.authenticatedExtractorArgsForTest()
        )
    }

    @Test
    fun download403IsRecoverable() {
        assertTrue(
            YtDlpClient.isRecoverableDownloadError(
                IllegalStateException(
                    "ERROR: unable to download video data: HTTP Error 403: Forbidden"
                )
            )
        )
    }

    @Test
    fun emptyFileFailureIsRecoverable() {
        assertTrue(
            YtDlpClient.isRecoverableDownloadError(
                IllegalStateException("The downloaded file is empty")
            )
        )
    }

    @Test
    fun acceptsNetscapeYouTubeSessionCookies() {
        val text = """
            # Netscape HTTP Cookie File
            .youtube.com	TRUE	/	TRUE	1893456000	SID	redacted
            .google.com	TRUE	/	TRUE	1893456000	HSID	redacted
        """.trimIndent()

        assertEquals(2, SessionCookieValidator.validateAndCount(text))
    }

    @Test
    fun detectsAuthenticatedYouTubeSessionCookies() {
        val text = """
            # Netscape HTTP Cookie File
            .youtube.com	TRUE	/	TRUE	0	SAPISID	redacted
            .youtube.com	TRUE	/	TRUE	0	YSC	visitor
        """.trimIndent()

        assertTrue(SessionCookieValidator.looksAuthenticated(text))
    }

    @Test
    fun rejectsCookieFilesWithoutYouTubeOrGoogleSession() {
        val text = """
            # Netscape HTTP Cookie File
            .example.com	TRUE	/	TRUE	1893456000	foo	bar
        """.trimIndent()

        assertThrows(IllegalStateException::class.java) {
            SessionCookieValidator.validateAndCount(text)
        }
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
