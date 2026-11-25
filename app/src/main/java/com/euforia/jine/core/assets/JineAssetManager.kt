package com.euforia.jine.core.assets

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.util.LruCache
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext

object JineAssetManager {
    private const val MAX_CACHE_SIZE_BYTES = 8 * 1024 * 1024

    private val textureCache = object : LruCache<String, ImageBitmap>(MAX_CACHE_SIZE_BYTES) {
        override fun sizeOf(key: String, value: ImageBitmap): Int {
            return value.width * value.height * 4
        }
    }

    @Synchronized
    fun getTexture(context: Context, path: String): ImageBitmap {
        textureCache.get(path)?.let {
            return it
        }

        val options = BitmapFactory.Options().apply {
            inScaled = false
            inPreferredConfig = Bitmap.Config.ARGB_8888
        }
        val bitmap = context.assets.open(path).use { stream ->
            BitmapFactory.decodeStream(stream, null, options)
        }
        val texture = checkNotNull(bitmap) {
            "Unable to decode texture: $path"
        }.asImageBitmap()

        textureCache.put(path, texture)
        return texture
    }

    fun getText(context: Context, path: String): String {
        return context.assets.open(path)
            .bufferedReader(Charsets.UTF_8)
            .use { it.readText() }
    }

    // AssetManager.list returns immediate entries, which may include subdirectories.
    fun listFiles(context: Context, directory: String): List<String> {
        val folder = directory.trimEnd('/')

        return context.assets.list(folder)
            .orEmpty()
            .sorted()
            .map { name ->
                if (folder.isEmpty()) name else "$folder/$name"
            }
    }

    @Synchronized
    fun clearCache() {
        textureCache.evictAll()
    }
}

@Composable
fun rememberAssetTexture(path: String): ImageBitmap {
    val context = LocalContext.current

    return remember(context, path) {
        JineAssetManager.getTexture(context, path)
    }
}
