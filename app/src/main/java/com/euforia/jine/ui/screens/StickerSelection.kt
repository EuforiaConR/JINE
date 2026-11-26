package com.euforia.jine.ui.screens

import com.euforia.jine.data.model.Message
import com.euforia.jine.data.model.MessageContent
import com.euforia.jine.data.model.MessageSender
import com.euforia.jine.data.model.PCHAN_PACK_ID
import com.euforia.jine.data.model.Sticker
import java.util.UUID

internal data class StickerPressResult(
    val selectedStickerId: String?,
    val sentMessage: Message? = null
)

internal fun handleStickerPress(
    selectedStickerId: String?,
    pressedStickerId: String,
    stickersById: Map<String, Sticker>
): StickerPressResult? {
    if (pressedStickerId !in stickersById) return null
    if (pressedStickerId != selectedStickerId) return StickerPressResult(pressedStickerId)

    return StickerPressResult(
        selectedStickerId = null,
        sentMessage = Message(
            id = UUID.randomUUID().toString(),
            sender = MessageSender.PCHAN,
            content = MessageContent.Sticker(PCHAN_PACK_ID, pressedStickerId),
            sentAt = System.currentTimeMillis()
        )
    )
}
