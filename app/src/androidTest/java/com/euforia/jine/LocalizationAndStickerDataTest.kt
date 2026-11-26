package com.euforia.jine

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.euforia.jine.core.assets.JineAssetManager
import com.euforia.jine.core.assets.JineAssets
import com.euforia.jine.core.localization.JineLanguageManager
import com.euforia.jine.core.localization.LocalizationContentException
import com.euforia.jine.data.model.Message
import com.euforia.jine.data.model.MessageContent
import com.euforia.jine.data.model.MessageSender
import com.euforia.jine.data.model.PCHAN_PACK_ID
import com.euforia.jine.data.repository.StickerCatalogException
import com.euforia.jine.data.repository.StickerRepository
import com.euforia.jine.ui.screens.GENERIC_DEMO_REPLY
import com.euforia.jine.ui.screens.StickerBatch
import com.euforia.jine.ui.screens.demoReplies
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import java.io.FileNotFoundException

@RunWith(AndroidJUnit4::class)
class LocalizationAndStickerDataTest {
    private val context get() = InstrumentationRegistry.getInstrumentation().targetContext

    @Test
    fun loadsAllPackagedStickersInDisplayOrderWithTranslations() {
        val stickers = StickerRepository.load(context)
        assertEquals(
            listOf("ok", "saikouka", "pien", "waritodoudemoii", "gomen", "bujishibou", "love", "sorena"),
            stickers.map { it.id }
        )
        assertEquals((0..7).toList(), stickers.map { it.order })

        for (sticker in stickers) {
            assertEquals(setOf("es", "en"), sticker.textures.keys)
            assertEquals(sticker.getTexture("es"), sticker.getTexture("fr"))
        }

        val manager = JineLanguageManager()
        for (language in listOf("es", "en")) {
            val catalog = manager.load(context, language)
            assertEquals(language, catalog.language)
            for (sticker in stickers) {
                val expectedPath = "${JineAssets.Stickers.PCHAN_DIRECTORY}/$language/${sticker.id}.png"
                assertEquals(expectedPath, sticker.getTexture(language))
                context.assets.open(expectedPath).use { stream -> assertTrue(stream.read() >= 0) }

                val description = catalog.translate(sticker.descriptionKey)
                assertTrue(description.isNotBlank())
                assertTrue(!description.startsWith("[missing: "))
            }
        }
    }

    @Test
    fun everyDemoReplyHasItsOwnTranslationInBothAssetCatalogs() {
        val singleBatches = StickerRepository.load(context).map { listOf(it.id) }
        val groupedBatches = listOf(
            listOf("love", "love"),
            listOf("ok", "ok"),
            listOf("ok", "gomen"),
            List(5) { "ok" }
        )
        val keys = (singleBatches + groupedBatches).flatMap { ids ->
            val messages = ids.mapIndexed { index, id ->
                Message("send-$index", MessageSender.PCHAN, MessageContent.Sticker(PCHAN_PACK_ID, id), 0L, 650L)
            }
            demoReplies(StickerBatch(messages))
        } + GENERIC_DEMO_REPLY
        assertEquals(14, keys.distinct().size)
        for (path in listOf(JineAssets.Languages.SPANISH, JineAssets.Languages.ENGLISH)) {
            val catalog = JSONObject(JineAssetManager.getText(context, path))
            for (key in keys) {
                assertTrue("Missing translation for $key in $path", (catalog.opt(key) as? String)?.isNotBlank() == true)
            }
        }
    }

    @Test
    fun narrativePreviewKeysHaveNonBlankTranslationsInBothAssetCatalogs() {
        val prefix = "dialogue.preview.narrative."
        val keys = setOf("intro", "question", "choice.gaming", "choice.talking", "choice.long", "reply")
            .map { prefix + it }.toSet()
        for (path in listOf(JineAssets.Languages.SPANISH, JineAssets.Languages.ENGLISH)) {
            val catalog = JSONObject(JineAssetManager.getText(context, path))
            assertEquals(keys, catalog.keys().asSequence().filter { it.startsWith(prefix) }.toSet())
            assertTrue(keys.all { catalog.getString(it).isNotBlank() })
        }
    }

    @Test
    fun missingEnglishKeyFallsBackAndDictionaryLoadsOnlyOnce() {
        val manager = JineLanguageManager()
        val reads = mutableListOf<String>()
        val read: (String) -> String = { path ->
            reads.add(path)
            if (path == JineAssets.Languages.SPANISH) {
                """{"title":"Hola","spanish_only":"Está en español"}"""
            } else {
                """{"title":"Hello"}"""
            }
        }
        val english = manager.load("en", read)
        assertEquals("Hello", english.translate("title"))
        assertEquals("Está en español", english.translate("spanish_only"))
        assertEquals("[missing: unknown]", english.translate("unknown"))
        assertEquals("Hola", manager.load("es", read).translate("title"))
        assertSame(english, manager.load("en", read))
        assertEquals(listOf(JineAssets.Languages.SPANISH, JineAssets.Languages.ENGLISH), reads)
    }

