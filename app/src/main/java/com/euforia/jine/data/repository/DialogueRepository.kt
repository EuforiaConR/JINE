package com.euforia.jine.data.repository

import android.content.Context
import android.util.JsonReader
import android.util.JsonToken
import com.euforia.jine.core.assets.JineAssetManager
import com.euforia.jine.core.assets.JineAssets
import com.euforia.jine.data.model.dialogue.DialogueAction
import com.euforia.jine.data.model.dialogue.DialogueDefinition
import com.euforia.jine.data.model.dialogue.DialogueNode
import com.euforia.jine.data.model.dialogue.DialogueOption
import com.euforia.jine.data.model.dialogue.DialogueValidationException
import java.io.IOException
import java.io.StringReader

internal class DialogueContentException(
    val assetPath: String,
    details: String,
    cause: Throwable? = null
) : IllegalStateException("Invalid dialogue '$assetPath': $details", cause)

internal object DialogueRepository {
    fun load(context: Context, assetPath: String): DialogueDefinition {
        val directory = JineAssets.Dialogues.DIALOGUES_DIRECTORY
        if (!assetPath.startsWith("$directory/") || !assetPath.endsWith(".json") ||
            assetPath.any { it == '\\' || it.isISOControl() } ||
            assetPath.split('/').any { it.isEmpty() || it == "." || it == ".." }
        ) {
            throw DialogueContentException(assetPath, "Expected a relative .json path under $directory/")
        }
        val json = try {
            JineAssetManager.getText(context, assetPath)
        } catch (error: IOException) {
            throw DialogueContentException(assetPath, "Could not read asset", error)
        }
        return parse(assetPath, json)
    }

    fun parse(assetPath: String, json: String): DialogueDefinition {
        checkJsonStrings(assetPath, json)
        return try {
            JsonReader(StringReader(json)).use { reader ->
                reader.isLenient = false
                Parser(assetPath, reader).readDefinition()
            }
        } catch (error: IOException) {
            throw DialogueContentException(assetPath, "Invalid JSON: ${error.message}", error)
        } catch (error: DialogueValidationException) {
            throw DialogueContentException(assetPath, "Invalid graph: ${error.message}", error)
        }
    }

    // Android's JsonReader also accepts unescaped controls and non-JSON escapes inside quoted strings.
    // Check those lexical rules here. JsonReader still owns tokenization and the grammar.
    private fun checkJsonStrings(assetPath: String, json: String) {
        fun fail(index: Int, details: String): Nothing =
            throw DialogueContentException(assetPath, "Character ${index + 1}: $details")

        if (json.startsWith('\uFEFF')) fail(0, "Unexpected byte order mark")
        var quoted = false
        var index = 0
        while (index < json.length) {
            val char = json[index]
            if (char == '"') {
                quoted = !quoted
            } else if (quoted && char == '\\') {
                index++
                when (json.getOrNull(index)) {
                    '"', '\\', '/', 'b', 'f', 'n', 'r', 't' -> Unit
                    'u' -> {
                        repeat(4) {
                            index++
                            val digit = json.getOrNull(index)
                            if (digit == null || (digit !in '0'..'9' && digit !in 'a'..'f' && digit !in 'A'..'F')) {
                                fail(index, "Expected four hexadecimal digits in a Unicode escape")
                            }
                        }
                    }
                    else -> fail(index, "Invalid JSON escape")
                }
            } else if (char < ' ' && (quoted || char !in "\t\r\n")) {
                fail(index, "Unexpected control character")
            }
            index++
        }
    }

    private class Parser(private val assetPath: String, private val reader: JsonReader) {
        fun readDefinition(): DialogueDefinition {
            var version: Long? = null
            var id: String? = null
            var entry: String? = null
            var nodes: Map<String, DialogueNode>? = null
            readObject("$") { name, path ->
                when (name) {
                    "schemaVersion" -> {
                        val number = readInteger(path)
                        if (number != 1L) fail(path, "Unsupported schema version $number. Expected 1")
                        version = number
                    }
                    "id" -> id = readString(path)
                    "entry" -> entry = readString(path)
                    "nodes" -> nodes = buildMap {
                        readObject(path) { nodeId, nodePath ->
                            put(nodeId, DialogueNode(readArray(nodePath, ::readAction)))
                        }
                    }
                    else -> fail(path, "Unknown field")
                }
            }
            expect("$", JsonToken.END_DOCUMENT)
            required("$.schemaVersion", version)
            return DialogueDefinition(required("$.id", id), required("$.entry", entry), required("$.nodes", nodes))
        }

