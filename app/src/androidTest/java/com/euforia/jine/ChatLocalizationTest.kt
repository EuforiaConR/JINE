package com.euforia.jine

import android.accessibilityservice.AccessibilityServiceInfo
import android.content.res.Configuration
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
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.euforia.jine.core.assets.JineAssets
import com.euforia.jine.core.audio.AudioSessionViewModel
import com.euforia.jine.core.audio.ChatSoundCoordinator
import com.euforia.jine.core.audio.JineAudioManager
import com.euforia.jine.core.audio.MusicPlayer
import com.euforia.jine.core.audio.SoundEffect
import com.euforia.jine.core.audio.SoundEffectPlayer
import com.euforia.jine.core.localization.JineLanguageManager
import com.euforia.jine.core.localization.ProvideLocalizedResources
import com.euforia.jine.core.localization.rememberLocalization
import com.euforia.jine.data.model.AME_PACK_ID
import com.euforia.jine.data.model.Message
import com.euforia.jine.data.model.MessageContent
import com.euforia.jine.data.model.MessageSender
import com.euforia.jine.data.model.PCHAN_PACK_ID
import com.euforia.jine.data.model.Sticker
import com.euforia.jine.data.model.StickerCatalogs
import com.euforia.jine.data.model.StickerPack
import com.euforia.jine.data.repository.StickerRepository
import com.euforia.jine.data.repository.DialogueRepository
import com.euforia.jine.ui.components.CHAT_MESSAGE_TAG_PREFIX
import com.euforia.jine.ui.components.CHAT_HISTORY_TAG
import com.euforia.jine.ui.components.CHAT_CHOICE_PANEL_TAG
import com.euforia.jine.ui.components.CHAT_CHOICE_TAG_PREFIX
import com.euforia.jine.ui.components.CHAT_SCROLLBAR_TAG
import com.euforia.jine.ui.components.ChatMessageItem
import com.euforia.jine.ui.components.PICKER_STICKER_TAG_PREFIX
import com.euforia.jine.ui.components.PixelImage
import com.euforia.jine.ui.components.StickerPicker
import com.euforia.jine.ui.screens.ChatScreen
import com.euforia.jine.ui.screens.CHAT_PANEL_TAG
import com.euforia.jine.ui.screens.ChatContent
import com.euforia.jine.ui.screens.ChatInputMode
import com.euforia.jine.ui.screens.ChatTimings
import com.euforia.jine.ui.screens.ChatUiState
import com.euforia.jine.ui.screens.ChatViewModel
import com.euforia.jine.ui.screens.CommunicationState
import com.euforia.jine.ui.screens.DialogueChoice
import com.euforia.jine.ui.screens.initialMessages
import com.euforia.jine.ui.screens.loadStickerCatalogs
import com.euforia.jine.ui.theme.ChatAnimationSpec
import com.euforia.jine.ui.theme.JineChatBackground
import com.euforia.jine.ui.theme.LocalChatAnimationSpec
import com.euforia.jine.ui.theme.JineSentBubble
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestCoroutineScheduler
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.util.Locale
import java.util.concurrent.CompletableFuture
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger
import kotlin.math.roundToInt

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(AndroidJUnit4::class)
class ChatLocalizationTest {
    private val instrumentation get() = InstrumentationRegistry.getInstrumentation()
    private val context get() = instrumentation.targetContext
    private val previewIds = listOf("preview_greeting", "preview_question", "preview_long_message")
    private val gaming = DialogueChoice("gaming", "dialogue.preview.narrative.choice.gaming")
    private val talking = DialogueChoice("talking", "dialogue.preview.narrative.choice.talking")
    private val longChoice = DialogueChoice("long", "dialogue.preview.narrative.choice.long")
    private val gamingEs = "¡Un directo de videojuegos!"
    private val talkingEs = "¡Un directo para conversar!"
    private val longChoiceEs = "Podríamos probar un juego tranquilo, charlar con el público y preparar juntos una lista de ideas para el siguiente directo."

    @Before
    fun exposeAccessibilityResourceIds() {
        val automation = instrumentation.uiAutomation
        automation.serviceInfo = automation.serviceInfo.apply {
            flags = flags or AccessibilityServiceInfo.FLAG_REPORT_VIEW_IDS or
                AccessibilityServiceInfo.FLAG_INCLUDE_NOT_IMPORTANT_VIEWS
        }
    }

