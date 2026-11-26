package com.euforia.jine

import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.ViewModelStore
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.euforia.jine.data.model.MessageContent
import com.euforia.jine.data.model.MessageSender
import com.euforia.jine.ui.screens.ChatTimings
import com.euforia.jine.ui.screens.ChatViewModel
import com.euforia.jine.ui.screens.CommunicationState
import com.euforia.jine.ui.screens.GENERIC_DEMO_REPLY
import com.euforia.jine.ui.screens.StickerBatch
import com.euforia.jine.ui.screens.StickerBatchType
import com.euforia.jine.ui.screens.demoReplies
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.io.IOException

@OptIn(ExperimentalCoroutinesApi::class)
class ChatViewModelTest {
    private val dispatcher = StandardTestDispatcher()
    private val store = ViewModelStore()
    private val validIds = setOf("ok", "saikouka", "pien", "waritodoudemoii", "gomen", "bujishibou", "love", "sorena")
    private val batches = mutableListOf<StickerBatch>()
    private val epoch = 1_700_000_000_000L

    @Before
    fun setMainDispatcher() {
        Dispatchers.setMain(dispatcher)
    }

    @After
    fun clearViewModel() {
        try {
            store.clear()
            dispatcher.scheduler.runCurrent()
        } finally {
            Dispatchers.resetMain()
        }
    }

    @Test
    fun initialMessagesAndUnconfirmedSelectionDoNotScheduleCommunication() = runTest(dispatcher) {
        val chat = createChat()
        val initial = chat.state.messages
        assertEquals(listOf("preview_greeting", "preview_question", "preview_long_message"), initial.map { it.id })
        assertEquals(
            listOf("greeting", "question", "long_message").map { MessageContent.Text("dialogue.preview.$it") },
            initial.map { it.content }
        )
        assertTrue(initial.all { it.sender == MessageSender.AME && it.sentAt == epoch && !it.isRead })
        assertEquals(CommunicationState.AVAILABLE, chat.state.communicationState)
        assertNull(chat.state.selectedStickerId)

        assertFalse(chat.onStickerPressed("unknown", validIds))
        assertTrue(chat.onStickerPressed("ok", validIds))
        assertEquals("ok", chat.state.selectedStickerId)
        assertTrue(chat.onStickerPressed("love", validIds))
        assertEquals("love", chat.state.selectedStickerId)
        chat.reconcileSelection(setOf("ok"))
        assertNull(chat.state.selectedStickerId)

        advanceUntilIdle()
        assertEquals(0L, testScheduler.currentTime)
        assertEquals(initial, chat.state.messages)
        assertEquals(CommunicationState.AVAILABLE, chat.state.communicationState)
    }

    @Test
    fun staggeredReadsProduceOneRepeatedReactionAfterTheLastRead() = runTest(dispatcher) {
        val chat = createChat()
        confirm(chat, "love")
        val first = chat.outgoing.single()
        assertEquals(CommunicationState.READING, chat.state.communicationState)
        assertTrue(chat.state.canSendSticker)
        assertNull(first.readAt)

        tick(400)
        confirm(chat, "love")
        val second = chat.outgoing.last()
        assertNotEquals(first.id, second.id)
        assertEquals(epoch + 400, second.sentAt)
        tick(250)
        assertEquals(listOf(epoch + 650, null), chat.outgoing.map { it.readAt })

        tick(250)
        confirm(chat, "love")
        val third = chat.outgoing.last()
        tick(150)
        assertEquals(listOf(epoch + 650, epoch + 1_050, null), chat.outgoing.map { it.readAt })
        tick(500)
        assertEquals(epoch + 1_550, chat.outgoing.last().readAt)
        tick(949)
        assertTrue(chat.replyKeys.isEmpty())
        assertTrue(batches.isEmpty())
        tick(1)

        assertEquals(listOf(first.id, second.id, third.id), batches.single().messages.map { it.id })
        assertEquals(chat.outgoing, batches.single().messages)
        assertEquals(StickerBatchType.REPEATED, batches.single().type)
        assertEquals(listOf("dialogue.demo.batch.repeated.love.01"), chat.replyKeys)
        assertEquals(listOf(epoch + 2_500), chat.replies.map { it.sentAt })
        assertEquals(CommunicationState.AVAILABLE, chat.state.communicationState)
    }

