package com.euforia.jine.ui.components

import androidx.annotation.DrawableRes
import androidx.compose.foundation.Image
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.imageResource

@Composable
fun PixelImage(
    @DrawableRes resourceId: Int,
    modifier: Modifier = Modifier,
    contentDescription: String? = null,
    tint: Color? = null,
    contentScale: ContentScale = ContentScale.Fit
) {
    Image(
        bitmap = ImageBitmap.imageResource(resourceId),
        contentDescription = contentDescription,
        modifier = modifier,
        contentScale = contentScale,
        filterQuality = FilterQuality.None,
        colorFilter = tint?.let {
            ColorFilter.tint(it)
        }
    )
}