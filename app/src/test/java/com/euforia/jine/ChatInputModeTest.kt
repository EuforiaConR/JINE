package com.euforia.jine

import com.euforia.jine.ui.screens.ChatInputMode
import com.euforia.jine.ui.screens.ChatUiState
import com.euforia.jine.ui.screens.CommunicationState
import com.euforia.jine.ui.screens.DialogueChoice
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

class ChatInputModeTest {
    private val first = DialogueChoice("gaming", "dialogue.choice.gaming")
    private val second = DialogueChoice("talking", "dialogue.choice.talking")

    @Test
    fun stickerAdmissionDependsOnInputModeAcrossCommunicationStates() {
        val choices = ChatInputMode.Choices("prompt", listOf(first))
        for (communication in CommunicationState.entries) {
            val state = ChatUiState(emptyList(), communication)
            assertEquals(ChatInputMode.Stickers, state.inputMode)
            assertTrue(state.canSendSticker)
            assertFalse(state.copy(inputMode = ChatInputMode.Hidden).canSendSticker)
            assertFalse(state.copy(inputMode = choices).canSendSticker)
        }
        assertEquals(choices, ChatUiState(emptyList(), inputMode = choices).copy(
            communicationState = CommunicationState.RESPONDING
        ).inputMode)
    }

    @Test
    fun choicesAllowOneOptionAndSnapshotSeveralOptionsInTheirPublishedOrder() {
        assertEquals(listOf(first), ChatInputMode.Choices("single", listOf(first)).options)
        val source = mutableListOf(second, first)
        val prompt = ChatInputMode.Choices("multiple", source)
        source.reverse()
        source.clear()
        assertEquals("multiple", prompt.promptId)
        assertEquals(listOf(second, first), prompt.options)
        assertThrows(UnsupportedOperationException::class.java) { (prompt.options as MutableList<DialogueChoice>).clear() }
    }

    @Test
    fun blankIdentifiersAndKeysEmptyPromptsAndDuplicateChoiceIdsAreRejected() {
        assertThrows(IllegalArgumentException::class.java) { DialogueChoice("", first.textKey) }
        assertThrows(IllegalArgumentException::class.java) { DialogueChoice(first.id, " ") }
        assertThrows(IllegalArgumentException::class.java) { ChatInputMode.Choices(" ", listOf(first)) }
        assertThrows(IllegalArgumentException::class.java) { ChatInputMode.Choices("prompt", emptyList()) }
        assertThrows(IllegalArgumentException::class.java) {
            ChatInputMode.Choices("prompt", listOf(first, first.copy(textKey = second.textKey)))
        }
    }
}
