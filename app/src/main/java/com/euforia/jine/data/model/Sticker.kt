package com.euforia.jine.data.model

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
