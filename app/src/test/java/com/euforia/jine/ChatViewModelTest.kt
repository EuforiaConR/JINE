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
import com.euforia.jine.ui.screens.demoReplies
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
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
    private val timings = ChatTimings(admissionMs = 1_000, readMs = 700, replyPreparationMs = 800, replyIntervalMs = 700)
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
    fun repeatedStickerSendsAreReadIndividuallyAndDoNotExtendTheBatch() = runTest(dispatcher) {
        val chat = createChat()
        confirm(chat, "ok")
        val first = chat.outgoing.single()
        assertEquals(CommunicationState.READING, chat.state.communicationState)
        assertTrue(chat.state.canSendSticker)
        assertNull(chat.state.selectedStickerId)
        assertNull(first.readAt)

        tick(350)
        confirm(chat, "ok")
        val second = chat.outgoing.last()
        assertEquals(first.content, second.content)
        assertNotEquals(first.id, second.id)
        assertEquals(epoch + 350, second.sentAt)
        assertNull(second.readAt)

        tick(349)
        assertEquals(listOf(first, second), chat.outgoing)
        tick(1)
        assertEquals(listOf(first.copy(readAt = epoch + 700), second), chat.outgoing)

        tick(299)
        assertTrue(chat.onStickerPressed("love", validIds))
        assertEquals("love", chat.state.selectedStickerId)
        tick(1)
        assertEquals(CommunicationState.RESPONDING, chat.state.communicationState)
        assertFalse(chat.state.canSendSticker)
        assertNull(chat.state.selectedStickerId)
        val blocked = chat.state
        assertFalse(chat.onStickerPressed("love", validIds))
        assertFalse(chat.onStickerPressed("love", validIds))
        assertEquals(blocked, chat.state)

        tick(49)
        assertNull(chat.outgoing.last().readAt)
        assertTrue(chat.replyKeys.isEmpty())
        tick(1)
        assertEquals(listOf(first.id, second.id), chat.outgoing.map { it.id })
        assertEquals(epoch + 1_050, chat.outgoing.last().readAt)
        assertTrue(chat.replyKeys.isEmpty())
    }

    @Test
    fun repliesWaitForAllReadsAndRunInFifoWithoutUnlockingBetweenSequences() = runTest(dispatcher) {
        val chat = createChat()
        confirm(chat, "love")
        tick(350)
        confirm(chat, "ok")
        tick(650)
        assertEquals(CommunicationState.RESPONDING, chat.state.communicationState)
        tick(50)
        assertTrue(chat.outgoing.all { it.isRead })

        tick(799)
        assertTrue(chat.replyKeys.isEmpty())
        tick(1)
        assertEquals(listOf("dialogue.demo.reply.love.01"), chat.replyKeys)
        assertFalse(chat.state.canSendSticker)
        tick(699)
        assertEquals(1, chat.replyKeys.size)
        tick(1)
        assertEquals(2, chat.replyKeys.size)
        assertFalse(chat.state.canSendSticker)
        assertFalse(chat.onStickerPressed("ok", validIds))
        tick(799)
        assertEquals(2, chat.replyKeys.size)
        tick(1)
        assertEquals(
            listOf("dialogue.demo.reply.love.01", "dialogue.demo.reply.love.02", "dialogue.demo.reply.ok.01"),
            chat.replyKeys
        )
        val replies = chat.state.messages.takeLast(3)
        assertEquals(listOf(epoch + 1_850, epoch + 2_550, epoch + 3_350), replies.map { it.sentAt })
        assertTrue(replies.all { it.sender == MessageSender.AME && !it.isRead })
        assertEquals(chat.state.messages.size, chat.state.messages.map { it.id }.distinct().size)
        assertEquals(CommunicationState.AVAILABLE, chat.state.communicationState)
        assertNull(chat.state.selectedStickerId)

        confirm(chat, "pien")
        assertEquals(CommunicationState.READING, chat.state.communicationState)
        advanceUntilIdle()
        assertEquals(4, chat.replyKeys.size)
        assertEquals("dialogue.demo.reply.pien.01", chat.replyKeys.last())
        assertEquals(CommunicationState.AVAILABLE, chat.state.communicationState)
    }

    @Test
    fun demoRepliesCoverEveryStickerAndLoveHasTwoTexts() {
        for (id in validIds) {
            assertEquals("dialogue.demo.reply.$id.01", demoReplies(id).first())
            assertEquals(if (id == "love") 2 else 1, demoReplies(id).size)
        }
        assertTrue(demoReplies("unknown").isEmpty())
    }

    @Test
    fun emptyMissingAndRecoverableRepliesEachGetOneGenericText() = runTest(dispatcher) {
        val chat = createChat(repliesFor = { id ->
            when (id) {
                "ok" -> listOf(" ")
                "love" -> throw IOException("Reply content unavailable")
                else -> demoReplies(id)
            }
        })
        confirm(chat, "ok")
        confirm(chat, "love")
        confirm(chat, "new", validIds + "new")
        advanceUntilIdle()
        assertEquals(3, chat.outgoing.size)
        assertEquals(List(3) { GENERIC_DEMO_REPLY }, chat.replyKeys)
        assertEquals(CommunicationState.AVAILABLE, chat.state.communicationState)
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
        tick(400)
        assertEquals(first.sentAt, chat.outgoing.first().readAt)
        wallTime += 10_000
        advanceUntilIdle()
        assertEquals(listOf(first.id, second.id), chat.outgoing.map { it.id })
        assertEquals(first.sentAt, chat.outgoing.first().readAt)
        assertEquals(wallTime, chat.outgoing.last().readAt)
    }

    @Test
    fun clearingDuringReadingCancelsAllPendingWork() = runTest(dispatcher) {
        val chat = createChat()
        confirm(chat, "ok")
        tick(200)
        val beforeClear = chat.state
        store.clear()
        advanceUntilIdle()
        assertEquals(beforeClear, chat.state)
        assertFalse(chat.onStickerPressed("ok", validIds))
    }

    @Test
    fun clearingBetweenReplyTextsCancelsTheRestOfTheBatch() = runTest(dispatcher) {
        val chat = createChat()
        confirm(chat, "love")
        confirm(chat, "ok")
        tick(1_800)
        assertEquals(listOf("dialogue.demo.reply.love.01"), chat.replyKeys)
        assertEquals(CommunicationState.RESPONDING, chat.state.communicationState)
        val beforeClear = chat.state
        store.clear()
        advanceUntilIdle()
        assertEquals(beforeClear, chat.state)
    }

    private fun createChat(
        now: () -> Long = { epoch + dispatcher.scheduler.currentTime },
        repliesFor: (String) -> List<String> = ::demoReplies
    ): ChatViewModel {
        var nextId = 0
        val factory = viewModelFactory {
            initializer { ChatViewModel(timings, now, { "test-${++nextId}" }, repliesFor) }
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
    private val ChatViewModel.replyKeys
        get() = state.messages.drop(3).filter { it.sender == MessageSender.AME }
            .map { (it.content as MessageContent.Text).textKey }
}
