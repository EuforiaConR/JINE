package com.euforia.jine.ui.screens

import com.euforia.jine.data.model.Message
import com.euforia.jine.data.model.MessageContent
import com.euforia.jine.data.model.MessageSender
import com.euforia.jine.data.model.PCHAN_PACK_ID

internal fun createStickerMessage(
    stickerId: String,
    validStickerIds: Set<String>,
    newMessageId: () -> String,
    now: () -> Long
): Message? {
    if (stickerId !in validStickerIds) return null

    return Message(
        id = newMessageId(),
        sender = MessageSender.PCHAN,
        content = MessageContent.Sticker(PCHAN_PACK_ID, stickerId),
        sentAt = now()
    )
}
