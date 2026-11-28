package com.euforia.jine.core.audio

import android.content.Context
import android.os.Looper
import android.util.Log
import androidx.annotation.MainThread
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import com.euforia.jine.core.assets.JineAssets
import kotlinx.coroutines.CancellationException

@MainThread
internal class Media3MusicPlayer(player: ExoPlayer?) : MusicPlayer {
    private var player = player
    private val listener = object : Player.Listener {
        override fun onPlayerError(error: PlaybackException) {
            fail("play", error)
        }
    }

    override fun prepare() = withPlayer("prepare") {
        addListener(listener)
        setAudioAttributes(
            AudioAttributes.Builder()
                .setUsage(C.USAGE_GAME)
                .setContentType(C.AUDIO_CONTENT_TYPE_MUSIC)
                .build(),
            /* handleAudioFocus = */ false
        )
        playWhenReady = false
        repeatMode = Player.REPEAT_MODE_ONE
        setMediaItem(MediaItem.fromUri("asset:///$TRACK"))
        prepare()
    }

    override fun play() = withPlayer("play") { play() }

    override fun pause() = withPlayer("pause") { pause() }

    override fun setVolume(volume: Float) = withPlayer("set volume for") { this.volume = volume }

    override fun release() {
        val current = player ?: return
        player = null
        releaseStep("remove the listener for") { current.removeListener(listener) }
        releaseStep("release", current::release)
    }

    private inline fun releaseStep(action: String, operation: () -> Unit) {
        try {
            operation()
        } catch (error: RuntimeException) {
            if (error is CancellationException) throw error
            Log.w(TAG, "Could not $action $TRACK", error)
        }
    }

    private inline fun withPlayer(action: String, operation: ExoPlayer.() -> Unit) {
        val current = player ?: return
        try {
            operation(current)
        } catch (error: RuntimeException) {
            if (error is CancellationException) throw error
            fail(action, error)
        }
    }

    private fun fail(action: String, error: Exception) {
        if (player == null) return
        Log.w(TAG, "Could not $action $TRACK", error)
        release()
    }

    companion object {
        private const val TAG = "JineAudio"
        private const val TRACK = JineAssets.Audio.Music.MAINLOOP_NORMAL

        fun create(context: Context): MusicPlayer {
            val player = try {
                ExoPlayer.Builder(context.applicationContext).setLooper(Looper.getMainLooper()).build()
            } catch (error: RuntimeException) {
                if (error is CancellationException) throw error
                Log.w(TAG, "Could not create ExoPlayer for $TRACK", error)
                null
            }
            return Media3MusicPlayer(player)
        }
    }
}
