package com.euforia.jine

import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.ViewModelStore
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.euforia.jine.data.model.MessageContent
import com.euforia.jine.data.model.MessageSender
import com.euforia.jine.data.model.dialogue.DialogueAction
import com.euforia.jine.data.model.dialogue.DialogueAction.End
import com.euforia.jine.data.model.dialogue.DialogueAction.SendText
import com.euforia.jine.data.model.dialogue.DialogueAction.ShowChoices
import com.euforia.jine.data.model.dialogue.DialogueAction.Wait
import com.euforia.jine.data.model.dialogue.DialogueDefinition
import com.euforia.jine.data.model.dialogue.DialogueNode
import com.euforia.jine.data.model.dialogue.DialogueOption
import com.euforia.jine.data.model.dialogue.DialogueValidationException
import com.euforia.jine.ui.screens.ChatInputMode
import com.euforia.jine.ui.screens.ChatTimings
import com.euforia.jine.ui.screens.ChatUiState
import com.euforia.jine.ui.screens.ChatViewModel
import com.euforia.jine.ui.screens.CommunicationState
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.io.IOException

@OptIn(ExperimentalCoroutinesApi::class)
class ChatNarrativeTest {
    private val dispatcher = StandardTestDispatcher()
    private val store = ViewModelStore()
    private val stickers = setOf("love", "ok", "gomen")
    private val linear = linearDialogue(SendText("narrative.start"), Wait(700), SendText("narrative.end"), End)
    private var idCalls = 0
    private var clockCalls = 0

    @Before
    fun useVirtualMain() {
        Dispatchers.setMain(dispatcher)
    }

    @After
    fun disposeViewModel() {
        try {
            store.clear()
            dispatcher.scheduler.runCurrent()
        } finally {
            Dispatchers.resetMain()
        }
    }

    @Test
    fun idleRequestHidesInputImmediatelyAndRestoresAnOrdinaryFreeBatchOnEnd() = runTest(dispatcher) {
        val chat = createChat()
        val initial = chat.state.messages
        assertTrue(chat.requestDialogue(linear))
        assertEquals(ChatInputMode.Hidden, chat.state.inputMode)
        assertEquals(CommunicationState.AVAILABLE, chat.state.communicationState)
        assertEquals(initial, chat.state.messages)
        assertRejectedWithoutEffects(chat) {
            assertFalse(chat.requestDialogue(linear))
            assertFalse(chat.sendSticker("ok", stickers))
            assertFalse(chat.chooseDialogueOption("none", "go"))
        }
        runCurrent()
        assertEquals(listOf("narrative.start"), chat.textKeys)
        tick(700)
        assertEquals(ChatInputMode.Stickers, chat.state.inputMode)
        assertTrue(chat.sendSticker("ok", stickers))
        tick(1_600)
        assertEquals(listOf("narrative.start", "narrative.end", "dialogue.demo.reply.ok.01"), chat.textKeys)
        assertEquals(listOf("test-1", "test-2", "test-3", "test-4"), chat.state.messages.drop(3).map { it.id })
        assertEquals(initial, chat.state.messages.take(3))
    }

    @Test
    fun invalidDefinitionFailsBeforeTheChatAcceptsARequest() = runTest(dispatcher) {
        val chat = createChat()
        assertRejectedWithoutEffects(chat) {
            assertThrows(DialogueValidationException::class.java) {
                chat.requestDialogue(linearDialogue(Wait(-1), End))
            }
        }
        runCurrent()
        assertEquals(ChatInputMode.Stickers, chat.state.inputMode)
        assertTrue(chat.textKeys.isEmpty())
    }

