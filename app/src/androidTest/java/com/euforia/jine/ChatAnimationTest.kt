package com.euforia.jine

import android.accessibilityservice.AccessibilityServiceInfo
import android.graphics.Bitmap
import android.graphics.Rect
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.view.MotionEvent
import android.view.PixelCopy
import android.view.accessibility.AccessibilityNodeInfo
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.runtime.BroadcastFrameClock
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.InternalComposeUiApi
import androidx.compose.ui.MotionDurationScale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.compositeOver
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.WindowRecomposerFactory
import androidx.compose.ui.platform.WindowRecomposerPolicy
import androidx.compose.ui.platform.createLifecycleAwareWindowRecomposer
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.euforia.jine.core.audio.AudioFocusChange
import com.euforia.jine.core.audio.AudioSessionViewModel
import com.euforia.jine.core.audio.ChatSoundEvents
import com.euforia.jine.core.audio.ChatSoundCoordinator
import com.euforia.jine.core.audio.JineAudioManager
import com.euforia.jine.core.audio.MusicPlayer
import com.euforia.jine.core.audio.SoundEffect
import com.euforia.jine.core.audio.SoundEffectPlayer
import com.euforia.jine.core.localization.ProvideLocalizedResources
import com.euforia.jine.core.localization.rememberLocalization
import com.euforia.jine.data.model.AME_PACK_ID
import com.euforia.jine.data.model.Message
import com.euforia.jine.data.model.MessageContent
import com.euforia.jine.data.model.MessageSender
import com.euforia.jine.data.model.PCHAN_PACK_ID
import com.euforia.jine.data.model.StickerCatalogs
import com.euforia.jine.data.model.StickerPack
import com.euforia.jine.data.model.dialogue.DialogueAction
import com.euforia.jine.data.model.dialogue.DialogueDefinition
import com.euforia.jine.data.model.dialogue.DialogueNode
import com.euforia.jine.data.model.dialogue.DialogueOption
import com.euforia.jine.data.repository.StickerRepository
import com.euforia.jine.ui.components.CHAT_CHOICE_PANEL_TAG
import com.euforia.jine.ui.components.CHAT_CHOICE_TAG_PREFIX
import com.euforia.jine.ui.components.CHAT_HISTORY_TAG
import com.euforia.jine.ui.components.CHAT_MESSAGE_TAG_PREFIX
import com.euforia.jine.ui.components.ChatHistory
import com.euforia.jine.ui.components.DialogueChoicePanel
import com.euforia.jine.ui.components.PICKER_STICKER_TAG_PREFIX
import com.euforia.jine.ui.screens.ChatInputMode
import com.euforia.jine.ui.screens.ChatScreen
import com.euforia.jine.ui.screens.ChatViewModel
import com.euforia.jine.ui.screens.DialogueChoice
import com.euforia.jine.ui.screens.rememberChatSounds
import com.euforia.jine.ui.theme.ChatAnimationSpec
import com.euforia.jine.ui.theme.JINE_PANEL_OVERLAY_ALPHA
import com.euforia.jine.ui.theme.JineBubble
import com.euforia.jine.ui.theme.JineChatBackground
import com.euforia.jine.ui.theme.JineReadReceipt
import com.euforia.jine.ui.theme.JineSentBubble
import com.euforia.jine.ui.theme.JineStickerOverlay
import com.euforia.jine.ui.theme.JineText
import com.euforia.jine.ui.theme.LocalChatAnimationSpec
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestCoroutineScheduler
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assert.assertNull
import org.junit.Test
import org.junit.runner.RunWith
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicLong
import kotlin.math.abs

@OptIn(InternalComposeUiApi::class, ExperimentalCoroutinesApi::class)
@RunWith(AndroidJUnit4::class)
class ChatAnimationTest {
    private val motion = TestMotionScale()

    private val instrumentation = InstrumentationRegistry.getInstrumentation()
    private val clock = BroadcastFrameClock()
    private var frameMillis = 0L
    private lateinit var scenario: ActivityScenario<ComponentActivity>
    private lateinit var listState: LazyListState

    private val monotonicNanos = AtomicLong()
    private val clockReads = AtomicInteger()
    private val spec = ChatAnimationSpec(nowNanos = {
        clockReads.incrementAndGet()
        monotonicNanos.get()
    })
    private val catalogs by lazy {
        StickerCatalogs(
            pchan = StickerRepository.load(instrumentation.targetContext),
            ame = StickerRepository.load(instrumentation.targetContext, StickerPack.AME)
        )
    }
    private val gaming = DialogueChoice("gaming", "dialogue.preview.narrative.choice.gaming")
    private val talking = DialogueChoice("talking", "dialogue.preview.narrative.choice.talking")
    private val longChoice = DialogueChoice("long", "dialogue.preview.narrative.choice.long")
    private val backdrop = JineStickerOverlay.copy(alpha = JINE_PANEL_OVERLAY_ALPHA).compositeOver(JineChatBackground)

