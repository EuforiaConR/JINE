package com.euforia.jine

import com.euforia.jine.data.model.dialogue.DialogueAction.End
import com.euforia.jine.data.model.dialogue.DialogueAction.Jump
import com.euforia.jine.data.model.dialogue.DialogueAction.SendText
import com.euforia.jine.data.model.dialogue.DialogueAction.ShowChoices
import com.euforia.jine.data.model.dialogue.DialogueAction.Wait
import com.euforia.jine.data.model.dialogue.DialogueDefinition
import com.euforia.jine.data.model.dialogue.DialogueNode
import com.euforia.jine.data.model.dialogue.DialogueOption

// The definition from phase 1.4.2b, preserved only for migration equivalence tests.
internal object LegacyStreamFixture {
    private const val STREAM_PREFIX = "dialogue.demo.narrative.stream."
    private const val PAUSE_MS = 700L

    val stream = DialogueDefinition(
        id = "stream",
        entryNodeId = "intro",
        nodes = mapOf(
            "intro" to DialogueNode(listOf(
                text("intro.01"),
                Wait(PAUSE_MS),
                text("intro.02"),
                Wait(PAUSE_MS),
                text("question.01"),
                ShowChoices(listOf(option("gaming", "gaming_reply"), option("talking", "talking_reply")))
            )),
            "gaming_reply" to DialogueNode(listOf(text("gaming.01"), Wait(PAUSE_MS), Jump("common_question"))),
            "talking_reply" to DialogueNode(listOf(text("talking.01"), Wait(PAUSE_MS), Jump("common_question"))),
            "common_question" to DialogueNode(listOf(
                text("question.02"),
                ShowChoices(listOf(option("help", "help_reply"), option("later", "later_reply")))
            )),
            "help_reply" to DialogueNode(listOf(text("help.01"), Wait(PAUSE_MS), Jump("outro"))),
            "later_reply" to DialogueNode(listOf(text("later.01"), Wait(PAUSE_MS), Jump("outro"))),
            "outro" to DialogueNode(listOf(text("outro.01"), End))
        )
    )

    private fun text(suffix: String) = SendText(STREAM_PREFIX + suffix)
    private fun option(id: String, nextNodeId: String) = DialogueOption(id, STREAM_PREFIX + "choice." + id, nextNodeId)
}
