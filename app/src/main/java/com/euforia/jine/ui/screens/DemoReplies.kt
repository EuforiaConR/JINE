package com.euforia.jine.ui.screens

internal const val GENERIC_DEMO_REPLY = "dialogue.demo.reply.generic.01"

internal fun demoReplies(stickerId: String): List<String> = when (stickerId) {
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
