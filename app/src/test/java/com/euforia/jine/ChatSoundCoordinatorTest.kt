package com.euforia.jine

import com.euforia.jine.core.audio.ChatAudioVolumes
import com.euforia.jine.core.audio.ChatSoundCoordinator
import com.euforia.jine.core.audio.SoundEffect
import com.euforia.jine.core.audio.SoundEffectPlayer
import com.euforia.jine.data.model.Message
import com.euforia.jine.data.model.MessageContent
import com.euforia.jine.data.model.MessageSender
import com.euforia.jine.data.model.PCHAN_PACK_ID
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class ChatSoundCoordinatorTest {
    @Test
    fun clicksAndOutgoingCompletionsAreIndependentAndEachIdIsConsumedOnce() = runTest {
        withAudio { sounds, player ->
            val sticker = sticker("first")
            val option = text("second", MessageSender.PCHAN)
            sounds.onInteractionAccepted()
            sounds.onMessageArrived(sticker)
            sounds.onMessageArrived(option)
            sounds.onAppearanceFinished("unknown")
            tick(199)
            assertEquals(listOf(SoundEffect.CLICK), player.effects)

            tick(1)
            sounds.onAppearanceFinished(sticker.id)
            sounds.onAppearanceFinished(sticker.id)
            sounds.onMessageArrived(sticker.copy(readAt = 200))
            sounds.onMessageArrived(option)
            tick(20)
            sounds.onAppearanceFinished(option.id)
            sounds.onAppearanceFinished(option.id)
            assertEquals(listOf(SoundEffect.CLICK, SoundEffect.SEND_STAMP, SoundEffect.SEND_STAMP), player.effects)
            assertEquals(listOf(0L, 200L, 220L), player.calls.map { it.at })
        }
    }

    @Test
    fun eachNewAmeMessagePlaysOnceOnArrival() = runTest {
        withAudio { sounds, player ->
            val incoming = text("ame-1")
            sounds.onMessageArrived(incoming)
            assertEquals(listOf(0L), player.calls.map { it.at })
            tick(100)
            val futureSticker = sticker("ame-2").copy(sender = MessageSender.AME)
            sounds.onMessageArrived(futureSticker)
            sounds.onMessageArrived(incoming)
            sounds.onMessageArrived(futureSticker)
            sounds.onAppearanceFinished(futureSticker.id)
            assertEquals(List(2) { SoundEffect.RECEIVE }, player.effects)
            assertEquals(listOf(0L, 100L), player.calls.map { it.at })
            tick(1_000)
            assertEquals(List(2) { SoundEffect.RECEIVE }, player.effects)
        }
    }

    @Test
    fun unavailableReceiveIsDiscardedAndNewMessagesCanStillPlay() = runTest {
        withAudio { sounds, player ->
            player.available = false
            val lost = text("unavailable")
            sounds.onMessageArrived(lost)
            player.available = true
            sounds.onMessageArrived(lost)
            tick(100)
            assertEquals(listOf(0L), player.calls.map { it.at })

            sounds.onMessageArrived(text("new"))
            assertEquals(listOf(0L, 100L), player.calls.map { it.at })
            tick(1_000)
            sounds.onInteractionAccepted()
            assertEquals(listOf(0L, 100L, 1_100L), player.calls.map { it.at })
            assertEquals(listOf(SoundEffect.RECEIVE, SoundEffect.RECEIVE, SoundEffect.CLICK), player.effects)
        }
    }

    @Test
    fun pauseStopsVoicesCancelsPendingStampsAndResumeUsesTheCurrentHistory() = runTest {
        val established = text("initial")
        withAudio(history = listOf(established)) { sounds, player ->
            sounds.onMessageArrived(established)
            val incoming = text("before-pause")
            val outgoing = sticker("unfinished")
            val inBackground = text("not-yet-observed-by-ui")
            sounds.onMessageArrived(incoming)
            sounds.onMessageArrived(outgoing)
            tick(50)
            sounds.pause()
            sounds.pause()
            sounds.onInteractionAccepted()
            assertEquals(1, player.stops)
            sounds.resume(listOf(established, incoming, outgoing, inBackground))
            sounds.onMessageArrived(inBackground)
            sounds.onAppearanceFinished(outgoing.id)
            tick(250)
            assertEquals(listOf(SoundEffect.RECEIVE), player.effects)
            sounds.onMessageArrived(text("new-after-resume"))
            assertEquals(List(2) { SoundEffect.RECEIVE }, player.effects)
            assertEquals(listOf(0L, 300L), player.calls.map { it.at })
        }
    }

    @Test
    fun muteCancelsPendingWorkAndVolumeIsMasterTimesSfxForNewVoices() = runTest {
        withAudio { sounds, player ->
            sounds.setVolumes(ChatAudioVolumes(master = 0.5f, sfx = 0.4f), emptyList())
            sounds.onInteractionAccepted()
            sounds.onMessageArrived(text("before-mute"))
            sounds.onMessageArrived(sticker("pending"))
            tick(40)
            sounds.setVolumes(ChatAudioVolumes(master = 0.5f, sfx = 0f), emptyList())
            sounds.onMessageArrived(text("while-muted"))
            sounds.onInteractionAccepted()
            assertEquals(1, player.stops)
            val waitingForComposition = text("waiting-for-composition")
            sounds.setVolumes(
                ChatAudioVolumes(master = 0.5f, sfx = 0.5f),
                listOf(waitingForComposition)
            )
            sounds.onMessageArrived(text("while-muted"))
            sounds.onMessageArrived(waitingForComposition)
            sounds.onAppearanceFinished("pending")
            tick(200)
            sounds.onMessageArrived(text("after-mute"))
            assertEquals(listOf(0.2f, 0.2f, 0.25f), player.calls.map { it.volume })
        }
    }

    @Test
    fun releaseIsIdempotentAndRejectsAllLateEvents() = runTest {
        withAudio { sounds, player ->
            sounds.onMessageArrived(text("incoming"))
            sounds.onMessageArrived(sticker("pending-stamp"))
            tick(50)
            sounds.release()
            sounds.release()
            sounds.resume(emptyList())
            sounds.onInteractionAccepted()
            sounds.onAppearanceFinished("pending-stamp")
            sounds.onMessageArrived(text("late"))
            tick(1_000)
            assertEquals(1, player.releases)
            assertEquals(1, player.stops)
            assertEquals(listOf(SoundEffect.RECEIVE), player.effects)
        }
    }

    private fun TestScope.withAudio(
        history: List<Message> = emptyList(),
        run: (ChatSoundCoordinator, RecordingPlayer) -> Unit
    ) {
        val player = RecordingPlayer { testScheduler.currentTime }
        val sounds = ChatSoundCoordinator(player)
        try {
            sounds.resume(history)
            run(sounds, player)
        } finally {
            sounds.release()
        }
    }

    private fun TestScope.tick(milliseconds: Long) {
        advanceTimeBy(milliseconds)
        runCurrent()
    }

    private fun text(id: String, sender: MessageSender = MessageSender.AME) =
        Message(id, sender, MessageContent.Text("test.text"), sentAt = 0L)

    private fun sticker(id: String) =
        Message(id, MessageSender.PCHAN, MessageContent.Sticker(PCHAN_PACK_ID, "ok"), sentAt = 0L)

    private class RecordingPlayer(private val now: () -> Long) : SoundEffectPlayer {
        data class Call(val effect: SoundEffect, val at: Long, val volume: Float)

        val calls = mutableListOf<Call>()
        val effects get() = calls.map { it.effect }
        var available = true
        var stops = 0
        var releases = 0

        override fun play(effect: SoundEffect, volume: Float): Boolean {
            assertTrue(releases == 0)
            calls.add(Call(effect, now(), volume))
            return available
        }

        override fun stopAll() { stops++ }
        override fun release() { releases++ }
    }
}