    @Test
    fun batchedRowsFadeIndependentlyAndLanguageReceiptAndWidthChangesKeepTheirProgress() = withHost {
        val oldAme = text("old-ame")
        val oldSticker = sticker("old-sticker")
        val first = text("new-ame")
        val sentText = text("new-text", MessageSender.PCHAN)
        val sentSticker = sticker("new-sticker")
        val second = text("next-ame")
        val messages = mutableStateOf(listOf(oldAme, oldSticker))
        val language = mutableStateOf("es")
        val width = mutableStateOf(360.dp)
        setContent { History(messages.value, language.value, width = width.value) }
        settle()
        assertHasColor(row(oldAme.id).pixels(), JineBubble)
        val establishedAme = row(oldAme.id).pixels()
        val establishedSticker = row(oldSticker.id).pixels()

        onMain { messages.value += listOf(first, sentText, sentSticker) }
        settle()
        for (id in listOf(first.id, sentText.id, sentSticker.id)) {
            val start = row(id).pixels()
            assertTrue("Row $id must start transparent", start.values.all { near(it, JineChatBackground.toArgb(), 1) })
        }
        val firstSize = row(first.id).size()
        at(50)
        assertHasColor(row(first.id).pixels(), faded(JineBubble, 0.25f))
        assertHasColor(row(sentText.id).pixels(), faded(JineSentBubble, 0.25f))
        assertBlended(visible = row(first.id).pixels(), reference = establishedAme, alpha = 0.25f)
        assertBlended(visible = row(sentSticker.id).pixels(), reference = establishedSticker, alpha = 0.25f)
        assertEquals(firstSize, row(first.id).size())

        at(100)
        onMain {
            language.value = "en"
            width.value = 320.dp
            messages.value = messages.value.map { if (it.id == sentText.id) it.copy(readAt = 100L) else it } + second
        }
        settle()
        assertText(row(first.id), "Are you there?")
        assertText(row(sentText.id), "Read")
        assertHasColor(row(first.id).pixels(), faded(JineBubble, 0.5f))
        assertHasColor(row(sentText.id).pixels(), faded(JineSentBubble, 0.5f))
        assertHasColor(row(sentText.id).pixels(), faded(JineReadReceipt, 0.5f), tolerance = 8)
        assertTrue(row(second.id).pixels().values.all { near(it, JineChatBackground.toArgb(), 1) })

        at(200)
        assertHasColor(row(first.id).pixels(), JineBubble)
        assertHasColor(row(second.id).pixels(), faded(JineBubble, 0.5f))
        onMain {
            messages.value = messages.value.map { if (it.id == sentSticker.id) it.copy(readAt = 200L) else it }
        }
        settle()
        assertText(row(sentSticker.id), "Read")
        assertHasColor(row(sentSticker.id).pixels(), JineReadReceipt, tolerance = 8)
        at(300)
        assertHasColor(row(second.id).pixels(), JineBubble)
    }

    @Test
    fun offscreenRowsUseTheirRemainingWindowAndRecyclingNeverReplaysIt() = withHost {
        val existing = List(30) { text("history-$it") }
        val messages = mutableStateOf(existing)
        val first = text("incoming")
        val second = text("offscreen")
        setContent { History(messages.value, height = 180.dp) }
        settle(5)
        drag(boundsOf(awaitTag(CHAT_HISTORY_TAG)), towardEnd = false)
        settle()
        scrollTo(0)
        assertHasColor(row(existing.first().id).pixels(), JineBubble)
        onMain { messages.value += first }
        settle()
        assertNull(findTag("$CHAT_MESSAGE_TAG_PREFIX${first.id}"))
        row(existing.first().id)

        at(100)
        scrollTo(existing.size)
        assertHasColor(row(first.id).pixels(), faded(JineBubble, 0.5f))
        scrollTo(0)
        onMain { messages.value += second }
        settle()
        at(350)
        scrollTo(existing.size + 1)
        assertHasColor(row(first.id).pixels(), JineBubble)
        assertHasColor(row(second.id).pixels(), JineBubble)
        scrollTo(0)
        assertHasColor(row(existing.first().id).pixels(), JineBubble)
    }

