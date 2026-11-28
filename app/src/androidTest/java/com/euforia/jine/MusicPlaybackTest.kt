package com.euforia.jine

import android.os.SystemClock
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import androidx.media3.common.MediaItem
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.euforia.jine.core.assets.JineAssets
import com.euforia.jine.core.audio.AudioFocusChange
import com.euforia.jine.core.audio.AudioFocusController
import com.euforia.jine.core.audio.AudioSessionViewModel
import com.euforia.jine.core.audio.ChatAudioVolumes
import com.euforia.jine.core.audio.ChatSoundCoordinator
import com.euforia.jine.core.audio.JineAudioManager
import com.euforia.jine.core.audio.Media3MusicPlayer
import com.euforia.jine.core.audio.MusicPlayer
import com.euforia.jine.core.audio.SilentSoundEffectPlayer
import com.euforia.jine.data.model.dialogue.DialogueAction
import com.euforia.jine.data.model.dialogue.DialogueDefinition
import com.euforia.jine.data.model.dialogue.DialogueNode
import com.euforia.jine.data.model.dialogue.DialogueOption
import com.euforia.jine.ui.screens.ChatInputMode
import com.euforia.jine.ui.screens.ChatScreen
import com.euforia.jine.ui.screens.ChatViewModel
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import java.io.DataInputStream
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import kotlin.math.abs

@RunWith(AndroidJUnit4::class)
class MusicPlaybackTest {
    private val instrumentation = InstrumentationRegistry.getInstrumentation()
    private val context = instrumentation.targetContext.applicationContext

    @Test
    fun packagedOggDecodesPausesResumesAndRepeatsTheSameTrack() {
        val path = JineAssets.Audio.Music.MAINLOOP_NORMAL
        val signature = ByteArray(4)
        DataInputStream(context.assets.open(path)).use { it.readFully(signature) }
        assertEquals("OggS", signature.decodeToString())

        val raw = onMain { ExoPlayer.Builder(context).build() }
        val music = Media3MusicPlayer(raw)
        val looped = CountDownLatch(1)
        val loopListener = object : Player.Listener {
            override fun onMediaItemTransition(mediaItem: MediaItem?, reason: Int) {
                if (reason == Player.MEDIA_ITEM_TRANSITION_REASON_REPEAT) looped.countDown()
            }
        }
        try {
            onMain {
                raw.addListener(loopListener)
                music.setVolume(0.1f)
                music.prepare()
            }
            awaitReady(raw)
            onMain {
                assertTrue(raw.duration > 0)
                assertEquals(1, raw.mediaItemCount)
                assertEquals("asset:///$path", raw.currentMediaItem?.localConfiguration?.uri.toString())
                assertEquals(Player.REPEAT_MODE_ONE, raw.repeatMode)
                assertFalse(raw.playWhenReady)
                music.play()
            }
            await("music advances") { raw.isPlaying && raw.currentPosition >= 450 }
            val pausedAt = onMain {
                music.pause()
                raw.currentPosition
            }
            assertHeldPosition(raw, pausedAt)
            onMain { music.play() }
            await("music continues from its paused position") { raw.currentPosition >= pausedAt + 200 }

            onMain { raw.seekTo(raw.duration - 600) }
            assertTrue("Media3 did not report repeating the track", looped.await(8, TimeUnit.SECONDS))
            await("music advances at the beginning of the next loop") {
                raw.isPlaying && raw.currentPosition in 100L..2_000L
            }
        } finally {
            onMain {
                raw.removeListener(loopListener)
                music.release()
                music.release()
            }
        }
    }

