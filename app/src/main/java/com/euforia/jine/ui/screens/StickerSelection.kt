package com.euforia.jine.ui.screens

import com.euforia.jine.data.model.Message
import com.euforia.jine.data.model.MessageContent
import com.euforia.jine.data.model.MessageSender
import com.euforia.jine.data.model.PCHAN_PACK_ID
import java.util.UUID

internal data class StickerPressResult(
    val selectedStickerId: String?,
    val sentMessage: Message? = null
)

internal fun handleStickerPress(
    selectedStickerId: String?,
    pressedStickerId: String,
    validStickerIds: Set<String>,
    newMessageId: () -> String = { UUID.randomUUID().toString() },
    now: () -> Long = System::currentTimeMillis
): StickerPressResult? {
    if (pressedStickerId !in validStickerIds) return null
    if (pressedStickerId != selectedStickerId) return StickerPressResult(pressedStickerId)

    return StickerPressResult(
        selectedStickerId = null,
        sentMessage = Message(
            id = newMessageId(),
            sender = MessageSender.PCHAN,
            content = MessageContent.Sticker(PCHAN_PACK_ID, pressedStickerId),
            sentAt = now()
        )
    )
}
