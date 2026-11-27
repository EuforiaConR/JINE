package com.euforia.jine.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.euforia.jine.R
import com.euforia.jine.core.assets.JineAssets
import com.euforia.jine.core.localization.localizedStringResource
import com.euforia.jine.ui.theme.JineBubble
import com.euforia.jine.ui.theme.JineChatBackground
import com.euforia.jine.ui.theme.JineSentBubble
import com.euforia.jine.ui.theme.JineText
import com.euforia.jine.ui.theme.ZpixFont
import com.euforia.jine.ui.theme.pixelFontSize

private const val DIALOGUE_SCALE = 1f

@Composable
fun MessageBubble(
    message: String,
    modifier: Modifier = Modifier,
    bubbleColor: Color = JineBubble,
    backgroundAlpha: (() -> Float)? = null
) {
    Box(
        modifier = modifier.widthIn(max = 280.dp),
        contentAlignment = Alignment.CenterStart
    ) {

        NineSliceImage(
            path = JineAssets.Dialogue.BALLOON,
            slices = NineSliceInsets(7),
            modifier = Modifier.matchParentSize().then(
                if (backgroundAlpha == null) Modifier else Modifier.graphicsLayer { alpha = backgroundAlpha() }
            ),
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

@Composable
internal fun SentMessage(message: String, isRead: Boolean) {
    val tailSpace = 7.dp * DIALOGUE_SCALE
    Column(Modifier.padding(end = tailSpace)) {
        Box {
            MessageBubble(message = message, bubbleColor = JineSentBubble)
            PixelImage(
                path = JineAssets.Dialogue.TAIL,
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .offset(x = tailSpace, y = (-2).dp * DIALOGUE_SCALE)
                    .size(width = 8.dp * DIALOGUE_SCALE, height = 24.dp * DIALOGUE_SCALE)
                    .graphicsLayer { scaleX = -1f },
                tint = JineSentBubble,
                contentScale = ContentScale.FillBounds
            )
        }
        if (isRead) MessageReadReceipt()
    }
}

@Preview(name = "Aparición y fondo de opción · 50 %", showBackground = true, widthDp = 360)
@Composable
private fun ChatOpacityPreview() {
    Column(
        Modifier.fillMaxWidth().background(JineChatBackground).padding(12.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        ReceivedMessage("Oye, P-chan…", Modifier.graphicsLayer { alpha = 0.5f })
        MessageBubble(
            message = "¡Un directo de videojuegos!",
            modifier = Modifier.align(Alignment.End).defaultMinSize(minHeight = 48.dp),
            bubbleColor = JineSentBubble,
            backgroundAlpha = { 0.5f }
        )
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