    @Test
    fun requestDuringReadingLetsTheStickerFinishBeforeTheFirstNarrativeText() = runTest(dispatcher) {
        val chat = createChat()
        assertTrue(chat.sendSticker("ok", stickers))
        tick(300)
        assertTrue(chat.requestDialogue(linear))
        assertRejectedWithoutEffects(chat) { assertFalse(chat.requestDialogue(linear)) }
        assertEquals(ChatInputMode.Stickers, chat.state.inputMode)
        tick(350)
        assertEquals(650L, chat.state.messages.last().readAt)
        tick(949)
        assertTrue(chat.textKeys.isEmpty())
        tick(1)
        assertEquals(listOf("dialogue.demo.reply.ok.01", "narrative.start"), chat.textKeys)
        assertEquals(listOf(1_600L, 1_600L), chat.state.messages.takeLast(2).map { it.sentAt })
        assertEquals(ChatInputMode.Hidden, chat.state.inputMode)
        assertEquals(CommunicationState.AVAILABLE, chat.state.communicationState)
    }

    @Test
    fun requestDuringPreparationKeepsTheBatchOpenAndReservesWhenTheLargerBatchCloses() = runTest(dispatcher) {
        val chat = createChat()
        val states = mutableListOf<ChatUiState>()
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { chat.uiState.collect { states.add(it) } }
        assertTrue(chat.sendSticker("love", stickers))
        tick(1_000)
        assertTrue(chat.requestDialogue(linear))
        assertEquals(ChatInputMode.Stickers, chat.state.inputMode)
        tick(200)
        assertTrue(chat.sendSticker("ok", stickers))
        tick(400)
        assertTrue(chat.textKeys.isEmpty())
        tick(250)
        assertEquals(listOf(650L, 1_850L), chat.state.messages.takeLast(2).map { it.readAt })
        assertRejectedWithoutEffects(chat) { assertFalse(chat.sendSticker("missing", stickers)) }
        tick(949)
        assertTrue(chat.textKeys.isEmpty())
        tick(1)
        assertEquals(listOf("dialogue.demo.batch.mixed.01", "narrative.start"), chat.textKeys)
        assertEquals(listOf(2_800L, 2_800L), chat.state.messages.takeLast(2).map { it.sentAt })
        val handover = states.dropWhile { it.inputMode != ChatInputMode.Hidden }
        assertTrue(handover.isNotEmpty())
        assertEquals(5, handover.first().messages.size)
        assertTrue(handover.all { it.inputMode == ChatInputMode.Hidden })
        assertRejectedWithoutEffects(chat) { assertFalse(chat.sendSticker("gomen", stickers)) }
    }

    @Test
    fun requestBetweenFreeRepliesImmediatelyClosesAdmissionWhenNoNextBatchExists() = runTest(dispatcher) {
        val chat = createChat()
        assertTrue(chat.sendSticker("love", stickers))
        tick(1_800)
        assertTrue(chat.requestDialogue(linear))
        assertEquals(ChatInputMode.Hidden, chat.state.inputMode)
        assertEquals(CommunicationState.RESPONDING, chat.state.communicationState)
        assertRejectedWithoutEffects(chat) { assertFalse(chat.sendSticker("ok", stickers)) }
        tick(499)
        assertEquals(listOf("dialogue.demo.reply.love.01"), chat.textKeys)
        tick(1)
        assertEquals(listOf("dialogue.demo.reply.love.01", "dialogue.demo.reply.love.02", "narrative.start"), chat.textKeys)
        assertEquals(listOf(2_300L, 2_300L), chat.state.messages.takeLast(2).map { it.sentAt })
    }

    @Test
    fun requestDuringAReactionWithAnotherBatchWaitsForThatBatchAndItsFullPreparation() = runTest(dispatcher) {
        val chat = createChat()
        assertTrue(chat.sendSticker("love", stickers))
        tick(1_700)
        assertTrue(chat.sendSticker("ok", stickers))
        tick(100)
        assertTrue(chat.requestDialogue(linear))
        assertEquals(ChatInputMode.Stickers, chat.state.inputMode)
        tick(500)
        assertEquals(CommunicationState.READING, chat.state.communicationState)
        assertEquals(listOf("dialogue.demo.reply.love.01", "dialogue.demo.reply.love.02"), chat.textKeys)
        tick(50)
        assertEquals(2_350L, chat.state.messages.first { it.id == "test-3" }.readAt)
        tick(949)
        assertEquals(ChatInputMode.Stickers, chat.state.inputMode)
        assertEquals(2, chat.textKeys.size)
        tick(1)
        assertEquals(listOf("dialogue.demo.reply.ok.01", "narrative.start"), chat.textKeys.takeLast(2))
        assertEquals(listOf(3_300L, 3_300L), chat.state.messages.takeLast(2).map { it.sentAt })
    }

