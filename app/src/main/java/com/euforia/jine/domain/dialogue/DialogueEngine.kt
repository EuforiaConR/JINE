package com.euforia.jine.domain.dialogue

import com.euforia.jine.data.model.dialogue.DialogueAction
import com.euforia.jine.data.model.dialogue.DialogueDefinition
import com.euforia.jine.data.model.dialogue.DialogueOption
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.ensureActive

internal class DialogueExecutionException(message: String) : IllegalStateException(message)

internal object DialogueEngine {
    suspend fun execute(
        definition: DialogueDefinition,
        sendText: (textKey: String) -> Unit,
        awaitChoice: suspend (options: List<DialogueOption>) -> String
    ) {
        val context = currentCoroutineContext()
        var nodeId = definition.entryNodeId
        node@ while (true) {
            for (action in definition.nodes.getValue(nodeId).actions) {
                context.ensureActive()
                when (action) {
                    is DialogueAction.SendText -> sendText(action.textKey)
                    is DialogueAction.Wait -> delay(action.durationMs)
                    is DialogueAction.ShowChoices -> {
                        val choiceId = awaitChoice(action.options)
                        context.ensureActive()
                        val option = action.options.firstOrNull { it.id == choiceId }
                            ?: throw DialogueExecutionException("Unknown option '$choiceId' in node '$nodeId'")
                        nodeId = option.nextNodeId
                        continue@node
                    }
                    is DialogueAction.Jump -> {
                        nodeId = action.nextNodeId
                        continue@node
                    }
                    DialogueAction.End -> return
                }
            }
        }
    }
}