    @Test
    fun unknownLanguageReadsOnlySpanish() {
        val spanish = JineLanguageManager().load("fr") { path ->
            assertEquals(JineAssets.Languages.SPANISH, path)
            """{"key":"Hola"}"""
        }
        assertEquals("es", spanish.language)
        assertEquals("Hola", spanish.translate("key"))
    }

    @Test
    fun missingOrInvalidEnglishUsesSpanishForTheWholeCatalog() {
        val optionalCatalogs = listOf<String?>(null, "{", """{"key":42}""")
        for (english in optionalCatalogs) {
            val catalog = JineLanguageManager().load("en") { path ->
                if (path == JineAssets.Languages.SPANISH) """{"key":"Hola"}"""
                else english ?: throw FileNotFoundException(path)
            }
            assertEquals("es", catalog.language)
            assertEquals("Hola", catalog.translate("key"))
        }
    }

    @Test
    fun invalidSpanishReportsItsPathAndRejectsNonStringValues() {
        val invalidBases = listOf<String?>(null, "{", """{"": "text"}""", """{"key":null}""", """{"key":{}}""")
        for (invalidBase in invalidBases) {
            val failure = assertThrows(LocalizationContentException::class.java) {
                JineLanguageManager().load("es") { path ->
                    invalidBase ?: throw FileNotFoundException(path)
                }
            }
            assertTrue(failure.message.orEmpty().contains(JineAssets.Languages.SPANISH))
        }
    }

    @Test
    fun emptyPackIsValidAndOrderDependsOnMetadata() {
        assertTrue(parsePack().isEmpty())
        val entries = parsePack(sticker(id = "first", order = 8), sticker(id = "second", order = 2))
        assertEquals(listOf("second", "first"), entries.map { it.id })
    }

    @Test
    fun malformedMetadataIsRejectedWithCatalogContext() {
        val cases = listOf(
            arrayOf(sticker().apply { remove("description_key") }) to "description_key",
            arrayOf(sticker().put("emotion", 7)) to "emotion",
            arrayOf(sticker(), sticker(order = 1)) to "Duplicate sticker ID",
            arrayOf(sticker(), sticker(id = "second")) to "duplicate order",
            arrayOf(sticker(order = -1)) to "non-negative integer",
            arrayOf(sticker().put("order", "1")) to "non-negative integer"
        )
        for ((entries, expectedMessage) in cases) {
            val failure = assertThrows(StickerCatalogException::class.java) { parsePack(*entries) }
            assertTrue(failure.message.orEmpty().contains(expectedMessage))
            assertTrue(failure.message.orEmpty().contains(FIXTURE_PATH))
        }
        val wrongPack = assertThrows(StickerCatalogException::class.java) {
            StickerRepository.parse(FIXTURE_PATH, """{"id":"other","stickers":[]}""") { }
        }
        assertTrue(wrongPack.message.orEmpty().contains("Expected pack ID 'pchan'"))
    }

    @Test
    fun spanishOnlyStickerFallsBackForUnlistedLanguages() {
        val sticker = parsePack(sticker()).single()
        assertEquals(mapOf("es" to SPANISH_TEXTURE), sticker.textures)
        assertEquals(SPANISH_TEXTURE, sticker.getTexture("en"))
        assertEquals(SPANISH_TEXTURE, sticker.getTexture("fr"))
    }

    @Test
    fun validatesEveryDeclaredVariantIncludingAdditionalLanguages() {
        val japaneseTexture = "${JineAssets.Stickers.PCHAN_DIRECTORY}/ja/ok.png"
        val textures = JSONObject()
            .put("es", SPANISH_TEXTURE)
            .put("en", ENGLISH_TEXTURE)
            .put("ja", japaneseTexture)
        val checkedPaths = mutableListOf<String>()
        val sticker = parsePack(sticker(textures = textures), checkTextureExists = checkedPaths::add).single()

        assertEquals(3, checkedPaths.size)
        assertEquals(setOf(SPANISH_TEXTURE, ENGLISH_TEXTURE, japaneseTexture), checkedPaths.toSet())
        assertEquals(SPANISH_TEXTURE, sticker.getTexture("es"))
        assertEquals(ENGLISH_TEXTURE, sticker.getTexture("en"))
        assertEquals(japaneseTexture, sticker.getTexture("ja"))
    }

