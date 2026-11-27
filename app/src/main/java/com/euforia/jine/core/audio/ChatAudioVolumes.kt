package com.euforia.jine.core.audio

internal data class ChatAudioVolumes(val master: Float = 1f, val sfx: Float = 1f) {
    init {
        require(master in 0f..1f && sfx in 0f..1f)
    }

    val effectiveSfx: Float get() = master * sfx
}
