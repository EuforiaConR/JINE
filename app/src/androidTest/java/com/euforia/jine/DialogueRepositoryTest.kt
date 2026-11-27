package com.euforia.jine

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.euforia.jine.core.assets.JineAssets
import com.euforia.jine.data.model.dialogue.DialogueAction
import com.euforia.jine.data.model.dialogue.DialogueValidationException
import com.euforia.jine.data.repository.DialogueContentException
import com.euforia.jine.data.repository.DialogueRepository
import com.euforia.jine.domain.dialogue.DialogueEngine
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import java.io.IOException

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(AndroidJUnit4::class)
class DialogueRepositoryTest {
    private val context get() = InstrumentationRegistry.getInstrumentation().targetContext
    private val fixturePath = "dialogues/tests/fixture.json"
    private val terminal = """{"type":"end"}"""

    @Test
    fun minimalFileFinishesWithoutPublishingAnything() = runTest {
        val definition = parse(document(terminal))
        assertEquals("fixture", definition.id)
        assertEquals("intro", definition.entryNodeId)
        assertEquals(listOf(DialogueAction.End), definition.nodes.getValue("intro").actions)
        DialogueEngine.execute(
            definition,
            sendText = { error("Unexpected message $it") },
            awaitChoice = { error("Unexpected prompt $it") }
        )
        assertEquals(0L, testScheduler.currentTime)
    }

    @Test
    fun fieldsCanBeReorderedAndDecodedWhileActionsAndOptionsKeepTheirOrder() {
        val definition = parse(
            """
            {
              "nodes": {
                "outro": [{ "type": "end" }],
                "intro": [
                  { "key": "dialogue.\u0061\/b", "type": "text" },
                  { "durationMs": 0, "type": "wait" },
                  { "options": [
                      { "next": "outro", "key": "choice.second", "id": "second" },
                      { "key": "choice.first", "id": "first", "next": "outro" }
                    ], "type": "choices" }
                ]
              },
              "entry": "intro",
              "id": "\u0066ixture",
              "schemaVersion": 1
            }
            """.trimIndent()
        )
        assertEquals("fixture", definition.id)
        val actions = definition.nodes.getValue(definition.entryNodeId).actions
        assertEquals(DialogueAction.SendText("dialogue.a/b"), actions[0])
        assertEquals(DialogueAction.Wait(0), actions[1])
        val choices = actions[2] as DialogueAction.ShowChoices
        assertEquals(listOf("second", "first"), choices.options.map { it.id })
        assertEquals(listOf("choice.second", "choice.first"), choices.options.map { it.textKey })
    }

    @Test
    fun waitsKeepTheirLongRangeAndEditingAWaitChangesExecutionTimeWithoutEditingTheEngine() = runTest {
        val boundary = parse(document("""{"type":"wait","durationMs":9223372036854775807},$terminal"""))
        assertEquals(DialogueAction.Wait(Long.MAX_VALUE), boundary.nodes.getValue("intro").actions.first())

        for (duration in listOf(0L, 700L, 1_200L)) {
            val definition = parse(document(
                """{"type":"text","key":"first"},{"type":"wait","durationMs":$duration},{"type":"text","key":"last"},$terminal"""
            ))
            val start = testScheduler.currentTime
            val sent = mutableListOf<Pair<String, Long>>()
            DialogueEngine.execute(
                definition,
                sendText = { sent.add(it to (testScheduler.currentTime - start)) },
                awaitChoice = { error("Unexpected prompt") }
            )
            assertEquals(listOf("first" to 0L, "last" to duration), sent)
        }
    }

    @Test
    fun schemaVersionMustBeAnExplicitSupportedInteger() {
        val valid = document(terminal)
        invalid(valid.replace("\"schemaVersion\":1,", ""), "$.schemaVersion", "Missing")
        for (version in listOf("0", "2", "\"1\"", "1.0", "1e0", "true", "null", "9223372036854775808")) {
            invalid(valid.replace("\"schemaVersion\":1", "\"schemaVersion\":$version"), "$.schemaVersion")
        }
    }

    @Test
    fun rejectsInvalidDocumentsAndNonStandardJsonSyntax() {
        val valid = document(terminal)
        for (json in listOf(
            "", "[]", "null", "{", "$valid{}", "$valid true", "/* comment */$valid",
            valid.replace("\"id\":", "'id':"), valid.replace("\"id\":", "id:"),
            valid.replace("]} }", "],} }"), valid.replace("$terminal]", "$terminal,]"),
            valid.replace("\"id\":", "\"id\"="), valid.replace("\"id\":", "\u000C\"id\":"),
            valid.replace("fixture", "first\nsecond"), valid.replace("fixture", """bad\q"""),
            valid.replace("fixture", """bad\u12x4"""), valid.replace("fixture", """bad\U0000"""),
            "\uFEFF$valid"
        )) {
            invalid(json)
        }
    }

