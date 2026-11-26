package com.euforia.jine.ui.theme

import androidx.compose.material3.Typography
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp

import androidx.compose.ui.text.font.Font
import com.euforia.jine.R

import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.TextUnit
import kotlin.math.roundToInt

val ZpixFont = FontFamily(
    Font(R.font.zpix)
)

val DinkieBitmapFont = FontFamily(
    Font(R.font.dinkie_bitmap_7px)
)

// Set of Material typography styles to start with
val Typography = Typography(
    bodyLarge = TextStyle(
        fontFamily = FontFamily.Default,
        fontWeight = FontWeight.Normal,
        fontSize = 16.sp,
        lineHeight = 24.sp,
        letterSpacing = 0.5.sp
    )
    /* Other default text styles to override
    titleLarge = TextStyle(
        fontFamily = FontFamily.Default,
        fontWeight = FontWeight.Normal,
        fontSize = 22.sp,
        lineHeight = 28.sp,
        letterSpacing = 0.sp
    ),
    labelSmall = TextStyle(
        fontFamily = FontFamily.Default,
        fontWeight = FontWeight.Medium,
        fontSize = 11.sp,
        lineHeight = 16.sp,
        letterSpacing = 0.5.sp
    )
    */
)
@Composable
fun pixelFontSize(
    fontSize: TextUnit,
    baseSize: Int = 12
): TextUnit {
    val density = LocalDensity.current

    return with(density) {
        val sizePx = fontSize.toPx()

        val scale = (sizePx / baseSize)
            .roundToInt()
            .coerceAtLeast(1)

        (baseSize * scale).toFloat().toSp()
    }
}
