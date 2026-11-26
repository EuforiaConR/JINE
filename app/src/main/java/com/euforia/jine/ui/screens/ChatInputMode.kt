package com.euforia.jine.ui.screens

import java.util.Collections

internal data class DialogueChoice(val id: String, val textKey: String) {
    init {
        require(id.isNotBlank()) { "Choice ID must not be blank" }
        require(textKey.isNotBlank()) { "Choice text key must not be blank" }
    }
}

internal sealed interface ChatInputMode {
    data object Stickers : ChatInputMode
    data object Hidden : ChatInputMode

    class Choices(val promptId: String, options: List<DialogueChoice>) : ChatInputMode {
        val options: List<DialogueChoice> = Collections.unmodifiableList(options.toList())

        init {
            require(promptId.isNotBlank()) { "Prompt ID must not be blank" }
            require(this.options.isNotEmpty()) { "A prompt must have at least one choice" }
            require(this.options.map { it.id }.toSet().size == this.options.size) {
                "Choice IDs must be unique within a prompt"
            }
        }
    }
}
