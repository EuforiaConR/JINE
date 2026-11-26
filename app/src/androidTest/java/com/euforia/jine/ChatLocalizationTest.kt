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
    fun startsInSpanishOnEnglishDeviceAndChangesLanguageWithoutSending() {
        val englishConfig = Configuration(context.resources.configuration).apply { setLocale(Locale.ENGLISH) }
        val englishContext = context.createConfigurationContext(englishConfig)
        val requestedLanguage = mutableStateOf(JineLanguageManager.DEFAULT_LANGUAGE)
        val sends = mutableListOf<Sticker>()
        val stickers = StickerRepository.load(context)

        ActivityScenario.launch(ComponentActivity::class.java).use { scenario ->
            scenario.onActivity { activity ->
                activity.setContent {
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
                activity.setContent {
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
            repeat(4) {
                if (rowInReadingArea("preview_greeting") == null) {
                    scrollHistory(AccessibilityNodeInfo.ACTION_SCROLL_BACKWARD)
                }
            }
            awaitInReadingArea("preview_greeting")
            swipeHistory(towardEnd = true)
            awaitNode(lastMessage)
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
                activity.setContent {
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
            activity.setContent {
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
            activity.setContent { ChatScreen(chat, language.value) { sends.add(it.id) } }
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
    fun heldStickerBrightensAndReturnsToIdleAfterCancelOrRelease() {
        val stickers = StickerRepository.load(context).take(1)
        val sends = mutableListOf<Sticker>()
        ActivityScenario.launch(ComponentActivity::class.java).use { scenario ->
            scenario.onActivity { activity ->
                activity.setContent {
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
                activity.setContent {
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
                activity.setContent {
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

    private fun controlledChatFactory(scheduler: TestCoroutineScheduler): ViewModelProvider.Factory {
        var nextId = 0
        return viewModelFactory {
            initializer {
                ChatViewModel(
                    timings = ChatTimings(readMs = 800, replyPreparationMs = 600, replyIntervalMs = 500),
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

    private fun swipe(x: Int, startY: Int, endY: Int, stepMillis: Long = 16) {
        val start = SystemClock.uptimeMillis()
        for (step in 0..10) {
            val action = when (step) {
                0 -> MotionEvent.ACTION_DOWN
                10 -> MotionEvent.ACTION_UP
                else -> MotionEvent.ACTION_MOVE
            }
            val y = startY + (endY - startY) * step / 10f
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
