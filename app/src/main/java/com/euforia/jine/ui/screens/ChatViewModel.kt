package com.euforia.jine.ui.screens

import androidx.annotation.MainThread
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.euforia.jine.data.model.Message
import com.euforia.jine.data.model.MessageContent
import com.euforia.jine.data.model.MessageSender
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.joinAll
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull
import java.io.IOException
import java.util.UUID
import java.util.logging.Level
import java.util.logging.Logger

internal data class ChatTimings(
    val readMs: Long = 650,
    val replyPreparationMs: Long = 950,
    val replyIntervalMs: Long = 700,
    val spamThreshold: Int = DEFAULT_SPAM_THRESHOLD
) {
    init {
        require(readMs >= 0 && replyPreparationMs >= 0 && replyIntervalMs >= 0)
        require(spamThreshold >= 2)
    }
}

internal class ChatViewModel(
    private val timings: ChatTimings = ChatTimings(),
    private val now: () -> Long = System::currentTimeMillis,
    private val newMessageId: () -> String = { UUID.randomUUID().toString() },
    private val repliesFor: (StickerBatch) -> List<String> = ::demoReplies,
    private val dispatcher: CoroutineDispatcher = Dispatchers.Main.immediate
) : ViewModel() {
    private class PendingSend(var message: Message, val reading: Job)

    private val _uiState = MutableStateFlow(ChatUiState(messages = initialMessages(now())))
    val uiState = _uiState.asStateFlow()

    // UI events and coroutine continuations share the main thread in production.
    private val pendingSends = linkedMapOf<String, PendingSend>()
    private var reactionCoordinator: Job? = null
    private var preparationInvalidated: CompletableDeferred<Unit>? = null

    @MainThread
    fun reconcileSelection(validStickerIds: Set<String>) {
        if (!viewModelScope.isActive) return
        val state = _uiState.value
        val selectedId = state.selectedStickerId
        if (selectedId != null && selectedId !in validStickerIds) {
            _uiState.value = state.copy(selectedStickerId = null)
        }
    }

    @MainThread
    fun onStickerPressed(stickerId: String, validStickerIds: Set<String>): Boolean {
        if (!viewModelScope.isActive) return false
        val state = _uiState.value
        val result = handleStickerPress(state.selectedStickerId, stickerId, validStickerIds, newMessageId, now)
            ?: return false
        val message = result.sentMessage
        if (message == null) {
            _uiState.value = state.copy(selectedStickerId = result.selectedStickerId)
            return true
        }

        val reading = viewModelScope.launch(dispatcher, start = CoroutineStart.LAZY) {
            delay(timings.readMs)
            ensureActive()
            markRead(message.id)
        }
        val invalidatedWait = preparationInvalidated
        pendingSends[message.id] = PendingSend(message, reading)
        val newCoordinator = if (reactionCoordinator == null) {
            viewModelScope.launch(dispatcher, start = CoroutineStart.LAZY) { processBatches() }
                .also { reactionCoordinator = it }
        } else {
            null
        }

        // Register and publish the send before starting either job, including with zero delays.
        _uiState.value = state.copy(
            messages = state.messages + message,
            selectedStickerId = null,
            communicationState = if (state.communicationState == CommunicationState.RESPONDING) {
                CommunicationState.RESPONDING
            } else {
                CommunicationState.READING
            }
        )
        invalidatedWait?.complete(Unit)
        reading.start()
        newCoordinator?.start()
        return true
    }

    private fun markRead(messageId: String) {
        val send = pendingSends[messageId] ?: return
        val message = send.message
        if (message.isRead) return
        val read = message.copy(readAt = maxOf(message.sentAt, now()))
        send.message = read
        val state = _uiState.value
        _uiState.value = state.copy(messages = state.messages.map { if (it.id == messageId) read else it })
    }

    private suspend fun processBatches() {
        try {
            while (pendingSends.isNotEmpty()) {
                _uiState.value = _uiState.value.copy(communicationState = CommunicationState.READING)
                val batch = awaitPreparedBatch()
                val keys = replyKeys(batch)
                currentCoroutineContext().ensureActive()

                // Close the batch and publish its first reply without suspending.
                pendingSends.clear()
                publishReply(keys.first())
                for (textKey in keys.drop(1)) {
                    delay(timings.replyIntervalMs)
                    currentCoroutineContext().ensureActive()
                    publishReply(textKey)
                }
            }
        } finally {
            reactionCoordinator = null
        }
        currentCoroutineContext().ensureActive()
        _uiState.value = _uiState.value.copy(communicationState = CommunicationState.AVAILABLE)
    }

    private suspend fun awaitPreparedBatch(): StickerBatch {
        while (true) {
            val unread = pendingSends.values.filter { !it.message.isRead }
            if (unread.isNotEmpty()) {
                unread.map { it.reading }.joinAll()
                continue
            }

            val preparedCount = pendingSends.size
            val invalidated = CompletableDeferred<Unit>()
            preparationInvalidated = invalidated
            try {
                withTimeoutOrNull(timings.replyPreparationMs) { invalidated.await() }
            } finally {
                preparationInvalidated = null
            }
            currentCoroutineContext().ensureActive()
            // Only this coordinator removes sends. A changed count also detects arrivals at the timeout.
            if (pendingSends.size == preparedCount) {
                return StickerBatch(pendingSends.values.map { it.message }, timings.spamThreshold)
            }
        }
    }

    private fun publishReply(textKey: String) {
        val reply = Message(
            id = newMessageId(),
            sender = MessageSender.AME,
            content = MessageContent.Text(textKey),
            sentAt = now()
        )
        val state = _uiState.value
        _uiState.value = state.copy(
            messages = state.messages + reply,
            communicationState = CommunicationState.RESPONDING
        )
    }

    private fun replyKeys(batch: StickerBatch): List<String> {
        val keys = try {
            repliesFor(batch).filter { it.isNotBlank() }
        } catch (error: IOException) {
            logger.log(Level.WARNING, "Could not load a reply for a ${batch.type} batch", error)
            return listOf(GENERIC_DEMO_REPLY)
        }
        return keys.ifEmpty {
            logger.warning("No reply for a ${batch.type} batch")
            listOf(GENERIC_DEMO_REPLY)
        }
    }

    private companion object {
        val logger: Logger = Logger.getLogger(ChatViewModel::class.java.name)
    }
}