    @Test
    fun stickerAtTheClosingInstantCanJoinIfItIsProcessedBeforeTheCoordinator() = runTest(dispatcher) {
        val chat = createChat()
        assertTrue(chat.sendSticker("love", stickers))
        tick(1_700)
        assertTrue(chat.sendSticker("ok", stickers))
        tick(100)
        assertTrue(chat.requestDialogue(linear))
        tick(550)
        advanceTimeBy(950)
        assertEquals(ChatInputMode.Stickers, chat.state.inputMode)
        assertTrue(chat.sendSticker("gomen", stickers))
        runCurrent()
        assertEquals(2, chat.textKeys.size)
        tick(650)
        assertEquals(3_950L, chat.state.messages.last().readAt)
        tick(950)
        assertEquals(ChatInputMode.Hidden, chat.state.inputMode)
        assertEquals(listOf("dialogue.demo.batch.mixed.01", "narrative.start"), chat.textKeys.takeLast(2))
        assertEquals(listOf(4_900L, 4_900L), chat.state.messages.takeLast(2).map { it.sentAt })
        assertRejectedWithoutEffects(chat) { assertFalse(chat.sendSticker("gomen", stickers)) }
    }

    @Test
    fun promptWaitsIndefinitelyAndAnEndBranchStillWaitsForReadAndPreparation() = runTest(dispatcher) {
        var wallTime = 3_000L
        val chat = createChat(now = { wallTime })
        val graph = singleChoice()
        assertTrue(chat.requestDialogue(graph))
        runCurrent()
        val first = chat.prompt
        assertEquals(listOf("go"), first.options.map { it.id })
        assertRejectedWithoutEffects(chat) {
            assertFalse(chat.chooseDialogueOption(first.promptId, "missing"))
            assertFalse(chat.chooseDialogueOption("expired-prompt", "go"))
            assertFalse(chat.sendSticker("ok", stickers))
        }
        val waiting = chat.state
        tick(300_000)
        assertEquals(waiting, chat.state)
        assertEquals(0, idCalls)

        assertTrue(chat.chooseDialogueOption(first.promptId, "go"))
        assertEquals(ChatInputMode.Hidden, chat.state.inputMode)
        val sent = chat.state.messages.last()
        assertEquals(MessageSender.PCHAN, sent.sender)
        assertEquals(MessageContent.Text("narrative.choice"), sent.content)
        assertNull(sent.readAt)
        assertRejectedWithoutEffects(chat) { assertFalse(chat.chooseDialogueOption(first.promptId, "go")) }
        wallTime -= 500
        tick(649)
        assertEquals(sent, chat.state.messages.last())
        tick(1)
        assertEquals(sent.copy(readAt = 3_000L), chat.state.messages.last())
        tick(949)
        assertEquals(ChatInputMode.Hidden, chat.state.inputMode)
        tick(1)
        assertEquals(ChatInputMode.Stickers, chat.state.inputMode)
        assertEquals(listOf("narrative.choice"), chat.textKeys)

        assertTrue(chat.requestDialogue(graph))
        runCurrent()
        val second = chat.prompt
        assertNotEquals(first.promptId, second.promptId)
        assertRejectedWithoutEffects(chat) { assertFalse(chat.chooseDialogueOption(first.promptId, "go")) }
        assertTrue(chat.chooseDialogueOption(second.promptId, "go"))
        tick(1_600)
        assertEquals(ChatInputMode.Stickers, chat.state.inputMode)
        assertEquals(listOf("test-1", "test-2"), chat.state.messages.takeLast(2).map { it.id })
    }