    @Test
    fun recreationTreatsCurrentRowsAsEstablishedAndDisposalStopsFrameReads() = withHost {
        val messages = mutableStateOf(listOf(text("initial")))
        val visible = mutableStateOf(true)

        fun attach() {
            scenario.onActivity { activity ->
                activity.setContent { if (visible.value) History(messages.value, height = 250.dp) }
            }
            settle(5)
        }
        attach()
        onMain { messages.value += text("before-recreation") }
        settle()
        at(100)
        assertHasColor(row("before-recreation").pixels(), faded(JineBubble, 0.5f))

        scenario.recreate()
        attach()
        assertHasColor(row("initial").pixels(), JineBubble)
        assertHasColor(row("before-recreation").pixels(), JineBubble)
        onMain { messages.value += text("after-recreation") }
        settle()
        assertTrue(row("after-recreation").pixels().values.all { near(it, JineChatBackground.toArgb(), 1) })
        at(150)
        assertHasColor(row("after-recreation").pixels(), faded(JineBubble, 0.25f))
        onMain { visible.value = false }
        settle()
        val reads = clockReads.get()
        advanceFrames(500)
        settle()
        assertEquals(reads, clockReads.get())
    }

    @Test
    fun choicesShareAReversingPulseWhileTextAndClickTargetsStayOpaqueAndStable() = withHost {
        val language = mutableStateOf("es")
        val prompt = mutableStateOf(ChatInputMode.Choices("original", listOf(gaming, talking)))
        val choices = mutableListOf<Pair<String, String>>()
        setContent {
            Choices(prompt.value, language.value) { promptId, choiceId -> choices.add(promptId to choiceId) }
        }
        settle(2)
        val initial = option(gaming).pixels()
        val textPixels = initial.values.indices.filter { near(initial.values[it], JineText.toArgb(), 1) }
        assertTrue("Expected solid text even when the background is transparent", textPixels.size > 10)
        val size = option(gaming).size()

        fun expectPhase(alpha: Float, sameText: Boolean = true) {
            for (choice in listOf(gaming, talking)) {
                val actual = option(choice).pixels()
                assertChoiceBackground(actual, alpha)
                if (sameText && choice == gaming) {
                    assertTrue("The opaque text must keep its color during the pulse", textPixels.all {
                        near(actual.values[it], initial.values[it], 2)
                    })
                }
            }
        }

        expectPhase(0f)
        tap(option(gaming))
        onMain { assertEquals(listOf("original" to "gaming"), choices) }
        for (alpha in listOf(0.5f, 1f, 0.5f, 0f, 0.5f)) {
            advanceFrames(400)
            instrumentation.waitForIdleSync()
            expectPhase(alpha)
            assertEquals(size, option(gaming).size())
        }
        onMain { language.value = "en" }
        settle(2)
        assertText(option(gaming), "A gaming stream!")
        expectPhase(0.5f, sameText = false)

        onMain {
            prompt.value = ChatInputMode.Choices("new-prompt", listOf(gaming, talking))
        }
        settle(2)
        expectPhase(0f, sameText = false)
        click(option(talking))
        onMain { assertEquals(listOf("original" to "gaming", "new-prompt" to "talking"), choices) }
    }

    @Test
    fun scrolledChoicesJoinTheCurrentPhaseAndTheSelectedTextUsesOnlyItsMessageFade() = withHost {
        val options = List(8) { gaming.copy(id = "choice-$it") } + longChoice
        var prompt by mutableStateOf<ChatInputMode.Choices?>(ChatInputMode.Choices("scrolling", options))
        val messages = mutableStateOf<List<Message>>(emptyList())
        val events = mutableListOf<Pair<String, String>>()
        setContent {
            Column {
                History(messages.value, height = 220.dp)
                prompt?.let { current ->
                    Choices(current, "es") { promptId, choiceId ->
                        events.add(promptId to choiceId)
                        val choice = current.options.single { it.id == choiceId }
                        messages.value += text("selected", MessageSender.PCHAN, choice.textKey)
                        prompt = null
                    }
                }
            }
        }
        settle(2)
        advanceFrames(400)
        val panel = awaitTag(CHAT_CHOICE_PANEL_TAG)
        repeat(10) {
            if (findTag("$CHAT_CHOICE_TAG_PREFIX${longChoice.id}") == null) {
                drag(boundsOf(panel), towardEnd = true)
                settle(2, step = 1)
            }
        }
        settle()
        assertChoiceBackground(option(longChoice).pixels(), 0.5f, tolerance = 16)
        click(option(longChoice))
        settle()
        assertNull(findTag(CHAT_CHOICE_PANEL_TAG))
        assertTrue(row("selected").pixels().values.all { near(it, JineChatBackground.toArgb(), 1) })
        at(100)
        assertHasColor(row("selected").pixels(), faded(JineSentBubble, 0.5f))
        at(200)
        val final = row("selected").pixels()
        assertHasColor(final, JineSentBubble)
        at(1_600)
        assertTrue(final.values.contentEquals(row("selected").pixels().values))
        onMain { assertEquals(listOf("scrolling" to "long"), events) }
    }

