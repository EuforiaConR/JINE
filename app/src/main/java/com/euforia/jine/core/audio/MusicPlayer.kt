package com.euforia.jine.core.audio

/** The session controls playback intent. Preparing a track must keep it paused. */
internal interface MusicPlayer {
    fun prepare()
    fun play()
    fun pause()
    fun setVolume(volume: Float)
    fun release()
}
