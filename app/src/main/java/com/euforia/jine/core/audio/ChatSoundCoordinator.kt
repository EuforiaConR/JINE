package com.euforia.jine.core.audio

import androidx.annotation.MainThread
import com.euforia.jine.data.model.Message
import com.euforia.jine.data.model.MessageSender

/** Receives accepted input and committed presentation events. All entry points are called on Main. */
@MainThread
internal class ChatSoundCoordinator(
    private val player: SoundEffectPlayer,
    volumes: ChatAudioVolumes = ChatAudioVolumes()
) {
    private val seenIds = mutableSetOf<String>()
    private val pendingStamps = mutableSetOf<String>()
    private var resumed = false
    private var released = false
    private var volume = volumes.effectiveSfx
    private val canPlay get() = resumed && !released && volume > 0f

    fun resume(history: List<Message>) {
        if (released || resumed) return
        history.mapTo(seenIds, Message::id)
        resumed = true
    }

    fun pause() {
        if (!resumed) return
        resumed = false
        stopPresentationSounds()
    }

    fun setVolumes(volumes: ChatAudioVolumes, currentHistory: List<Message>) {
        if (released) return
        val wasAudible = volume > 0f
        volume = volumes.effectiveSfx
        if (wasAudible && volume == 0f && resumed) stopPresentationSounds()
        if (!wasAudible && volume > 0f) currentHistory.mapTo(seenIds, Message::id)
    }

    fun onInteractionAccepted() {
        if (canPlay) player.play(SoundEffect.CLICK, volume)
    }

    fun onMessageArrived(message: Message) {
        if (released || !seenIds.add(message.id) || !canPlay) return
        when (message.sender) {
            MessageSender.PCHAN -> pendingStamps.add(message.id)
            MessageSender.AME -> player.play(SoundEffect.RECEIVE, volume)
        }
    }

    fun onAppearanceFinished(id: String) {
        if (pendingStamps.remove(id) && canPlay) player.play(SoundEffect.SEND_STAMP, volume)
    }

    fun release() {
        if (released) return
        pause()
        released = true
        seenIds.clear()
        player.release()
    }

    private fun stopPresentationSounds() {
        pendingStamps.clear()
        player.stopAll()
    }
}