    @Test
    fun startsInSpanishOnEnglishDeviceAndChangesLanguageWithoutSending() {
        val englishConfig = Configuration(context.resources.configuration).apply { setLocale(Locale.ENGLISH) }
        val englishContext = context.createConfigurationContext(englishConfig)
        val requestedLanguage = mutableStateOf(JineLanguageManager.DEFAULT_LANGUAGE)
        val sends = mutableListOf<Sticker>()
        val stickers = StickerRepository.load(context)

        ActivityScenario.launch(ComponentActivity::class.java).use { scenario ->
            scenario.onActivity { activity ->
                activity.setStaticChatContent {
                    CompositionLocalProvider(
                        LocalContext provides englishContext,
                        LocalConfiguration provides englishConfig
                    ) {
                        ChatScreen(language = requestedLanguage.value, onStickerSent = sends::add)
                    }
                }
            }

            awaitNode("p-chaaaan!!")
            awaitNode("¿Estás ahí?")
            awaitNode("Tengo muchas cosas que contarte hoy. Espero que podamos hablar un rato.")
            awaitNode("Minimizar")
            assertEquals(previewIds, visibleMessageIds())

            val spanish = JineLanguageManager().load(context, "es")
            val positions = stickers.map { sticker ->
                boundsOf(awaitPickerSticker(sticker.id, spanish.translate(sticker.descriptionKey)))
            }
            assertEquals(4, positions.take(4).map { it.left }.distinct().size)
            assertEquals(1, positions.take(4).map { it.top }.distinct().size)
            assertEquals(1, positions.drop(4).map { it.top }.distinct().size)
            assertTrue(positions[4].top > positions[0].top)
            for (sticker in StickerRepository.load(context, StickerPack.AME)) {
                assertNull(findVisibleNode { it.viewIdResourceName == "$PICKER_STICKER_TAG_PREFIX${sticker.id}" })
            }

            val spanishLove = boundsOf(awaitPickerSticker("love", "Que viva el amor"))
            val spanishLovePixels = capturePixels(scenario, spanishLove)
            scenario.onActivity { requestedLanguage.value = JineLanguageManager.ENGLISH }

            awaitNode("Are you there?")
            awaitNode("Minimize")
            instrumentation.uiAutomation.waitForIdle(250, 5_000)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                // Read the current semantics tree after the locale change, including off-focus cells.
                instrumentation.uiAutomation.clearCache()
            }
            awaitPickerSticker("ok", "Okay")
            val englishLove = boundsOf(awaitPickerSticker("love", "Love forever"))
            assertEquals(spanishLove, englishLove)
            assertFalse(
                "The love sticker should change visually with the language",
                spanishLovePixels.contentEquals(capturePixels(scenario, englishLove))
            )
            assertEquals(previewIds, visibleMessageIds())
            scenario.onActivity { assertTrue(sends.isEmpty()) }
        }
    }

    @Test
    fun compactChatAndStickerGridScrollIndependently() {
        val lastMessage = "Tengo muchas cosas que contarte hoy. Espero que podamos hablar un rato."
        val factory = controlledChatFactory(TestCoroutineScheduler())
        val sends = mutableListOf<String>()
        ActivityScenario.launch(ComponentActivity::class.java).use { scenario ->
            lateinit var chat: ChatViewModel
            scenario.onActivity { activity ->
                chat = ViewModelProvider(activity, factory)[ChatViewModel::class.java]
                activity.setStaticChatContent {
                    Box(Modifier.size(width = 320.dp, height = 260.dp)) {
                        ChatScreen(chat, onStickerSent = { sends.add(it.id) })
                    }
                }
            }

            awaitInReadingArea("preview_long_message", wholeRow = false)
            scrollPickerToLastRow()
            scenario.onActivity {
                assertEquals(previewIds, chat.messageIds())
                assertTrue(sends.isEmpty())
            }
            awaitInReadingArea("preview_long_message", wholeRow = false)
            repeat(6) {
                if (rowInReadingArea("preview_greeting") == null) {
                    swipeHistory(towardEnd = false, finishWithoutFling = true)
                }
            }
            awaitInReadingArea("preview_greeting")
            repeat(4) {
                val longRow = findNodeInTag("${CHAT_MESSAGE_TAG_PREFIX}preview_long_message") {
                    it.text?.toString() == lastMessage
                }
                if (longRow == null || rowInReadingArea("preview_long_message", wholeRow = false) == null) {
                    swipeHistory(towardEnd = true)
                }
            }
            awaitMessage(lastMessage, "preview_long_message")
            awaitInReadingArea("preview_long_message", wholeRow = false)
            val lastSticker = awaitPickerSticker("sorena", "Esto.")
            assertTrue(lastSticker.performAction(AccessibilityNodeInfo.ACTION_CLICK))
            awaitMessage("Esto.", "timed-1")
            awaitInReadingArea("timed-1", wholeRow = false)
            scenario.onActivity {
                assertEquals(previewIds + "timed-1", chat.messageIds())
                assertEquals(listOf("sorena"), sends)
            }
            assertEquals(panelBounds().top, scrollbarBounds().bottom)
        }
    }

    @Test
    fun eachPhysicalTapSendsOnceAndKeepsHistoryIdsAndOrderWhenLanguageChanges() {
        val language = mutableStateOf("es")
        val sends = mutableListOf<String>()
        val factory = controlledChatFactory(TestCoroutineScheduler())
        ActivityScenario.launch(ComponentActivity::class.java).use { scenario ->
            lateinit var chat: ChatViewModel
            scenario.onActivity { activity ->
                chat = ViewModelProvider(activity, factory)[ChatViewModel::class.java]
                activity.setStaticChatContent {
                    ChatScreen(chat, language = language.value, onStickerSent = { sends.add(it.id) })
                }
            }

            val ok = awaitPickerSticker("ok", "De acuerdo")
            assertTrue(ok.isEnabled)
            assertTrue(ok.isClickable)
            assertFalse(ok.isCheckable)
            assertTrue(ok.findNode { it.className == "android.widget.Button" } != null)
            tap(ok)
            awaitMessage("De acuerdo", "timed-1")
            val firstSend = chat.messageIds()
            assertEquals(4, firstSend.size)
            assertEquals(previewIds, firstSend.take(3))
            val historySticker = awaitMessage("De acuerdo", firstSend.last())
            awaitInReadingArea(firstSend.last())
            val row = checkNotNull(findVisibleNode { it.viewIdResourceName == "$CHAT_MESSAGE_TAG_PREFIX${firstSend.last()}" })
            assertEquals(boundsOf(row).right, boundsOf(historySticker).right)
            assertFalse(historySticker.isClickable)
            tap(historySticker)
            assertEquals(firstSend, chat.messageIds())

            repeat(2) { tap(ok) }
            awaitMessage("De acuerdo", "timed-3")
            scenario.onActivity {
                assertEquals(previewIds + (1..3).map { "timed-$it" }, chat.messageIds())
                assertEquals(listOf("ok", "ok", "ok"), sends)
            }

            tap(awaitPickerSticker("love", "Que viva el amor"))
            awaitMessage("Que viva el amor", "timed-4")
            val sentIds = chat.messageIds()
            assertEquals(previewIds + (1..4).map { "timed-$it" }, sentIds)
            assertEquals(listOf("ok", "ok", "ok", "love"), chat.uiState.value.messages.drop(3).map {
                (it.content as MessageContent.Sticker).stickerId
            })
            assertNull(findVisibleNode { it.text?.toString() == "Leído" })

            scenario.onActivity { language.value = "en" }
            awaitNode("Minimize")
            sentIds.drop(3).take(3).forEach { awaitMessage("Okay", it) }
            awaitMessage("Love forever", sentIds.last())
            assertEquals(sentIds, chat.messageIds())
            assertNull(findVisibleNode { it.text?.toString() == "Read" })
            scenario.onActivity {
                assertEquals(listOf("ok", "ok", "ok", "love"), sends)
            }
        }
    }

    @Test
    fun activeBatchesSurviveRecreationAndAcceptStickersDuringAReaction() {
        val scheduler = TestCoroutineScheduler()
        val factory = controlledChatFactory(scheduler)
        val language = mutableStateOf("es")
        val sends = mutableListOf<String>()
        fun showChat(activity: ComponentActivity): ChatViewModel {
            val chat = ViewModelProvider(activity, factory)[ChatViewModel::class.java]
            activity.setStaticChatContent {
                ChatScreen(chat, language = language.value, onStickerSent = { sends.add(it.id) })
            }
            return chat
        }

        ActivityScenario.launch(ComponentActivity::class.java).use { scenario ->
            lateinit var chat: ChatViewModel
            scenario.onActivity { chat = showChat(it) }
            tap(awaitPickerSticker("love", "Que viva el amor"))
            awaitMessage("Que viva el amor", "timed-1")
            assertEquals(CommunicationState.READING, chat.uiState.value.communicationState)
            assertTrue(awaitPickerSticker("gomen", "Lo siento").isEnabled)
            assertNull(findNodeInTag("${CHAT_MESSAGE_TAG_PREFIX}timed-1") { it.text?.toString() == "Leído" })

            advanceChat(scenario, scheduler, 800)
            awaitMessage("Leído", "timed-1")
            assertTrue(awaitPickerSticker("gomen", "Lo siento").isEnabled)
            val beforeRecreation = chat.uiState.value

            scenario.recreate()
            scenario.onActivity { assertSame(chat, showChat(it)) }
            assertTrue(awaitPickerSticker("gomen", "Lo siento").isEnabled)
            assertEquals(beforeRecreation, chat.uiState.value)
            assertEquals(previewIds + "timed-1", chat.messageIds())
            scenario.onActivity { assertEquals(listOf("love"), sends) }

            advanceChat(scenario, scheduler, 600)
            awaitMessage("<3 <3 <3", "timed-2")
            assertEquals(CommunicationState.RESPONDING, chat.uiState.value.communicationState)
            val activeSticker = awaitPickerSticker("gomen", "Lo siento")
            assertTrue(activeSticker.isEnabled)
            tap(activeSticker)
            awaitMessage("Lo siento", "timed-3")
            awaitInReadingArea("timed-3")
            assertEquals(previewIds + (1..3).map { "timed-$it" }, chat.messageIds())
            assertEquals(CommunicationState.RESPONDING, chat.uiState.value.communicationState)

            advanceChat(scenario, scheduler, 200)
            tap(awaitPickerSticker("ok", "De acuerdo"))
            awaitMessage("De acuerdo", "timed-4")
            val beforeLanguageChange = chat.uiState.value
            scenario.onActivity {
                assertEquals(listOf("love", "gomen", "ok"), sends)
                language.value = "en"
            }
            awaitNode("Minimize")
            awaitMessage("Love forever", "timed-1")
            awaitMessage("Read", "timed-1")
            awaitMessage("Sorry", "timed-3")
            awaitMessage("Okay", "timed-4")
            assertEquals(beforeLanguageChange, chat.uiState.value)

            advanceChat(scenario, scheduler, 300)
            awaitMessage("i have so much to tell you today", "timed-5")
            assertEquals(CommunicationState.READING, chat.uiState.value.communicationState)
            advanceChat(scenario, scheduler, 300)
            awaitMessage("Read", "timed-3")
            assertNull(findNodeInTag("${CHAT_MESSAGE_TAG_PREFIX}timed-4") { it.text?.toString() == "Read" })
            advanceChat(scenario, scheduler, 200)
            awaitMessage("Read", "timed-4")
            advanceChat(scenario, scheduler, 599)
            assertEquals(previewIds + (1..5).map { "timed-$it" }, chat.messageIds())
            advanceChat(scenario, scheduler, 1)
            awaitMessage("hahaha what are you trying to tell me?", "timed-6")
            awaitInReadingArea("timed-6")
            assertEquals(CommunicationState.AVAILABLE, chat.uiState.value.communicationState)
            assertEquals(previewIds + (1..6).map { "timed-$it" }, chat.messageIds())
            assertEquals(
                listOf("dialogue.demo.reply.love.01", "dialogue.demo.reply.love.02", "dialogue.demo.batch.mixed.01"),
                chat.uiState.value.messages.drop(3).mapNotNull { (it.content as? MessageContent.Text)?.textKey }
            )
        }
    }

    @Test
    fun longHistoryFollowsNewContentAndKeepsTheAnchorWhileReadingOlderMessages() {
        val scheduler = TestCoroutineScheduler()
        val factory = controlledChatFactory(scheduler)
        val language = mutableStateOf("es")
        val sends = mutableListOf<String>()
        fun showChat(activity: ComponentActivity): ChatViewModel {
            val chat = ViewModelProvider(activity, factory)[ChatViewModel::class.java]
            activity.setStaticChatContent { ChatScreen(chat, language.value) { sends.add(it.id) } }
            return chat
        }

        ActivityScenario.launch(ComponentActivity::class.java).use { scenario ->
            lateinit var chat: ChatViewModel
            scenario.onActivity {
                chat = showChat(it)
                repeat(5) { assertTrue(chat.sendSticker("love", setOf("love"))) }
                scheduler.advanceUntilIdle()
            }
            awaitInReadingArea(chat.messageIds().last())

            val initialPicker = awaitPickerSticker("love", "Que viva el amor")
            assertEquals(CommunicationState.AVAILABLE, chat.uiState.value.communicationState)
            assertTrue(initialPicker.isEnabled)
            tap(initialPicker)
            val love = chat.messageIds().last()
            awaitMessage("Que viva el amor", love)
            awaitInReadingArea(love)
            advanceChat(scenario, scheduler, 800)
            awaitMessage("Leído", love)
            awaitInReadingArea(love)

            advanceChat(scenario, scheduler, 600)
            val firstReply = chat.messageIds().last()
            awaitMessage("<3 <3 <3", firstReply)
            val end = awaitInReadingArea(firstReply)
            val activePicker = awaitPickerSticker("ok", "De acuerdo")
            assertTrue(activePicker.isEnabled)
            val panel = panelBounds()
            val firstCell = boundsOf(activePicker)
            val secondCell = boundsOf(awaitPickerSticker("saikouka", "¡Tía!"))
            assertTrue(firstCell.right < secondCell.left)
            tap((firstCell.right + secondCell.left) / 2f, firstCell.exactCenterY())
            swipe(panel.left + 2, panel.top + 16, panel.bottom - 16)
            assertEquals(end, awaitInReadingArea(firstReply))
            assertEquals(firstReply, chat.messageIds().last())
            scenario.onActivity { assertEquals(listOf("love"), sends) }

            tap(activePicker)
            val ok = chat.messageIds().last()
            awaitMessage("De acuerdo", ok)
            awaitInReadingArea(ok)
            assertNull(findNodeInTag("$CHAT_MESSAGE_TAG_PREFIX$ok") { it.text?.toString() == "Leído" })
            scenario.onActivity { assertEquals(listOf("love", "ok"), sends) }

            swipeHistory(towardEnd = false)
            awaitValue("last send out of the reading area") {
                Unit.takeIf { rowInReadingArea(ok) == null }
            }
            val (anchorId, anchorBounds) = firstReadableRow()
            val beforeRecreation = chat.uiState.value
            scenario.recreate()
            scenario.onActivity { assertSame(chat, showChat(it)) }
            assertEquals(beforeRecreation, chat.uiState.value)
            assertEquals(anchorBounds, awaitInReadingArea(anchorId))

            scenario.onActivity { language.value = "en" }
            awaitNode("Minimize")
            assertEquals(beforeRecreation, chat.uiState.value)
            val translatedAnchor = awaitInReadingArea(anchorId)
            advanceChat(scenario, scheduler, 500)
            val secondReply = chat.messageIds().last()
            assertEquals(MessageContent.Text("dialogue.demo.reply.love.02"), chat.uiState.value.messages.last().content)
            assertEquals(translatedAnchor, awaitInReadingArea(anchorId))
            assertNull(rowInReadingArea(secondReply))

            scrollHistoryToBoundary(AccessibilityNodeInfo.ACTION_SCROLL_FORWARD, secondReply)
            awaitMessage("i have so much to tell you today", secondReply)
            awaitInReadingArea(secondReply)
            advanceChat(scenario, scheduler, 300)
            awaitMessage("Read", ok)
            awaitInReadingArea(secondReply)
            advanceChat(scenario, scheduler, 600)
            val finalReply = chat.messageIds().last()
            awaitMessage("okaaaay", finalReply)
            awaitInReadingArea(finalReply)
            assertTrue(awaitPickerSticker("ok", "Okay").isEnabled)

            swipeHistory(towardEnd = false)
            awaitValue("final reply out of the reading area") {
                Unit.takeIf { rowInReadingArea(finalReply) == null }
            }
            val beforeSend = chat.messageIds()
            tap(awaitPickerSticker("gomen", "Sorry"))
            val newSend = chat.messageIds().last()
            awaitMessage("Sorry", newSend)
            awaitInReadingArea(newSend)
            assertEquals(beforeSend, chat.messageIds().dropLast(1))
            scenario.onActivity { assertEquals(listOf("love", "ok", "gomen"), sends) }
        }
    }

    @Test
    fun scrollbarTracksHistoryAtBothEndsAndInTheMiddle() {
        val stickers = StickerRepository.load(context)
        val base = initialMessages(0L)
        val history = List(36) { index -> base[index % base.size].copy(id = "bar-$index") }
        val displayedHistory = mutableStateOf(history)
        ActivityScenario.launch(ComponentActivity::class.java).use { scenario ->
            scenario.onActivity { activity ->
                activity.setStaticChatContent {
                    val catalogs = remember { StickerCatalogs(pchan = stickers) }
                    ChatContent(ChatUiState(displayedHistory.value), "es", catalogs)
                }
            }
            awaitInReadingArea(history.last().id)
            val track = scrollbarBounds()
            assertEquals(panelBounds().top, track.bottom)
            val end = thumbRows(scenario, track)
            assertEquals(track.height() - 1, end.last)
            assertTrue(end.first > 0)

            scrollHistoryToBoundary(AccessibilityNodeInfo.ACTION_SCROLL_BACKWARD, history.first().id)
            awaitInReadingArea(history.first().id)
            assertEquals(0, thumbRows(scenario, track).first)
            val panel = panelBounds()
            val overlayWithHistory = capturePixels(scenario, panel)
            val historyX = boundsOf(awaitTag(CHAT_HISTORY_TAG)).centerX()
            swipe(historyX, track.centerY() + track.height() / 8, track.centerY(), stepMillis = 40)
            awaitValue("thumb between the two ends") {
                val middle = thumbRows(scenario, track)
                middle.takeIf { it.first > 0 && it.last < track.height() - 1 }
            }

            scenario.onActivity { displayedHistory.value = emptyList() }
            awaitValue("empty history") {
                Unit.takeIf { findVisibleNode { it.viewIdResourceName?.startsWith(CHAT_MESSAGE_TAG_PREFIX) == true } == null }
            }
            assertEquals(panel, panelBounds())
            assertFalse(overlayWithHistory.contentEquals(capturePixels(scenario, panel)))
            assertEquals(0 until track.height(), thumbRows(scenario, track))
        }
    }

    @Test
    fun compactPanelUsesMeasuredHeightForPickerEmptyAndErrorStates() {
        val catalog = mutableStateOf<List<Sticker>?>(StickerRepository.load(context))
        val height = mutableStateOf(260.dp)
        val base = initialMessages(0L)
        val history = List(12) { index -> base[index % 2].copy(id = "compact-$index") }
        ActivityScenario.launch(ComponentActivity::class.java).use { scenario ->
            scenario.onActivity { activity ->
                activity.setStaticChatContent {
                    Box(Modifier.size(320.dp, height.value)) {
                        val catalogs = remember(catalog.value) { StickerCatalogs(pchan = catalog.value) }
                        ChatContent(ChatUiState(history), "es", catalogs)
                    }
                }
            }
            awaitInReadingArea(history.last().id)
            val compactPanel = panelBounds()
            assertEquals(compactPanel.top, scrollbarBounds().bottom)
            scrollPickerToLastRow()
            awaitPickerSticker("sorena", "Esto.")
            awaitInReadingArea(history.last().id)

            scenario.onActivity { catalog.value = emptyList() }
            awaitNode("No hay stickers disponibles.")
            awaitInReadingArea(history.last().id)
            assertTrue(panelBounds().height() < compactPanel.height())
            assertEquals(panelBounds().top, scrollbarBounds().bottom)

            scenario.onActivity { catalog.value = null }
            awaitNode("No se pudieron cargar los stickers.")
            awaitInReadingArea(history.last().id)
            assertEquals(panelBounds().top, scrollbarBounds().bottom)

            scenario.onActivity {
                catalog.value = StickerRepository.load(context)
                height.value = 650.dp
            }
            awaitPickerSticker("sorena", "Esto.")
            awaitInReadingArea(history.last().id)
            assertTrue(panelBounds().height() > compactPanel.height())
            assertEquals(panelBounds().top, scrollbarBounds().bottom)
        }
    }

    @Test
    fun malformedDebugDialogueLeavesFreeChatAvailableAndRetryDoesNotStartTheDemo() {
        val scheduler = TestCoroutineScheduler()
        val factory = controlledChatFactory(scheduler, ChatTimings())
        val attempts = AtomicInteger()
        ActivityScenario.launch(ComponentActivity::class.java).use { scenario ->
            lateinit var chat: ChatViewModel
            scenario.onActivity { activity ->
                chat = ViewModelProvider(activity, factory)[ChatViewModel::class.java]
                activity.setStaticChatContent {
                    JineApp(chat, loadDialogue = { appContext ->
                        if (attempts.incrementAndGet() == 1) {
                            DialogueRepository.parse("dialogues/tests/invalid.json", """{"schemaVersion":1,}""")
                        } else {
                            DialogueRepository.load(appContext, JineAssets.Dialogues.STREAM_DEMO)
                        }
                    })
                }
            }
            val retry = awaitNode("Debug · Error al cargar la demo. Reintentar")
            awaitScrollbarAtPanel(CHAT_PANEL_TAG)
            assertEquals(previewIds, chat.messageIds())
            tap(awaitPickerSticker("ok", "De acuerdo"))
            awaitMessage("De acuerdo", "timed-1")
            advanceChat(scenario, scheduler, 1_600)
            awaitMessage("okeeey", "timed-2")
            val freeState = chat.uiState.value
            tap(retry)
            val demo = awaitNode("Debug · Demo narrativa")
            assertEquals(2, attempts.get())
            assertEquals(freeState, chat.uiState.value)
            tap(demo)
            advanceChat(scenario, scheduler, 0)
            awaitHiddenPanel()
            awaitMessage("oye, p-chan!!", "timed-3")
        }
    }

    @Test
    fun ameDebugDemoShowsAllEightLocalizedStickersAndLeavesTheRealChatIntact() {
        val scheduler = TestCoroutineScheduler()
        val factory = controlledChatFactory(scheduler, ChatTimings())
        val effects = mutableListOf<SoundEffect>()
        val soundPlayer = object : SoundEffectPlayer {
            override fun play(effect: SoundEffect, volume: Float): Boolean = effects.add(effect)
            override fun stopAll() = Unit
            override fun release() = Unit
        }
        var musicStarts = 0
        val music = object : MusicPlayer {
            override fun prepare() = Unit
            override fun play() { musicStarts++ }
            override fun pause() = Unit
            override fun setVolume(volume: Float) = Unit
            override fun release() = Unit
        }
        val focus = TestAudioFocus()
        ActivityScenario.launch(ComponentActivity::class.java).use { scenario ->
            lateinit var chat: ChatViewModel
            scenario.onActivity { activity ->
                chat = ViewModelProvider(activity, factory)[ChatViewModel::class.java]
                val audioFactory = viewModelFactory {
                    initializer { AudioSessionViewModel(JineAudioManager(music, ChatSoundCoordinator(soundPlayer), focus)) }
                }
                val audio = ViewModelProvider(activity, audioFactory)[AudioSessionViewModel::class.java]
                val host = audio.bindHost(activity.lifecycle)
                activity.setStaticChatContent { JineApp(chat, audioHost = host) }
            }
            tap(awaitPickerSticker("ok", "De acuerdo"))
            awaitMessage("De acuerdo", "timed-1")
            tap(awaitNode("Debug · Stickers de Ame"))
            assertNull(findVisibleNode { it.text?.toString() == "Añadir sticker de Ame" })
            advanceChat(scenario, scheduler, 1_600)
            awaitMessage("okeeey", "timed-2")
            val realState = chat.uiState.value
            var effectsBeforeDemo = 0
            scenario.onActivity { effectsBeforeDemo = effects.size }

            tap(awaitNode("Debug · Stickers de Ame"))
            awaitNode("Que viva el amor")
            awaitNode("Leído")
            awaitHiddenPanel()
            val spanish = listOf(
                "Retrato de Ame", "Nerviosa", "Selfie con morritos", "Soy una bolita",
                "Qué poco mono", "Ignorando", "Derretida", "Durmiendo"
            )
            val english = listOf(
                "Ame portrait", "Nervous", "Selfie", "I'm orb", "So not cute!", "Ignore", "Melting", "Sleeping"
            )
            val demoIds = spanish.map { label ->
                tap(awaitNode("Añadir sticker de Ame"))
                awaitNode(label)
                visibleMessageIds().single { id ->
                    findNodeInTag("$CHAT_MESSAGE_TAG_PREFIX$id") { it.contentDescription?.toString() == label } != null
                }
            }
            assertEquals(8, demoIds.toSet().size)
            tap(awaitNode("English"))
            awaitMessage(english.last(), demoIds.last())
            val unseen = demoIds.zip(english).toMap().toMutableMap()
            for (page in 0..8) {
                instrumentation.uiAutomation.waitForIdle(250, 5_000)
                refreshAccessibility()
                unseen.entries.removeAll { (id, label) ->
                    findNodeInTag("$CHAT_MESSAGE_TAG_PREFIX$id") { it.contentDescription?.toString() == label } != null
                }
                if (unseen.isEmpty()) break
                if (!awaitTag(CHAT_HISTORY_TAG).performAction(AccessibilityNodeInfo.ACTION_SCROLL_BACKWARD)) break
            }
            assertTrue("Missing localized stickers: ${unseen.values}", unseen.isEmpty())
            assertEquals(realState, chat.uiState.value)
            tap(awaitNode("Volver al chat"))
            awaitPickerSticker("ok", "De acuerdo")
            awaitMessage("okeeey", "timed-2")
            assertEquals(realState, chat.uiState.value)
            assertTrue(visibleMessageIds().none { it in demoIds })
            awaitNode("Debug · Demo narrativa")
            scenario.onActivity {
                assertEquals(List(8) { SoundEffect.RECEIVE }, effects.drop(effectsBeforeDemo))
                assertEquals(1, musicStarts)
                assertEquals(1, focus.requests)
            }
        }
    }

    @Test
    fun realNarrativePreservesTheReadingAnchorAndSurvivesRecreationAtTheSecondChoice() {
        val scheduler = TestCoroutineScheduler()
        val factory = controlledChatFactory(scheduler, ChatTimings())
        val dialogue = DialogueRepository.load(context, JineAssets.Dialogues.STREAM_DEMO)
        val language = mutableStateOf("es")
        val stickerSends = mutableListOf<String>()
        fun showChat(activity: ComponentActivity): ChatViewModel {
            val chat = ViewModelProvider(activity, factory)[ChatViewModel::class.java]
            activity.setStaticChatContent { ChatScreen(chat, language = language.value, onStickerSent = { stickerSends.add(it.id) }) }
            return chat
        }

        ActivityScenario.launch(ComponentActivity::class.java).use { scenario ->
            lateinit var chat: ChatViewModel
            scenario.onActivity {
                chat = showChat(it)
                repeat(18) { assertTrue(chat.sendSticker("love", setOf("love"))) }
                scheduler.advanceUntilIdle()
            }
            val freeHistory = chat.uiState.value.messages
            awaitPickerSticker("love", "Que viva el amor")
            awaitScrollbarAtPanel(CHAT_PANEL_TAG)
            awaitInReadingArea(freeHistory.last().id)
            swipeHistory(towardEnd = false, finishWithoutFling = true)
            awaitValue("older rows before starting the demo") {
                Unit.takeIf { rowInReadingArea(freeHistory.last().id) == null }
            }
            val readingTop = scrollbarBounds().top
            val (anchor, anchorTop) = visibleMessageIds().firstNotNullOf { id ->
                // A partially clipped accessibility node can report a different height after re-layout.
                rowInReadingArea(id)?.takeIf { it.top > readingTop }?.let { id to it.top }
            }

            scenario.onActivity {
                assertTrue(chat.requestDialogue(dialogue))
                scheduler.runCurrent()
            }
            awaitHiddenPanel()
            assertEquals(anchorTop, awaitInReadingArea(anchor).top)
            assertEquals(listOf("intro.01"), chat.narrativeKeys())
            advanceChat(scenario, scheduler, 1_400)
            val first = DialogueChoice("gaming", "dialogue.demo.narrative.stream.choice.gaming")
            val firstButton = awaitChoice(first, "¡De videojuegos!")
            awaitScrollbarAtPanel(CHAT_CHOICE_PANEL_TAG)
            assertEquals(anchorTop, awaitInReadingArea(anchor).top)
            assertEquals(listOf("intro.01", "intro.02", "question.01"), chat.narrativeKeys())
            assertNull(rowInReadingArea(chat.messageIds().last()))

            tap(firstButton)
            awaitHiddenPanel()
            val firstReply = chat.uiState.value.messages.last()
            assertEquals(MessageSender.PCHAN, firstReply.sender)
            assertEquals(MessageContent.Text(first.textKey), firstReply.content)
            awaitMessage("¡De videojuegos!", firstReply.id)
            awaitInReadingArea(firstReply.id)
            assertNull(rowInReadingArea(anchor))
            assertNull(findNodeInTag("$CHAT_MESSAGE_TAG_PREFIX${firstReply.id}") { it.text?.toString() == "Leído" })
            advanceChat(scenario, scheduler, 650)
            awaitMessage("Leído", firstReply.id)
            advanceChat(scenario, scheduler, 1_650)
            val later = DialogueChoice("later", "dialogue.demo.narrative.stream.choice.later")
            awaitChoice(later, "Podemos verlo luego")
            val beforeRecreation = chat.uiState.value
            val promptId = (beforeRecreation.inputMode as ChatInputMode.Choices).promptId
            assertEquals(listOf("intro.01", "intro.02", "question.01", "choice.gaming", "gaming.01", "question.02"), chat.narrativeKeys())
            scenario.moveToState(Lifecycle.State.CREATED)
            advanceChat(scenario, scheduler, 30_000)
            assertEquals(beforeRecreation, chat.uiState.value)
            scenario.moveToState(Lifecycle.State.RESUMED)
            scenario.recreate()
            scenario.onActivity {
                assertSame(chat, showChat(it))
                language.value = "en"
            }
            val laterButton = awaitChoice(later, "We can plan it later")
            awaitScrollbarAtPanel(CHAT_CHOICE_PANEL_TAG)
            awaitMessage("A gaming stream!", firstReply.id)
            awaitMessage("Read", firstReply.id)
            assertEquals(beforeRecreation, chat.uiState.value)
            assertEquals(promptId, (chat.uiState.value.inputMode as ChatInputMode.Choices).promptId)
            assertTrue(laterButton.performAction(AccessibilityNodeInfo.ACTION_CLICK))
            awaitHiddenPanel()
            val secondReply = chat.uiState.value.messages.last()
            awaitMessage("We can plan it later", secondReply.id)
            advanceChat(scenario, scheduler, 650)
            awaitMessage("Read", secondReply.id)
            advanceChat(scenario, scheduler, 1_650)
            val outro = chat.uiState.value.messages.last()
            awaitMessage("anyway, i'll tell you how it goes!", outro.id)
            awaitInReadingArea(outro.id)
            awaitScrollbarAtPanel(CHAT_PANEL_TAG)
            val ok = awaitPickerSticker("ok", "Okay")
            tap(ok)
            tap(ok)
            val outgoing = chat.uiState.value.messages.takeLast(2)
            assertTrue(outgoing.all { it.content == MessageContent.Sticker(PCHAN_PACK_ID, "ok") })
            assertEquals(2, outgoing.map { it.id }.distinct().size)
            advanceChat(scenario, scheduler, 1_600)
            awaitMessage("hahaha i get it!!", chat.messageIds().last())
            val sentIds = outgoing.map { it.id }.toSet()
            assertEquals(sentIds, chat.uiState.value.messages.filter { it.id in sentIds && it.isRead }.map { it.id }.toSet())
            assertEquals(freeHistory, chat.uiState.value.messages.take(freeHistory.size))
            assertEquals(
                listOf("intro.01", "intro.02", "question.01", "choice.gaming", "gaming.01", "question.02",
                    "choice.later", "later.01", "outro.01"),
                chat.narrativeKeys()
            )
            scenario.onActivity { assertEquals(listOf("ok", "ok"), stickerSends) }
        }
    }

    @Test
    fun panelModesKeepTheLatestRowVisibleAndDeliverOnlyExplicitChoices() {
        val history = narrativeHistory()
        val state = mutableStateOf(ChatUiState(history))
        val language = mutableStateOf("es")
        val stickers = StickerRepository.load(context)
        val events = mutableListOf<Pair<String, String>>()
        val options = listOf(gaming, talking)
        ActivityScenario.launch(ComponentActivity::class.java).use { scenario ->
            scenario.onActivity { activity ->
                activity.setStaticChatContent {
                    ChatContent(
                        uiState = state.value,
                        language = language.value,
                        catalogs = remember { StickerCatalogs(pchan = stickers) },
                        onChoicePressed = { promptId, choiceId ->
                            events.add(promptId to choiceId)
                            val choice = options.single { it.id == choiceId }
                            state.value = state.value.copy(
                                messages = state.value.messages + sentChoice("sent-$promptId", choice),
                                inputMode = ChatInputMode.Hidden
                            )
                        }
                    )
                }
            }
            awaitPickerSticker("love", "Que viva el amor")
            val stickerPanel = awaitScrollbarAtPanel(CHAT_PANEL_TAG)
            awaitInReadingArea(history.last().id)
            scenario.onActivity { state.value = state.value.copy(inputMode = ChatInputMode.Hidden) }
            awaitHiddenPanel()
            awaitInReadingArea(history.last().id)

            scenario.onActivity { state.value = state.value.copy(inputMode = ChatInputMode.Choices("first", options)) }
            val first = awaitChoice(gaming, gamingEs)
            val nextBounds = boundsOf(awaitChoice(talking, talkingEs))
            assertTrue(first.isClickable)
            assertFalse(first.isCheckable)
            assertTrue(first.findNode { it.className == "android.widget.Button" } != null)
            val choicePanel = awaitScrollbarAtPanel(CHAT_CHOICE_PANEL_TAG)
            assertEquals(stickerPanel, choicePanel)
            assertNull(findVisibleNode { it.viewIdResourceName == CHAT_PANEL_TAG })
            awaitInReadingArea(history.last().id)
            val firstBounds = boundsOf(first)
            assertTrue(JineSentBubble.toArgb() in capturePixels(scenario, firstBounds))
            tap(choicePanel.left + 8f, firstBounds.exactCenterY())
            assertTrue(firstBounds.bottom < nextBounds.top)
            tap(nextBounds.exactCenterX(), (firstBounds.bottom + nextBounds.top) / 2f)
            val down = SystemClock.uptimeMillis()
            sendPointer(down, MotionEvent.ACTION_DOWN, firstBounds.exactCenterX(), firstBounds.exactCenterY())
            sendPointer(down, MotionEvent.ACTION_CANCEL, firstBounds.exactCenterX(), firstBounds.exactCenterY())
            scenario.onActivity { assertTrue(events.isEmpty()) }
            awaitInReadingArea(history.last().id)
            tap(first)
            awaitHiddenPanel()
            awaitMessage(gamingEs, "sent-first")
            awaitInReadingArea("sent-first")
            scenario.onActivity {
                assertEquals(listOf("first" to "gaming"), events)
                assertEquals(history + sentChoice("sent-first", gaming), state.value.messages)
                state.value = state.value.copy(inputMode = ChatInputMode.Choices("second", options))
            }
            awaitChoice(talking, talkingEs)
            awaitScrollbarAtPanel(CHAT_CHOICE_PANEL_TAG)
            val beforeLocaleChange = state.value
            scenario.onActivity { language.value = "en" }
            val second = awaitChoice(talking, "A chatting stream!")
            awaitMessage("A gaming stream!", "sent-first")
            assertEquals(beforeLocaleChange, state.value)
            scenario.onActivity { assertEquals(listOf("first" to "gaming"), events) }
            assertTrue(second.performAction(AccessibilityNodeInfo.ACTION_CLICK))
            awaitHiddenPanel()
            awaitMessage("A chatting stream!", "sent-second")
            scenario.onActivity {
                assertEquals(listOf("first" to "gaming", "second" to "talking"), events)
                state.value = state.value.copy(inputMode = ChatInputMode.Stickers)
            }
            awaitPickerSticker("love", "Love forever")
            assertEquals(stickerPanel, awaitScrollbarAtPanel(CHAT_PANEL_TAG))
            assertNull(findVisibleNode { it.viewIdResourceName == CHAT_CHOICE_PANEL_TAG })
            awaitInReadingArea("sent-second")
            assertEquals(history + sentChoice("sent-first", gaming) + sentChoice("sent-second", talking), state.value.messages)
        }
    }

    @Test
    fun hiddenPanelReleasesGesturesAndModeChangesPreserveTheAnchorUntilPchanSends() {
        val history = narrativeHistory()
        val state = mutableStateOf(ChatUiState(history, inputMode = ChatInputMode.Choices("anchor", listOf(gaming))))
        val stickers = StickerRepository.load(context)
        ActivityScenario.launch(ComponentActivity::class.java).use { scenario ->
            scenario.onActivity { activity ->
                activity.setStaticChatContent {
                    ChatContent(state.value, "es", remember { StickerCatalogs(pchan = stickers) })
                }
            }
            awaitInReadingArea(history.last().id)
            val panel = awaitScrollbarAtPanel(CHAT_CHOICE_PANEL_TAG)
            scenario.onActivity { state.value = state.value.copy(inputMode = ChatInputMode.Hidden) }
            awaitHiddenPanel()
            awaitInReadingArea(history.last().id)
            swipe(panel.centerX(), panel.top + panel.height() / 8, panel.bottom - panel.height() / 8)
            awaitValue("earlier history after swiping the former panel") {
                Unit.takeIf { rowInReadingArea(history.last().id) == null }
            }
            val (anchor, position) = firstReadableRow()

            scenario.onActivity { state.value = state.value.copy(inputMode = ChatInputMode.Stickers) }
            awaitScrollbarAtPanel(CHAT_PANEL_TAG)
            assertEquals(position, awaitInReadingArea(anchor))
            val ame = Message("new-ame", MessageSender.AME, MessageContent.Text("dialogue.preview.narrative.reply"), 1L)
            scenario.onActivity {
                state.value = state.value.copy(
                    messages = state.value.messages + ame,
                    inputMode = ChatInputMode.Choices("after-ame", listOf(gaming))
                )
            }
            awaitChoice(gaming, gamingEs)
            awaitScrollbarAtPanel(CHAT_CHOICE_PANEL_TAG)
            assertEquals(position, awaitInReadingArea(anchor))
            assertNull(rowInReadingArea(ame.id))

            scenario.onActivity {
                state.value = state.value.copy(
                    messages = state.value.messages + sentChoice("from-older-history", gaming),
                    inputMode = ChatInputMode.Hidden
                )
            }
            awaitHiddenPanel()
            awaitMessage(gamingEs, "from-older-history")
            awaitInReadingArea("from-older-history")
            assertNull(rowInReadingArea(anchor))
        }
    }

    @Test
    fun compactChoicesScrollIndependentlyAndResetForAnEqualHeightPrompt() {
        val shortOptions = List(3) { index -> talking.copy(id = "short-$index") }
        val options = listOf(gaming) + shortOptions + longChoice
        val history = narrativeHistory()
        val state = mutableStateOf(ChatUiState(history, inputMode = ChatInputMode.Choices("compact", options)))
        val catalog = mutableStateOf<List<Sticker>?>(null)
        val height = mutableStateOf(260.dp)
        val events = mutableListOf<Pair<String, String>>()
        fun showChat(activity: ComponentActivity) {
            activity.setStaticChatContent {
                Box(Modifier.size(320.dp, height.value)) {
                    ChatContent(
                        state.value, "es", remember(catalog.value) { StickerCatalogs(pchan = catalog.value) },
                        onChoicePressed = { prompt, choice -> events.add(prompt to choice) }
                    )
                }
            }
        }
        ActivityScenario.launch(ComponentActivity::class.java).use { scenario ->
            scenario.onActivity(::showChat)
            awaitChoice(gaming, gamingEs)
            val compact = awaitScrollbarAtPanel(CHAT_CHOICE_PANEL_TAG)
            awaitHistoryEnd(history.last().id)
            val bar = scrollbarBounds()
            val readingArea = Rect(boundsOf(awaitTag(CHAT_HISTORY_TAG)).left, bar.top, bar.left, bar.bottom)
            val historyPixels = capturePixels(scenario, readingArea)
            assertNull(findVisibleNode { it.text?.toString() == "No se pudieron cargar los stickers." })
            val lastTag = "$CHAT_CHOICE_TAG_PREFIX${longChoice.id}"
            assertNull(findVisibleNode { it.viewIdResourceName == lastTag })
            val inset = compact.height() / 12
            val beforeSwipe = capturePixels(scenario, compact)
            swipe(compact.centerX(), compact.bottom - inset, compact.top + inset)
            assertFalse("A real gesture must scroll the choices", beforeSwipe.contentEquals(capturePixels(scenario, compact)))
            repeat(18) {
                if (findChoice(longChoice, longChoiceEs) == null) {
                    swipe(compact.centerX(), compact.bottom - inset, compact.top + inset)
                }
            }
            assertTrue(
                "Expected the long choice within the scrolled panel. Visible options: " +
                    options.mapNotNull { option ->
                        findVisibleNode { it.viewIdResourceName == "$CHAT_CHOICE_TAG_PREFIX${option.id}" }
                            ?.let { "${option.id} ${boundsOf(it)}" }
                    },
                findChoice(longChoice, longChoiceEs) != null
            )
            awaitChoice(longChoice, longChoiceEs)
            assertTrue(
                "Scrolling the choice list must not move the history above it",
                historyPixels.contentEquals(capturePixels(scenario, readingArea))
            )
            scenario.onActivity {
                assertTrue(events.isEmpty())
                catalog.value = emptyList()
                state.value = state.value.copy(inputMode = ChatInputMode.Choices("next-compact", options))
            }
            awaitChoice(gaming, gamingEs)
            assertEquals(compact, awaitScrollbarAtPanel(CHAT_CHOICE_PANEL_TAG))
            assertNull(findVisibleNode { it.viewIdResourceName == lastTag })
            assertNull(findVisibleNode { it.text?.toString() == "No hay stickers disponibles." })

            scenario.onActivity { height.value = 650.dp }
            awaitValue("larger choice panel after resizing") {
                awaitScrollbarAtPanel(CHAT_CHOICE_PANEL_TAG).takeIf { it.height() > compact.height() }
            }
            awaitHistoryEnd(history.last().id)
            scenario.recreate()
            scenario.onActivity(::showChat)
            awaitChoice(gaming, gamingEs)
            awaitScrollbarAtPanel(CHAT_CHOICE_PANEL_TAG)
            awaitHistoryEnd(history.last().id)
            scenario.onActivity { assertTrue(events.isEmpty()) }
        }
    }

    @Test
    fun receivedStickerIsExactlyTheLocalizedSpriteWithAnInsetAndNoDecoration() {
        val language = mutableStateOf("es")
        val catalogs = StickerCatalogs(pchan = null, ame = StickerRepository.load(context, StickerPack.AME))
        val received = Message("received", MessageSender.AME, MessageContent.Sticker(AME_PACK_ID, "kawaikunai"), 1L, 2L)
        val background = JineChatBackground.toArgb()
        ActivityScenario.launch(ComponentActivity::class.java).use { scenario ->
            scenario.onActivity { activity ->
                activity.setStaticChatContent {
                    val localization = rememberLocalization(language.value)
                    ProvideLocalizedResources(localization.language) {
                        Column(Modifier.fillMaxWidth().background(JineChatBackground)) {
                            ChatMessageItem(received, localization, catalogs)
                            PixelImage(
                                path = "${StickerPack.AME.textureDirectory}/${localization.language}/kawaikunai.png",
                                modifier = Modifier.size(72.dp),
                                contentDescription = "Reference received sprite"
                            )
                        }
                    }
                }
            }

            fun assertSticker(label: String, receipt: String): IntArray {
                val tag = "$CHAT_MESSAGE_TAG_PREFIX${received.id}"
                val image = awaitMessage(label, received.id)
                val bounds = boundsOf(image)
                val row = boundsOf(awaitTag(tag))
                val density = context.resources.displayMetrics.density
                assertEquals((40 * density).roundToInt(), bounds.left - row.left)
                assertEquals((72 * density).roundToInt(), bounds.width())
                assertEquals(bounds.height(), row.height())
                assertTrue(bounds.centerX() < row.centerX())
                assertFalse(image.isClickable)
                assertNull(findNodeInTag(tag) { it.text?.toString() in setOf(label, receipt) })

                val pixels = capturePixels(scenario, bounds)
                val reference = capturePixels(scenario, boundsOf(awaitNode("Reference received sprite")))
                assertTrue("Expected the exact localized texture", pixels.contentEquals(reference))
                val rowPixels = capturePixels(scenario, row)
                assertTrue("Expected only the chat background outside the sprite", rowPixels.indices.all { index ->
                    val x = row.left + index % row.width()
                    x in bounds.left until bounds.right || rowPixels[index] == background
                })
                return pixels
            }

            val spanish = assertSticker("Qué poco mono", "Leído")
            scenario.onActivity { language.value = "en" }
            val english = assertSticker("So not cute!", "Read")
            assertFalse(spanish.contentEquals(english))
            assertEquals(listOf(received.id), visibleMessageIds())
        }
    }

    @Test
    fun catalogFailuresAreIsolatedAndThePickerOnlyShowsPchanStickers() {
        fun catalogsWithout(missing: StickerPack) = loadStickerCatalogs { pack ->
            val path = if (pack == missing) "stickers/missing_${pack.id}.json" else pack.catalogPath
            StickerRepository.load(context, pack, path)
        }

        val withoutAme = catalogsWithout(StickerPack.AME)
        val withoutPchan = catalogsWithout(StickerPack.PCHAN)
        assertNull(withoutAme.ame)
        assertNull(withoutPchan.pchan)
        val factory = controlledChatFactory(TestCoroutineScheduler())
        val selected = mutableListOf<String>()
        ActivityScenario.launch(ComponentActivity::class.java).use { scenario ->
            lateinit var chat: ChatViewModel
            scenario.onActivity { activity ->
                chat = ViewModelProvider(activity, factory)[ChatViewModel::class.java]
                activity.setStaticChatContent {
                    ChatScreen(chat, catalogs = withoutAme, onStickerSent = { selected.add(it.id) })
                }
            }
            awaitPickerSticker("love", "Que viva el amor")
            tap(awaitPickerSticker("ok", "De acuerdo"))
            awaitMessage("De acuerdo", "timed-1")
            scenario.onActivity { assertEquals(listOf("ok"), selected) }

            val ame = Message("ame-catalog-only", MessageSender.AME, MessageContent.Sticker(AME_PACK_ID, "amechan"), 1L)
            val pchan = Message("missing-pchan", MessageSender.PCHAN, MessageContent.Sticker(PCHAN_PACK_ID, "love"), 0L)
            val catalogs = mutableStateOf(withoutPchan)
            scenario.onActivity { activity ->
                activity.setStaticChatContent {
                    ChatContent(ChatUiState(listOf(pchan, ame)), "es", catalogs.value)
                }
            }
            awaitNode("No se pudieron cargar los stickers.")
            awaitMessage("Retrato de Ame", ame.id)
            awaitMessage("Mensaje no disponible", pchan.id)
            scenario.onActivity { catalogs.value = StickerCatalogs(withoutAme.pchan, withoutPchan.ame) }
            awaitPickerSticker("ok", "De acuerdo")
            awaitInReadingArea(ame.id)
            awaitMessage("Retrato de Ame", ame.id)
            awaitMessage("Que viva el amor", pchan.id)
            scenario.onActivity { catalogs.value = withoutAme }
            awaitPickerSticker("ok", "De acuerdo")
            awaitMessage("Mensaje no disponible", ame.id)
        }
    }

    @Test
    fun readReceiptBelongsToOneMessageAndSentImageMatchesTheLocalizedAsset() {
        val language = mutableStateOf("es")
        val catalogs = StickerCatalogs(pchan = StickerRepository.load(context))
        val sticker = MessageContent.Sticker(PCHAN_PACK_ID, "love")
        val unread = Message("unread", MessageSender.PCHAN, sticker, sentAt = 1L)
        val read = Message("read", MessageSender.PCHAN, sticker, sentAt = 2L, readAt = 3L)
        val unavailable = listOf(
            unread.copy(id = "wrong_pack", content = sticker.copy(packId = "ame")),
            unread.copy(id = "unknown_sticker", content = sticker.copy(stickerId = "unknown")),
            unread.copy(id = "ame_sticker", sender = MessageSender.AME)
        )
        val short = Message(
            "pchan_short",
            MessageSender.PCHAN,
            MessageContent.Text("dialogue.preview.narrative.reply"),
            sentAt = 1L,
            readAt = 2L
        )
        val long = sentChoice("pchan_long", longChoice)
        ActivityScenario.launch(ComponentActivity::class.java).use { scenario ->
            scenario.onActivity { activity ->
                activity.setStaticChatContent {
                    val localization = rememberLocalization(language.value)
                    ProvideLocalizedResources(localization.language) {
                        Column(Modifier.fillMaxWidth().background(JineChatBackground)) {
                            ChatMessageItem(unread, localization, catalogs)
                            ChatMessageItem(read, localization, catalogs)
                            for (message in unavailable) {
                                ChatMessageItem(message, localization, catalogs)
                            }
                            ChatMessageItem(short, localization, catalogs)
                            ChatMessageItem(long, localization, catalogs)
                            PixelImage(
                                path = "${JineAssets.Stickers.PCHAN_DIRECTORY}/${localization.language}/love.png",
                                modifier = Modifier.size(72.dp),
                                contentDescription = "Reference sprite"
                            )
                        }
                    }
                }
            }

            unavailable.forEach { awaitMessage("Mensaje no disponible", it.id) }
            val spanish = checkReadSticker(scenario, "Que viva el amor", "Leído", read.id, unread.id)
            val shortText = boundsOf(awaitMessage("¡Buena idea!", short.id))
            val longText = boundsOf(awaitMessage(longChoiceEs, long.id))
            val shortRow = boundsOf(awaitTag("$CHAT_MESSAGE_TAG_PREFIX${short.id}"))
            val receipt = boundsOf(awaitMessage("Leído", short.id))
            assertTrue(shortText.left > shortRow.centerX())
            assertTrue(longText.height() > shortText.height())
            assertTrue(receipt.top >= shortText.bottom)
            assertTrue(receipt.left <= shortText.left)
            assertNull(findNodeInTag("$CHAT_MESSAGE_TAG_PREFIX${long.id}") { it.text?.toString() == "Leído" })
            for (message in listOf(short, long)) {
                assertNull(findNodeInTag("$CHAT_MESSAGE_TAG_PREFIX${message.id}") { it.contentDescription != null })
            }
            val rowPixels = capturePixels(scenario, shortRow)
            val green = JineSentBubble.toArgb()
            val bubbleLeft = rowPixels.indices.filter { rowPixels[it] == green }.minOf { it % shortRow.width() }
            assertTrue(kotlin.math.abs(shortRow.left + bubbleLeft - receipt.left) <= 1)
            // Green beyond the bubble body in the reserved right-hand strip is the reflected tail.
            val tailWidth = (context.resources.displayMetrics.density * 5).toInt()
            assertTrue(rowPixels.indices.any { rowPixels[it] == green && it % shortRow.width() >= shortRow.width() - tailWidth })
            assertFalse(rowPixels.indices.any { rowPixels[it] == green && it % shortRow.width() < shortRow.width() / 3 })
            scenario.onActivity { language.value = "en" }
            unavailable.forEach { awaitMessage("Message unavailable", it.id) }
            awaitMessage("Good idea!", short.id)
            awaitMessage("Read", short.id)
            awaitMessage("We could try a cozy game, chat with the audience and put together a list of ideas for the next stream.", long.id)
            assertNull(findNodeInTag("$CHAT_MESSAGE_TAG_PREFIX${long.id}") { it.text?.toString() == "Read" })
            val english = checkReadSticker(scenario, "Love forever", "Read", read.id, unread.id)
            assertFalse(spanish.contentEquals(english))
        }
    }

    @Test
    fun heldStickerBrightensAndReturnsToIdleAfterCancelOrRelease() {
        val stickers = StickerRepository.load(context).take(1)
        val sends = mutableListOf<Sticker>()
        ActivityScenario.launch(ComponentActivity::class.java).use { scenario ->
            scenario.onActivity { activity ->
                activity.setStaticChatContent {
                    Box(Modifier.safeDrawingPadding().background(JineChatBackground)) {
                        StickerPicker(
                            stickers = stickers,
                            onStickerPressed = sends::add,
                            descriptionFor = { "De acuerdo" }
                        )
                    }
                }
            }
            val bounds = boundsOf(awaitPickerSticker("ok", "De acuerdo"))
            val idle = capturePixels(scenario, bounds)
            val x = bounds.exactCenterX()
            val y = bounds.exactCenterY()
            for (end in listOf(MotionEvent.ACTION_CANCEL, MotionEvent.ACTION_UP)) {
                val down = SystemClock.uptimeMillis()
                sendPointer(down, MotionEvent.ACTION_DOWN, x, y)
                try {
                    awaitValue("pressed sticker pixels") {
                        capturePixels(scenario, bounds).takeUnless { it.contentEquals(idle) }
                    }
                    scenario.onActivity { assertTrue(sends.isEmpty()) }
                } finally {
                    sendPointer(down, end, x, y)
                }
                awaitValue("idle sticker pixels") {
                    capturePixels(scenario, bounds).takeIf { it.contentEquals(idle) }
                }
                scenario.onActivity {
                    assertEquals(if (end == MotionEvent.ACTION_UP) stickers else emptyList<Sticker>(), sends)
                }
            }
        }
    }

    @Test
    fun disabledPickerDoesNotEmitAnEvent() {
        val stickers = StickerRepository.load(context).take(1)
        val sends = mutableListOf<Sticker>()
        ActivityScenario.launch(ComponentActivity::class.java).use { scenario ->
            scenario.onActivity { activity ->
                activity.setStaticChatContent {
                    Box(Modifier.safeDrawingPadding()) {
                        ProvideLocalizedResources("en") {
                            StickerPicker(
                                stickers = stickers,
                                onStickerPressed = sends::add,
                                descriptionFor = { "Okay" },
                                enabled = false,
                                language = JineLanguageManager.ENGLISH
                            )
                        }
                    }
                }
            }
            val sticker = awaitPickerSticker("ok", "Okay")
            assertFalse(sticker.isEnabled)
            tap(sticker)
            assertFalse(sticker.performAction(AccessibilityNodeInfo.ACTION_CLICK))
            scenario.onActivity { assertTrue(sends.isEmpty()) }
        }
    }

    @Test
    fun emptyPickerShowsLocalizedStatus() {
        val sends = mutableListOf<Sticker>()
        ActivityScenario.launch(ComponentActivity::class.java).use { scenario ->
            scenario.onActivity { activity ->
                activity.setStaticChatContent {
                    Box(Modifier.safeDrawingPadding()) {
                        ProvideLocalizedResources("en") {
                            StickerPicker(emptyList(), sends::add, { it.descriptionKey })
                        }
                    }
                }
            }
            val status = awaitNode("No stickers available.")
            assertFalse(status.isClickable)
            tap(status)
            scenario.onActivity { assertTrue(sends.isEmpty()) }
        }
    }

    private fun ComponentActivity.setStaticChatContent(content: @Composable () -> Unit) {
        setContent {
            CompositionLocalProvider(LocalChatAnimationSpec provides ChatAnimationSpec.Static, content = content)
        }
    }

    private fun controlledChatFactory(
        scheduler: TestCoroutineScheduler,
        timings: ChatTimings = ChatTimings(readMs = 800, replyPreparationMs = 600, replyIntervalMs = 500)
    ): ViewModelProvider.Factory {
        var nextId = 0
        return viewModelFactory {
            initializer {
                ChatViewModel(
                    timings = timings,
                    now = { 1_700_000_000_000L + scheduler.currentTime },
                    newMessageId = { "timed-${++nextId}" },
                    dispatcher = StandardTestDispatcher(scheduler)
                )
            }
        }
    }

    private fun advanceChat(scenario: ActivityScenario<ComponentActivity>, scheduler: TestCoroutineScheduler, ms: Long) {
        scenario.onActivity {
            scheduler.advanceTimeBy(ms)
            scheduler.runCurrent()
        }
    }

    private fun checkReadSticker(
        scenario: ActivityScenario<ComponentActivity>,
        description: String,
        receipt: String,
        readId: String,
        unreadId: String
    ): IntArray {
        awaitMessage(description, unreadId)
        val sticker = boundsOf(awaitMessage(description, readId))
        val readLabel = boundsOf(awaitMessage(receipt, readId))
        assertNull(findNodeInTag("$CHAT_MESSAGE_TAG_PREFIX$unreadId") { it.text?.toString() == receipt })
        assertEquals(sticker.left, readLabel.left)
        assertTrue(readLabel.top >= sticker.bottom)

        val pixels = capturePixels(scenario, sticker)
        val expectedPixels = capturePixels(scenario, boundsOf(awaitNode("Reference sprite")))
        assertTrue("The sent sticker should display the exact localized PNG", pixels.contentEquals(expectedPixels))
        return pixels
    }

    private fun awaitPickerSticker(id: String, label: String): AccessibilityNodeInfo = awaitValue("sticker '$id' with label '$label'") {
        findVisibleNode { it.viewIdResourceName == "$PICKER_STICKER_TAG_PREFIX$id" }?.takeIf { node ->
            node.findNode { it.contentDescription?.toString() == label } != null
        }
    }

    private fun awaitMessage(label: String, messageId: String): AccessibilityNodeInfo {
        return awaitNode(label, withinTag = "$CHAT_MESSAGE_TAG_PREFIX$messageId")
    }

    private fun awaitChoice(choice: DialogueChoice, label: String): AccessibilityNodeInfo =
        awaitValue("choice '${choice.id}' with label '$label'") { findChoice(choice, label) }

    private fun findChoice(choice: DialogueChoice, label: String): AccessibilityNodeInfo? =
        findVisibleNode { it.viewIdResourceName == "$CHAT_CHOICE_TAG_PREFIX${choice.id}" }?.takeIf { node ->
            node.findNode { it.text?.toString() == label } != null
        }

    private fun narrativeHistory(): List<Message> {
        val base = initialMessages(0L)
        return List(36) { index -> base[index % base.size].copy(id = "narrative-$index") } +
            Message(
                "narrative-question",
                MessageSender.AME,
                MessageContent.Text("dialogue.preview.narrative.question"),
                sentAt = 1L
            )
    }

    private fun sentChoice(id: String, choice: DialogueChoice): Message =
        Message(id, MessageSender.PCHAN, MessageContent.Text(choice.textKey), sentAt = 2L)

    private fun awaitScrollbarAtPanel(tag: String): Rect = awaitValue("scrollbar ending at $tag") {
        val panel = findVisibleNode { it.viewIdResourceName == tag }?.let(::boundsOf) ?: return@awaitValue null
        val bar = findVisibleNode { it.viewIdResourceName == CHAT_SCROLLBAR_TAG }?.let(::boundsOf)
        panel.takeIf { it.top == bar?.bottom }
    }

    private fun awaitHiddenPanel() {
        awaitValue("full reading area without a panel") {
            val panel = findVisibleNode {
                it.viewIdResourceName == CHAT_PANEL_TAG || it.viewIdResourceName == CHAT_CHOICE_PANEL_TAG
            }
            val history = findVisibleNode { it.viewIdResourceName == CHAT_HISTORY_TAG }?.let(::boundsOf)
            val bar = findVisibleNode { it.viewIdResourceName == CHAT_SCROLLBAR_TAG }?.let(::boundsOf)
            Unit.takeIf { panel == null && history != null && bar?.bottom == history.bottom }
        }
    }

    private fun ChatViewModel.messageIds() = uiState.value.messages.map { it.id }

    private fun ChatViewModel.narrativeKeys(): List<String> {
        val prefix = "dialogue.demo.narrative.stream."
        return uiState.value.messages.mapNotNull { message ->
            (message.content as? MessageContent.Text)?.textKey?.takeIf { it.startsWith(prefix) }?.removePrefix(prefix)
        }
    }

    private fun awaitTag(tag: String): AccessibilityNodeInfo = awaitValue(tag) {
        findVisibleNode { it.viewIdResourceName == tag }
    }

    private fun panelBounds() = boundsOf(awaitTag(CHAT_PANEL_TAG))

    private fun scrollbarBounds() = boundsOf(awaitTag(CHAT_SCROLLBAR_TAG))

    private fun awaitInReadingArea(id: String, wholeRow: Boolean = true): Rect =
        awaitValue("row $id above the panel") { rowInReadingArea(id, wholeRow) }

    private fun awaitHistoryEnd(id: String): Rect = awaitValue("row $id at the end of the history") {
        val marginPx = 12 * context.resources.displayMetrics.density
        rowInReadingArea(id, wholeRow = false)?.takeIf {
            kotlin.math.abs(scrollbarBounds().bottom - it.bottom - marginPx) <= 2f
        }
    }

    private fun rowInReadingArea(id: String, wholeRow: Boolean = true): Rect? {
        val node = findVisibleNode { it.viewIdResourceName == "$CHAT_MESSAGE_TAG_PREFIX$id" } ?: return null
        val row = boundsOf(node)
        val area = scrollbarBounds()
        return row.takeIf {
            it.bottom in (area.top + 1)..area.bottom && (!wholeRow || it.top >= area.top)
        }
    }

    private fun firstReadableRow(): Pair<String, Rect> = visibleMessageIds().firstNotNullOf { id ->
        rowInReadingArea(id)?.let { id to it }
    }

    private fun scrollHistoryToBoundary(action: Int, boundaryMessageId: String) {
        val forward = action == AccessibilityNodeInfo.ACTION_SCROLL_FORWARD
        val area = scrollbarBounds()
        val marginPx = (context.resources.displayMetrics.density * if (forward) 12 else 10).toInt()
        repeat(15) {
            val row = rowInReadingArea(boundaryMessageId, wholeRow = !forward)
            if (row != null) {
                val edge = if (forward) area.bottom - row.bottom else row.top - area.top
                if (kotlin.math.abs(edge - marginPx) <= 2) return
            }
            if (!awaitTag(CHAT_HISTORY_TAG).performAction(action)) {
                awaitInReadingArea(boundaryMessageId, wholeRow = !forward)
                return
            }
            instrumentation.uiAutomation.waitForIdle(250, 5_000)
            refreshAccessibility()
        }
        throw AssertionError(
            "History did not reach its scroll boundary at '$boundaryMessageId', " +
                "reading area $area, target row ${rowInReadingArea(boundaryMessageId, !forward)}, " +
                "margin $marginPx, visible rows ${visibleMessageIds()}"
        )
    }

    private fun swipeHistory(towardEnd: Boolean, finishWithoutFling: Boolean = false) {
        val area = scrollbarBounds()
        val history = boundsOf(awaitTag(CHAT_HISTORY_TAG))
        val start = area.top + area.height() / 8
        val end = area.bottom - area.height() / 8
        swipe(
            history.centerX(),
            if (towardEnd) end else start,
            if (towardEnd) start else end,
            finishWithoutFling = finishWithoutFling
        )
    }

    private fun thumbRows(scenario: ActivityScenario<ComponentActivity>, track: Rect): IntRange {
        val pixels = capturePixels(scenario, track)
        val rows = (0 until track.height()).filter { y ->
            pixels[y * track.width() + track.width() / 2] == 0xFFEA9FE7.toInt()
        }
        return rows.first()..rows.last()
    }

    private fun visibleMessageIds(): List<String> {
        instrumentation.uiAutomation.waitForIdle(250, 5_000)
        refreshAccessibility()
        val messages = mutableListOf<Pair<Int, String>>()
        fun collect(node: AccessibilityNodeInfo) {
            val tag = node.viewIdResourceName
            if (node.isVisibleToUser && tag != null && tag.startsWith(CHAT_MESSAGE_TAG_PREFIX)) {
                messages.add(boundsOf(node).top to tag.removePrefix(CHAT_MESSAGE_TAG_PREFIX))
            }
            for (index in 0 until node.childCount) {
                node.getChild(index)?.let(::collect)
            }
        }
        instrumentation.uiAutomation.rootInActiveWindow?.let(::collect)
        return messages.sortedBy { it.first }.map { it.second }
    }

    // A picker cell and a sent sticker can have the same localized description.
    private fun awaitNode(
        label: String,
        withinTag: String? = null
    ): AccessibilityNodeInfo = awaitValue("'$label' under '$withinTag'") {
        findNodeInTag(withinTag) { node ->
            node.text?.toString() == label || node.contentDescription?.toString() == label
        }
    }

    private fun <T : Any> awaitValue(description: String, read: () -> T?): T {
        val deadline = SystemClock.uptimeMillis() + 5_000
        do {
            read()?.let { return it }
            SystemClock.sleep(50)
            refreshAccessibility()
        } while (SystemClock.uptimeMillis() < deadline)

        throw AssertionError("Did not see $description")
    }

    private fun refreshAccessibility() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            instrumentation.uiAutomation.clearCache()
        }
    }

    private fun findVisibleNode(matches: (AccessibilityNodeInfo) -> Boolean): AccessibilityNodeInfo? {
        return findNodeInTag(null, matches)
    }

    private fun findNodeInTag(
        tag: String?,
        matches: (AccessibilityNodeInfo) -> Boolean
    ): AccessibilityNodeInfo? {
        val root = instrumentation.uiAutomation.rootInActiveWindow ?: return null
        val scope = if (tag == null) root else root.findNode { it.viewIdResourceName == tag }
        return scope?.findNode(matches)
    }

    private fun AccessibilityNodeInfo.findNode(matches: (AccessibilityNodeInfo) -> Boolean): AccessibilityNodeInfo? {
        if (isVisibleToUser && matches(this)) return this
        for (index in 0 until childCount) {
            val found = getChild(index)?.findNode(matches)
            if (found != null) return found
        }
        return null
    }

    private fun scrollPickerToLastRow() {
        val column = boundsOf(awaitPickerSticker("ok", "De acuerdo"))
        val panel = panelBounds()
        val inset = panel.height() / 8
        repeat(4) {
            if (findNodeInTag("${PICKER_STICKER_TAG_PREFIX}sorena") { it.contentDescription?.toString() == "Esto." } != null) {
                return
            }
            swipe(column.centerX(), panel.bottom - inset, panel.top + inset)
        }
        awaitPickerSticker("sorena", "Esto.")
    }

    private fun tap(node: AccessibilityNodeInfo) {
        val bounds = boundsOf(node)
        tap(bounds.exactCenterX(), bounds.exactCenterY())
    }

    private fun tap(x: Float, y: Float) {
        val start = SystemClock.uptimeMillis()
        sendPointer(start, MotionEvent.ACTION_DOWN, x, y)
        sendPointer(start, MotionEvent.ACTION_UP, x, y)
    }

    private fun sendPointer(downTime: Long, action: Int, x: Float, y: Float) {
        val event = MotionEvent.obtain(downTime, SystemClock.uptimeMillis(), action, x, y, 0)
        try {
            instrumentation.sendPointerSync(event)
        } finally {
            event.recycle()
        }
    }

    private fun swipe(x: Int, startY: Int, endY: Int, stepMillis: Long = 16, finishWithoutFling: Boolean = false) {
        val start = SystemClock.uptimeMillis()
        for (step in 0..10) {
            val action = when (step) {
                0 -> MotionEvent.ACTION_DOWN
                10 -> MotionEvent.ACTION_UP
                else -> MotionEvent.ACTION_MOVE
            }
            val y = startY + (endY - startY) * step / 10f
            if (step == 10 && finishWithoutFling) {
                repeat(4) {
                    sendPointer(start, MotionEvent.ACTION_MOVE, x.toFloat(), y)
                    SystemClock.sleep(40)
                }
            }
            sendPointer(start, action, x.toFloat(), y)
            if (step < 10) SystemClock.sleep(stepMillis)
        }
        instrumentation.uiAutomation.waitForIdle(250, 5_000)
        refreshAccessibility()
    }

    private fun boundsOf(node: AccessibilityNodeInfo): Rect = Rect().also(node::getBoundsInScreen)

    private fun capturePixels(scenario: ActivityScenario<ComponentActivity>, bounds: Rect): IntArray {
        instrumentation.uiAutomation.waitForIdle(250, 5_000)
        val width = bounds.width()
        val height = bounds.height()
        val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        try {
            val result = CompletableFuture<Int>()
            scenario.onActivity { activity ->
                val location = IntArray(2)
                activity.window.decorView.getLocationOnScreen(location)
                val windowBounds = Rect(bounds).apply { offset(-location[0], -location[1]) }
                PixelCopy.request(
                    activity.window,
                    windowBounds,
                    bitmap,
                    { result.complete(it) },
                    Handler(Looper.getMainLooper())
                )
            }
            assertEquals(PixelCopy.SUCCESS, result.get(5, TimeUnit.SECONDS).toInt())
            return IntArray(width * height).also { pixels ->
                bitmap.getPixels(pixels, 0, width, 0, 0, width, height)
            }
        } finally {
            bitmap.recycle()
        }
    }
}
