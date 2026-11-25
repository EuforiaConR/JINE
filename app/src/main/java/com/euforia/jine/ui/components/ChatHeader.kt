package com.euforia.jine.ui.components

import androidx.compose.foundation.layout.*
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.euforia.jine.R
import com.euforia.jine.ui.theme.JineText
import com.euforia.jine.ui.theme.ZpixFont
import com.euforia.jine.ui.theme.pixelFontSize

@Composable
fun ChatHeader(
    modifier: Modifier = Modifier
) {
    val iconSize = 20.dp * JINE_UI_SCALE

    Row(
        modifier = modifier
            .fillMaxSize()
            .padding(horizontal = 2.dp * JINE_UI_SCALE),
        verticalAlignment = Alignment.CenterVertically
    ) {

        PixelImage(
            resourceId = R.drawable.windowbase_icon,
            modifier = Modifier.size(iconSize),
            contentDescription = "JINE icon"
        )

        Spacer(
            modifier = Modifier.width(4.dp * JINE_UI_SCALE)
        )

        Text(
            text = "JINE",
            fontFamily = ZpixFont,
            fontSize = pixelFontSize(12.sp * JINE_UI_SCALE),
            letterSpacing = 0.sp,
            color = JineText
        )

        Spacer(
            modifier = Modifier.weight(1f)
        )

        Row(
            horizontalArrangement = Arrangement.spacedBy(
                2.dp * JINE_UI_SCALE
            ),
            verticalAlignment = Alignment.CenterVertically
        ) {
            PixelImage(
                resourceId = R.drawable.button_minimize,
                modifier = Modifier.size(iconSize),
                contentDescription = "Minimize"
            )

            PixelImage(
                resourceId = R.drawable.button_maximize,
                modifier = Modifier.size(iconSize),
                contentDescription = "Maximize"
            )

            PixelImage(
                resourceId = R.drawable.button_close,
                modifier = Modifier.size(iconSize),
                contentDescription = "Close"
            )
        }
    }
}