    @Test
    fun zeroSystemScaleFinishesActiveMotionAndKeepsNewMessagesAndOptionsOpaque() = withHost {
        val messages = mutableStateOf<List<Message>>(emptyList())
        val prompt = ChatInputMode.Choices("reduced", listOf(gaming))
        val events = mutableListOf<Pair<String, String>>()
        setContent {
            Column {
                History(messages.value, height = 150.dp)
                Choices(prompt, "en") { promptId, choiceId -> events.add(promptId to choiceId) }
            }
        }
        settle(2)
        onMain { messages.value += text("already-fading") }
        settle(2)
        at(100)
        assertHasColor(row("already-fading").pixels(), faded(JineBubble, 0.5f))
        onMain {
            motion.scaleFactor = 0f
            messages.value += text("no-fade")
        }
        settle()
        assertHasColor(row("already-fading").pixels(), JineBubble)
        assertHasColor(row("no-fade").pixels(), JineBubble)
        assertChoiceBackground(option(gaming).pixels(), 1f)
        click(option(gaming))
        onMain { assertEquals(listOf("reduced" to "gaming"), events) }
        advanceFrames(1_600)
        assertChoiceBackground(option(gaming).pixels(), 1f)
        onMain {
            motion.scaleFactor = 2f
            messages.value += text("scaled")
        }
        settle()
        assertHasColor(row("no-fade").pixels(), JineBubble)
        at(200)
        assertHasColor(row("scaled").pixels(), faded(JineBubble, 0.25f))
    }

    @Test
    fun realScreenSoundsOnlyAcceptedActionsAndUsesTheFrameThatCompletesTheSend() = withHost {
        val scheduler = TestCoroutineScheduler()
        val audio = RecordingSounds(scheduler)
        val language = mutableStateOf("es")
        val chat = attachSoundChat(scheduler, audio, language)
        val ok = awaitTag("${PICKER_STICKER_TAG_PREFIX}ok")
        assertTrue(audio.effects.isEmpty())
        val initial = chat.uiState.value.messages

        val bounds = boundsOf(ok)
        val down = SystemClock.uptimeMillis()
        pointer(down, MotionEvent.ACTION_DOWN, bounds.exactCenterX(), bounds.exactCenterY())
        pointer(down, MotionEvent.ACTION_CANCEL, bounds.exactCenterX(), bounds.exactCenterY())
        assertEquals(initial, chat.uiState.value.messages)
        assertTrue(audio.effects.isEmpty())

        tap(ok)
        settle()
        val outgoing = chat.uiState.value.messages.last()
        assertEquals(MessageContent.Sticker(PCHAN_PACK_ID, "ok"), outgoing.content)
        assertEquals(listOf(SoundEffect.CLICK), audio.effects)
        at(199)
        assertEquals(listOf(SoundEffect.CLICK), audio.effects)
        at(200)
        assertEquals(listOf(SoundEffect.CLICK, SoundEffect.SEND_STAMP), audio.effects)

        advanceChatTime(scheduler, 650)
        assertTrue(chat.uiState.value.messages.last().isRead)
        onMain { language.value = "en" }
        settle()
        assertEquals(listOf(SoundEffect.CLICK, SoundEffect.SEND_STAMP), audio.effects)
        advanceChatTime(scheduler, 950)
        assertEquals(MessageSender.AME, chat.uiState.value.messages.last().sender)
        assertEquals(listOf(1_600L), audio.receiveTimes)
        advanceChatTime(scheduler, 500)
        assertEquals(listOf(1_600L), audio.receiveTimes)

        // Keep the old picker rendered while the ViewModel has already closed admission.
        val stalePicker = awaitTag("${PICKER_STICKER_TAG_PREFIX}ok")
        val beforeChoice = chat.uiState.value.messages
        onMain { assertTrue(chat.requestDialogue(soundTestPrompt())) }
        tap(stalePicker)
        assertEquals(beforeChoice, chat.uiState.value.messages)
        assertEquals(1, audio.effects.count { it == SoundEffect.CLICK })
        advanceChatTime(scheduler, 0)

        val choice = option(gaming)
        click(choice)
        tap(choice) // The still-rendered button now carries an already-consumed prompt ID.
        assertEquals(beforeChoice.size + 1, chat.uiState.value.messages.size)
        assertEquals(MessageContent.Text(gaming.textKey), chat.uiState.value.messages.last().content)
        assertEquals(2, audio.effects.count { it == SoundEffect.CLICK })
        onMain { motion.scaleFactor = 0f }
        settle()
        assertEquals(
            listOf(SoundEffect.CLICK, SoundEffect.SEND_STAMP, SoundEffect.RECEIVE,
                SoundEffect.CLICK, SoundEffect.SEND_STAMP),
            audio.effects
        )
        advanceChatTime(scheduler, 1_600)
        awaitTag("${PICKER_STICKER_TAG_PREFIX}ok")
        assertEquals(5, audio.effects.size)
    }