    @Test
    fun confirmationDuringPreparationRestartsTheFullIntervalAfterItsRead() = runTest(dispatcher) {
        val chat = createChat()
        confirm(chat, "love")
        tick(650)
        assertTrue(chat.outgoing.single().isRead)
        assertEquals(CommunicationState.READING, chat.state.communicationState)
        tick(550)
        confirm(chat, "ok")
        tick(400)
        assertTrue(batches.isEmpty())
        assertTrue(chat.replyKeys.isEmpty())
        assertNull(chat.outgoing.last().readAt)
        tick(250)
        assertEquals(epoch + 1_850, chat.outgoing.last().readAt)
        tick(949)
        assertTrue(batches.isEmpty())
        tick(1)

        assertEquals(listOf("love", "ok"), batches.single().stickerIds)
        assertEquals(StickerBatchType.MIXED, batches.single().type)
        assertEquals(listOf("dialogue.demo.batch.mixed.01"), chat.replyKeys)
        assertEquals(epoch + 2_800, chat.replies.single().sentAt)
    }

    @Test
    fun selectionAndInvalidPressesDoNotRestartPreparationOrClearSelectionAcrossAReaction() = runTest(dispatcher) {
        val chat = createChat()
        assertTrue(chat.state.canSendSticker)
        confirm(chat, "love")
        tick(1_200)
        assertTrue(chat.onStickerPressed("ok", validIds))
        assertFalse(chat.onStickerPressed("unknown", validIds))
        assertTrue(batches.isEmpty())

        tick(400)
        assertEquals(CommunicationState.RESPONDING, chat.state.communicationState)
        assertTrue(chat.state.canSendSticker)
        assertEquals("ok", chat.state.selectedStickerId)
        assertEquals(listOf("dialogue.demo.reply.love.01"), chat.replyKeys)
        assertEquals(StickerBatchType.SINGLE, batches.single().type)
        tick(699)
        assertEquals(1, chat.replies.size)
        tick(1)
        assertEquals(listOf("dialogue.demo.reply.love.01", "dialogue.demo.reply.love.02"), chat.replyKeys)
        assertEquals(listOf(epoch + 1_600, epoch + 2_300), chat.replies.map { it.sentAt })
        assertEquals(CommunicationState.AVAILABLE, chat.state.communicationState)
        assertEquals("ok", chat.state.selectedStickerId)
    }

    @Test
    fun newSendDuringAReactionIsReadIndependentlyAndWaitsForTheNextPreparation() = runTest(dispatcher) {
        val chat = createChat()
        confirm(chat, "love")
        tick(1_600)
        tick(200)
        confirm(chat, "ok")
        assertEquals(CommunicationState.RESPONDING, chat.state.communicationState)
        assertNull(chat.outgoing.last().readAt)
        tick(500)
        assertEquals(2, chat.replies.size)
        assertEquals(CommunicationState.READING, chat.state.communicationState)
        assertNull(chat.outgoing.last().readAt)
        tick(150)
        assertEquals(epoch + 2_450, chat.outgoing.last().readAt)
        tick(949)
        assertEquals(1, batches.size)
        tick(1)

        assertEquals(listOf(listOf("love"), listOf("ok")), batches.map { it.stickerIds })
        assertEquals(
            listOf("dialogue.demo.reply.love.01", "dialogue.demo.reply.love.02", "dialogue.demo.reply.ok.01"),
            chat.replyKeys
        )
        assertEquals(listOf(epoch + 1_600, epoch + 2_300, epoch + 3_400), chat.replies.map { it.sentAt })
        assertEquals(listOf("test-1", "test-2", "test-3", "test-4", "test-5"), chat.state.messages.drop(3).map { it.id })
        assertTrue(chat.replies.all { !it.isRead })
        assertEquals(CommunicationState.AVAILABLE, chat.state.communicationState)

        confirm(chat, "pien")
        assertEquals(CommunicationState.READING, chat.state.communicationState)
        advanceUntilIdle()
        assertEquals("dialogue.demo.reply.pien.01", chat.replyKeys.last())
        assertEquals(CommunicationState.AVAILABLE, chat.state.communicationState)
    }

