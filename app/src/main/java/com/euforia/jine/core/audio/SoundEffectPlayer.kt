package com.euforia.jine.core.audio

import com.euforia.jine.core.assets.JineAssets

internal enum class SoundEffect(val assetPath: String) {
    CLICK(JineAssets.Audio.Sfx.CLICK),
    SEND_STAMP(JineAssets.Audio.Sfx.SEND_STAMP),
    RECEIVE(JineAssets.Audio.Sfx.RECEIVE)
}

internal interface SoundEffectPlayer {
    fun play(effect: SoundEffect, volume: Float): Boolean
    fun stopAll()
    fun release()
}

internal object SilentSoundEffectPlayer : SoundEffectPlayer {
    override fun play(effect: SoundEffect, volume: Float) = false
    override fun stopAll() = Unit
    override fun release() = Unit
}
