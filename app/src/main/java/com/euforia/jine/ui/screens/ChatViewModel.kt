package com.euforia.jine.ui.screens

import androidx.annotation.MainThread
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.euforia.jine.data.model.Message
import com.euforia.jine.data.model.MessageContent
import com.euforia.jine.data.model.MessageSender
import com.euforia.jine.data.model.dialogue.DialogueDefinition
import com.euforia.jine.data.model.dialogue.DialogueOption
import com.euforia.jine.domain.dialogue.DialogueEngine
import com.euforia.jine.domain.dialogue.DialogueExecutionException
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

    private class NarrativeSession(val definition: DialogueDefinition) {
        var job: Job? = null
        var prompt: PendingPrompt? = null
    }

    private class PendingPrompt(val id: String, val options: List<DialogueOption>) {
        val answer = CompletableDeferred<DialogueAnswer>()
    }

    private data class DialogueAnswer(val choiceId: String, val messageId: String)

    private val _uiState = MutableStateFlow(ChatUiState(messages = initialMessages(now())))
    val uiState = _uiState.asStateFlow()

    // UI events and coroutine continuations share the main thread in production.
    private val pendingSends = linkedMapOf<String, PendingSend>()
    private var reactionCoordinator: Job? = null
    private var preparationInvalidated: CompletableDeferred<Unit>? = null
    private var narrativeSession: NarrativeSession? = null
    private var nextPromptNumber = 0L

    @MainThread
    fun sendSticker(stickerId: String, validStickerIds: Set<String>): Boolean {
        if (!viewModelScope.isActive) return false
        val state = _uiState.value
        if (!state.canSendSticker) return false
        val message = createStickerMessage(stickerId, validStickerIds, newMessageId, now) ?: return false

        val reading = viewModelScope.launch(dispatcher, start = CoroutineStart.LAZY) {
            delay(timings.readMs)
            ensureActive()
            markStickerRead(message.id)
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

    @MainThread
    fun requestDialogue(definition: DialogueDefinition): Boolean {
        if (!viewModelScope.isActive || narrativeSession != null) return false
        val session = NarrativeSession(definition)
        narrativeSession = session
        if (reactionCoordinator == null) {
            startDialogue(session)
        } else if (pendingSends.isEmpty() && _uiState.value.communicationState == CommunicationState.RESPONDING) {
            // This reaction has already committed, and no later batch needs admission.
            hideInput()
        }
        return true
    }

    @MainThread
    fun chooseDialogueOption(promptId: String, choiceId: String): Boolean {
        if (!viewModelScope.isActive) return false
        val session = narrativeSession ?: return false
        val prompt = session.prompt ?: return false
        val state = _uiState.value
        val mode = state.inputMode as? ChatInputMode.Choices ?: return false
        if (session.job?.isActive != true || prompt.id != promptId || mode.promptId != promptId) return false
        val option = prompt.options.firstOrNull { it.id == choiceId } ?: return false

        session.prompt = null
        val message = try {
            newTextMessage(MessageSender.PCHAN, option.textKey)
        } catch (error: IOException) {
            prompt.answer.completeExceptionally(error)
            return false
        }
        _uiState.value = state.copy(messages = state.messages + message, inputMode = ChatInputMode.Hidden)
        prompt.answer.complete(DialogueAnswer(choiceId, message.id))
        return true
    }

    private fun markStickerRead(messageId: String) {
        val send = pendingSends[messageId] ?: return
        val message = send.message
        if (message.isRead) return
        val read = message.copy(readAt = maxOf(message.sentAt, now()))
        send.message = read
        publishReadMessage(read)
    }

    private fun markNarrativeRead(messageId: String) {
        val message = _uiState.value.messages.firstOrNull { it.id == messageId } ?: return
        if (message.isRead) return
        publishReadMessage(message.copy(readAt = maxOf(message.sentAt, now())))
    }

    private fun publishReadMessage(read: Message) {
        val state = _uiState.value
        _uiState.value = state.copy(messages = state.messages.map { if (it.id == read.id) read else it })
    }

    private suspend fun processBatches() {
        try {
            while (pendingSends.isNotEmpty()) {
                _uiState.value = _uiState.value.copy(communicationState = CommunicationState.READING)
                val batch = awaitPreparedBatch()
                val keys = replyKeys(batch)
                currentCoroutineContext().ensureActive()

                // Close admission for a requested dialogue at the same boundary that commits the batch.
                pendingSends.clear()
                if (narrativeSession != null) hideInput()
                currentCoroutineContext().ensureActive()
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
        val session = narrativeSession
        if (session == null) {
            _uiState.value = _uiState.value.copy(communicationState = CommunicationState.AVAILABLE)
        } else {
            startDialogue(session)
        }
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
        val reply = newTextMessage(MessageSender.AME, textKey)
        val state = _uiState.value
        _uiState.value = state.copy(
            messages = state.messages + reply,
            communicationState = CommunicationState.RESPONDING
        )
    }

    private fun hideInput() {
        _uiState.value = _uiState.value.copy(inputMode = ChatInputMode.Hidden)
    }

    private fun startDialogue(session: NarrativeSession) {
        val job = viewModelScope.launch(dispatcher, start = CoroutineStart.LAZY) { runDialogue(session) }
        session.job = job
        _uiState.value = _uiState.value.copy(
            communicationState = CommunicationState.AVAILABLE,
            inputMode = ChatInputMode.Hidden
        )
        job.start()
    }

    private suspend fun runDialogue(session: NarrativeSession) {
        try {
            DialogueEngine.execute(
                session.definition,
                sendText = { textKey ->
                    val message = newTextMessage(MessageSender.AME, textKey)
                    val state = _uiState.value
                    _uiState.value = state.copy(messages = state.messages + message)
                },
                awaitChoice = { options -> awaitDialogueChoice(session, options) }
            )
        } catch (error: IOException) {
            logger.log(Level.WARNING, "Could not complete dialogue '${session.definition.id}'", error)
        } catch (error: DialogueExecutionException) {
            logger.log(Level.WARNING, "Could not complete dialogue '${session.definition.id}'", error)
        } finally {
            session.prompt?.answer?.cancel()
            session.prompt = null
            session.job = null
            if (narrativeSession === session) narrativeSession = null
        }
        currentCoroutineContext().ensureActive()
        _uiState.value = _uiState.value.copy(inputMode = ChatInputMode.Stickers)
    }

    private suspend fun awaitDialogueChoice(session: NarrativeSession, options: List<DialogueOption>): String {
        val prompt = PendingPrompt("dialogue-prompt-${++nextPromptNumber}", options)
        session.prompt = prompt
        val mode = ChatInputMode.Choices(prompt.id, options.map { DialogueChoice(it.id, it.textKey) })
        _uiState.value = _uiState.value.copy(inputMode = mode)

        val answer = prompt.answer.await()
        delay(timings.readMs)
        currentCoroutineContext().ensureActive()
        markNarrativeRead(answer.messageId)
        delay(timings.replyPreparationMs)
        return answer.choiceId
    }

    private fun newTextMessage(sender: MessageSender, textKey: String) = Message(
        id = newMessageId(),
        sender = sender,
        content = MessageContent.Text(textKey),
        sentAt = now()
    )

    override fun onCleared() {
        narrativeSession?.prompt?.answer?.cancel()
        narrativeSession = null
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
