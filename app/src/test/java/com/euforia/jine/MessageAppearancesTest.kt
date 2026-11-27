package com.euforia.jine

import com.euforia.jine.ui.components.MessageArrivalTracker
import com.euforia.jine.ui.components.AppearanceUpdate
import com.euforia.jine.ui.components.MessageAppearances
import com.euforia.jine.ui.components.MessageFadeWindow
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class MessageAppearancesTest {
    @Test
    fun initialHistoryIncludingAReattachedScreenIsAlreadyEstablished() {
        val history = listOf("existing-ame", "existing-pchan")
        val tracker = MessageArrivalTracker(history)
        assertTrue(history.all(tracker::knows))
        assertTrue(tracker.register(history.toList()).isEmpty())
        assertFalse(tracker.knows("incoming"))
        assertEquals(listOf("incoming"), tracker.register(history + "incoming"))

        val reattached = MessageArrivalTracker(history + "incoming")
        assertTrue(reattached.register(history + "incoming").isEmpty())
        assertEquals(listOf("after-reattachment"), reattached.register(history + listOf("incoming", "after-reattachment")))
    }

    @Test
    fun anEmptyHistoryReportsEveryNewIdInOrderOnlyOnce() {
        val tracker = MessageArrivalTracker(emptyList())
        assertEquals(listOf("ame", "text", "sticker-1", "sticker-2"),
            tracker.register(listOf("ame", "text", "sticker-1", "sticker-2", "sticker-1")))
        assertTrue(tracker.register(listOf("sticker-2", "ame", "sticker-1", "text")).isEmpty())
        assertTrue(tracker.register(emptyList()).isEmpty())
        assertTrue(tracker.register(listOf("text", "sticker-2")).isEmpty())
    }

    @Test
    fun aWindowFadesLinearlyFromObservationAndStaysOpaqueAfterItsDeadline() {
        val first = MessageFadeWindow(nanos(1_000), nanos(200))
        for ((elapsed, alpha) in listOf(-1L to 0f, 0L to 0f, 50L to 0.25f, 100L to 0.5f, 150L to 0.75f,
            200L to 1f, 800L to 1f)) {
            assertEquals(alpha, first.alphaAt(nanos(1_000 + elapsed)), 0f)
        }
    }

    @Test
    fun overlappingArrivalsAndDisabledMotionHaveIndependentDeadlines() {
        val first = MessageFadeWindow(nanos(0), nanos(200))
        val second = MessageFadeWindow(nanos(100), nanos(200))
        assertEquals(0.5f, first.alphaAt(nanos(100)), 0f)
        assertEquals(0f, second.alphaAt(nanos(100)), 0f)
        assertEquals(1f, first.alphaAt(nanos(200)), 0f)
        assertEquals(0.5f, second.alphaAt(nanos(200)), 0f)
        assertEquals(1f, MessageFadeWindow(nanos(100), 0).alphaAt(nanos(100)), 0f)
    }

    @Test
    fun arrivalAndRealFadeCompletionAreReportedOnceEvenIfTheAlphaIsQueriedAgain() {
        val tracker = MessageAppearances(listOf("initial"))
        val updated = listOf("initial", "first", "second")
        assertEquals(AppearanceUpdate(listOf("first", "second")), tracker.observe(updated, nanos(0), nanos(200)))
        assertTrue(tracker.advance(nanos(199)).isEmpty())
        assertEquals(setOf("first", "second"), tracker.advance(nanos(200)).toSet())
        assertFalse(tracker.hasActiveFades)
        assertEquals(1f, tracker.alpha("first"), 0f)
        assertEquals(AppearanceUpdate(emptyList()), tracker.observe(updated, nanos(200), nanos(200)))
        assertTrue(tracker.advance(nanos(300)).isEmpty())
    }

    @Test
    fun reducingMotionToZeroFinishesTheActiveFadesAndAnyArrivalsInTheSameUpdate() {
        val tracker = MessageAppearances(listOf("initial"))
        assertEquals(AppearanceUpdate(listOf("fading")), tracker.observe(listOf("initial", "fading"), nanos(0), nanos(200)))
        val result = tracker.observe(listOf("initial", "fading", "instant"), nanos(100), 0)
        assertEquals(AppearanceUpdate(listOf("instant"), listOf("fading", "instant")), result)
        assertEquals(1f, tracker.alpha("fading"), 0f)
        assertEquals(1f, tracker.alpha("instant"), 0f)
        assertEquals(AppearanceUpdate(emptyList()), tracker.observe(listOf("initial", "fading", "instant"), nanos(100), 0))
        assertTrue(tracker.advance(nanos(400)).isEmpty())
    }

    private fun nanos(milliseconds: Long) = milliseconds * 1_000_000
}
