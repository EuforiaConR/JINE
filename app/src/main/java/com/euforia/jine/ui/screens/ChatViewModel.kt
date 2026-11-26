package com.euforia.jine.ui.screens

import androidx.annotation.MainThread
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.euforia.jine.data.model.Message
import com.euforia.jine.data.model.MessageContent
import com.euforia.jine.data.model.MessageSender
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.joinAll
import kotlinx.coroutines.launch
import java.io.IOException
import java.util.UUID
import java.util.logging.Level
import java.util.logging.Logger

internal data class ChatTimings(
    val admissionMs: Long = 1_000,
    val readMs: Long = 650,
    val replyPreparationMs: Long = 950,
    val replyIntervalMs: Long = 700
)

internal class ChatViewModel(
    private val timings: ChatTimings = ChatTimings(),
    private val now: () -> Long = System::currentTimeMillis,
    private val newMessageId: () -> String = { UUID.randomUUID().toString() },
    private val repliesFor: (String) -> List<String> = ::demoReplies,
    private val dispatcher: CoroutineDispatcher = Dispatchers.Main.immediate
) : ViewModel() {
    private data class PendingSend(val messageId: String, val stickerId: String, val reading: Job)

    private val _uiState = MutableStateFlow(ChatUiState(messages = initialMessages(now())))
    val uiState = _uiState.asStateFlow()

    // UI events and coroutine continuations share the main thread in production.
    private val pendingSends = mutableListOf<PendingSend>()

    @MainThread
    fun reconcileSelection(validStickerIds: Set<String>) {
        val state = _uiState.value
        val selectedId = state.selectedStickerId
        if (selectedId != null && selectedId !in validStickerIds) {
            _uiState.value = state.copy(selectedStickerId = null)
        }
    }

    @MainThread
    fun onStickerPressed(stickerId: String, validStickerIds: Set<String>): Boolean {
        val state = _uiState.value
        if (!state.canSendSticker || !viewModelScope.isActive) return false
        val result = handleStickerPress(state.selectedStickerId, stickerId, validStickerIds, newMessageId, now)
            ?: return false
        val message = result.sentMessage
        if (message == null) {
            _uiState.value = state.copy(selectedStickerId = result.selectedStickerId)
            return true
        }

        val reading = viewModelScope.launch(dispatcher, start = CoroutineStart.LAZY) {
            delay(timings.readMs)
            markRead(message.id)
        }
        pendingSends.add(PendingSend(message.id, stickerId, reading))
        _uiState.value = state.copy(
            messages = state.messages + message,
            selectedStickerId = null,
            communicationState = CommunicationState.READING
        )
        reading.start()

        if (state.communicationState == CommunicationState.AVAILABLE) {
            viewModelScope.launch(dispatcher) { processBatch() }
        }
        return true
    }

    private fun markRead(messageId: String) {
        val state = _uiState.value
        val readAt = now()
        _uiState.value = state.copy(messages = state.messages.map { message ->
            if (message.id == messageId && message.sender == MessageSender.PCHAN && !message.isRead) {
                message.copy(readAt = maxOf(message.sentAt, readAt))
            } else {
                message
            }
        })
    }

    private suspend fun processBatch() {
        try {
            delay(timings.admissionMs)
            _uiState.value = _uiState.value.copy(
                selectedStickerId = null,
                communicationState = CommunicationState.RESPONDING
            )
            val batch = pendingSends.toList()
            pendingSends.clear()
            batch.map { it.reading }.joinAll()

            for (send in batch) {
                delay(timings.replyPreparationMs)
                for ((index, textKey) in replyKeys(send).withIndex()) {
                    if (index > 0) delay(timings.replyIntervalMs)
                    val reply = Message(
                        id = newMessageId(),
                        sender = MessageSender.AME,
                        content = MessageContent.Text(textKey),
                        sentAt = now()
                    )
                    val state = _uiState.value
                    _uiState.value = state.copy(messages = state.messages + reply)
                }
            }
        } finally {
            // A cleared ViewModel cancels the worker and cannot publish another availability change.
            if (currentCoroutineContext().isActive) {
                _uiState.value = _uiState.value.copy(communicationState = CommunicationState.AVAILABLE)
            }
        }
    }

    private fun replyKeys(send: PendingSend): List<String> {
        val keys = try {
            repliesFor(send.stickerId).filter { it.isNotBlank() }
        } catch (error: IOException) {
            logger.log(Level.WARNING, "Could not load the reply for message '${send.messageId}'", error)
            return listOf(GENERIC_DEMO_REPLY)
        }
        return keys.ifEmpty {
            logger.warning("No reply for sticker '${send.stickerId}' in message '${send.messageId}'")
            listOf(GENERIC_DEMO_REPLY)
        }
    }

    private companion object {
        val logger: Logger = Logger.getLogger(ChatViewModel::class.java.name)
    }
}
