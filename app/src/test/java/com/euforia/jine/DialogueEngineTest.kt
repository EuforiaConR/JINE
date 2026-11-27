package com.euforia.jine

import com.euforia.jine.data.model.dialogue.DialogueAction
import com.euforia.jine.data.model.dialogue.DialogueAction.End
import com.euforia.jine.data.model.dialogue.DialogueAction.SendText
import com.euforia.jine.data.model.dialogue.DialogueAction.ShowChoices
import com.euforia.jine.data.model.dialogue.DialogueAction.Wait
import com.euforia.jine.data.model.dialogue.DialogueDefinition
import com.euforia.jine.data.model.dialogue.DialogueNode
import com.euforia.jine.data.model.dialogue.DialogueOption
import com.euforia.jine.domain.dialogue.DialogueEngine
import com.euforia.jine.domain.dialogue.DialogueExecutionException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.cancel
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class DialogueEngineTest {
    @Test
    fun noChoicesAndZeroWaitsNeedNoImplicitDelays() = runTest {
        val emitted = mutableListOf<Pair<String, Long>>()
        DialogueEngine.execute(
            linear(SendText("first"), Wait(75), SendText("second"), Wait(0), SendText("third"), End),
            sendText = { emitted.add(it to testScheduler.currentTime) },
            awaitChoice = { error("This dialogue has no choices") }
        )
        assertEquals(listOf("first" to 0L, "second" to 75L, "third" to 75L), emitted)
    }

    @Test
    fun rejectsAnUnknownChoiceReturnedByTheHost() = runTest {
        val failure = runCatching {
            DialogueEngine.execute(singleChoice(), sendText = { error("Unexpected text") }, awaitChoice = { "obsolete" })
        }.exceptionOrNull()
        assertTrue(failure is DialogueExecutionException)
    }

    @Test
    fun cancellingWaitOrPromptPreventsFutureActions() = runTest {
        val texts = mutableListOf<String>()
        val waiting = launch {
            DialogueEngine.execute(
                linear(SendText("before"), Wait(900), SendText("after"), End),
                sendText = { texts.add(it) },
                awaitChoice = { error("Unexpected choice") }
            )
        }
        runCurrent()
        waiting.cancel()
        advanceTimeBy(1_000)
        assertEquals(listOf("before"), texts)

        val answer = CompletableDeferred<String>()
        val prompting = launch {
            DialogueEngine.execute(singleChoice(), sendText = { texts.add(it) }, awaitChoice = { answer.await() })
        }
        runCurrent()
        prompting.cancel()
        answer.complete("go")
        runCurrent()
        assertEquals(listOf("before"), texts)
    }

    @Test
    fun checksCancellationEvenBetweenImmediateEffects() = runTest {
        val texts = mutableListOf<String>()
        launch {
            val context = currentCoroutineContext()
            DialogueEngine.execute(
                linear(SendText("before"), Wait(0), SendText("after"), End),
                sendText = {
                    texts.add(it)
                    context.cancel()
                },
                awaitChoice = { error("Unexpected choice") }
            )
        }
        runCurrent()
        assertEquals(listOf("before"), texts)
    }

    private fun linear(vararg actions: DialogueAction) =
        DialogueDefinition("linear", "entry", mapOf("entry" to DialogueNode(actions.toList())))

    private fun singleChoice() = DialogueDefinition(
        "single-choice", "entry",
        mapOf(
            "entry" to DialogueNode(listOf(ShowChoices(listOf(DialogueOption("go", "go.key", "end"))))),
            "end" to DialogueNode(listOf(SendText("end"), End))
        )
    )
}