    @Test
    fun texturesMustBePresentAndAnObject() {
        val noTextures = sticker().apply { remove("textures") }
        val wrongTypes = listOf(SPANISH_TEXTURE, JSONArray(), 42, JSONObject.NULL)
        for (entry in listOf(noTextures) + wrongTypes.map { sticker().put("textures", it) }) {
            val failure = assertThrows(StickerCatalogException::class.java) { parsePack(entry) }
            assertTrue(failure.message.orEmpty().contains("'textures' must be an object"))
        }
    }

    @Test
    fun texturesRequireSpanishAndNonBlankStringVariants() {
        val withoutSpanish = listOf(JSONObject(), JSONObject().put("en", ENGLISH_TEXTURE))
        for (textures in withoutSpanish) {
            val failure = assertThrows(StickerCatalogException::class.java) {
                parsePack(sticker(textures = textures))
            }
            assertTrue(failure.message.orEmpty().contains("missing default texture 'es'"))
        }

        val invalidVariants = listOf("" to ENGLISH_TEXTURE, "en" to "", "en" to 42, "en" to JSONObject.NULL)
        for ((language, variant) in invalidVariants) {
            val textures = JSONObject().put("es", SPANISH_TEXTURE).put(language, variant)
            val failure = assertThrows(StickerCatalogException::class.java) {
                parsePack(sticker(textures = textures))
            }
            assertTrue(failure.message.orEmpty().contains("invalid texture for language '$language'"))
        }
    }

    @Test
    fun missingSpanishOrEnglishTextureReportsFullContext() {
        for (language in listOf("es", "en")) {
            val path = "${JineAssets.Stickers.PCHAN_DIRECTORY}/$language/missing.png"
            val textures = JSONObject().put("es", SPANISH_TEXTURE).put(language, path)
            val failure = assertThrows(StickerCatalogException::class.java) {
                parsePack(sticker(textures = textures))
            }
            assertTextureErrorContext(failure, language, path)
            assertTrue(failure.message.orEmpty().contains("could not open texture"))
        }
    }

    @Test
    fun invalidPathsAreRejectedBeforeOpeningTheVariant() {
        val paths = listOf(
            "textures/ui/windowbase_icon.png",
            "textures/stickers/../ui/windowbase_icon.png",
            "${JineAssets.Stickers.PCHAN_DIRECTORY}/en/./ok.png",
            "${JineAssets.Stickers.PCHAN_DIRECTORY}/en//ok.png",
            "${JineAssets.Stickers.PCHAN_DIRECTORY}/en\\ok.png",
            "${JineAssets.Stickers.PCHAN_DIRECTORY}/en/ok.jpg"
        )
        for (path in paths) {
            val textures = JSONObject().put("es", SPANISH_TEXTURE).put("en", path)
            val failure = assertThrows(StickerCatalogException::class.java) {
                parsePack(sticker(textures = textures)) { openedPath ->
                    assertEquals(SPANISH_TEXTURE, openedPath)
                }
            }
            assertTextureErrorContext(failure, "en", path)
            assertTrue(failure.message.orEmpty().contains("invalid texture"))
        }
    }

    private fun assertTextureErrorContext(failure: StickerCatalogException, language: String, path: String) {
        val message = failure.message.orEmpty()
        assertTrue(message.contains("'$FIXTURE_PATH'"))
        assertTrue(message.contains("Sticker 'example'"))
        assertTrue(message.contains("'$language'"))
        assertTrue(message.contains("'$path'"))
    }

    private fun parsePack(
        vararg entries: JSONObject,
        checkTextureExists: (String) -> Unit = { path -> context.assets.open(path).close() }
    ) = StickerRepository.parse(
        FIXTURE_PATH,
        """{"id":"pchan","stickers":[${entries.joinToString()}]}""",
        checkTextureExists
    )

    private fun sticker(
        id: String = "example",
        textures: JSONObject = JSONObject().put("es", SPANISH_TEXTURE),
        order: Int = 0
    ) = JSONObject()
        .put("id", id)
        .put("textures", textures)
        .put("description_key", "sticker.test.example")
        .put("emotion", "agreement")
        .put("order", order)

    private companion object {
        const val FIXTURE_PATH = "stickers/fixture.json"
        const val SPANISH_TEXTURE = "${JineAssets.Stickers.PCHAN_DIRECTORY}/es/ok.png"
        const val ENGLISH_TEXTURE = "${JineAssets.Stickers.PCHAN_DIRECTORY}/en/ok.png"
    }
}