    @Test
    fun soundDoesNotReplayAfterResumeOrRecreationWithAccumulatedHistory() = withHost {
        val scheduler = TestCoroutineScheduler()
        val originalAudio = RecordingSounds(scheduler)
        val chat = attachSoundChat(scheduler, originalAudio)
        tap(awaitTag("${PICKER_STICKER_TAG_PREFIX}ok"))
        settle()
        assertEquals(listOf(SoundEffect.CLICK), originalAudio.effects)

        scenario.moveToState(Lifecycle.State.CREATED)
        onMain {
            scheduler.advanceTimeBy(1_600)
            scheduler.runCurrent()
        }
        assertEquals(MessageSender.AME, chat.uiState.value.messages.last().sender)
        assertEquals(1, originalAudio.stops)
        scenario.moveToState(Lifecycle.State.RESUMED)
        at(500)
        assertEquals(listOf(SoundEffect.CLICK), originalAudio.effects)

        tap(awaitTag("${PICKER_STICKER_TAG_PREFIX}gomen"))
        settle()
        at(700)
        assertEquals(listOf(SoundEffect.CLICK, SoundEffect.CLICK, SoundEffect.SEND_STAMP), originalAudio.effects)
        advanceChatTime(scheduler, 1_600)
        assertEquals(listOf(3_200L), originalAudio.receiveTimes)

        scenario.recreate()
        val restored = attachSoundChat(scheduler, originalAudio)
        assertTrue(chat === restored)
        assertEquals(0, originalAudio.releases)
        advanceChatTime(scheduler, 500)
        assertEquals(listOf(3_200L), originalAudio.receiveTimes)
        val effectsBeforeSend = originalAudio.effects
        tap(awaitTag("${PICKER_STICKER_TAG_PREFIX}ok"))
        settle()
        at(900)
        assertEquals(effectsBeforeSend + listOf(SoundEffect.CLICK, SoundEffect.SEND_STAMP), originalAudio.effects)
    }

    @Test
    fun newAmeStickerUsesTheHistoryFadeAndOneReceiveWithNoReplay() = withHost {
        val language = mutableStateOf("es")
        val established = ameSticker("established-sticker", "kawaikunai")
        val history = mutableStateOf(List(8) { text("older-$it", key = "dialogue.preview.greeting") } + established)
        val audio = RecordingSounds(TestCoroutineScheduler())
        val focus = TestAudioFocus()
        var preparations = 0
        var plays = 0
        val music = object : MusicPlayer by NoMusic {
            override fun prepare() { preparations++ }
            override fun play() { plays++ }
        }
        scenario.onActivity { activity ->
            val factory = viewModelFactory {
                initializer { AudioSessionViewModel(JineAudioManager(music, ChatSoundCoordinator(audio), focus)) }
            }
            val session = ViewModelProvider(activity, factory)[AudioSessionViewModel::class.java]
            val host = session.bindHost(activity.lifecycle)
            activity.setContent {
                History(history.value, language.value, height = 230.dp, sounds = rememberChatSounds(host) { history.value })
            }
        }
        settle(5)
        val reference = row(established.id).pixels()
        assertTrue(audio.effects.isEmpty())

        val first = ameSticker("new-sticker", "kawaikunai")
        onMain { history.value += first }
        at(0)
        assertTrue(row(first.id).pixels().values.all { near(it, JineChatBackground.toArgb(), 1) })
        assertEquals(listOf(SoundEffect.RECEIVE), audio.effects)
        at(100)
        assertBlended(row(first.id).pixels(), reference, alpha = 0.5f)
        onMain { language.value = "en" }
        at(200)
        assertTrue(row(first.id).find { it.contentDescription?.toString() == "So not cute!" } != null)
        val englishPixels = row(first.id).pixels().values
        scrollTo(0)
        assertNull(findTag("$CHAT_MESSAGE_TAG_PREFIX${first.id}"))
        scrollTo(history.value.lastIndex)
        assertTrue(englishPixels.contentEquals(row(first.id).pixels().values))
        assertEquals(listOf(SoundEffect.RECEIVE), audio.effects)
        assertEquals(1, plays)

        val instant = ameSticker("instant-sticker", "aseru")
        onMain {
            motion.scaleFactor = 0f
            history.value += instant
        }
        at(200)
        assertTrue(row(instant.id).pixels().values.any { !near(it, JineChatBackground.toArgb(), 1) })
        assertEquals(List(2) { SoundEffect.RECEIVE }, audio.effects)

        val undelivered = ameSticker("while-focus-lost", "zzz")
        onMain {
            focus.emit(AudioFocusChange.TRANSIENT_LOSS)
            history.value += undelivered
            focus.emit(AudioFocusChange.GAIN)
        }
        settle()
        row(undelivered.id)
        assertEquals(List(2) { SoundEffect.RECEIVE }, audio.effects)

        val inBackground = ameSticker("while-backgrounded", "kiitenai")
        scenario.moveToState(Lifecycle.State.CREATED)
        onMain { history.value += inBackground }
        scenario.moveToState(Lifecycle.State.RESUMED)
        settle()
        row(inBackground.id)
        assertEquals(List(2) { SoundEffect.RECEIVE }, audio.effects)

        val next = ameSticker("after-resume", "amechan")
        onMain { history.value += next }
        settle()
        row(next.id)
        assertEquals(List(3) { SoundEffect.RECEIVE }, audio.effects)
        assertEquals(1, preparations)
        assertEquals(3, plays)
        assertEquals(2, focus.requests)
    }

