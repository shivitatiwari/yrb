package com.apoorv.yrb.download

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class QualitySelectorTest {
    @Test
    fun supportedQualitiesAreDescendingAndComplete() {
        assertEquals(listOf(2160, 1440, 1080, 720, 480, 360), QualitySelector.supported)
    }

    @Test
    fun selectorTargetsExactRequestedHeightAndHasFallbacks() {
        val selector = QualitySelector.selector(1080)
        assertTrue(selector.contains("height=1080"))
        assertTrue(selector.contains("bestaudio"))
        assertTrue(selector.contains("/"))
    }
}
