package com.euforia.jine

import com.euforia.jine.core.audio.AudioFocusChange
import com.euforia.jine.core.audio.AudioFocusController
import com.euforia.jine.core.audio.ChatAudioVolumes
import com.euforia.jine.core.audio.ChatSoundCoordinator
import com.euforia.jine.core.audio.JineAudioManager
import com.euforia.jine.core.audio.MusicPlayer
import com.euforia.jine.core.audio.SoundEffect
import com.euforia.jine.core.audio.SoundEffectPlayer
import com.euforia.jine.data.model.Message
import com.euforia.jine.data.model.MessageContent
import com.euforia.jine.data.model.MessageSender
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

class JineAudioManagerTest {
    @Test
    fun musicWaitsForFocusAndChatEventsDoNotChangeTheTrackOrFocus() {
        val session = Session()
        val host = session.manager.attachHost()
        val initial = session.publish("initial")
        assertFalse(session.music.playing)
        assertEquals(listOf("prepare"), session.events)
        host.resume()
        host.resume()
        val chat = session.attachChat(host)
        chat.onMessageArrived(initial)
        assertTrue(session.sfx.calls.isEmpty())

        val outgoing = session.publish("outgoing", MessageSender.PCHAN)
        chat.onInteractionAccepted()
        chat.onMessageArrived(outgoing)
        assertEquals(listOf(SoundEffect.CLICK), session.sfx.effects)
        chat.onAppearanceFinished(outgoing.id)
        chat.onMessageArrived(outgoing.copy(readAt = 1L))
        val incoming = session.publish("incoming")
        chat.onMessageArrived(incoming)
        chat.onMessageArrived(incoming)
        assertEquals(listOf(SoundEffect.CLICK, SoundEffect.SEND_STAMP, SoundEffect.RECEIVE), session.sfx.effects)
        assertEquals(listOf("prepare", "request", "play"), session.events)

        chat.detach()
        chat.onInteractionAccepted()
        assertTrue(session.music.playing)
        assertEquals(1, session.sfx.stops)
        assertEquals(3, session.sfx.calls.size)
    }

    @Test
    fun newHostContinuesFromTheSamePositionAndIgnoresOldHostAndChatEvents() {
        val session = Session()
        val oldHost = session.manager.attachHost()
        oldHost.resume()
        val oldChat = session.attachChat(oldHost)
        session.manager.setVolumes(ChatAudioVolumes(bgm = 0.4f))
        session.music.advance(400)
        oldHost.pause()
        oldHost.pause()
        session.music.advance(200)
        assertEquals(400L, session.music.position)
        val accumulated = session.publish("accumulated")

        val newHost = session.manager.attachHost()
        val newChat = session.attachChat(newHost)
        newHost.resume()
        oldHost.pause()
        oldHost.detach()
        oldChat.detach()
        oldChat.attach()
        oldChat.onInteractionAccepted()
        session.focus.emit(0, AudioFocusChange.LOSS)
        assertTrue(session.music.playing)
        assertEquals(0.4f, session.music.volume, 0.001f)
        assertEquals(400L, session.music.position)
        session.music.advance(100)
        assertEquals(500L, session.music.position)
        newChat.onMessageArrived(accumulated)
        assertTrue(session.sfx.calls.isEmpty())
        newChat.onMessageArrived(session.publish("new"))
        assertEquals(listOf(SoundEffect.RECEIVE), session.sfx.effects)
        assertEquals(listOf("prepare", "request", "play", "pause", "abandon", "request", "play"), session.events)
    }

    @Test
    fun transientLossAndDuckPauseBothChannelsThenGainUsesTheLatestHistory() {
        val session = Session()
        val host = session.manager.attachHost()
        val chat = session.attachChat(host)
        host.resume()
        val first = session.publish("fading", MessageSender.PCHAN)
        chat.onMessageArrived(first)
        chat.onInteractionAccepted()
        for (change in listOf(AudioFocusChange.TRANSIENT_LOSS, AudioFocusChange.CAN_DUCK)) {
            session.focus.emit(0, change)
            assertFalse(session.music.playing)
            val undelivered = session.publish("missed-$change")
            chat.onInteractionAccepted()
            session.focus.emit(0, AudioFocusChange.GAIN)
            assertTrue(session.music.playing)
            chat.onMessageArrived(undelivered)
        }
        chat.onAppearanceFinished(first.id)
        assertEquals(listOf(SoundEffect.CLICK), session.sfx.effects)
        chat.onMessageArrived(session.publish("after-gain"))
        assertEquals(listOf(SoundEffect.CLICK, SoundEffect.RECEIVE), session.sfx.effects)
        assertEquals(2, session.sfx.stops)
        assertEquals(1, session.focus.requests)
        assertEquals(0, session.focus.abandons)
    }

