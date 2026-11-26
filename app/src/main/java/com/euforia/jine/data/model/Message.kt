package com.euforia.jine.data.model

enum class MessageSender {
    AME,
    PCHAN
}

sealed interface MessageContent {
    data class Text(val textKey: String) : MessageContent

    data class Sticker(
        val packId: String,
        val stickerId: String
    ) : MessageContent
}

data class Message(
    val id: String,
    val sender: MessageSender,
    val content: MessageContent,
    val sentAt: Long,
    val readAt: Long? = null
) {
    val isRead: Boolean
        get() = readAt != null
}

internal fun MessageContent.Sticker.resolvePchanSticker(stickersById: Map<String, Sticker>): Sticker? {
    return if (packId == PCHAN_PACK_ID) stickersById[stickerId] else null
}
