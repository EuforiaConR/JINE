package com.euforia.jine.core.localization

import android.content.Context
import android.util.Log
import com.euforia.jine.core.assets.JineAssetManager
import com.euforia.jine.core.assets.JineAssets
import org.json.JSONException
import org.json.JSONObject
import java.io.IOException
import java.util.concurrent.ConcurrentHashMap

private const val LOG_TAG = "JineLocalization"

class LocalizationSnapshot internal constructor(
    val language: String,
    private val translations: Map<String, String>,
    private val spanish: LocalizationSnapshot? = null
) {
    private val reportedMissingKeys = ConcurrentHashMap.newKeySet<String>()

    fun translate(key: String): String {
        return translations[key] ?: spanish?.translations?.get(key) ?: missingTranslation(key)
    }

    private fun missingTranslation(key: String): String {
        if (reportedMissingKeys.add(key)) {
            Log.w(LOG_TAG, "Missing translation '$key' in $language and Spanish")
        }
        return "[missing: $key]"
    }
}

class LocalizationContentException(path: String, cause: Exception) :
    IllegalStateException("Invalid language catalog '$path': ${cause.message}", cause)

class JineLanguageManager {
    companion object {
        const val DEFAULT_LANGUAGE = "es"
        const val ENGLISH = "en"
    }

    private val catalogs = mutableMapOf<String, LocalizationSnapshot>()

    fun load(context: Context, language: String): LocalizationSnapshot {
        return load(language) { path -> JineAssetManager.getText(context, path) }
    }

    // The reader is used synchronously. Only parsed dictionaries are kept.
    @Synchronized
    internal fun load(language: String, readText: (String) -> String): LocalizationSnapshot {
        val spanish = catalogs.getOrPut(DEFAULT_LANGUAGE) {
            val translations = readDictionary(JineAssets.Languages.SPANISH, readText)
            LocalizationSnapshot(DEFAULT_LANGUAGE, translations)
        }

        if (language == DEFAULT_LANGUAGE) return spanish
        if (language != ENGLISH) {
            Log.w(LOG_TAG, "Unsupported language '$language'; using Spanish")
            return spanish
        }

        return catalogs.getOrPut(ENGLISH) {
            try {
                val englishTranslations = readDictionary(JineAssets.Languages.ENGLISH, readText)
                LocalizationSnapshot(ENGLISH, englishTranslations, spanish)
            } catch (error: LocalizationContentException) {
                Log.w(LOG_TAG, "Using Spanish instead of '$language'", error)
                spanish
            }
        }
    }

    private fun readDictionary(path: String, readText: (String) -> String): Map<String, String> {
        try {
            val json = JSONObject(readText(path))
            return buildMap {
                for (key in json.keys()) {
                    val value = json.get(key)
                    if (key.isBlank() || value !is String) {
                        throw JSONException("Expected a non-blank key and a string value for '$key'")
                    }
                    put(key, value)
                }
            }
        } catch (error: IOException) {
            throw LocalizationContentException(path, error)
        } catch (error: JSONException) {
            throw LocalizationContentException(path, error)
        }
    }
}