    @Test
    fun denialAndPermanentLossWaitForForegroundOrExplicitUnmute() {
        val session = Session(grantFocus = false)
        val host = session.manager.attachHost()
        val chat = session.attachChat(host)
        host.resume()
        host.resume()
        chat.onMessageArrived(session.publish("while-denied"))
        session.focus.emit(0, AudioFocusChange.GAIN)
        assertEquals(1, session.focus.requests)
        assertFalse(session.music.playing)
        assertTrue(session.sfx.calls.isEmpty())

        session.focus.grantFocus = true
        host.pause()
        host.resume()
        assertTrue(session.music.playing)
        session.focus.emit(1, AudioFocusChange.LOSS)
        chat.onInteractionAccepted()
        session.focus.emit(1, AudioFocusChange.GAIN)
        session.manager.setVolumes(ChatAudioVolumes(bgm = 0.5f))
        assertEquals(2, session.focus.requests)
        assertEquals(1, session.focus.abandons)
        assertFalse(session.music.playing)
        assertTrue(session.sfx.calls.isEmpty())

        session.manager.setVolumes(ChatAudioVolumes(bgm = 0f))
        session.manager.setVolumes(ChatAudioVolumes())
        assertEquals(3, session.focus.requests)
        assertTrue(session.music.playing)
    }

    @Test
    fun lateReadinessCannotStartMusicAfterLossAndLateGainCannotStartItAfterPause() {
        val session = Session(ready = false)
        val host = session.manager.attachHost()
        host.resume()
        assertFalse(session.music.playing)
        session.focus.emit(0, AudioFocusChange.TRANSIENT_LOSS)
        session.music.ready = true
        assertFalse(session.music.playing)
        session.focus.emit(0, AudioFocusChange.GAIN)
        assertTrue(session.music.playing)
        host.pause()
        session.focus.emit(0, AudioFocusChange.GAIN)
        assertFalse(session.music.playing)
        assertEquals(listOf("prepare", "request", "play", "pause", "play", "pause", "abandon"), session.events)
    }

    @Test
    fun independentVolumesStopOnlyMutedChannelsAndBothMutedAbandonFocus() {
        val session = Session()
        val host = session.manager.attachHost()
        val chat = session.attachChat(host)
        host.resume()
        session.manager.setVolumes(ChatAudioVolumes(master = 0.5f, sfx = 0.4f, bgm = 0.6f))
        chat.onInteractionAccepted()
        assertEquals(0.2f, session.sfx.calls.single().second, 0.001f)
        assertEquals(0.3f, session.music.volume, 0.001f)

        session.music.advance(500)
        session.manager.setVolumes(ChatAudioVolumes(bgm = 0f))
        assertFalse(session.music.playing)
        chat.onInteractionAccepted()
        assertEquals(2, session.sfx.calls.size)
        val pending = session.publish("before-sfx-mute", MessageSender.PCHAN)
        chat.onMessageArrived(pending)
        session.manager.setVolumes(ChatAudioVolumes(sfx = 0f))
        assertTrue(session.music.playing)
        assertEquals(500L, session.music.position)
        val unnoticed = session.publish("muted")
        chat.onInteractionAccepted()
        session.manager.setVolumes(ChatAudioVolumes())
        chat.onMessageArrived(unnoticed)
        chat.onAppearanceFinished(pending.id)
        assertEquals(2, session.sfx.calls.size)
        assertEquals(1, session.focus.requests)
        assertEquals(1, session.sfx.stops)

        session.manager.setVolumes(ChatAudioVolumes(master = 0f))
        assertFalse(session.music.playing)
        assertEquals(1, session.focus.abandons)
        host.pause()
        session.manager.setVolumes(ChatAudioVolumes())
        session.focus.emit(0, AudioFocusChange.GAIN)
        assertFalse(session.music.playing)
        assertEquals(1, session.focus.requests)
        host.resume()
        assertTrue(session.music.playing)
        assertEquals(2, session.focus.requests)
    }

