package com.euforia.jine.core.audio

import android.content.Context
import android.media.AudioAttributes
import android.media.SoundPool
import android.os.Handler
import android.os.Looper
import android.util.Log
import androidx.annotation.MainThread
import kotlinx.coroutines.CancellationException
import java.io.IOException

internal enum class SoundEffectLoadState { LOADING, READY, FAILED, RELEASED }

/** A fresh pool per player. Completion callbacks run on Main after load returns. */
internal interface SoundPoolDriver {
    fun setLoadListener(listener: ((sampleId: Int, status: Int) -> Unit)?)
    fun load(effect: SoundEffect): Int
    fun play(sampleId: Int, volume: Float): Int
    fun stop(streamId: Int)
    fun release()
}

@MainThread
internal class SoundPoolEffectPlayer(
    private val pool: SoundPoolDriver,
    private val maxStreams: Int,
    private val warn: (String, Throwable?) -> Unit
) : SoundEffectPlayer {
    private data class Sample(val id: Int, var state: SoundEffectLoadState)

    private val samples = mutableMapOf<SoundEffect, Sample>()
    private val recentStreams = ArrayDeque<Int>()
    private val playbackFailures = mutableSetOf<SoundEffect>()
    private var released = false

    init {
        require(maxStreams > 0)
        pool.setLoadListener(::onLoadComplete)
        for (effect in SoundEffect.entries) {
            samples[effect] = loadSample(effect)
        }
    }

    internal fun loadState(effect: SoundEffect): SoundEffectLoadState =
        if (released) SoundEffectLoadState.RELEASED else samples.getValue(effect).state

    override fun play(effect: SoundEffect, volume: Float): Boolean {
        if (released || volume <= 0f) return false
        val sample = samples.getValue(effect)
        if (sample.state != SoundEffectLoadState.READY) return false

        val streamId = try {
            pool.play(sample.id, volume)
        } catch (error: RuntimeException) {
            if (error is CancellationException) throw error
            reportPlaybackFailure(effect, error)
            return false
        }
        if (streamId == 0) {
            reportPlaybackFailure(effect, null)
            return false
        }

        recentStreams.remove(streamId)
        // Keep every potentially playing voice stoppable, even if the native pool reused a slot.
        if (recentStreams.size == maxStreams) stopStream(recentStreams.removeFirst())
        recentStreams.addLast(streamId)
        return true
    }

    override fun stopAll() {
        while (recentStreams.isNotEmpty()) stopStream(recentStreams.removeFirst())
    }

    override fun release() {
        if (released) return
        released = true
        stopAll()
        samples.clear()
        platformAction("Could not remove SoundPool listener") { pool.setLoadListener(null) }
        platformAction("Could not release SoundPool", pool::release)
    }

    private fun loadSample(effect: SoundEffect): Sample {
        try {
            val id = pool.load(effect)
            if (id != 0) return Sample(id, SoundEffectLoadState.LOADING)
            warn("SoundPool returned no sample ID for ${effect.assetPath}", null)
        } catch (error: IOException) {
            warn("Could not open ${effect.assetPath}", error)
        } catch (error: RuntimeException) {
            if (error is CancellationException) throw error
            warn("Could not load ${effect.assetPath}", error)
        }
        return Sample(0, SoundEffectLoadState.FAILED)
    }

    private fun onLoadComplete(sampleId: Int, status: Int) {
        if (released) return
        val (effect, sample) = samples.entries.firstOrNull { (_, sample) ->
            sample.id == sampleId && sample.state == SoundEffectLoadState.LOADING
        } ?: return
        sample.state = if (status == 0) SoundEffectLoadState.READY else SoundEffectLoadState.FAILED
        if (status != 0) warn("Could not decode ${effect.assetPath} (status $status)", null)
    }

    private fun reportPlaybackFailure(effect: SoundEffect, error: Throwable?) {
        if (playbackFailures.add(effect)) warn("Could not play ${effect.assetPath}", error)
    }

    private fun stopStream(streamId: Int) =
        platformAction("Could not stop SoundPool stream $streamId") { pool.stop(streamId) }

    private inline fun platformAction(description: String, action: () -> Unit) {
        try {
            action()
        } catch (error: RuntimeException) {
            if (error is CancellationException) throw error
            warn(description, error)
        }
    }

    companion object {
        private const val TAG = "JineAudio"
        private const val MAX_STREAMS = 8

        fun create(context: Context): SoundEffectPlayer {
            val driver = try {
                AndroidSoundPoolDriver(context.applicationContext, MAX_STREAMS)
            } catch (error: RuntimeException) {
                if (error is CancellationException) throw error
                Log.w(TAG, "SoundPool initialization failed", error)
                return SilentSoundEffectPlayer
            }
            return try {
                SoundPoolEffectPlayer(driver, MAX_STREAMS) { message, error -> Log.w(TAG, message, error) }
            } catch (error: RuntimeException) {
                try {
                    driver.release()
                } catch (releaseError: RuntimeException) {
                    if (releaseError is CancellationException) throw releaseError
                    error.addSuppressed(releaseError)
                }
                if (error is CancellationException) throw error
                Log.w(TAG, "SoundPool preload could not start", error)
                SilentSoundEffectPlayer
            }
        }
    }
}

private class AndroidSoundPoolDriver(private val context: Context, maxStreams: Int) : SoundPoolDriver {
    private val main = Handler(Looper.getMainLooper())
    private val pool = SoundPool.Builder()
        .setMaxStreams(maxStreams)
        .setAudioAttributes(
            AudioAttributes.Builder()
                .setUsage(AudioAttributes.USAGE_GAME)
                .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                .build()
        )
        .build()

    override fun setLoadListener(listener: ((Int, Int) -> Unit)?) {
        pool.setOnLoadCompleteListener(listener?.let { notify ->
            SoundPool.OnLoadCompleteListener { source, id, status ->
                if (source === pool) {
                    if (Looper.myLooper() == main.looper) notify(id, status)
                    else main.post { notify(id, status) }
                }
            }
        })
    }

    override fun load(effect: SoundEffect): Int =
        context.assets.openFd(effect.assetPath).use { asset -> pool.load(asset, 1) }

    override fun play(sampleId: Int, volume: Float): Int = pool.play(sampleId, volume, volume, 1, 0, 1f)
    override fun stop(streamId: Int) = pool.stop(streamId)
    override fun release() = pool.release()
}