    @Test
    fun rejectsForbiddenFieldsAndMissingOrIncorrectTypesAtTheirLogicalPosition() {
        val valid = document(terminal)
        invalid(valid.replace("\"id\":\"fixture\",", ""), "$.id", "Missing")
        invalid(valid.replace("\"id\":\"fixture\"", "\"id\":null"), "$.id", "STRING")
        invalid(valid.replace("\"entry\":\"intro\"", "\"entry\":1"), "$.entry", "STRING")
        invalid(valid.replace("\"id\":", "\"story\":0,\"id\":"), "$.story", "Unknown field")
        invalid(valid.replace("\"nodes\":", "\"graph\":"), "$.graph", "Unknown field")
        invalid(document("null"), "$.nodes.intro[0]", "BEGIN_OBJECT")
        invalid(document("""{"type":"text"},$terminal"""), "$.nodes.intro[0].key", "Missing")
        invalid(document("""{"type":"text","key":" "},$terminal"""), "$.nodes.intro[0].key", "non-blank")
        invalid(document("""{"type":"text","key":"line","sender":"ame"},$terminal"""), "$.nodes.intro[0].sender")
        invalid(document("""{"type":"end","key":"not.allowed"}"""), "$.nodes.intro[0].key", "Unexpected field")
        invalid(document("""{"type":"choices","options":{}}"""), "$.nodes.intro[0].options", "BEGIN_ARRAY")
        invalid(document("""{"type":"choices","options":[{"id":"go","key":"go"}]}"""), ".options[0].next", "Missing")
        invalid(document("""{"type":"choices","options":[{"id":"go","key":"go","next":"intro","timeout":0}]}"""),
            ".options[0].timeout", "Unknown field")
    }

    @Test
    fun rejectsDuplicatePropertiesBeforeTheyCanOverwriteRootNodesActionsOrOptions() {
        val valid = document(terminal)
        for ((json, path) in listOf(
            valid.replace("\"schemaVersion\":1", "\"schemaVersion\":1,\"schemaVersion\":1") to "$.schemaVersion",
            valid.replace("\"id\":\"fixture\"", """ "id":"fixture","\u0069d":"other" """) to "$.id",
            valid.replace("\"intro\":[$terminal]", "\"intro\":[$terminal],\"intro\":[$terminal]") to "$.nodes.intro",
            document("""{"type":"end","type":"end"}""") to "$.nodes.intro[0].type",
            document("""{"type":"text","key":"one","key":"two"},$terminal""") to "$.nodes.intro[0].key",
            document("""{"type":"choices","options":[{"id":"go","key":"go","next":"intro","next":"other"}]}""")
                to "$.nodes.intro[0].options[0].next"
        )) {
            invalid(json, path, "Duplicate property")
        }
    }

    @Test
    fun rejectsCoercibleNumericValuesAndIntegersOutsideThePermittedRange() {
        for (duration in listOf("-1", "0.0", "1.5", "1e3", "1E-1", "\"50\"", "true", "null", "9223372036854775808")) {
            invalid(document("""{"type":"wait","durationMs":$duration},$terminal"""), "$.nodes.intro[0].durationMs")
        }
        for (notJson in listOf("01", "+1", "Infinity", "NaN")) {
            invalid(document("""{"type":"wait","durationMs":$notJson},$terminal"""))
        }
    }

    @Test
    fun checksChoiceShapesAndDelegatesGraphValidityToTheDomain() {
        invalid(document("""{"type":"sticker","key":"other"}"""), "$.nodes.intro[0].type", "Unknown action")
        invalid(document("""{"type":"choices","options":[{"id":" ","key":"choice","next":"intro"}]}"""), ".options[0].id")
        for ((json, reason) in listOf(
            document("""{"type":"choices","options":[]}""") to "Empty choices",
            document(
                """{"type":"choices","options":[{"id":"same","key":"one","next":"outro"},{"id":"same","key":"two","next":"outro"}]}""",
                """ ,"outro":[$terminal] """
            ) to "duplicate choice ID",
            document("""{"type":"jump","next":"missing"}""") to "Unknown destination",
            document(terminal).replace("\"entry\":\"intro\"", "\"entry\":\"missing\"") to "entry node",
            document("") to "terminal action",
            document("""{"type":"text","key":"line"}""") to "terminal action",
            document("""$terminal,{"type":"text","key":"line"}""") to "terminal action",
            document("""{"type":"jump","next":"intro"}""") to "Cycle",
            document(terminal, """ ,"orphan":[$terminal] """) to "Unreachable"
        )) {
            val failure = invalid(json, "Invalid graph", reason)
            assertTrue(failure.cause is DialogueValidationException)
        }
    }

