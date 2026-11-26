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
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.euforia.jine.core.assets.JineAssets
import com.euforia.jine.core.localization.JineLanguageManager
import com.euforia.jine.core.localization.ProvideLocalizedResources
import com.euforia.jine.core.localization.rememberLocalization
import com.euforia.jine.data.model.Message
import com.euforia.jine.data.model.MessageContent
import com.euforia.jine.data.model.MessageSender
import com.euforia.jine.data.model.PCHAN_PACK_ID
import com.euforia.jine.data.model.Sticker
import com.euforia.jine.data.repository.StickerRepository
import com.euforia.jine.ui.components.CHAT_MESSAGE_TAG_PREFIX
import com.euforia.jine.ui.components.CHAT_HISTORY_TAG
import com.euforia.jine.ui.components.CHAT_SCROLLBAR_TAG
import com.euforia.jine.ui.components.ChatMessageItem
import com.euforia.jine.ui.components.PICKER_STICKER_TAG_PREFIX
import com.euforia.jine.ui.components.PixelImage
import com.euforia.jine.ui.components.StickerPicker
import com.euforia.jine.ui.screens.ChatScreen
import com.euforia.jine.ui.screens.CHAT_PANEL_TAG
import com.euforia.jine.ui.screens.ChatContent
import com.euforia.jine.ui.screens.ChatTimings
import com.euforia.jine.ui.screens.ChatUiState
import com.euforia.jine.ui.screens.ChatViewModel
import com.euforia.jine.ui.screens.CommunicationState
import com.euforia.jine.ui.screens.initialMessages
import com.euforia.jine.ui.theme.JineChatBackground
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

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(AndroidJUnit4::class)
class ChatLocalizationTest {
    private val instrumentation get() = InstrumentationRegistry.getInstrumentation()
    private val context get() = instrumentation.targetContext
    private val previewIds = listOf("preview_greeting", "preview_question", "preview_long_message")

    @Before
    fun exposeAccessibilityResourceIds() {
        val automation = instrumentation.uiAutomation
        automation.serviceInfo = automation.serviceInfo.apply {
            flags = flags or AccessibilityServiceInfo.FLAG_REPORT_VIEW_IDS or
                AccessibilityServiceInfo.FLAG_INCLUDE_NOT_IMPORTANT_VIEWS
        }
    }

