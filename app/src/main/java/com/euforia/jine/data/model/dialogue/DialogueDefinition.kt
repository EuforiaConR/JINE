package com.euforia.jine.data.model.dialogue

import java.util.Collections

internal data class DialogueOption(val id: String, val textKey: String, val nextNodeId: String)

internal sealed interface DialogueAction {
    data class SendText(val textKey: String) : DialogueAction
    data class Wait(val durationMs: Long) : DialogueAction

    sealed interface Terminal : DialogueAction

    class ShowChoices(options: List<DialogueOption>) : Terminal {
        val options: List<DialogueOption> = Collections.unmodifiableList(options.toList())
    }

    data class Jump(val nextNodeId: String) : Terminal
    data object End : Terminal
}

internal class DialogueNode(actions: List<DialogueAction>) {
    val actions: List<DialogueAction> = Collections.unmodifiableList(actions.toList())
}

internal class DialogueValidationException(message: String) : IllegalArgumentException(message)

/** An immutable, validated graph. Callers construct it before requesting a dialogue. */
internal class DialogueDefinition(
    val id: String,
    val entryNodeId: String,
    nodes: Map<String, DialogueNode>
) {
    val nodes: Map<String, DialogueNode> = Collections.unmodifiableMap(LinkedHashMap(nodes))

    init {
        validate(id.isNotBlank()) { "Dialogue ID must not be blank" }
        validate(entryNodeId.isNotBlank() && entryNodeId in this.nodes) { "Invalid entry node '$entryNodeId'" }
        val destinations = this.nodes.mapValues { (nodeId, node) -> validateNode(nodeId, node) }
        validateGraph(destinations)
    }

    private fun validateNode(nodeId: String, node: DialogueNode): List<String> {
        validate(nodeId.isNotBlank()) { "Node ID must not be blank" }
        val terminal = node.actions.lastOrNull()
        validate(terminal is DialogueAction.Terminal && node.actions.dropLast(1).none { it is DialogueAction.Terminal }) {
            "Node '$nodeId' must have exactly one terminal action at the end"
        }
        for (action in node.actions) {
            when (action) {
                is DialogueAction.SendText -> validate(action.textKey.isNotBlank()) {
                    "Blank text key in node '$nodeId'"
                }
                is DialogueAction.Wait -> validate(action.durationMs >= 0) { "Negative wait in node '$nodeId'" }
                is DialogueAction.Terminal -> Unit
            }
        }
        return when (terminal) {
            is DialogueAction.ShowChoices -> {
                validate(terminal.options.isNotEmpty()) { "Empty choices in node '$nodeId'" }
                val choiceIds = mutableSetOf<String>()
                terminal.options.map { option ->
                    validate(option.id.isNotBlank() && choiceIds.add(option.id)) {
                        "Blank or duplicate choice ID '${option.id}' in node '$nodeId'"
                    }
                    validate(option.textKey.isNotBlank()) { "Blank choice text key in node '$nodeId'" }
                    destination(nodeId, option.nextNodeId)
                }
            }
            is DialogueAction.Jump -> listOf(destination(nodeId, terminal.nextNodeId))
            DialogueAction.End -> emptyList()
            else -> error("The terminal action was already validated")
        }
    }

    private fun destination(nodeId: String, targetId: String): String {
        validate(targetId.isNotBlank() && targetId in nodes) { "Unknown destination '$targetId' in node '$nodeId'" }
        return targetId
    }

    private fun validateGraph(destinations: Map<String, List<String>>) {
        val visited = mutableSetOf(entryNodeId)
        val visiting = mutableSetOf(entryNodeId)
        val stack = ArrayDeque<Pair<String, Iterator<String>>>()
        stack.addLast(entryNodeId to destinations.getValue(entryNodeId).iterator())

        while (stack.isNotEmpty()) {
            val (nodeId, children) = stack.last()
            if (!children.hasNext()) {
                stack.removeLast()
                visiting.remove(nodeId)
                continue
            }
            val child = children.next()
            validate(child !in visiting) { "Cycle through node '$child'" }
            if (visited.add(child)) {
                visiting.add(child)
                stack.addLast(child to destinations.getValue(child).iterator())
            }
        }
        validate(visited.size == nodes.size) { "Unreachable nodes: ${nodes.keys - visited}" }
    }

    private inline fun validate(condition: Boolean, message: () -> String) {
        if (!condition) throw DialogueValidationException("Dialogue '$id': ${message()}")
    }
}
