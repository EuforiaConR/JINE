package com.euforia.jine.core.audio

import androidx.annotation.MainThread
import com.euforia.jine.data.model.Message

internal interface ChatSoundEvents {
    fun onInteractionAccepted()
    fun onMessageArrived(message: Message)
    fun onAppearanceFinished(id: String)
}

/** Owns the playback policy for one foreground session. All transitions run on Main. */
@MainThread
internal class JineAudioManager(
    private val music: MusicPlayer,
    private val sounds: ChatSoundCoordinator,
    private val focus: AudioFocusController
) {
    private enum class FocusState { IDLE, GRANTED, SUSPENDED, BLOCKED }

    private var activeHost: Host? = null
    private var activeChat: Chat? = null
    private var hostResumed = false
    private var focusState = FocusState.IDLE
    private var focusToken: Any? = null
    private var released = false
    private var musicRequested = false
    private var volumes = ChatAudioVolumes()
    private val hasAudibleChannel get() = volumes.effectiveBgm > 0f || volumes.effectiveSfx > 0f

    init {
        music.setVolume(volumes.effectiveBgm)
        music.prepare()
    }

    fun attachHost(): Host {
        activeHost?.detach()
        return Host().also { if (!released) activeHost = it }
    }

    fun setVolumes(newVolumes: ChatAudioVolumes) {
        if (released || volumes == newVolumes) return
        val previous = volumes
        volumes = newVolumes
        val unmutedSfx = previous.effectiveSfx == 0f && volumes.effectiveSfx > 0f
        val unmutedBgm = previous.effectiveBgm == 0f && volumes.effectiveBgm > 0f
        if (volumes.effectiveSfx == 0f) sounds.pause()
        sounds.setVolumes(volumes, if (unmutedSfx) activeChat?.history?.invoke().orEmpty() else emptyList())
        if (previous.effectiveBgm != volumes.effectiveBgm) music.setVolume(volumes.effectiveBgm)

        if (!hasAudibleChannel) {
            revokeFocus(FocusState.IDLE)
        } else {
            if (unmutedSfx || unmutedBgm) requestFocus()
            synchronizePlayback()
        }
    }

    fun release() {
        if (released) return
        released = true
        hostResumed = false
        activeChat = null
        activeHost = null
        revokeFocus(FocusState.IDLE)
        music.release()
        sounds.release()
    }

    private fun requestFocus() {
        if (!hostResumed || !hasAudibleChannel || focusToken != null) return
        val token = Any()
        focusToken = token
        val granted = focus.request { change ->
            if (focusToken === token) onFocusChange(change)
        }
        focusState = if (granted) FocusState.GRANTED else FocusState.BLOCKED
        if (!granted) focusToken = null
    }

    private fun onFocusChange(change: AudioFocusChange) {
        when (change) {
            AudioFocusChange.GAIN -> {
                if (focusState == FocusState.SUSPENDED) focusState = FocusState.GRANTED
                synchronizePlayback()
            }
            AudioFocusChange.TRANSIENT_LOSS, AudioFocusChange.CAN_DUCK -> {
                focusState = FocusState.SUSPENDED
                synchronizePlayback()
            }
            AudioFocusChange.LOSS -> revokeFocus(FocusState.BLOCKED)
        }
    }

    private fun revokeFocus(next: FocusState) {
        val hadRequest = focusToken != null
        focusToken = null
        focusState = next
        synchronizePlayback()
        if (hadRequest) focus.abandon()
    }

    private fun synchronizePlayback() {
        val allowed = !released && hostResumed && focusState == FocusState.GRANTED
        val playMusic = allowed && volumes.effectiveBgm > 0f
        if (musicRequested != playMusic) {
            musicRequested = playMusic
            if (playMusic) music.play() else music.pause()
        }
        val chat = activeChat
        if (allowed && volumes.effectiveSfx > 0f && chat != null) {
            sounds.resume(chat.history())
        } else {
            sounds.pause()
        }
    }

    /** Identity of one Activity attachment. This handle holds no Activity or lifecycle reference. */
    inner class Host internal constructor() {
        fun resume() {
            if (activeHost !== this || hostResumed) return
            hostResumed = true
            requestFocus()
            synchronizePlayback()
        }

        fun pause() {
            if (activeHost !== this || !hostResumed) return
            hostResumed = false
            revokeFocus(FocusState.IDLE)
        }

        fun detach() {
            if (activeHost !== this) return
            pause()
            activeChat = null
            activeHost = null
        }

        fun createChat(history: () -> List<Message>): Chat = Chat(this, history)
    }

    inner class Chat internal constructor(private val host: Host, internal val history: () -> List<Message>) : ChatSoundEvents {
        fun attach() {
            if (activeHost !== host || activeChat === this) return
            sounds.pause()
            activeChat = this
            synchronizePlayback()
        }

        fun detach() {
            if (activeChat !== this) return
            activeChat = null
            sounds.pause()
        }

        override fun onInteractionAccepted() {
            if (activeChat === this) sounds.onInteractionAccepted()
        }

        override fun onMessageArrived(message: Message) {
            if (activeChat === this) sounds.onMessageArrived(message)
        }

        override fun onAppearanceFinished(id: String) {
            if (activeChat === this) sounds.onAppearanceFinished(id)
        }
    }
}
