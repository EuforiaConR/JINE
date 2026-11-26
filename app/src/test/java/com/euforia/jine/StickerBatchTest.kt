package com.euforia.jine

import com.euforia.jine.data.model.Message
import com.euforia.jine.data.model.MessageContent
import com.euforia.jine.data.model.MessageSender
import com.euforia.jine.data.model.PCHAN_PACK_ID
import com.euforia.jine.ui.screens.ChatTimings
import com.euforia.jine.ui.screens.StickerBatch
import com.euforia.jine.ui.screens.StickerBatchType
import com.euforia.jine.ui.screens.classifyStickerBatch
import com.euforia.jine.ui.screens.demoReplies
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

class StickerBatchTest {
    private val catalogIds = listOf("ok", "saikouka", "pien", "waritodoudemoii", "gomen", "bujishibou", "love", "sorena")

    @Test
    fun singlesRetainEveryDemoReplyIncludingBothLoveTexts() {
        for (id in catalogIds) {
            val batch = batch(listOf(id))
            assertEquals(StickerBatchType.SINGLE, batch.type)
            val expected = if (id == "love") listOf("01", "02") else listOf("01")
            assertEquals(expected.map { "dialogue.demo.reply.$id.$it" }, demoReplies(batch))
        }
        assertTrue(demoReplies(batch(listOf("unknown"))).isEmpty())
    }

    @Test
    fun twoToFourStickersProduceOneRepeatedOrMixedReaction() {
        for (size in 2..4) {
            val love = batch(List(size) { "love" })
            val other = batch(List(size) { "ok" })
            val mixed = batch(catalogIds.take(size))
            assertEquals(StickerBatchType.REPEATED, love.type)
            assertEquals(StickerBatchType.REPEATED, other.type)
            assertEquals(StickerBatchType.MIXED, mixed.type)
            assertEquals(listOf("dialogue.demo.batch.repeated.love.01"), demoReplies(love))
            assertEquals(listOf("dialogue.demo.batch.repeated.generic.01"), demoReplies(other))
            assertEquals(listOf("dialogue.demo.batch.mixed.01"), demoReplies(mixed))
        }
    }

    @Test
    fun spamThresholdIsInclusiveAndTakesPrecedenceOverStickerIdentity() {
        for (ids in listOf(List(5) { "love" }, catalogIds.take(5), catalogIds)) {
            val batch = batch(ids)
            assertEquals(StickerBatchType.SPAM, batch.type)
            assertEquals(listOf("dialogue.demo.batch.spam.01"), demoReplies(batch))
        }
        assertEquals(StickerBatchType.SPAM, classifyStickerBatch(listOf("ok", "love"), spamThreshold = 2))
        assertEquals(StickerBatchType.REPEATED, classifyStickerBatch(List(5) { "ok" }, spamThreshold = 6))
    }

    @Test
    fun snapshotPreservesIdsOrderAndReadTimestamps() {
        val first = message("send-1", "love", 30L)
        val second = message("send-2", "love", 10L)
        val pending = mutableListOf(first, second)
        val batch = StickerBatch(pending)
        pending.clear()
        assertEquals(listOf(first, second), batch.messages)
        assertEquals(listOf("send-1", "send-2"), batch.messages.map { it.id })
        assertEquals(listOf("love", "love"), batch.stickerIds)
    }

    @Test
    fun emptyAndInvalidBatchesAndInvalidTimingConfigurationAreRejected() {
        assertThrows(IllegalArgumentException::class.java) { StickerBatch(emptyList()) }
        val valid = message("send-1", "ok", 0L)
        assertThrows(IllegalArgumentException::class.java) {
            StickerBatch(listOf(valid.copy(sender = MessageSender.AME)))
        }
        assertThrows(IllegalArgumentException::class.java) {
            StickerBatch(listOf(valid.copy(content = MessageContent.Text("key"))))
        }
        assertThrows(IllegalArgumentException::class.java) { classifyStickerBatch(listOf("ok"), spamThreshold = 1) }
        assertThrows(IllegalArgumentException::class.java) { ChatTimings(spamThreshold = 1) }
        assertThrows(IllegalArgumentException::class.java) { ChatTimings(readMs = -1) }
        assertThrows(IllegalArgumentException::class.java) { ChatTimings(replyPreparationMs = -1) }
        assertThrows(IllegalArgumentException::class.java) { ChatTimings(replyIntervalMs = -1) }
    }

    private fun batch(ids: List<String>) = StickerBatch(ids.mapIndexed { index, id ->
        message("send-$index", id, index.toLong())
    })

    private fun message(id: String, stickerId: String, sentAt: Long) = Message(
        id = id,
        sender = MessageSender.PCHAN,
        content = MessageContent.Sticker(PCHAN_PACK_ID, stickerId),
        sentAt = sentAt,
        readAt = sentAt + 650
    )
}
