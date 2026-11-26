package com.euforia.jine.ui.screens

import com.euforia.jine.data.model.Message
import com.euforia.jine.data.model.MessageContent
import com.euforia.jine.data.model.MessageSender
import com.euforia.jine.data.model.PCHAN_PACK_ID

internal const val DEFAULT_SPAM_THRESHOLD = 5

internal enum class StickerBatchType {
    SINGLE,
    REPEATED,
    MIXED,
    SPAM
}

internal class StickerBatch(messages: List<Message>, spamThreshold: Int = DEFAULT_SPAM_THRESHOLD) {
    val messages = messages.toList()
    val stickerIds = this.messages.map { message ->
        val sticker = message.content as? MessageContent.Sticker
        require(message.sender == MessageSender.PCHAN && sticker?.packId == PCHAN_PACK_ID)
        sticker.stickerId
    }
    val type = classifyStickerBatch(stickerIds, spamThreshold)
}

internal fun classifyStickerBatch(
    stickerIds: List<String>,
    spamThreshold: Int = DEFAULT_SPAM_THRESHOLD
): StickerBatchType {
    require(spamThreshold >= 2)
    require(stickerIds.isNotEmpty())
    return when {
        stickerIds.size >= spamThreshold -> StickerBatchType.SPAM
        stickerIds.size == 1 -> StickerBatchType.SINGLE
        stickerIds.all { it == stickerIds.first() } -> StickerBatchType.REPEATED
        else -> StickerBatchType.MIXED
    }
}
