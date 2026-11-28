package com.euforia.jine.core.audio

import android.content.Context
import android.media.AudioAttributes
import android.media.AudioFocusRequest
import android.media.AudioManager
import android.os.Handler
import android.os.Looper
import android.util.Log
import androidx.annotation.MainThread
import kotlinx.coroutines.CancellationException

internal enum class AudioFocusChange { GAIN, TRANSIENT_LOSS, CAN_DUCK, LOSS }

/** Requests lasting focus. Changes are delivered on Main after [request] returns. */
internal interface AudioFocusController {
    fun request(onChange: (AudioFocusChange) -> Unit): Boolean
    fun abandon()
}

@MainThread
internal class AndroidAudioFocusController(context: Context) : AudioFocusController {
    private val audioManager = context.applicationContext.getSystemService(AudioManager::class.java)
    private val main = Handler(Looper.getMainLooper())
    private var activeRequest: AudioFocusRequest? = null

    override fun request(onChange: (AudioFocusChange) -> Unit): Boolean {
        val manager = audioManager ?: return false
        val request = AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN)
            .setAudioAttributes(
                AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_GAME)
                    .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
                    .build()
            )
            .setWillPauseWhenDucked(true)
            .setAcceptsDelayedFocusGain(false)
            .setOnAudioFocusChangeListener({ change ->
                when (change) {
                    AudioManager.AUDIOFOCUS_GAIN -> onChange(AudioFocusChange.GAIN)
                    AudioManager.AUDIOFOCUS_LOSS_TRANSIENT -> onChange(AudioFocusChange.TRANSIENT_LOSS)
                    AudioManager.AUDIOFOCUS_LOSS_TRANSIENT_CAN_DUCK -> onChange(AudioFocusChange.CAN_DUCK)
                    AudioManager.AUDIOFOCUS_LOSS -> onChange(AudioFocusChange.LOSS)
                }
            }, main)
            .build()
        return try {
            val granted = manager.requestAudioFocus(request) == AudioManager.AUDIOFOCUS_REQUEST_GRANTED
            if (granted) activeRequest = request
            granted
        } catch (error: RuntimeException) {
            if (error is CancellationException) throw error
            Log.w("JineAudio", "Could not request audio focus", error)
            false
        }
    }

    override fun abandon() {
        val request = activeRequest ?: return
        activeRequest = null
        try {
            audioManager?.abandonAudioFocusRequest(request)
        } catch (error: RuntimeException) {
            if (error is CancellationException) throw error
            Log.w("JineAudio", "Could not abandon audio focus", error)
        }
    }
}
