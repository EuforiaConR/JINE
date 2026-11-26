package com.euforia.jine

import com.euforia.jine.ui.components.ScrollbarGeometry
import com.euforia.jine.ui.components.calculateScrollbarGeometry
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ChatScrollbarTest {
    @Test
    fun emptyAndNonScrollableHistoriesFillTheTrack() {
        for (content in listOf(0f, 60f, 300f, Float.NaN)) {
            assertEquals(
                ScrollbarGeometry(0f, 80f),
                calculateScrollbarGeometry(80f, 16f, content, 0f, false, false)
            )
        }
    }

    @Test
    fun boundariesOverrideImperfectEstimates() {
        assertEquals(ScrollbarGeometry(0f, 50f), geometry(offset = 110f, backward = false))
        assertEquals(ScrollbarGeometry(50f, 50f), geometry(offset = 3f, forward = false))
    }

    @Test
    fun intermediateProgressFollowsTheEstimatedScrollDistance() {
        assertEquals(ScrollbarGeometry(12.5f, 50f), geometry(offset = 25f))
        assertEquals(ScrollbarGeometry(37.5f, 50f), geometry(offset = 75f))
        assertEquals(ScrollbarGeometry(0f, 50f), geometry(offset = -40f))
        assertEquals(ScrollbarGeometry(50f, 50f), geometry(offset = 1_000f))
    }

    @Test
    fun hugeHistoriesAndShortTracksRespectThumbLimits() {
        val smallThumb = calculateScrollbarGeometry(100f, 16f, 5_000f, 600f, true, true)
        assertEquals(16f, smallThumb.height, 0f)
        assertTrue(smallThumb.top in 0f..84f)
        assertEquals(
            ScrollbarGeometry(0f, 7f),
            calculateScrollbarGeometry(7f, 16f, 300f, 50f, true, false)
        )
    }

    @Test
    fun invalidMeasurementsHaveABoundedFallback() {
        for (height in listOf(0f, -1f, Float.NaN, Float.POSITIVE_INFINITY)) {
            assertEquals(
                ScrollbarGeometry(0f, 0f),
                calculateScrollbarGeometry(height, 16f, 500f, 50f, true, true)
            )
        }
        for (content in listOf(0f, 12f, 100f, Float.NaN, Float.POSITIVE_INFINITY)) {
            assertEquals(
                ScrollbarGeometry(42f, 16f),
                calculateScrollbarGeometry(100f, 16f, content, Float.NaN, true, true)
            )
        }
    }

    private fun geometry(offset: Float, backward: Boolean = true, forward: Boolean = true) =
        calculateScrollbarGeometry(100f, 16f, 200f, offset, backward, forward)
}
