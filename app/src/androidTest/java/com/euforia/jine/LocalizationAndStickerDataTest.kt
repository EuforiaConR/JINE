package com.euforia.jine

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.euforia.jine.core.assets.JineAssets
import com.euforia.jine.core.localization.JineLanguageManager
import com.euforia.jine.core.localization.LocalizationContentException
import com.euforia.jine.data.repository.StickerCatalogException
import com.euforia.jine.data.repository.StickerRepository
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
            listOf("ok", "love", "gomen", "pien", "sorena", "waritodoudemoii", "bujisyuuryou", "saikouka"),
            stickers.map { it.id }
        )
        assertEquals("textures/stickers/pchan/sticker_saikouka.png", stickers.last().texture)
        val manager = JineLanguageManager()
        for (language in listOf("es", "en")) {
            val catalog = manager.load(context, language)
            assertEquals(language, catalog.language)
            for (sticker in stickers) {
                assertTrue(catalog.translate(sticker.descriptionKey).isNotBlank())
                assertTrue(!catalog.translate(sticker.descriptionKey).startsWith("[missing: "))
            }
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
    fun invalidOrMissingTextureReportsStickerAndPath() {
        val paths = listOf("textures/stickers/../ui/windowbase_icon.png", "textures/stickers/pchan/missing.png")
        for (path in paths) {
            val failure = assertThrows(StickerCatalogException::class.java) {
                parsePack(sticker(texture = path))
            }
            val message = failure.message.orEmpty()
            assertTrue(message.contains("Sticker 'example'"))
            assertTrue(message.contains(path))
        }
    }

    private fun parsePack(vararg entries: JSONObject) = StickerRepository.parse(
        FIXTURE_PATH,
        """{"id":"pchan","stickers":[${entries.joinToString()}]}"""
    ) { path -> context.assets.open(path).close() }

    private fun sticker(
        id: String = "example",
        texture: String = "${JineAssets.Stickers.PCHAN_DIRECTORY}/sticker_pchan_ok.png",
        order: Int = 0
    ) = JSONObject()
        .put("id", id)
        .put("texture", texture)
        .put("description_key", "sticker.test.example")
        .put("emotion", "agreement")
        .put("order", order)

    private companion object {
        const val FIXTURE_PATH = "stickers/fixture.json"
    }
}
