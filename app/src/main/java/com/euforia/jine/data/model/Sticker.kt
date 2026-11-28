package com.euforia.jine.data.model

import com.euforia.jine.core.assets.JineAssets

internal const val PCHAN_PACK_ID = "pchan"
internal const val AME_PACK_ID = "ame"

internal enum class StickerPack(val id: String, val catalogPath: String, val textureDirectory: String) {
    PCHAN(PCHAN_PACK_ID, JineAssets.Stickers.PCHAN_CATALOG, JineAssets.Stickers.PCHAN_DIRECTORY),
    AME(AME_PACK_ID, JineAssets.Stickers.AME_CATALOG, JineAssets.Stickers.AME_DIRECTORY)
}

data class Sticker(
    val id: String,
    val textures: Map<String, String>,
    val descriptionKey: String,
    val emotion: String,
    val order: Int
) {
    fun getTexture(language: String): String {
        return textures[language]
            ?: textures["es"]
            ?: error("Missing default texture for sticker: $id")
    }
}