    @Test
    fun deniedFocusLeavesChatFunctionalAndNewEffectsCanPlayAfterResume() = withHost {
        val scheduler = TestCoroutineScheduler()
        val player = RecordingSounds(scheduler)
        val focus = TestAudioFocus(granted = false)
        val chat = attachSoundChat(scheduler, player, focus = focus)
        tap(awaitTag("${PICKER_STICKER_TAG_PREFIX}ok"))
        settle()
        at(200)
        advanceChatTime(scheduler, 1_600)
        assertEquals(MessageSender.AME, chat.uiState.value.messages.last().sender)
        assertTrue(player.effects.isEmpty())
        assertEquals(1, focus.requests)

        scenario.moveToState(Lifecycle.State.CREATED)
        onMain { focus.granted = true }
        scenario.moveToState(Lifecycle.State.RESUMED)
        at(500)
        assertTrue(player.effects.isEmpty())
        tap(awaitTag("${PICKER_STICKER_TAG_PREFIX}ok"))
        settle()
        at(700)
        assertEquals(listOf(SoundEffect.CLICK, SoundEffect.SEND_STAMP), player.effects)
        assertEquals(2, focus.requests)
    }

    private fun attachSoundChat(
        scheduler: TestCoroutineScheduler,
        player: RecordingSounds,
        language: androidx.compose.runtime.State<String> = mutableStateOf("es"),
        focus: TestAudioFocus = TestAudioFocus()
    ): ChatViewModel {
        lateinit var chat: ChatViewModel
        scenario.onActivity { activity ->
            val dispatcher = StandardTestDispatcher(scheduler)
            val factory = viewModelFactory {
                initializer { ChatViewModel(now = { scheduler.currentTime }, dispatcher = dispatcher) }
            }
            chat = ViewModelProvider(activity, factory)[ChatViewModel::class.java]
            val audioFactory = viewModelFactory {
                initializer { AudioSessionViewModel(JineAudioManager(NoMusic, ChatSoundCoordinator(player), focus)) }
            }
            val audio = ViewModelProvider(activity, audioFactory)[AudioSessionViewModel::class.java]
            val host = audio.bindHost(activity.lifecycle)
            activity.setContent {
                CompositionLocalProvider(LocalChatAnimationSpec provides spec) {
                    ChatScreen(chat, language = language.value, audioHost = host)
                }
            }
        }
        settle(5)
        return chat
    }

    private fun advanceChatTime(scheduler: TestCoroutineScheduler, milliseconds: Long) {
        onMain {
            scheduler.advanceTimeBy(milliseconds)
            scheduler.runCurrent()
        }
        settle()
    }

    private fun soundTestPrompt() = DialogueDefinition(
        "audio-test", "prompt",
        mapOf(
            "prompt" to DialogueNode(listOf(DialogueAction.ShowChoices(
                listOf(DialogueOption(gaming.id, gaming.textKey, "end"))
            ))),
            "end" to DialogueNode(listOf(DialogueAction.End))
        )
    )

    private class RecordingSounds(private val scheduler: TestCoroutineScheduler) : SoundEffectPlayer {
        private val calls = mutableListOf<Pair<SoundEffect, Long>>()
        val effects get() = calls.map { it.first }
        val receiveTimes get() = calls.filter { it.first == SoundEffect.RECEIVE }.map { it.second }
        var stops = 0
            private set
        var releases = 0
            private set

        override fun play(effect: SoundEffect, volume: Float): Boolean {
            check(releases == 0)
            calls.add(effect to scheduler.currentTime)
            return true
        }

        override fun stopAll() { stops++ }
        override fun release() { releases++ }
    }

    private object NoMusic : MusicPlayer {
        override fun prepare() = Unit
        override fun play() = Unit
        override fun pause() = Unit
        override fun setVolume(volume: Float) = Unit
        override fun release() = Unit
    }

