package com.euforia.jine

import com.euforia.jine.data.model.MessageContent
import com.euforia.jine.data.model.MessageSender
import com.euforia.jine.data.model.PCHAN_PACK_ID
import com.euforia.jine.data.model.Sticker
import com.euforia.jine.data.model.resolvePchanSticker
import com.euforia.jine.ui.screens.handleStickerPress
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class StickerSelectionTest {
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
    fun firstPressSelectsAndAnotherStickerReplacesTheSelection() {
        val first = checkNotNull(handleStickerPress(null, "ok", stickers))
        assertEquals("ok", first.selectedStickerId)
        assertNull(first.sentMessage)

        val replacement = checkNotNull(handleStickerPress(first.selectedStickerId, "love", stickers))
        assertEquals("love", replacement.selectedStickerId)
        assertNull(replacement.sentMessage)
    }

    @Test
    fun pressingTheSelectionCreatesAnUnreadMessageAndClearsTheSelection() {
        val result = checkNotNull(handleStickerPress("ok", "ok", stickers))
        assertNull(result.selectedStickerId)

        val message = checkNotNull(result.sentMessage)
        assertTrue(message.id.isNotBlank())
        assertTrue(message.sentAt > 0L)
        assertEquals(MessageSender.PCHAN, message.sender)
        assertEquals(MessageContent.Sticker(PCHAN_PACK_ID, "ok"), message.content)
        assertNull(message.readAt)
        assertFalse(message.isRead)
    }

    @Test
    fun repeatedAndDifferentStickersProduceIndependentMessagesInPressOrder() {
        var selectedStickerId: String? = null
        val messages = listOf("ok", "ok", "ok", "ok", "love", "love").mapNotNull { id ->
            val result = checkNotNull(handleStickerPress(selectedStickerId, id, stickers))
            selectedStickerId = result.selectedStickerId
            result.sentMessage
        }

        assertEquals(listOf("ok", "ok", "love"), messages.map {
            (it.content as MessageContent.Sticker).stickerId
        })
        assertEquals(3, messages.map { it.id }.distinct().size)
        assertTrue(messages.none { it.isRead })
        assertNull(selectedStickerId)

        val readCopy = messages[1].copy(readAt = messages[1].sentAt)
        assertEquals(messages[1].id, readCopy.id)
        assertTrue(readCopy.isRead)
        assertFalse(messages[0].isRead)
        assertNotEquals(messages[0].id, readCopy.id)
    }

    @Test
    fun unknownStickerIsIgnoredEvenIfAnOldSelectionMatches() {
        assertNull(handleStickerPress("unavailable", "unavailable", stickers))
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