    @Test
    fun invalidVolumesAreRejectedForAllThreeChannels() {
        for (bad in listOf(-0.1f, 1.1f, Float.NaN, Float.POSITIVE_INFINITY)) {
            assertThrows(IllegalArgumentException::class.java) { ChatAudioVolumes(master = bad) }
            assertThrows(IllegalArgumentException::class.java) { ChatAudioVolumes(sfx = bad) }
            assertThrows(IllegalArgumentException::class.java) { ChatAudioVolumes(bgm = bad) }
        }
    }

    @Test
    fun aBackendFailureDoesNotSilenceTheOtherChannel() {
        val session = Session()
        val host = session.manager.attachHost()
        val chat = session.attachChat(host)
        host.resume()
        session.sfx.available = false
        chat.onMessageArrived(session.publish("before-sfx-ready"))
        assertTrue(session.music.playing)
        session.music.failed = true
        session.sfx.available = true
        chat.onMessageArrived(session.publish("after-music-error"))
        assertEquals(listOf(SoundEffect.RECEIVE), session.sfx.effects)
        assertFalse(session.music.playing)
        assertEquals(1, session.focus.requests)
        assertEquals(1, session.events.count { it == "prepare" })
    }

    @Test
    fun releaseAbandonsFocusAndFreesResourcesOnceThenNewSessionStartsAtZero() {
        val session = Session()
        val host = session.manager.attachHost()
        val chat = session.attachChat(host)
        host.resume()
        session.music.advance(600)
        chat.onMessageArrived(session.publish("unfinished", MessageSender.PCHAN))
        session.manager.release()
        session.manager.release()
        host.resume()
        session.manager.attachHost().resume()
        session.focus.emit(0, AudioFocusChange.GAIN)
        chat.onAppearanceFinished("unfinished")
        assertEquals(1, session.music.releases)
        assertEquals(1, session.sfx.releases)
        assertEquals(1, session.focus.requests)
        assertEquals(1, session.focus.abandons)
        assertTrue(session.sfx.calls.isEmpty())
        assertFalse(session.music.playing)

        val fresh = Session()
        fresh.manager.attachHost().resume()
        assertEquals(0L, fresh.music.position)
        assertEquals(1f, fresh.music.volume, 0.001f)
        assertTrue(fresh.music.playing)
    }

    private class Session(grantFocus: Boolean = true, ready: Boolean = true) {
        val events = mutableListOf<String>()
        val music = FakeMusic(events, ready)
        val sfx = FakeSounds()
        val focus = FakeFocus(events, grantFocus)
        private val history = mutableListOf<Message>()
        val manager = JineAudioManager(music, ChatSoundCoordinator(sfx), focus)

        fun publish(id: String, sender: MessageSender = MessageSender.AME): Message =
            Message(id, sender, MessageContent.Text("test.text"), sentAt = 0L).also(history::add)

        fun attachChat(host: JineAudioManager.Host) = host.createChat { history }.also { it.attach() }
    }

    private class FakeMusic(private val events: MutableList<String>, var ready: Boolean) : MusicPlayer {
        private var requested = false
        var failed = false
        var volume = 0f
            private set
        var position = 0L
            private set
        var releases = 0
            private set
        val playing get() = requested && ready && !failed

        fun advance(ms: Long) {
            if (playing) position += ms
        }

        override fun prepare() { events.add("prepare") }
        override fun play() {
            requested = true
            events.add("play")
        }

        override fun pause() {
            requested = false
            events.add("pause")
        }

        override fun setVolume(volume: Float) { this.volume = volume }

        override fun release() {
            requested = false
            releases++
        }
    }

    private class FakeSounds : SoundEffectPlayer {
        val calls = mutableListOf<Pair<SoundEffect, Float>>()
        val effects get() = calls.map { it.first }
        var available = true
        var stops = 0
        var releases = 0

        override fun play(effect: SoundEffect, volume: Float): Boolean {
            if (!available) return false
            calls.add(effect to volume)
            return true
        }

        override fun stopAll() { stops++ }
        override fun release() { releases++ }
    }

    private class FakeFocus(private val events: MutableList<String>, var grantFocus: Boolean) : AudioFocusController {
        private val callbacks = mutableListOf<(AudioFocusChange) -> Unit>()
        val requests get() = callbacks.size
        var abandons = 0
            private set

        override fun request(onChange: (AudioFocusChange) -> Unit): Boolean {
            events.add("request")
            callbacks.add(onChange)
            return grantFocus
        }

        override fun abandon() {
            abandons++
            events.add("abandon")
        }

        fun emit(request: Int, change: AudioFocusChange) = callbacks[request](change)
    }
}