    @Composable
    private fun History(
        messages: List<Message>,
        language: String = "es",
        height: Dp = 540.dp,
        width: Dp = 360.dp,
        sounds: ChatSoundEvents? = null
    ) {
        val localization = rememberLocalization(language)
        CompositionLocalProvider(LocalChatAnimationSpec provides spec) {
            ProvideLocalizedResources(localization.language) {
                Box(Modifier.safeDrawingPadding()) {
                    BoxWithConstraints(Modifier.width(width).height(height).background(JineChatBackground)) {
                        val state = rememberLazyListState()
                        SideEffect { listState = state }
                        ChatHistory(
                            messages, localization, catalogs, state,
                            IntSize(constraints.maxWidth, constraints.maxHeight), panelHeightPx = 0, sounds = sounds
                        )
                    }
                }
            }
        }
    }

    @Composable
    private fun Choices(mode: ChatInputMode.Choices, language: String, choose: (String, String) -> Unit) {
        val localization = rememberLocalization(language)
        CompositionLocalProvider(LocalChatAnimationSpec provides spec) {
            Box(Modifier.safeDrawingPadding().fillMaxWidth().background(JineChatBackground)) {
                DialogueChoicePanel(mode, localization, choose)
            }
        }
    }

    private fun settle(frames: Int = 3, step: Long = 16) {
        repeat(frames) { advanceFrames(step) }
    }

    // Use the real Android view system and its gestures, but deliver Compose frames on demand.
    private fun withHost(block: () -> Unit) {
        val factory = WindowRecomposerFactory { view ->
            view.createLifecycleAwareWindowRecomposer(clock + motion)
        }
        WindowRecomposerPolicy.withFactory(factory) {
            ActivityScenario.launch(ComponentActivity::class.java).use {
                scenario = it
                val automation = instrumentation.uiAutomation
                automation.serviceInfo = automation.serviceInfo.apply {
                    flags = flags or AccessibilityServiceInfo.FLAG_REPORT_VIEW_IDS or
                        AccessibilityServiceInfo.FLAG_INCLUDE_NOT_IMPORTANT_VIEWS
                }
                block()
            }
        }
    }

    private fun setContent(content: @Composable () -> Unit) {
        scenario.onActivity { it.setContent(content = content) }
    }

    private fun onMain(action: () -> Unit) {
        scenario.onActivity { action() }
    }

    private fun advanceFrames(milliseconds: Long) {
        frameMillis += milliseconds
        onMain { clock.sendFrame(frameMillis * 1_000_000) }
        instrumentation.waitForIdleSync()
    }

    private fun at(milliseconds: Long) {
        monotonicNanos.set(milliseconds * 1_000_000)
        settle()
    }

    private fun scrollTo(index: Int) {
        onMain { listState.requestScrollToItem(index) }
        settle(4)
    }

    private fun text(
        id: String,
        sender: MessageSender = MessageSender.AME,
        key: String = "dialogue.preview.question"
    ) = Message(id, sender, MessageContent.Text(key), sentAt = 0L)

    private fun sticker(id: String) =
        Message(id, MessageSender.PCHAN, MessageContent.Sticker(PCHAN_PACK_ID, "love"), sentAt = 0L)

    private fun ameSticker(messageId: String, stickerId: String) =
        Message(messageId, MessageSender.AME, MessageContent.Sticker(AME_PACK_ID, stickerId), 0L, readAt = 1L)

    private fun row(id: String) = awaitTag("$CHAT_MESSAGE_TAG_PREFIX$id")
    private fun option(choice: DialogueChoice) = awaitTag("$CHAT_CHOICE_TAG_PREFIX${choice.id}")

    private fun awaitTag(tag: String): AccessibilityNodeInfo {
        val deadline = SystemClock.uptimeMillis() + 4_000
        do {
            findTag(tag)?.let { return it }
            instrumentation.waitForIdleSync()
        } while (SystemClock.uptimeMillis() < deadline)
        throw AssertionError("Could not find visible tag $tag")
    }

