package com.euforia.jine.data.repository

import android.content.Context
import com.euforia.jine.core.assets.JineAssetManager
import com.euforia.jine.core.assets.JineAssets
import com.euforia.jine.data.model.Sticker
import com.euforia.jine.data.model.StickerPack
import org.json.JSONArray
import org.json.JSONException
import org.json.JSONObject
import java.io.IOException

class StickerCatalogException(catalogPath: String, details: String, cause: Exception? = null) :
    IllegalStateException("Invalid sticker catalog '$catalogPath': $details", cause)

object StickerRepository {
    fun load(context: Context, catalogPath: String = JineAssets.Stickers.PCHAN_CATALOG): List<Sticker> =
        load(context, StickerPack.PCHAN, catalogPath)

    internal fun load(context: Context, pack: StickerPack, catalogPath: String = pack.catalogPath): List<Sticker> {
        val json = try {
            JineAssetManager.getText(context, catalogPath)
        } catch (error: IOException) {
            throw StickerCatalogException(catalogPath, "Could not read catalog", error)
        }

        return parse(catalogPath, json, pack) { path ->
            context.assets.open(path).close()
        }
    }

    internal fun parse(
        catalogPath: String,
        json: String,
        expectedPack: StickerPack = StickerPack.PCHAN,
        checkTextureExists: (String) -> Unit
    ): List<Sticker> {
        val catalog = try {
            JSONObject(json)
        } catch (error: JSONException) {
            throw StickerCatalogException(catalogPath, "Invalid JSON object", error)
        }

        if (catalog.opt("id") != expectedPack.id) {
            throw StickerCatalogException(catalogPath, "Expected pack ID '${expectedPack.id}'")
        }
        val entries = catalog.opt("stickers") as? JSONArray
            ?: throw StickerCatalogException(catalogPath, "'stickers' must be an array")

        val ids = mutableSetOf<String>()
        val orders = mutableSetOf<Int>()
        val stickers = List(entries.length()) { index ->
            val entry = entries.optJSONObject(index)
                ?: throw StickerCatalogException(catalogPath, "Entry #$index must be an object")
            val sticker = try {
                readSticker(entry)
            } catch (error: JSONException) {
                throw StickerCatalogException(catalogPath, "Entry #$index: ${error.message}", error)
            }

            if (!ids.add(sticker.id)) {
                throw StickerCatalogException(catalogPath, "Duplicate sticker ID '${sticker.id}'")
            }
            if (!orders.add(sticker.order)) {
                throw StickerCatalogException(
                    catalogPath,
                    "Sticker '${sticker.id}' uses duplicate order ${sticker.order}"
                )
            }
            validateTextures(catalogPath, expectedPack, sticker, checkTextureExists)
            sticker
        }

        return stickers.sortedBy { it.order }
    }

    private fun readSticker(entry: JSONObject): Sticker {
        val id = entry.requiredString("id")
        val order = entry.opt("order") as? Int
        if (order == null || order < 0) {
            throw JSONException("Sticker '$id' must have a non-negative integer 'order'")
        }

        return Sticker(
            id = id,
            textures = readTextures(entry, id),
            descriptionKey = entry.requiredString("description_key"),
            emotion = entry.requiredString("emotion"),
            order = order
        )
    }

    private fun readTextures(entry: JSONObject, stickerId: String): Map<String, String> {
        val variants = entry.opt("textures") as? JSONObject
            ?: throw JSONException("Sticker '$stickerId': 'textures' must be an object")

        val textures = buildMap {
            for (language in variants.keys()) {
                val path = variants.opt(language)
                if (language.isBlank() || path !is String || path.isBlank()) {
                    throw JSONException("Sticker '$stickerId': invalid texture for language '$language'")
                }
                put(language, path)
            }
        }
        if ("es" !in textures) {
            throw JSONException("Sticker '$stickerId': missing default texture 'es'")
        }
        return textures
    }

    private fun JSONObject.requiredString(field: String): String {
        val value = opt(field)
        if (value !is String || value.isBlank()) {
            throw JSONException("'$field' must be a non-blank string")
        }
        return value
    }

    private fun validateTextures(
        catalogPath: String,
        expectedPack: StickerPack,
        sticker: Sticker,
        checkTextureExists: (String) -> Unit
    ) {
        for ((language, path) in sticker.textures) {
            val validPath = path.startsWith("${expectedPack.textureDirectory}/") &&
                path.endsWith(".png") &&
                '\\' !in path &&
                path.split('/').none { it.isEmpty() || it == "." || it == ".." }

            if (!validPath) {
                throw StickerCatalogException(
                    catalogPath,
                    "Sticker '${sticker.id}' has invalid texture '$path' for '$language'"
                )
            }

            try {
                checkTextureExists(path)
            } catch (error: IOException) {
                throw StickerCatalogException(
                    catalogPath,
                    "Sticker '${sticker.id}' could not open texture '$path' for '$language'",
                    error
                )
            }
        }
    }
}