    @Test
    fun nextBatchAlreadyReadStillPreparesAfterTheActiveReactionAndCanGrow() = runTest(dispatcher) {
        val chat = createChat()
        confirm(chat, "love")
        tick(1_600)
        confirm(chat, "ok")
        tick(25)
        confirm(chat, "gomen")
        tick(650)
        assertTrue(chat.outgoing.all { it.isRead })
        assertEquals(CommunicationState.RESPONDING, chat.state.communicationState)
        tick(25)
        assertEquals(2, chat.replies.size)
        assertEquals(CommunicationState.READING, chat.state.communicationState)

        tick(350)
        confirm(chat, "pien")
        tick(600)
        assertEquals(1, batches.size)
        tick(50)
        assertEquals(epoch + 3_300, chat.outgoing.last().readAt)
        tick(949)
        assertEquals(1, batches.size)
        tick(1)
        assertEquals(listOf(listOf("love"), listOf("ok", "gomen", "pien")), batches.map { it.stickerIds })
        assertEquals(listOf(epoch + 1_600, epoch + 2_300, epoch + 4_250), chat.replies.map { it.sentAt })
        assertEquals("dialogue.demo.batch.mixed.01", chat.replyKeys.last())
        assertEquals(CommunicationState.AVAILABLE, chat.state.communicationState)
    }

    @Test
    fun aReadNextBatchCannotReuseTimeSpentWaitingForTheActiveReaction() = runTest(dispatcher) {
        val chat = createChat()
        confirm(chat, "love")
        tick(1_600)
        confirm(chat, "ok")
        tick(650)
        assertTrue(chat.outgoing.last().isRead)
        assertEquals(CommunicationState.RESPONDING, chat.state.communicationState)
        tick(50)
        assertEquals(CommunicationState.READING, chat.state.communicationState)
        tick(949)
        assertEquals(2, chat.replies.size)
        tick(1)
        assertEquals(epoch + 3_250, chat.replies.last().sentAt)
        assertEquals(CommunicationState.AVAILABLE, chat.state.communicationState)
    }

    @Test
    fun continuousSendsAreKeptInOrderAndProduceASingleSpamReaction() = runTest(dispatcher) {
        val chat = createChat(timings = ChatTimings(spamThreshold = 3))
        val ids = List(20) { if (it % 2 == 0) "ok" else "love" }
        for (id in ids) {
            confirm(chat, id)
            tick(500)
            assertTrue(chat.replyKeys.isEmpty())
        }
        tick(150)
        assertTrue(chat.outgoing.all { it.isRead })
        tick(949)
        assertTrue(batches.isEmpty())
        tick(1)
        assertEquals(ids, batches.single().stickerIds)
        assertEquals(chat.outgoing, batches.single().messages)
        assertEquals(ids.size, chat.outgoing.map { it.id }.distinct().size)
        assertEquals(StickerBatchType.SPAM, batches.single().type)
        assertEquals(listOf("dialogue.demo.batch.spam.01"), chat.replyKeys)
        assertEquals(CommunicationState.AVAILABLE, chat.state.communicationState)
    }

    @Test
    fun aConfirmationProcessedAtTheDeadlineBeforeEmissionStillJoinsTheOpenBatch() = runTest(dispatcher) {
        val chat = createChat()
        confirm(chat, "love")
        tick(650)
        advanceTimeBy(950)
        assertTrue(batches.isEmpty())
        confirm(chat, "ok")
        assertTrue(batches.isEmpty())
        tick(1_600)
        assertEquals(listOf("love", "ok"), batches.single().stickerIds)
        assertEquals(epoch + 3_200, chat.replies.single().sentAt)
    }

    @Test
    fun zeroDelayJobsCanCompleteInlineAndTheNextSendStartsAnotherCoordinator() = runTest(dispatcher) {
        val chat = createChat(
            timings = ChatTimings(readMs = 0, replyPreparationMs = 0, replyIntervalMs = 0),
            workerDispatcher = UnconfinedTestDispatcher(testScheduler)
        )
        confirm(chat, "love")
        assertEquals(CommunicationState.AVAILABLE, chat.state.communicationState)
        assertEquals(epoch, chat.outgoing.single().readAt)
        confirm(chat, "ok")
        assertEquals(listOf(listOf("love"), listOf("ok")), batches.map { it.stickerIds })
        assertEquals(listOf("test-1", "test-2", "test-3", "test-4", "test-5"), chat.state.messages.drop(3).map { it.id })
        assertTrue(chat.outgoing.all { it.isRead })
        assertEquals(3, chat.replies.size)
        assertEquals(CommunicationState.AVAILABLE, chat.state.communicationState)
    }

