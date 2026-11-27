package com.euforia.jine

import com.euforia.jine.data.model.dialogue.DialogueAction
import com.euforia.jine.data.model.dialogue.DialogueAction.End
import com.euforia.jine.data.model.dialogue.DialogueAction.Jump
import com.euforia.jine.data.model.dialogue.DialogueAction.SendText
import com.euforia.jine.data.model.dialogue.DialogueAction.ShowChoices
import com.euforia.jine.data.model.dialogue.DialogueAction.Wait
import com.euforia.jine.data.model.dialogue.DialogueDefinition
import com.euforia.jine.data.model.dialogue.DialogueNode
import com.euforia.jine.data.model.dialogue.DialogueOption
import com.euforia.jine.data.model.dialogue.DialogueValidationException
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

class DialogueDefinitionTest {
    private val option = DialogueOption("go", "choice.go", "end")

    @Test
    fun snapshotsTheNodesActionsAndOptionsWithoutChangingTheirOrder() {
        val options = mutableListOf(option, option.copy(id = "later"))
        val choices = ShowChoices(options)
        val actions = mutableListOf(SendText("question"), Wait(0), choices)
        val nodes = linkedMapOf("entry" to DialogueNode(actions), "end" to DialogueNode(listOf(End)))
        val dialogue = DialogueDefinition("draft", "entry", nodes)

        options.reverse()
        options.clear()
        actions.clear()
        nodes.clear()

        assertEquals(listOf("entry", "end"), dialogue.nodes.keys.toList())
        assertEquals(listOf(SendText("question"), Wait(0), choices), dialogue.nodes.getValue("entry").actions)
        assertEquals(listOf("go", "later"), choices.options.map { it.id })
        assertThrows(UnsupportedOperationException::class.java) {
            (choices.options as MutableList<DialogueOption>).clear()
        }
    }

    @Test
    fun rejectsBlankIdentifiersAndMissingEntryOrDestinations() {
        reject("Dialogue ID") { DialogueDefinition(" ", "entry", mapOf("entry" to DialogueNode(listOf(End)))) }
        reject("entry node") { DialogueDefinition("empty", "entry", emptyMap()) }
        reject("entry node") { DialogueDefinition("bad-entry", " ", mapOf(" " to DialogueNode(listOf(End)))) }
        reject("Node ID") {
            DialogueDefinition("bad-node", "entry", mapOf("entry" to DialogueNode(listOf(End)), "" to DialogueNode(listOf(End))))
        }
        reject("Unknown destination") { definition(Jump("missing")) }
        reject("Unknown destination") { definition(ShowChoices(listOf(option.copy(nextNodeId = " ")))) }
    }

    @Test
    fun rejectsEmptyNodesMissingTerminalsAndActionsAfterTerminal() {
        reject("terminal action") { definition() }
        reject("terminal action") { definition(SendText("only")) }
        reject("terminal action") { definition(End, SendText("too-late")) }
        reject("terminal action") { definition(Jump("end"), End) }
    }

    @Test
    fun rejectsNegativeWaitsBlankKeysAndEmptyOrDuplicateChoices() {
        reject("Negative wait") { definition(Wait(-1), End) }
        reject("Blank text key") { definition(SendText(" "), End) }
        reject("Empty choices") { definition(ShowChoices(emptyList())) }
        reject("choice ID") { definition(ShowChoices(listOf(option.copy(id = "")))) }
        reject("choice ID") { definition(ShowChoices(listOf(option, option.copy(textKey = "other.key")))) }
        reject("Blank choice text key") { definition(ShowChoices(listOf(option.copy(textKey = " ")))) }
    }

    @Test
    fun rejectsUnreachableNodesAndCyclesThroughJumpsAndChoices() {
        reject("Unreachable") { definition(End) }
        reject("Cycle") {
            DialogueDefinition(
                "cycle", "entry",
                mapOf(
                    "entry" to DialogueNode(listOf(Jump("loop"))),
                    "loop" to DialogueNode(listOf(ShowChoices(listOf(option.copy(nextNodeId = "entry")))))
                )
            )
        }
    }

    private fun definition(vararg actions: DialogueAction) = DialogueDefinition(
        "test", "entry",
        mapOf("entry" to DialogueNode(actions.toList()), "end" to DialogueNode(listOf(End)))
    )

    private fun reject(reason: String, construct: () -> DialogueDefinition) {
        val failure = assertThrows(DialogueValidationException::class.java) { construct() }
        assertTrue(failure.message.orEmpty(), failure.message.orEmpty().contains(reason))
    }
}
