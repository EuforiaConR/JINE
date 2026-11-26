package com.euforia.jine.ui.screens

import com.euforia.jine.data.model.Message
import com.euforia.jine.data.model.MessageContent
import com.euforia.jine.data.model.MessageSender

internal enum class CommunicationState {
    AVAILABLE,
    READING,
    RESPONDING
}

internal data class ChatUiState(
    val messages: List<Message>,
    val selectedStickerId: String? = null,
    val communicationState: CommunicationState = CommunicationState.AVAILABLE
) {
    val canSendSticker: Boolean
        get() = communicationState != CommunicationState.RESPONDING
}

internal fun initialMessages(sentAt: Long): List<Message> {
    return listOf("greeting", "question", "long_message").map { name ->
        Message(
            id = "preview_$name",
            sender = MessageSender.AME,
            content = MessageContent.Text("dialogue.preview.$name"),
            sentAt = sentAt
        )
    }
}