    @Test
    fun anInlinePromptCanBeAnsweredOnPublicationAndTheChatCanBeReusedWithZeroDelays() = runTest(dispatcher) {
        val inline = UnconfinedTestDispatcher(testScheduler)
        val chat = createChat(timings = ChatTimings(0, 0, 0), worker = inline)
        val answered = mutableListOf<String>()
        backgroundScope.launch(inline) {
            chat.uiState.collect { state ->
                val mode = state.inputMode as? ChatInputMode.Choices ?: return@collect
                assertFalse(chat.requestDialogue(linear))
                assertTrue(chat.chooseDialogueOption(mode.promptId, "go"))
                answered.add(mode.promptId)
            }
        }
        repeat(2) {
            assertTrue(chat.requestDialogue(singleChoice(SendText("narrative.branch"), End)))
            runCurrent()
            assertEquals(ChatInputMode.Stickers, chat.state.inputMode)
        }
        assertEquals(2, answered.distinct().size)
        assertTrue(chat.sendSticker("ok", stickers))
        runCurrent()
        assertEquals(
            listOf("narrative.choice", "narrative.branch", "narrative.choice", "narrative.branch", "dialogue.demo.reply.ok.01"),
            chat.textKeys
        )
        assertTrue(chat.state.messages.filter { it.sender == MessageSender.PCHAN }.all { it.isRead })
    }

    @Test
    fun inlineRequestWhileASendIsBeingPublishedStillWaitsForItsFreeReply() = runTest(dispatcher) {
        val inline = UnconfinedTestDispatcher(testScheduler)
        val chat = createChat(timings = ChatTimings(0, 0, 0), worker = inline)
        var accepted = false
        backgroundScope.launch(inline) {
            chat.uiState.collect { state ->
                if (!accepted && state.communicationState == CommunicationState.READING) {
                    accepted = chat.requestDialogue(linearDialogue(SendText("narrative.inline"), End))
                    assertTrue(accepted)
                    assertEquals(ChatInputMode.Stickers, chat.state.inputMode)
                }
            }
        }
        assertTrue(chat.sendSticker("ok", stickers))
        runCurrent()
        assertTrue(accepted)
        assertEquals(listOf("dialogue.demo.reply.ok.01", "narrative.inline"), chat.textKeys)
        assertTrue(chat.state.messages.single { it.sender == MessageSender.PCHAN }.isRead)
        assertEquals(ChatInputMode.Stickers, chat.state.inputMode)
        assertEquals(CommunicationState.AVAILABLE, chat.state.communicationState)
    }

    @Test
    fun clearingAtAnyNarrativeStagePreventsLateMessagesAndStateRestoration() = runTest(dispatcher) {
        val stages: List<TestScope.(ChatViewModel) -> Unit> = listOf(
            { chat ->
                assertTrue(chat.sendSticker("ok", stickers))
                tick(300)
                assertTrue(chat.requestDialogue(linear))
            },
            { chat ->
                assertTrue(chat.sendSticker("love", stickers))
                tick(1_800)
                assertTrue(chat.requestDialogue(linear))
            },
            { chat ->
                assertTrue(chat.requestDialogue(linear))
                tick(300)
            },
            { chat ->
                assertTrue(chat.requestDialogue(singleChoice()))
                runCurrent()
            },
            { chat ->
                assertTrue(chat.requestDialogue(singleChoice()))
                runCurrent()
                assertTrue(chat.chooseDialogueOption(chat.prompt.promptId, "go"))
                tick(300)
            },
            { chat ->
                assertTrue(chat.requestDialogue(singleChoice(SendText("late"), End)))
                runCurrent()
                assertTrue(chat.chooseDialogueOption(chat.prompt.promptId, "go"))
                tick(800)
            }
        )
        for (stage in stages) {
            val chat = createChat()
            stage(chat)
            val beforeClear = chat.state
            val prompt = (beforeClear.inputMode as? ChatInputMode.Choices)?.promptId ?: "closed"
            store.clear()
            tick(10_000)
            assertEquals(beforeClear, chat.state)
            assertRejectedWithoutEffects(chat) {
                assertFalse(chat.requestDialogue(linear))
                assertFalse(chat.sendSticker("ok", stickers))
                assertFalse(chat.chooseDialogueOption(prompt, "go"))
            }
        }
    }

