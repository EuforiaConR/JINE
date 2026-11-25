package com.euforia.jine.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import com.euforia.jine.core.assets.rememberAssetTexture
import kotlin.math.roundToInt

data class NineSliceInsets(
    val left: Int,
    val top: Int,
    val right: Int,
    val bottom: Int
) {
    constructor(size: Int) : this(size, size, size, size)
}

@Composable
fun NineSliceImage(
    path: String,
    slices: NineSliceInsets,
    modifier: Modifier = Modifier,
    scale: Float = 1f,
    tint: Color? = null
) {
    val bitmap = rememberAssetTexture(path)

    require(
        slices.left >= 0 &&
                slices.top >= 0 &&
                slices.right >= 0 &&
                slices.bottom >= 0 &&
                slices.left + slices.right < bitmap.width &&
                slices.top + slices.bottom < bitmap.height
    ) {
        "Invalid Nine-Slice dimensions"
    }

    require(scale.isFinite() && scale > 0f) {
        "Invalid Nine-Slice scale"
    }

    val colorFilter = tint?.let {
        ColorFilter.tint(it)
    }

    Canvas(modifier = modifier) {

        val width = size.width.roundToInt()
        val height = size.height.roundToInt()

        val pixelScale = scale.dp.toPx()

        // Coordenadas de origen.
        val srcX = intArrayOf(
            0,
            slices.left,
            bitmap.width - slices.right,
            bitmap.width
        )

        val srcY = intArrayOf(
            0,
            slices.top,
            bitmap.height - slices.bottom,
            bitmap.height
        )

        // Dimensiones de los bordes en pantalla.
        var left = slices.left * pixelScale
        var top = slices.top * pixelScale
        var right = slices.right * pixelScale
        var bottom = slices.bottom * pixelScale

        // Evitar que los bordes se superpongan.
        if (left + right > width) {
            val factor = width / (left + right)
            left *= factor
            right *= factor
        }

        if (top + bottom > height) {
            val factor = height / (top + bottom)
            top *= factor
            bottom *= factor
        }

        // Coordenadas de destino.
        val dstX = intArrayOf(
            0,
            left.roundToInt(),
            (width - right).roundToInt(),
            width
        )

        val dstY = intArrayOf(
            0,
            top.roundToInt(),
            (height - bottom).roundToInt(),
            height
        )

        // Dibujar las nueve regiones.
        for (row in 0 until 3) {
            for (column in 0 until 3) {

                val srcWidth =
                    srcX[column + 1] - srcX[column]

                val srcHeight =
                    srcY[row + 1] - srcY[row]

                val dstWidth =
                    dstX[column + 1] - dstX[column]

                val dstHeight =
                    dstY[row + 1] - dstY[row]

                if (
                    srcWidth <= 0 || srcHeight <= 0 ||
                    dstWidth <= 0 || dstHeight <= 0
                ) {
                    continue
                }

                drawImage(
                    image = bitmap,
                    srcOffset = IntOffset(
                        srcX[column],
                        srcY[row]
                    ),
                    srcSize = IntSize(
                        srcWidth,
                        srcHeight
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
