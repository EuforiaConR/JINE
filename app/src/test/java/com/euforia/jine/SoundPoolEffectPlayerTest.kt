package com.euforia.jine

import com.euforia.jine.core.audio.SoundEffect
import com.euforia.jine.core.audio.SoundEffectLoadState
import com.euforia.jine.core.audio.SoundPoolDriver
import com.euforia.jine.core.audio.SoundPoolEffectPlayer
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.IOException

class SoundPoolEffectPlayerTest {
    @Test
    fun preloadsAllEffectsAndNeverQueuesAttemptsMadeBeforeTheirCallbacks() {
        val driver = FakePool()
        val warnings = mutableListOf<String>()
        val player = SoundPoolEffectPlayer(driver, 8) { message, _ -> warnings.add(message) }
        try {
            assertEquals(SoundEffect.entries.toList(), driver.loads)
            assertTrue(SoundEffect.entries.all { player.loadState(it) == SoundEffectLoadState.LOADING })
            assertFalse(player.play(SoundEffect.CLICK, 1f))
            driver.complete(SoundEffect.CLICK)
            driver.complete(SoundEffect.SEND_STAMP, status = -1)
            assertTrue(driver.plays.isEmpty())
            assertFalse(player.play(SoundEffect.SEND_STAMP, 1f))
            assertEquals(SoundEffectLoadState.FAILED, player.loadState(SoundEffect.SEND_STAMP))
            assertTrue(player.play(SoundEffect.CLICK, 0.4f))
            assertEquals(listOf(driver.sampleId(SoundEffect.CLICK) to 0.4f), driver.plays)
            assertTrue(warnings.single().contains(SoundEffect.SEND_STAMP.assetPath))
        } finally {
            player.release()
        }
    }

    @Test
    fun openFailureLoadZeroAndPlayZeroAreIndependentAndUsefulWarningsAreBounded() {
        val driver = FakePool(loadFailure = SoundEffect.CLICK, loadZero = SoundEffect.RECEIVE)
        val warnings = mutableListOf<String>()
        val player = SoundPoolEffectPlayer(driver, 8) { message, _ -> warnings.add(message) }
        try {
            assertEquals(SoundEffectLoadState.FAILED, player.loadState(SoundEffect.CLICK))
            assertEquals(SoundEffectLoadState.FAILED, player.loadState(SoundEffect.RECEIVE))
            driver.complete(SoundEffect.SEND_STAMP)
            driver.rejectPlayback = true
            repeat(3) { assertFalse(player.play(SoundEffect.SEND_STAMP, 1f)) }
            assertEquals(3, warnings.size)
            driver.rejectPlayback = false
            assertTrue(player.play(SoundEffect.SEND_STAMP, 1f))
        } finally {
            player.release()
        }
    }

    @Test
    fun stopUsesBoundedStreamIdsAndReleaseIgnoresOldCallbacksEvenWhenAnotherPoolReusesSampleIds() {
        val driver = FakePool()
        val old = SoundPoolEffectPlayer(driver, 2) { _, _ -> }
        val oldCallback = checkNotNull(driver.listener)
        driver.complete(SoundEffect.CLICK)
        repeat(3) { assertTrue(old.play(SoundEffect.CLICK, 1f)) }
        assertEquals(listOf(50), driver.stops)
        old.stopAll()
        assertEquals(listOf(50, 51, 52), driver.stops)
        old.release()
        old.release()
        assertEquals(1, driver.releaseCount)
        assertNull(driver.listener)

        val freshDriver = FakePool()
        val fresh = SoundPoolEffectPlayer(freshDriver, 2) { _, _ -> }
        try {
            oldCallback(freshDriver.sampleId(SoundEffect.RECEIVE), 0)
            assertEquals(SoundEffectLoadState.RELEASED, old.loadState(SoundEffect.RECEIVE))
            assertEquals(SoundEffectLoadState.LOADING, fresh.loadState(SoundEffect.RECEIVE))
            assertFalse(old.play(SoundEffect.CLICK, 1f))
            assertFalse(fresh.play(SoundEffect.RECEIVE, 1f))
            freshDriver.complete(SoundEffect.RECEIVE)
            assertTrue(fresh.play(SoundEffect.RECEIVE, 1f))
        } finally {
            fresh.release()
        }
    }

    private class FakePool(
        private val loadFailure: SoundEffect? = null,
        private val loadZero: SoundEffect? = null
    ) : SoundPoolDriver {
        var listener: ((Int, Int) -> Unit)? = null
            private set
        val loads = mutableListOf<SoundEffect>()
        val plays = mutableListOf<Pair<Int, Float>>()
        val stops = mutableListOf<Int>()
        var rejectPlayback = false
        var releaseCount = 0
        private var nextStream = 50

        override fun setLoadListener(listener: ((Int, Int) -> Unit)?) { this.listener = listener }
        fun sampleId(effect: SoundEffect) = 10 + effect.ordinal
        fun complete(effect: SoundEffect, status: Int = 0) { checkNotNull(listener).invoke(sampleId(effect), status) }

        override fun load(effect: SoundEffect): Int {
            checkNotNull(listener)
            loads.add(effect)
            if (effect == loadFailure) throw IOException("Unreadable")
            return if (effect == loadZero) 0 else sampleId(effect)
        }

        override fun play(sampleId: Int, volume: Float): Int {
            plays.add(sampleId to volume)
            return if (rejectPlayback) 0 else nextStream++
        }

        override fun stop(streamId: Int) { stops.add(streamId) }
        override fun release() { releaseCount++ }
    }
}
