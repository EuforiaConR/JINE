package com.euforia.jine.ui.components

import androidx.annotation.DrawableRes
import androidx.compose.foundation.Canvas
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.res.imageResource
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import kotlin.math.roundToInt

@Composable
fun NineSliceImage(
    @DrawableRes resourceId: Int,
    sliceSize: Int,
    cornerSize: Dp,
    modifier: Modifier = Modifier,
    tint: Color? = null
) {
    val bitmap = ImageBitmap.imageResource(resourceId)

    require(
        sliceSize > 0 &&
                sliceSize * 2 < bitmap.width &&
                sliceSize * 2 < bitmap.height
    ) {
        "Invalid Nine-Slice dimensions"
    }

    val colorFilter = tint?.let {
        ColorFilter.tint(it)
    }

    Canvas(modifier = modifier) {

        val width = size.width.roundToInt()
        val height = size.height.roundToInt()

        val corner = cornerSize.toPx().roundToInt()
            .coerceAtLeast(0)

        val cornerX = corner.coerceAtMost(width / 2)
        val cornerY = corner.coerceAtMost(height / 2)

        // Coordenadas del sprite original.
        val srcX = intArrayOf(
            0,
            sliceSize,
            bitmap.width - sliceSize,
            bitmap.width
        )

        val srcY = intArrayOf(
            0,
            sliceSize,
            bitmap.height - sliceSize,
            bitmap.height
        )

        // Coordenadas del área de destino.
        val dstX = intArrayOf(
            0,
            cornerX,
            width - cornerX,
            width
        )

        val dstY = intArrayOf(
            0,
            cornerY,
            height - cornerY,
            height
        )

        // Dibujar las nueve regiones.
        for (row in 0 until 3) {
            for (column in 0 until 3) {

                val dstWidth = dstX[column + 1] - dstX[column]
                val dstHeight = dstY[row + 1] - dstY[row]

                if (dstWidth <= 0 || dstHeight <= 0) {
                    continue
                }

                drawImage(
                    image = bitmap,

                    srcOffset = IntOffset(
                        srcX[column],
                        srcY[row]
                    ),

                    srcSize = IntSize(
                        srcX[column + 1] - srcX[column],
                        srcY[row + 1] - srcY[row]
                    ),

                    dstOffset = IntOffset(
                        dstX[column],
                        dstY[row]
                    ),

                    dstSize = IntSize(
                        dstWidth,
                        dstHeight
                    ),

                    colorFilter = colorFilter,
                    filterQuality = FilterQuality.None
                )
            }
        }
    }
}