    private fun findTag(tag: String): AccessibilityNodeInfo? {
        val automation = instrumentation.uiAutomation
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) automation.clearCache()
        return automation.rootInActiveWindow?.find { it.viewIdResourceName == tag }
    }

    private fun AccessibilityNodeInfo.find(matches: (AccessibilityNodeInfo) -> Boolean): AccessibilityNodeInfo? {
        if (isVisibleToUser && matches(this)) return this
        for (index in 0 until childCount) {
            getChild(index)?.find(matches)?.let { return it }
        }
        return null
    }

    private fun assertText(node: AccessibilityNodeInfo, text: String) {
        assertTrue("Expected accessible text '$text'", node.find { it.text?.toString() == text } != null)
    }

    private fun boundsOf(node: AccessibilityNodeInfo) = Rect().also(node::getBoundsInScreen)
    private fun AccessibilityNodeInfo.size() = boundsOf(this).let { IntSize(it.width(), it.height()) }

    private fun click(node: AccessibilityNodeInfo) {
        assertTrue(node.performAction(AccessibilityNodeInfo.ACTION_CLICK))
        instrumentation.waitForIdleSync()
    }

    private fun tap(node: AccessibilityNodeInfo) {
        val bounds = boundsOf(node)
        val down = SystemClock.uptimeMillis()
        pointer(down, MotionEvent.ACTION_DOWN, bounds.exactCenterX(), bounds.exactCenterY())
        pointer(down, MotionEvent.ACTION_UP, bounds.exactCenterX(), bounds.exactCenterY())
        instrumentation.waitForIdleSync()
    }

    private fun drag(bounds: Rect, towardEnd: Boolean) {
        val down = SystemClock.uptimeMillis()
        val inset = bounds.height() / 8f
        val top = bounds.top + inset
        val bottom = bounds.bottom - inset
        val start = if (towardEnd) bottom else top
        val end = if (towardEnd) top else bottom
        val x = bounds.exactCenterX()
        pointer(down, MotionEvent.ACTION_DOWN, x, start)
        for (step in 1..8) {
            SystemClock.sleep(16)
            pointer(down, MotionEvent.ACTION_MOVE, x, start + (end - start) * step / 8f)
        }
        repeat(3) {
            SystemClock.sleep(40)
            pointer(down, MotionEvent.ACTION_MOVE, x, end)
        }
        pointer(down, MotionEvent.ACTION_UP, x, end)
        instrumentation.waitForIdleSync()
    }

    private fun pointer(down: Long, action: Int, x: Float, y: Float) {
        val event = MotionEvent.obtain(down, SystemClock.uptimeMillis(), action, x, y, 0)
        try {
            instrumentation.sendPointerSync(event)
        } finally {
            event.recycle()
        }
    }

    private class TestMotionScale : MotionDurationScale {
        override var scaleFactor by mutableFloatStateOf(1f)
    }

    private class Pixels(val height: Int, val width: Int, val values: IntArray)

    private fun AccessibilityNodeInfo.pixels(): Pixels {
        val bounds = boundsOf(this)
        val bitmap = Bitmap.createBitmap(bounds.width(), bounds.height(), Bitmap.Config.ARGB_8888)
        try {
            val ready = CountDownLatch(1)
            var result: Int? = null
            scenario.onActivity { activity ->
                val location = IntArray(2)
                activity.window.decorView.getLocationOnScreen(location)
                val source = Rect(bounds).apply { offset(-location[0], -location[1]) }
                PixelCopy.request(activity.window, source, bitmap, { code ->
                    result = code
                    ready.countDown()
                }, Handler(Looper.getMainLooper()))
            }
            assertTrue("Timed out waiting for window pixels", ready.await(5, TimeUnit.SECONDS))
            assertEquals(PixelCopy.SUCCESS, result)
            val values = IntArray(bitmap.width * bitmap.height)
            bitmap.getPixels(values, 0, bitmap.width, 0, 0, bitmap.width, bitmap.height)
            return Pixels(bitmap.height, bitmap.width, values)
        } finally {
            bitmap.recycle()
        }
    }

    private fun faded(color: Color, alpha: Float, background: Color = JineChatBackground): Color =
        color.copy(alpha = alpha).compositeOver(background)

    private fun assertHasColor(pixels: Pixels, color: Color, tolerance: Int = 4) {
        assertTrue("Expected rendered color ${color.toArgb().toUInt().toString(16)}",
            pixels.values.count { near(it, color.toArgb(), tolerance) } > 8)
    }

    private fun assertChoiceBackground(pixels: Pixels, alpha: Float, tolerance: Int = 13) {
        // Sample inside the right text padding, away from the rounded corners.
        val inset = (pixels.width / 30).coerceIn(3, 12)
        val actual = pixels.values[(pixels.height / 2) * pixels.width + pixels.width - inset]
        val expected = faded(JineSentBubble, alpha, backdrop).toArgb()
        assertTrue("Expected choice alpha $alpha, color ${expected.toUInt().toString(16)}, got ${actual.toUInt().toString(16)}",
            near(actual, expected, tolerance))
    }

    private fun assertBlended(visible: Pixels, reference: Pixels, alpha: Float) {
        assertEquals(reference.width, visible.width)
        val commonSize = minOf(reference.height, visible.height) * visible.width
        val samples = (0 until commonSize step 19).filter {
            !near(reference.values[it], JineChatBackground.toArgb(), 32)
        }
        assertTrue(samples.size > 100)
        assertTrue("Expected group alpha across the rendered row contents", samples.count {
            near(visible.values[it], faded(Color(reference.values[it]), alpha).toArgb(), 7)
        } > samples.size * 0.95)
    }

    private fun near(actual: Int, expected: Int, tolerance: Int): Boolean =
        abs((actual and 0xff) - (expected and 0xff)) <= tolerance &&
            abs((actual shr 8 and 0xff) - (expected shr 8 and 0xff)) <= tolerance &&
            abs((actual shr 16 and 0xff) - (expected shr 16 and 0xff)) <= tolerance
}