    @Test
    fun emptyBlankAndRecoverableRepliesFallBackOncePerBatchAndKeepAcceptingSends() = runTest(dispatcher) {
        val chat = createChat(repliesFor = { batch ->
            when (batch.stickerIds.single()) {
                "ok" -> listOf(" ", "")
                "love" -> throw IOException("Reply content unavailable")
                "pien" -> listOf("", "dialogue.demo.reply.pien.01", " ")
                else -> emptyList()
            }
        })
        for (id in listOf("ok", "love", "new", "pien")) {
            confirm(chat, id, validIds + "new")
            advanceUntilIdle()
            assertEquals(CommunicationState.AVAILABLE, chat.state.communicationState)
        }
        assertEquals(4, batches.size)
        assertTrue(chat.outgoing.all { it.isRead })
        assertEquals(List(3) { GENERIC_DEMO_REPLY } + "dialogue.demo.reply.pien.01", chat.replyKeys)
    }

    @Test
    fun backwardWallClockDoesNotMoveMessagesOrOverwriteReadAt() = runTest(dispatcher) {
        var wallTime = epoch
        val chat = createChat(now = { wallTime })
        confirm(chat, "ok")
        val first = chat.outgoing.single()
        wallTime -= 5_000
        tick(300)
        confirm(chat, "pien")
        val second = chat.outgoing.last()
        assertTrue(second.sentAt < first.sentAt)
        tick(350)
        assertEquals(first.sentAt, chat.outgoing.first().readAt)
        wallTime += 10_000
        advanceUntilIdle()
        assertEquals(listOf(first.id, second.id), chat.outgoing.map { it.id })
        assertEquals(first.sentAt, chat.outgoing.first().readAt)
        assertEquals(wallTime, chat.outgoing.last().readAt)
        assertEquals(1_900L, testScheduler.currentTime)
    }

    @Test
    fun clearingDuringReadingCancelsAllPendingWork() = runTest(dispatcher) {
        val chat = createChat()
        confirm(chat, "ok")
        tick(200)
        assertClearingStopsChat(chat)
        assertTrue(batches.isEmpty())
    }

    @Test
    fun clearingDuringPreparationCancelsTheReaction() = runTest(dispatcher) {
        val chat = createChat()
        confirm(chat, "ok")
        tick(1_200)
        assertTrue(chat.outgoing.single().isRead)
        assertTrue(chat.onStickerPressed("love", validIds))
        assertClearingStopsChat(chat)
        assertTrue(batches.isEmpty())
    }

    @Test
    fun clearingBetweenReplyTextsCancelsTheRestAndTheNextBatchRead() = runTest(dispatcher) {
        val chat = createChat()
        confirm(chat, "love")
        tick(1_800)
        assertEquals(listOf("dialogue.demo.reply.love.01"), chat.replyKeys)
        assertEquals(CommunicationState.RESPONDING, chat.state.communicationState)
        confirm(chat, "ok")
        assertClearingStopsChat(chat)
        assertEquals(1, batches.size)
        assertNull(chat.outgoing.last().readAt)
    }

    private fun TestScope.assertClearingStopsChat(chat: ChatViewModel) {
        val beforeClear = chat.state
        store.clear()
        advanceUntilIdle()
        assertFalse(chat.onStickerPressed("ok", validIds))
        chat.reconcileSelection(emptySet())
        assertEquals(beforeClear, chat.state)
    }

    private fun createChat(
        now: () -> Long = { epoch + dispatcher.scheduler.currentTime },
        timings: ChatTimings = ChatTimings(),
        workerDispatcher: CoroutineDispatcher = dispatcher,
        repliesFor: (StickerBatch) -> List<String> = ::demoReplies
    ): ChatViewModel {
        var nextId = 0
        val factory = viewModelFactory {
            initializer {
                ChatViewModel(timings, now, { "test-${++nextId}" }, {
                    batches.add(it)
                    repliesFor(it)
                }, workerDispatcher)
            }
        }
        return ViewModelProvider(store, factory)[ChatViewModel::class.java]
    }

    private fun confirm(chat: ChatViewModel, id: String, allowed: Set<String> = validIds) {
        assertTrue(chat.onStickerPressed(id, allowed))
        assertEquals(id, chat.state.selectedStickerId)
        assertTrue(chat.onStickerPressed(id, allowed))
        assertNull(chat.state.selectedStickerId)
        dispatcher.scheduler.runCurrent()
    }

    private fun TestScope.tick(ms: Long) {
        advanceTimeBy(ms)
        runCurrent()
    }

    private val ChatViewModel.state get() = uiState.value
    private val ChatViewModel.outgoing get() = state.messages.filter { it.sender == MessageSender.PCHAN }
    private val ChatViewModel.replies get() = state.messages.drop(3).filter { it.sender == MessageSender.AME }
    private val ChatViewModel.replyKeys get() = replies.map { (it.content as MessageContent.Text).textKey }
}