    @Test
    fun aRecoverableFailurePreservesPostedMessagesAndReleasesAdmission() = runTest(dispatcher) {
        val chat = createChat(id = { index ->
            if (index == 2) throw IOException("Temporary fixture failure")
            "test-$index"
        })
        assertTrue(chat.requestDialogue(linear))
        tick(700)
        assertEquals(listOf("narrative.start"), chat.textKeys)
        assertEquals(ChatInputMode.Stickers, chat.state.inputMode)
        assertTrue(chat.sendSticker("ok", stickers))
        tick(1_600)
        assertEquals(listOf("narrative.start", "dialogue.demo.reply.ok.01"), chat.textKeys)
    }

    @Test
    fun aRecoverableChoiceWriteFailureReleasesThePromptSoTheDialogueCanBeRetried() = runTest(dispatcher) {
        val chat = createChat(id = { index ->
            if (index == 1) throw IOException("Temporary choice write failure")
            "test-$index"
        })
        val graph = singleChoice()
        assertTrue(chat.requestDialogue(graph))
        runCurrent()
        val failedPrompt = chat.prompt.promptId
        assertFalse(chat.chooseDialogueOption(failedPrompt, "go"))
        runCurrent()
        assertEquals(ChatInputMode.Stickers, chat.state.inputMode)
        assertTrue(chat.textKeys.isEmpty())

        assertTrue(chat.requestDialogue(graph))
        runCurrent()
        assertFalse(chat.chooseDialogueOption(failedPrompt, "go"))
        assertTrue(chat.chooseDialogueOption(chat.prompt.promptId, "go"))
        tick(1_600)
        assertEquals(listOf("narrative.choice"), chat.textKeys)
        assertEquals(ChatInputMode.Stickers, chat.state.inputMode)
    }

    private fun createChat(
        timings: ChatTimings = ChatTimings(),
        worker: CoroutineDispatcher = dispatcher,
        now: () -> Long = { dispatcher.scheduler.currentTime },
        id: (Int) -> String = { "test-$it" }
    ): ChatViewModel {
        val factory = viewModelFactory {
            initializer {
                ChatViewModel(
                    timings = timings,
                    now = {
                        clockCalls++
                        now()
                    },
                    newMessageId = { id(++idCalls) },
                    dispatcher = worker
                )
            }
        }
        return ViewModelProvider(store, factory)[ChatViewModel::class.java]
    }

    private fun assertRejectedWithoutEffects(chat: ChatViewModel, action: () -> Unit) {
        val before = chat.state
        val calls = idCalls to clockCalls
        action()
        assertEquals(before, chat.state)
        assertEquals(calls, idCalls to clockCalls)
    }

    private fun linearDialogue(vararg actions: DialogueAction) =
        DialogueDefinition("linear", "entry", mapOf("entry" to DialogueNode(actions.toList())))

    private fun singleChoice(vararg ending: DialogueAction = arrayOf(End)): DialogueDefinition = DialogueDefinition(
        "single-choice", "entry",
        mapOf(
            "entry" to DialogueNode(listOf(ShowChoices(listOf(DialogueOption("go", "narrative.choice", "end"))))),
            "end" to DialogueNode(ending.toList())
        )
    )

    private fun TestScope.tick(milliseconds: Long) {
        advanceTimeBy(milliseconds)
        runCurrent()
    }

    private val ChatViewModel.state get() = uiState.value
    private val ChatViewModel.prompt get() = state.inputMode as ChatInputMode.Choices
    private val ChatViewModel.textKeys
        get() = state.messages.drop(3).mapNotNull { (it.content as? MessageContent.Text)?.textKey }
}
