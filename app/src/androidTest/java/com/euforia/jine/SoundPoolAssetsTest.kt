package com.euforia.jine

import android.os.SystemClock
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.euforia.jine.core.audio.SoundEffect
import com.euforia.jine.core.audio.SoundEffectLoadState
import com.euforia.jine.core.audio.SoundPoolEffectPlayer
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class SoundPoolAssetsTest {
    @Test
    fun packagedWavsOpenAsDescriptorsAndSoundPoolDecodesAllThreeSamples() {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val context = instrumentation.targetContext
        for (effect in SoundEffect.entries) {
            context.assets.openFd(effect.assetPath).use {
                assertTrue("Expected a non-empty WAV descriptor for ${effect.assetPath}", it.length > 0)
            }
        }
        lateinit var player: SoundPoolEffectPlayer
        instrumentation.runOnMainSync {
            player = SoundPoolEffectPlayer.create(context) as SoundPoolEffectPlayer
        }
        try {
            val deadline = SystemClock.uptimeMillis() + 5_000
            var states: Map<SoundEffect, SoundEffectLoadState>
            do {
                var snapshot = emptyMap<SoundEffect, SoundEffectLoadState>()
                instrumentation.runOnMainSync { snapshot = SoundEffect.entries.associateWith(player::loadState) }
                states = snapshot
                if (states.values.none { it == SoundEffectLoadState.LOADING }) break
                SystemClock.sleep(20)
            } while (SystemClock.uptimeMillis() < deadline)
            for (effect in SoundEffect.entries) {
                assertEquals(effect.assetPath, SoundEffectLoadState.READY, states[effect])
            }
            instrumentation.runOnMainSync {
                for (effect in SoundEffect.entries) {
                    assertTrue("SoundPool should allocate a stream for ${effect.assetPath}", player.play(effect, 0.1f))
                }
                player.stopAll()
            }
        } finally {
            instrumentation.runOnMainSync {
                player.release()
                player.release()
                assertFalse(player.play(SoundEffect.CLICK, 1f))
            }
        }
    }
}