        private fun readAction(path: String): DialogueAction {
            var type: String? = null
            var key: String? = null
            var duration: Long? = null
            var options: List<DialogueOption>? = null
            var next: String? = null
            val fields = readObject(path) { name, field ->
                when (name) {
                    "type" -> type = readString(field)
                    "key" -> key = readString(field)
                    "durationMs" -> duration = readInteger(field)
                    "options" -> options = readArray(field, ::readOption)
                    "next" -> next = readString(field)
                    else -> fail(field, "Unknown field")
                }
            }
            val actionType = required("$path.type", type)
            fun allowOnly(payload: String? = null) {
                fields.firstOrNull { it != "type" && it != payload }?.let {
                    fail("$path.$it", "Unexpected field for action '$actionType'")
                }
            }
            return when (actionType) {
                "text" -> {
                    allowOnly("key")
                    DialogueAction.SendText(required("$path.key", key))
                }
                "wait" -> {
                    allowOnly("durationMs")
                    DialogueAction.Wait(required("$path.durationMs", duration))
                }
                "choices" -> {
                    allowOnly("options")
                    DialogueAction.ShowChoices(required("$path.options", options))
                }
                "jump" -> {
                    allowOnly("next")
                    DialogueAction.Jump(required("$path.next", next))
                }
                "end" -> {
                    allowOnly()
                    DialogueAction.End
                }
                else -> fail("$path.type", "Unknown action '$actionType'")
            }
        }

        private fun readOption(path: String): DialogueOption {
            var id: String? = null
            var key: String? = null
            var next: String? = null
            readObject(path) { name, field ->
                when (name) {
                    "id" -> id = readString(field)
                    "key" -> key = readString(field)
                    "next" -> next = readString(field)
                    else -> fail(field, "Unknown field")
                }
            }
            return DialogueOption(required("$path.id", id), required("$path.key", key), required("$path.next", next))
        }

        private fun readObject(path: String, readField: (name: String, path: String) -> Unit): Set<String> {
            expect(path, JsonToken.BEGIN_OBJECT)
            reader.beginObject()
            val seen = mutableSetOf<String>()
            while (reader.hasNext()) {
                val name = reader.nextName()
                val fieldPath = "$path.$name"
                if (!seen.add(name)) fail(fieldPath, "Duplicate property")
                readField(name, fieldPath)
            }
            reader.endObject()
            return seen
        }

        private fun <T> readArray(path: String, readElement: (String) -> T): List<T> {
            expect(path, JsonToken.BEGIN_ARRAY)
            reader.beginArray()
            val elements = mutableListOf<T>()
            while (reader.hasNext()) elements.add(readElement("$path[${elements.size}]"))
            reader.endArray()
            return elements
        }

        private fun readString(path: String): String {
            expect(path, JsonToken.STRING)
            return reader.nextString().also { if (it.isBlank()) fail(path, "Expected a non-blank string") }
        }

        private fun readInteger(path: String): Long {
            expect(path, JsonToken.NUMBER)
            val raw = reader.nextString()
            val number = raw.toLongOrNull()
            if (!jsonInteger.matches(raw) || number == null || number < 0L) {
                fail(path, "Expected a non-negative JSON integer within Long range")
            }
            return number
        }

        private fun expect(path: String, token: JsonToken) {
            val actual = reader.peek()
            if (actual != token) fail(path, "Expected $token, found $actual")
        }

        private fun <T : Any> required(path: String, value: T?): T = value ?: fail(path, "Missing required field")

        private fun fail(path: String, details: String): Nothing =
            throw DialogueContentException(assetPath, "$path: $details")
    }

    private val jsonInteger = Regex("-?(0|[1-9][0-9]*)")
}
