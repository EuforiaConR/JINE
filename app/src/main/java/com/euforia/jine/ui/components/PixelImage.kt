package com.euforia.jine.ui.components

import androidx.compose.foundation.Image
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.layout.ContentScale
import com.euforia.jine.core.assets.rememberAssetTexture

@Composable
fun PixelImage(
    path: String,
    modifier: Modifier = Modifier,
    contentDescription: String? = null,
    tint: Color? = null,
    contentScale: ContentScale = ContentScale.Fit
) {
    val bitmap = rememberAssetTexture(path)

    Image(
        bitmap = bitmap,
        contentDescription = contentDescription,
        modifier = modifier,
        contentScale = contentScale,
        filterQuality = FilterQuality.None,
        colorFilter = tint?.let {
            ColorFilter.tint(it)
        }
    )
}