    @Test
    fun recreatedHostRetainsPlayingPositionVolumeAndAnActiveDialogue() {
        val focus = TestAudioFocus()
        var creations = 0
        var releases = 0
        lateinit var raw: ExoPlayer
        val audioFactory = viewModelFactory {
            initializer {
                creations++
                raw = ExoPlayer.Builder(context).build()
                val backend = Media3MusicPlayer(raw)
                val music = object : MusicPlayer by backend {
                    override fun release() {
                        backend.release()
                        releases++
                    }
                }
                AudioSessionViewModel(JineAudioManager(music, ChatSoundCoordinator(SilentSoundEffectPlayer), focus))
            }
        }
        ActivityScenario.launch(ComponentActivity::class.java).use { scenario ->
            lateinit var session: AudioSessionViewModel
            lateinit var chat: ChatViewModel
            fun bind() = scenario.onActivity { activity ->
                session = ViewModelProvider(activity, audioFactory)[AudioSessionViewModel::class.java]
                chat = ViewModelProvider(activity)[ChatViewModel::class.java]
                val host = session.bindHost(activity.lifecycle)
                activity.setContent { ChatScreen(chat, audioHost = host) }
            }

            bind()
            awaitReady(raw)
            await("foreground music advances") { raw.isPlaying && raw.currentPosition >= 500 }
            val originalSession = session
            val originalPlayer = raw
            val originalChat = chat
            scenario.onActivity {
                session.setVolumes(ChatAudioVolumes(bgm = 0.35f))
                assertTrue(chat.requestDialogue(dialogueWaitingForChoice()))
            }
            await("the dialogue reaches its prompt") { chat.uiState.value.inputMode is ChatInputMode.Choices }
            val prompt = onMain { chat.uiState.value.inputMode as ChatInputMode.Choices }

            scenario.moveToState(Lifecycle.State.CREATED)
            val backgroundPosition = onMain { raw.currentPosition }
            assertHeldPosition(raw, backgroundPosition)
            scenario.moveToState(Lifecycle.State.RESUMED)
            await("resuming advances the same player") { raw.isPlaying && raw.currentPosition >= backgroundPosition + 150 }

            val beforeRecreate = onMain { raw.currentPosition }
            scenario.recreate()
            bind()
            assertSame(originalSession, session)
            assertSame(originalPlayer, raw)
            assertSame(originalChat, chat)
            assertEquals(1, creations)
            assertEquals(0, releases)
            onMain {
                assertEquals(prompt, chat.uiState.value.inputMode)
                assertEquals(0.35f, raw.volume, 0.001f)
                assertTrue(raw.currentPosition >= beforeRecreate - 60)
            }
            await("the recreated host resumes from its previous position") {
                raw.isPlaying && raw.currentPosition >= beforeRecreate + 150
            }
            scenario.onActivity { assertTrue(chat.chooseDialogueOption(prompt.promptId, "yes")) }
            assertEquals(3, focus.requests)
        }
        assertEquals(1, releases)
        assertEquals(3, focus.abandons)
    }

    private fun dialogueWaitingForChoice() = DialogueDefinition(
        id = "audio-rotation",
        entryNodeId = "prompt",
        nodes = mapOf(
            "prompt" to DialogueNode(listOf(DialogueAction.ShowChoices(listOf(
                DialogueOption("yes", "dialogue.demo.reply.ok.01", "end")
            )))),
            "end" to DialogueNode(listOf(DialogueAction.End))
        )
    )

    private fun awaitReady(raw: ExoPlayer) {
        val ready = CountDownLatch(1)
        val observer = object : Player.Listener {
            override fun onPlaybackStateChanged(playbackState: Int) {
                if (playbackState == Player.STATE_READY) ready.countDown()
            }

            override fun onPlayerError(error: PlaybackException) = ready.countDown()
        }
        onMain {
            raw.addListener(observer)
            if (raw.playbackState == Player.STATE_READY || raw.playerError != null) ready.countDown()
        }
        try {
            assertTrue("Media3 did not reach READY", ready.await(10, TimeUnit.SECONDS))
            onMain {
                assertNull(raw.playerError)
                assertEquals(Player.STATE_READY, raw.playbackState)
            }
        } finally {
            onMain { raw.removeListener(observer) }
        }
    }

    private fun assertHeldPosition(raw: ExoPlayer, position: Long) {
        // Observe two points while paused so a player still advancing cannot pass.
        SystemClock.sleep(250)
        onMain {
            assertFalse(raw.isPlaying)
            assertTrue("Position advanced while paused", abs(raw.currentPosition - position) <= 60)
        }
    }

    private fun await(description: String, condition: () -> Boolean) {
        val deadline = SystemClock.uptimeMillis() + 8_000
        do {
            if (onMain(condition)) return
            SystemClock.sleep(25)
        } while (SystemClock.uptimeMillis() < deadline)
        throw AssertionError("Timed out waiting until $description")
    }

    private fun <T> onMain(action: () -> T): T {
        var result: Result<T>? = null
        instrumentation.runOnMainSync { result = runCatching(action) }
        return checkNotNull(result).getOrThrow()
    }
}

internal class TestAudioFocus(var granted: Boolean = true) : AudioFocusController {
    var requests = 0
        private set
    var abandons = 0
        private set

    override fun request(onChange: (AudioFocusChange) -> Unit): Boolean {
        requests++
        return granted
    }

    override fun abandon() { abandons++ }
}
