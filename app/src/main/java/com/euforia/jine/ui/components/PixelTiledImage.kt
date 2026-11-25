package com.euforia.jine.ui.components

import androidx.annotation.DrawableRes
import androidx.compose.foundation.Canvas
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.res.imageResource
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import kotlin.math.roundToInt

@Composable
fun PixelTiledImage(
    @DrawableRes resourceId: Int,
    modifier: Modifier = Modifier,
    scale: Float = 1f
) {
    val bitmap = ImageBitmap.imageResource(resourceId)

    require(scale.isFinite() && scale > 0f)

    Canvas(
        modifier = modifier.clipToBounds()
    ) {

        val pixelScale = scale.dp.toPx()

        val tileWidth = (bitmap.width * pixelScale)
            .roundToInt()
            .coerceAtLeast(1)

        val tileHeight = (bitmap.height * pixelScale)
            .roundToInt()
            .coerceAtLeast(1)

        val canvasWidth = size.width.roundToInt()
        val canvasHeight = size.height.roundToInt()

        for (y in 0 until canvasHeight step tileHeight) {
            for (x in 0 until canvasWidth step tileWidth) {

                drawImage(
                    image = bitmap,
                    srcOffset = IntOffset.Zero,
                    srcSize = IntSize(
                        bitmap.width,
                        bitmap.height
                    ),
                    dstOffset = IntOffset(x, y),
                    dstSize = IntSize(
                        tileWidth,
                        tileHeight
                    ),
                    filterQuality = FilterQuality.None
                )
            }
        }
    }
}