    @Test
    fun startsInSpanishOnEnglishDeviceAndChangesLanguageWithSelectionIntact() {
        val englishConfig = Configuration(context.resources.configuration).apply { setLocale(Locale.ENGLISH) }
        val englishContext = context.createConfigurationContext(englishConfig)
        val requestedLanguage = mutableStateOf(JineLanguageManager.DEFAULT_LANGUAGE)
        val selected = mutableListOf<Sticker>()
        val stickers = StickerRepository.load(context)

        ActivityScenario.launch(ComponentActivity::class.java).use { scenario ->
            scenario.onActivity { activity ->
                activity.setContent {
                    CompositionLocalProvider(
                        LocalContext provides englishContext,
                        LocalConfiguration provides englishConfig
                    ) {
                        ChatScreen(language = requestedLanguage.value, onStickerSelected = selected::add)
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

            val firstSticker = awaitPickerSticker("ok", "De acuerdo", selected = false)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                assertEquals("Sin seleccionar", firstSticker.stickerControl().stateDescription.toString())
            }
            tap(firstSticker)
            awaitPickerSticker("ok", "De acuerdo", selected = true)
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
            val englishSticker = awaitPickerSticker("ok", "Okay", selected = true)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                assertEquals("Sticker selected", englishSticker.stickerControl().stateDescription.toString())
            }
            val englishLove = boundsOf(awaitPickerSticker("love", "Love forever"))
            assertEquals(spanishLove, englishLove)
            assertFalse(
                "The love sticker should change visually with the language",
                spanishLovePixels.contentEquals(capturePixels(scenario, englishLove))
            )
            assertEquals(previewIds, visibleMessageIds())
            scenario.onActivity { assertEquals(listOf(stickers.first()), selected) }
        }
    }

    @Test
    fun compactChatAndStickerGridScrollIndependently() {
        val lastMessage = "Tengo muchas cosas que contarte hoy. Espero que podamos hablar un rato."
        ActivityScenario.launch(ComponentActivity::class.java).use { scenario ->
            scenario.onActivity { activity ->
                activity.setContent {
                    Box(Modifier.size(width = 320.dp, height = 260.dp)) {
                        ChatScreen()
                    }
                }
            }

            scrollForwardFrom(awaitPickerSticker("ok", "De acuerdo"))
            val lastSticker = awaitPickerSticker("sorena", "Esto.").stickerControl()
            assertTrue(lastSticker.performAction(AccessibilityNodeInfo.ACTION_CLICK))
            awaitPickerSticker("sorena", "Esto.", selected = true)

            awaitInReadingArea("preview_long_message", wholeRow = false)
            repeat(4) {
                if (rowInReadingArea("preview_greeting") == null) {
                    scrollHistory(AccessibilityNodeInfo.ACTION_SCROLL_BACKWARD)
                }
            }
            awaitInReadingArea("preview_greeting")
            swipeHistory(towardEnd = true)
            awaitNode(lastMessage)
            awaitInReadingArea("preview_long_message", wholeRow = false)
            awaitPickerSticker("sorena", "Esto.", selected = true)
            assertEquals(panelBounds().top, scrollbarBounds().bottom)
        }
    }

    @Test
    fun confirmsStickerSendsAndKeepsHistoryIdsAndOrderWhenLanguageChanges() {
        val language = mutableStateOf("es")
        val presses = mutableListOf<String>()
        val factory = controlledChatFactory(TestCoroutineScheduler())
        ActivityScenario.launch(ComponentActivity::class.java).use { scenario ->
            lateinit var chat: ChatViewModel
            scenario.onActivity { activity ->
                chat = ViewModelProvider(activity, factory)[ChatViewModel::class.java]
                activity.setContent {
                    ChatScreen(chat, language = language.value, onStickerSelected = { presses.add(it.id) })
                }
            }

            tap(awaitPickerSticker("ok", "De acuerdo", selected = false))
            awaitPickerSticker("ok", "De acuerdo", selected = true)
            assertEquals(previewIds, chat.messageIds())

            tap(awaitPickerSticker("love", "Que viva el amor", selected = false))
            awaitPickerSticker("love", "Que viva el amor", selected = true)
            awaitPickerSticker("ok", "De acuerdo", selected = false)
            assertEquals(previewIds, chat.messageIds())

            tap(awaitPickerSticker("ok", "De acuerdo", selected = false))
            tap(awaitPickerSticker("ok", "De acuerdo", selected = true))
            awaitPickerSticker("ok", "De acuerdo", selected = false)
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

            tap(awaitPickerSticker("ok", "De acuerdo", selected = false))
            awaitPickerSticker("ok", "De acuerdo", selected = true)
            assertEquals(firstSend, chat.messageIds())
            tap(awaitPickerSticker("ok", "De acuerdo", selected = true))
            awaitPickerSticker("ok", "De acuerdo", selected = false)
            val secondSend = chat.messageIds()
            assertEquals(5, secondSend.size)
            assertEquals(firstSend, secondSend.take(4))
            awaitMessage("De acuerdo", secondSend.last())

            tap(awaitPickerSticker("love", "Que viva el amor", selected = false))
            tap(awaitPickerSticker("love", "Que viva el amor", selected = true))
            awaitPickerSticker("love", "Que viva el amor", selected = false)
            val sentIds = chat.messageIds()
            assertEquals(6, sentIds.size)
            assertEquals(secondSend, sentIds.take(5))
            assertEquals(sentIds.size, sentIds.distinct().size)
            awaitMessage("Que viva el amor", sentIds.last())
            assertNull(findVisibleNode { it.text?.toString() == "Leído" })

            scenario.onActivity { language.value = "en" }
            awaitMessage("Are you there?", "preview_question")
            awaitMessage("Okay", sentIds[3])
            awaitMessage("Okay", sentIds[4])
            awaitMessage("Love forever", sentIds[5])
            assertEquals(sentIds, chat.messageIds())
            assertNull(findVisibleNode { it.text?.toString() == "Read" })
            scenario.onActivity {
                assertEquals(listOf("ok", "love", "ok", "ok", "ok", "ok", "love", "love"), presses)
            }
        }
    }

    @Test
    fun activeBatchSurvivesRecreationAndLocalizesWhileThePickerIsLocked() {
        val scheduler = TestCoroutineScheduler()
        val factory = controlledChatFactory(scheduler)
        val language = mutableStateOf("es")
        val presses = mutableListOf<String>()
        fun showChat(activity: ComponentActivity): ChatViewModel {
            val chat = ViewModelProvider(activity, factory)[ChatViewModel::class.java]
            activity.setContent {
                ChatScreen(chat, language = language.value, onStickerSelected = { presses.add(it.id) })
            }
            return chat
        }

        ActivityScenario.launch(ComponentActivity::class.java).use { scenario ->
            lateinit var chat: ChatViewModel
            scenario.onActivity { chat = showChat(it) }
            tap(awaitPickerSticker("love", "Que viva el amor", selected = false))
            tap(awaitPickerSticker("love", "Que viva el amor", selected = true))
            awaitMessage("Que viva el amor", "timed-1")
            assertNull(findNodeInTag("${CHAT_MESSAGE_TAG_PREFIX}timed-1") { it.text?.toString() == "Leído" })

            advanceChat(scenario, scheduler, 350)
            tap(awaitPickerSticker("ok", "De acuerdo", selected = false))
            tap(awaitPickerSticker("ok", "De acuerdo", selected = true))
            awaitMessage("De acuerdo", "timed-2")
            tap(awaitPickerSticker("gomen", "Lo siento", selected = false))
            awaitPickerSticker("gomen", "Lo siento", selected = true)
            val beforeRecreation = chat.uiState.value

            scenario.recreate()
            scenario.onActivity { assertSame(chat, showChat(it)) }
            awaitPickerSticker("gomen", "Lo siento", selected = true)
            assertEquals(beforeRecreation, chat.uiState.value)
            assertEquals(previewIds + listOf("timed-1", "timed-2"), chat.messageIds())

            advanceChat(scenario, scheduler, 450)
            awaitMessage("Leído", "timed-1")
            assertNull(findNodeInTag("${CHAT_MESSAGE_TAG_PREFIX}timed-2") { it.text?.toString() == "Leído" })

            advanceChat(scenario, scheduler, 200)
            val locked = awaitPickerSticker("gomen", "Lo siento", selected = false)
            assertEquals(CommunicationState.RESPONDING, chat.uiState.value.communicationState)
            assertFalse(locked.stickerControl().isEnabled)
            val blockedState = chat.uiState.value
            tap(locked)
            scenario.onActivity {
                assertEquals(listOf("love", "love", "ok", "ok", "gomen"), presses)
                assertEquals(blockedState, chat.uiState.value)
                language.value = "en"
            }
            awaitMessage("Are you there?", "preview_question")
            awaitMessage("Love forever", "timed-1")
            awaitMessage("Read", "timed-1")
            assertEquals(blockedState, chat.uiState.value)

            advanceChat(scenario, scheduler, 150)
            awaitMessage("Read", "timed-2")
            advanceChat(scenario, scheduler, 600)
            awaitMessage("<3 <3 <3", "timed-3")
            assertFalse(awaitPickerSticker("ok", "Okay").stickerControl().isEnabled)
            advanceChat(scenario, scheduler, 500)
            awaitMessage("i have so much to tell you today", "timed-4")
            assertFalse(awaitPickerSticker("ok", "Okay").stickerControl().isEnabled)
            advanceChat(scenario, scheduler, 600)
            awaitMessage("okaaaay", "timed-5")
            assertTrue(awaitPickerSticker("ok", "Okay").stickerControl().isEnabled)
            assertEquals(CommunicationState.AVAILABLE, chat.uiState.value.communicationState)
            assertEquals(previewIds + (1..5).map { "timed-$it" }, chat.messageIds())
        }
    }

    @Test
    fun longHistoryFollowsNewContentAndKeepsTheAnchorWhileReadingOlderMessages() {
        val scheduler = TestCoroutineScheduler()
        val factory = controlledChatFactory(scheduler)
        val language = mutableStateOf("es")
        val presses = mutableListOf<String>()
        fun showChat(activity: ComponentActivity): ChatViewModel {
            val chat = ViewModelProvider(activity, factory)[ChatViewModel::class.java]
            activity.setContent { ChatScreen(chat, language.value) { presses.add(it.id) } }
            return chat
        }

        ActivityScenario.launch(ComponentActivity::class.java).use { scenario ->
            lateinit var chat: ChatViewModel
            scenario.onActivity {
                chat = showChat(it)
                repeat(5) {
                    assertTrue(chat.onStickerPressed("love", setOf("love")))
                    assertTrue(chat.onStickerPressed("love", setOf("love")))
                }
                scheduler.advanceUntilIdle()
            }
            awaitInReadingArea(chat.messageIds().last())

            val initialPicker = awaitPickerSticker("love", "Que viva el amor", selected = false)
            assertEquals(CommunicationState.AVAILABLE, chat.uiState.value.communicationState)
            assertTrue(initialPicker.stickerControl().isEnabled)
            tap(initialPicker)
            scenario.onActivity { assertEquals("love", chat.uiState.value.selectedStickerId) }
            tap(awaitPickerSticker("love", "Que viva el amor", selected = true))
            val love = chat.messageIds().last()
            awaitMessage("Que viva el amor", love)
            awaitInReadingArea(love)
            advanceChat(scenario, scheduler, 350)
            tap(awaitPickerSticker("ok", "De acuerdo", selected = false))
            tap(awaitPickerSticker("ok", "De acuerdo", selected = true))
            val ok = chat.messageIds().last()
            awaitMessage("De acuerdo", ok)
            awaitInReadingArea(ok)
            advanceChat(scenario, scheduler, 450)
            awaitMessage("Leído", love)
            awaitInReadingArea(ok)
            assertNull(findNodeInTag("$CHAT_MESSAGE_TAG_PREFIX$ok") { it.text?.toString() == "Leído" })
            advanceChat(scenario, scheduler, 350)
            awaitMessage("Leído", ok)
            awaitInReadingArea(ok)

            advanceChat(scenario, scheduler, 600)
            val firstReply = chat.messageIds().last()
            awaitMessage("<3 <3 <3", firstReply)
            val end = awaitInReadingArea(firstReply)
            val lockedPicker = awaitPickerSticker("ok", "De acuerdo")
            assertFalse(lockedPicker.stickerControl().isEnabled)
            tap(lockedPicker)
            val panel = panelBounds()
            swipe(panel.left + 2, panel.top + 16, panel.bottom - 16)
            assertEquals(end, awaitInReadingArea(firstReply))
            assertEquals(listOf("love", "love", "ok", "ok"), presses)

            swipeHistory(towardEnd = false)
            awaitValue("last reply out of the reading area") {
                Unit.takeIf { rowInReadingArea(firstReply) == null }
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
            advanceChat(scenario, scheduler, 600)
            val finalReply = chat.messageIds().last()
            awaitMessage("okaaaay", finalReply)
            awaitInReadingArea(finalReply)
            assertTrue(awaitPickerSticker("ok", "Okay").stickerControl().isEnabled)

            swipeHistory(towardEnd = false)
            val (olderId, olderBounds) = firstReadableRow()
            tap(awaitPickerSticker("gomen", "Sorry", selected = false))
            awaitPickerSticker("gomen", "Sorry", selected = true)
            assertEquals(finalReply, chat.messageIds().last())
            assertEquals(olderBounds, awaitInReadingArea(olderId))
            tap(awaitPickerSticker("gomen", "Sorry", selected = true))
            val newSend = chat.messageIds().last()
            awaitMessage("Sorry", newSend)
            awaitInReadingArea(newSend)
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
                activity.setContent { ChatContent(ChatUiState(displayedHistory.value), "es", stickers) }
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
                activity.setContent {
                    Box(Modifier.size(320.dp, height.value)) {
                        ChatContent(ChatUiState(history), "es", catalog.value)
                    }
                }
            }
            awaitInReadingArea(history.last().id)
            val compactPanel = panelBounds()
            assertEquals(compactPanel.top, scrollbarBounds().bottom)
            scrollForwardFrom(awaitPickerSticker("ok", "De acuerdo"))
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
    fun readReceiptBelongsToOneMessageAndSentImageMatchesTheLocalizedAsset() {
        val language = mutableStateOf("es")
        val stickersById = StickerRepository.load(context).associateBy { it.id }
        val sticker = MessageContent.Sticker(PCHAN_PACK_ID, "love")
        val unread = Message("unread", MessageSender.PCHAN, sticker, sentAt = 1L)
        val read = Message("read", MessageSender.PCHAN, sticker, sentAt = 2L, readAt = 3L)
        val unavailable = listOf(
            unread.copy(id = "wrong_pack", content = sticker.copy(packId = "ame")),
            unread.copy(id = "unknown_sticker", content = sticker.copy(stickerId = "unknown")),
            unread.copy(id = "ame_sticker", sender = MessageSender.AME),
            unread.copy(id = "pchan_text", content = MessageContent.Text("dialogue.preview.greeting"))
        )
        ActivityScenario.launch(ComponentActivity::class.java).use { scenario ->
            scenario.onActivity { activity ->
                activity.setContent {
                    val localization = rememberLocalization(language.value)
                    ProvideLocalizedResources(localization.language) {
                        Column(Modifier.fillMaxWidth().background(JineChatBackground)) {
                            ChatMessageItem(unread, localization, stickersById)
                            ChatMessageItem(read, localization, stickersById)
                            for (message in unavailable) {
                                ChatMessageItem(message, localization, stickersById)
                            }
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
            scenario.onActivity { language.value = "en" }
            unavailable.forEach { awaitMessage("Message unavailable", it.id) }
            val english = checkReadSticker(scenario, "Love forever", "Read", read.id, unread.id)
            assertFalse(spanish.contentEquals(english))
        }
    }

    @Test
    fun disabledPickerDoesNotEmitAnEvent() {
        val stickers = StickerRepository.load(context).take(1)
        val selected = mutableListOf<Sticker>()
        ActivityScenario.launch(ComponentActivity::class.java).use { scenario ->
            scenario.onActivity { activity ->
                activity.setContent {
                    ProvideLocalizedResources("en") {
                        StickerPicker(
                            stickers = stickers,
                            selectedStickerId = stickers.single().id,
                            onStickerSelected = selected::add,
                            descriptionFor = { "Okay" },
                            enabled = false,
                            language = JineLanguageManager.ENGLISH
                        )
                    }
                }
            }
            val label = awaitPickerSticker("ok", "Okay", selected = true)
            assertFalse(label.stickerControl().isEnabled)
            tap(label)
            scenario.onActivity { assertTrue(selected.isEmpty()) }
        }
    }

    @Test
    fun emptyPickerShowsLocalizedStatus() {
        ActivityScenario.launch(ComponentActivity::class.java).use { scenario ->
            scenario.onActivity { activity ->
                activity.setContent {
                    ProvideLocalizedResources("en") {
                        StickerPicker(emptyList(), null, {}, { it.descriptionKey })
                    }
                }
            }
            awaitNode("No stickers available.")
        }
    }

    private fun controlledChatFactory(scheduler: TestCoroutineScheduler): ViewModelProvider.Factory {
        var nextId = 0
        return viewModelFactory {
            initializer {
                ChatViewModel(
                    timings = ChatTimings(admissionMs = 1_000, readMs = 800, replyPreparationMs = 600, replyIntervalMs = 500),
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

    private fun awaitPickerSticker(id: String, label: String, selected: Boolean? = null): AccessibilityNodeInfo {
        return awaitNode(label, selected, "$PICKER_STICKER_TAG_PREFIX$id")
    }

    private fun awaitMessage(label: String, messageId: String): AccessibilityNodeInfo {
        return awaitNode(label, withinTag = "$CHAT_MESSAGE_TAG_PREFIX$messageId")
    }

    private fun ChatViewModel.messageIds() = uiState.value.messages.map { it.id }

    private fun awaitTag(tag: String): AccessibilityNodeInfo = awaitValue(tag) {
        findVisibleNode { it.viewIdResourceName == tag }
    }

    private fun panelBounds() = boundsOf(awaitTag(CHAT_PANEL_TAG))

    private fun scrollbarBounds() = boundsOf(awaitTag(CHAT_SCROLLBAR_TAG))

    private fun awaitInReadingArea(id: String, wholeRow: Boolean = true): Rect =
        awaitValue("row $id above the panel") { rowInReadingArea(id, wholeRow) }

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

    private fun scrollHistory(action: Int) {
        assertTrue(awaitTag(CHAT_HISTORY_TAG).performAction(action))
        instrumentation.uiAutomation.waitForIdle(250, 5_000)
        refreshAccessibility()
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

    private fun swipeHistory(towardEnd: Boolean) {
        val area = scrollbarBounds()
        val history = boundsOf(awaitTag(CHAT_HISTORY_TAG))
        val start = area.top + area.height() / 8
        val end = area.bottom - area.height() / 8
        swipe(history.centerX(), if (towardEnd) end else start, if (towardEnd) start else end)
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
        selected: Boolean? = null,
        withinTag: String? = null
    ): AccessibilityNodeInfo = awaitValue("'$label' under '$withinTag' (selected: $selected)") {
        findNodeInTag(withinTag) { node ->
            val matches = node.text?.toString() == label || node.contentDescription?.toString() == label
            matches && (selected == null || node.isStickerSelected() == selected)
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

    // Compose can expose the description as a child of the checkable selection control.
    private fun AccessibilityNodeInfo.stickerControl(): AccessibilityNodeInfo {
        return if (isCheckable) this else parent ?: this
    }

    @Suppress("DEPRECATION") // Android maps selected controls other than tabs to checked.
    private fun AccessibilityNodeInfo.isStickerSelected() = stickerControl().isChecked

    private fun scrollForwardFrom(node: AccessibilityNodeInfo) {
        var ancestor = node.parent
        while (ancestor != null && !ancestor.isScrollable) {
            ancestor = ancestor.parent
        }
        assertTrue("Expected a scrollable container", ancestor != null)
        assertTrue(ancestor?.performAction(AccessibilityNodeInfo.ACTION_SCROLL_FORWARD) == true)
        instrumentation.uiAutomation.waitForIdle(250, 5_000)
    }

    private fun tap(node: AccessibilityNodeInfo) {
        val bounds = boundsOf(node)
        val start = SystemClock.uptimeMillis()
        for (action in listOf(MotionEvent.ACTION_DOWN, MotionEvent.ACTION_UP)) {
            val event = MotionEvent.obtain(
                start, SystemClock.uptimeMillis(), action, bounds.exactCenterX(), bounds.exactCenterY(), 0
            )
            try {
                instrumentation.sendPointerSync(event)
            } finally {
                event.recycle()
            }
        }
    }

    private fun swipe(x: Int, startY: Int, endY: Int, stepMillis: Long = 16) {
        val start = SystemClock.uptimeMillis()
        for (step in 0..10) {
            val action = when (step) {
                0 -> MotionEvent.ACTION_DOWN
                10 -> MotionEvent.ACTION_UP
                else -> MotionEvent.ACTION_MOVE
            }
            val y = startY + (endY - startY) * step / 10f
            val event = MotionEvent.obtain(start, SystemClock.uptimeMillis(), action, x.toFloat(), y, 0)
            try {
                instrumentation.sendPointerSync(event)
            } finally {
                event.recycle()
            }
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
