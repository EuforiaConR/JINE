package com.euforia.jine

import com.euforia.jine.data.model.MessageContent
import com.euforia.jine.data.model.MessageSender
import com.euforia.jine.data.model.PCHAN_PACK_ID
import com.euforia.jine.data.model.Sticker
import com.euforia.jine.data.model.resolvePchanSticker
import com.euforia.jine.ui.screens.createStickerMessage
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class StickerSendingTest {
    private val stickers = listOf("ok", "love").mapIndexed { order, id ->
        Sticker(
            id = id,
            textures = mapOf(
                "es" to "textures/stickers/pchan/es/$id.png",
                "en" to "textures/stickers/pchan/en/$id.png"
            ),
            descriptionKey = "sticker.pchan.$id",
            emotion = "example",
            order = order
        )
    }.associateBy { it.id }

    @Test
    fun oneValidStickerCreatesAnUnreadMessageUsingTheSuppliedIdAndTime() {
        val message = checkNotNull(createStickerMessage("ok", stickers.keys, { "message-1" }, { 123L }))
        assertEquals("message-1", message.id)
        assertEquals(123L, message.sentAt)
        assertEquals(MessageSender.PCHAN, message.sender)
        assertEquals(MessageContent.Sticker(PCHAN_PACK_ID, "ok"), message.content)
        assertNull(message.readAt)
        assertFalse(message.isRead)
    }

    @Test
    fun repeatedAndDifferentStickersKeepOrderAndIndependentIdsWithTheSameTimestamp() {
        var nextId = 0
        val messages = listOf("ok", "ok", "love").map { id ->
            checkNotNull(createStickerMessage(id, stickers.keys, { "message-${++nextId}" }, { 123L }))
        }

        assertEquals(listOf("ok", "ok", "love"), messages.map {
            (it.content as MessageContent.Sticker).stickerId
        })
        assertEquals(listOf("message-1", "message-2", "message-3"), messages.map { it.id })
        assertTrue(messages.all { it.sentAt == 123L })
        assertTrue(messages.none { it.isRead })

        val readCopy = messages[1].copy(readAt = messages[1].sentAt)
        assertEquals(messages[1].id, readCopy.id)
        assertTrue(readCopy.isRead)
        assertFalse(messages[0].isRead)
        assertNotEquals(messages[0].id, readCopy.id)
    }

    @Test
    fun unknownStickerAndEmptyCatalogDoNotCallTheIdOrClockProviders() {
        val id = { error("Invalid sticker must not consume a message ID") }
        val now = { error("Invalid sticker must not read the clock") }
        assertNull(createStickerMessage("unavailable", stickers.keys, id, now))
        assertNull(createStickerMessage("ok", emptySet(), id, now))
    }

    @Test
    fun stickerReferenceUsesPackAndIdAndResolvesPathsByLanguage() {
        val content = MessageContent.Sticker(PCHAN_PACK_ID, "love")
        val resolved = checkNotNull(content.resolvePchanSticker(stickers))
        assertEquals("textures/stickers/pchan/es/love.png", resolved.getTexture("es"))
        assertEquals("textures/stickers/pchan/en/love.png", resolved.getTexture("en"))
        assertEquals(resolved.getTexture("es"), resolved.getTexture("fr"))
        assertNull(content.copy(packId = "ame").resolvePchanSticker(stickers))
        assertNull(content.copy(stickerId = "unknown").resolvePchanSticker(stickers))
    }
}
