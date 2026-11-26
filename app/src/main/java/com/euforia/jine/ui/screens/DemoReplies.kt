package com.euforia.jine.ui.screens

internal const val GENERIC_DEMO_REPLY = "dialogue.demo.reply.generic.01"

internal fun demoReplies(batch: StickerBatch): List<String> = when (batch.type) {
    StickerBatchType.SINGLE -> singleStickerReplies(batch.stickerIds.single())
    StickerBatchType.REPEATED -> if (batch.stickerIds.first() == "love") {
        listOf("dialogue.demo.batch.repeated.love.01")
    } else {
        listOf("dialogue.demo.batch.repeated.generic.01")
    }
    StickerBatchType.MIXED -> listOf("dialogue.demo.batch.mixed.01")
    StickerBatchType.SPAM -> listOf("dialogue.demo.batch.spam.01")
}

private fun singleStickerReplies(stickerId: String): List<String> = when (stickerId) {
    "ok" -> listOf("dialogue.demo.reply.ok.01")
    "saikouka" -> listOf("dialogue.demo.reply.saikouka.01")
    "pien" -> listOf("dialogue.demo.reply.pien.01")
    "waritodoudemoii" -> listOf("dialogue.demo.reply.waritodoudemoii.01")
    "gomen" -> listOf("dialogue.demo.reply.gomen.01")
    "bujishibou" -> listOf("dialogue.demo.reply.bujishibou.01")
    "love" -> listOf("dialogue.demo.reply.love.01", "dialogue.demo.reply.love.02")
    "sorena" -> listOf("dialogue.demo.reply.sorena.01")
    else -> emptyList()
}