    @Test
    fun theSameChoiceIdCanBeReusedInDifferentPrompts() {
        val definition = parse(document(
            """{"type":"choices","options":[{"id":"go","key":"first","next":"second"}]}""",
            ""","second":[{"type":"choices","options":[{"id":"go","key":"second","next":"outro"}]}],"outro":[$terminal]"""
        ))
        assertEquals(3, definition.nodes.size)
        val first = definition.nodes.getValue("intro").actions.single() as DialogueAction.ShowChoices
        val second = definition.nodes.getValue("second").actions.single() as DialogueAction.ShowChoices
        assertEquals(first.options.single().id, second.options.single().id)
    }

    @Test
    fun loadRejectsUnsafeAndMissingPathsAndStillLoadsAValidAssetAfterFailure() {
        val prefix = JineAssets.Dialogues.DIALOGUES_DIRECTORY
        for (path in listOf(
            "../lang/es.json", "lang/es.json", "$prefix/../lang/es.json", "/$prefix/stream.json",
            "C:/$prefix/stream.json", "$prefix//stream.json", "$prefix/./stream.json",
            "$prefix/demo\\stream.json", "$prefix/stream.txt", "$prefix/stream\n.json", "$prefix/demo.json/"
        )) {
            val failure = assertThrows(DialogueContentException::class.java) { DialogueRepository.load(context, path) }
            assertEquals(path, failure.assetPath)
            assertTrue(failure.message.orEmpty().contains("relative .json path"))
        }
        val missing = "$prefix/demo/not_packaged.json"
        val failure = assertThrows(DialogueContentException::class.java) { DialogueRepository.load(context, missing) }
        assertEquals(missing, failure.assetPath)
        assertTrue(failure.cause is IOException)
        repeat(2) { assertEquals("stream", DialogueRepository.load(context, JineAssets.Dialogues.STREAM_DEMO).id) }
    }

    @Test
    fun packagedStreamMatchesEveryNodeActionAndChoiceOfTheKotlinReference() {
        val actual = DialogueRepository.load(context, JineAssets.Dialogues.STREAM_DEMO)
        val previous = LegacyStreamFixture.stream
        assertEquals(previous.id, actual.id)
        assertEquals(previous.entryNodeId, actual.entryNodeId)
        assertEquals(previous.nodes.keys, actual.nodes.keys)
        assertEquals(7, actual.nodes.size)
        for ((id, expectedNode) in previous.nodes) {
            val actualActions = actual.nodes.getValue(id).actions
            assertEquals("Action count in $id", expectedNode.actions.size, actualActions.size)
            for ((index, expected) in expectedNode.actions.withIndex()) {
                val action = actualActions[index]
                if (expected is DialogueAction.ShowChoices) {
                    assertTrue("$id[$index] should present choices", action is DialogueAction.ShowChoices)
                    assertEquals(expected.options, (action as DialogueAction.ShowChoices).options)
                } else {
                    assertEquals("$id[$index]", expected, action)
                }
            }
        }
    }

    @Test
    fun packagedStreamRunsTheFourCombinationsWithTheOriginalOrderAndVirtualTimes() = runTest {
        val definition = DialogueRepository.load(context, JineAssets.Dialogues.STREAM_DEMO)
        for (first in listOf("gaming", "talking")) {
            for (second in listOf("help", "later")) {
                val choices = ArrayDeque(listOf(first, second))
                val texts = mutableListOf<Pair<String, Long>>()
                val start = testScheduler.currentTime
                DialogueEngine.execute(
                    definition,
                    sendText = { texts.add(it.substringAfter("stream.") to (testScheduler.currentTime - start)) },
                    awaitChoice = { options ->
                        val expected = if (choices.size == 2) listOf("gaming", "talking") else listOf("help", "later")
                        assertEquals(expected, options.map { it.id })
                        choices.removeFirst()
                    }
                )
                assertEquals(
                    listOf("intro.01", "intro.02", "question.01", "$first.01", "question.02", "$second.01", "outro.01"),
                    texts.map { it.first }
                )
                assertEquals(listOf(0L, 700L, 1_400L, 1_400L, 2_100L, 2_100L, 2_800L), texts.map { it.second })
                assertTrue(choices.isEmpty())
            }
        }
    }

    private fun document(actions: String, extraNodes: String = ""): String =
        """{"schemaVersion":1,"id":"fixture","entry":"intro","nodes":{"intro":[$actions]$extraNodes} }"""

    private fun parse(json: String) = DialogueRepository.parse(fixturePath, json)

    private fun invalid(json: String, vararg details: String): DialogueContentException {
        val failure = assertThrows("Expected this JSON to fail: $json", DialogueContentException::class.java) {
            parse(json)
        }
        assertEquals(fixturePath, failure.assetPath)
        assertTrue(fixturePath in failure.message.orEmpty())
        for (detail in details) {
            assertTrue("Expected '$detail' in ${failure.message}", detail in failure.message.orEmpty())
        }
        return failure
    }
}
