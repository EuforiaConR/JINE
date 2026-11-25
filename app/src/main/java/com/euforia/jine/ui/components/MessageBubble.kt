package com.euforia.jine.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.euforia.jine.R
import com.euforia.jine.core.assets.JineAssets
import com.euforia.jine.core.localization.localizedStringResource
import com.euforia.jine.ui.theme.JineBubble
import com.euforia.jine.ui.theme.JineText
import com.euforia.jine.ui.theme.ZpixFont
import com.euforia.jine.ui.theme.pixelFontSize

private const val DIALOGUE_SCALE = 1f

@Composable
fun MessageBubble(
    message: String,
    modifier: Modifier = Modifier,
    bubbleColor: Color = JineBubble
) {
    Box(
        modifier = modifier.widthIn(max = 280.dp)
    ) {

        NineSliceImage(
            path = JineAssets.Dialogue.BALLOON,
            slices = NineSliceInsets(7),
            modifier = Modifier.matchParentSize(),
            scale = DIALOGUE_SCALE,
            tint = bubbleColor
        )

        Text(
            text = message,
            fontFamily = ZpixFont,
            fontSize = pixelFontSize(16.sp),
            lineHeight = 22.sp,
            letterSpacing = 0.sp,
            color = JineText,
            modifier = Modifier.padding(
                horizontal = 10.dp,
                vertical = 6.dp
            )
        )
    }
}

@Composable
fun ReceivedMessage(
    message: String,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier,
        verticalAlignment = Alignment.Top
    ) {

        PixelImage(
            path = JineAssets.Avatars.AME,
            modifier = Modifier.size(32.dp),
            contentDescription = localizedStringResource(R.string.chat_ame_avatar)
        )

        Spacer(
            modifier = Modifier.width(2.dp)
        )

        Box {

            MessageBubble(
                message = message,
                modifier = Modifier.padding(
                    start = 7.dp * DIALOGUE_SCALE
                )
            )

            PixelImage(
                path = JineAssets.Dialogue.TAIL,
                modifier = Modifier
                    .align(Alignment.TopStart)
                    .offset(
                        y = (-2).dp * DIALOGUE_SCALE
                    )
                    .size(
                        width = 8.dp * DIALOGUE_SCALE,
                        height = 24.dp * DIALOGUE_SCALE
                    ),
                tint = JineBubble,
                contentScale = ContentScale.FillBounds
            )
        }
    }
}

@Preview(showBackground = true, widthDp = 360)
@Composable
fun ReceivedMessagePreview() {

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(Color(0xFFEE97ED))
            .padding(12.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {

        ReceivedMessage(
            message = "p-chaaaan!!"
        )

        ReceivedMessage(
            message = "¿Estás ahí?"
        )

        ReceivedMessage(
            message = "Tengo muchas cosas que contarte hoy. " +
                    "Espero que podamos hablar un rato."
        )
    }
}
