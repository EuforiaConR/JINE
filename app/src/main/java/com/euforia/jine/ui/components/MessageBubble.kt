package com.euforia.jine.ui.components

import androidx.compose.foundation.layout.*
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.euforia.jine.R
import com.euforia.jine.ui.theme.JineBubble
import com.euforia.jine.ui.theme.JineText
import com.euforia.jine.ui.theme.ZpixFont

import androidx.compose.foundation.background
import androidx.compose.ui.tooling.preview.Preview
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
            resourceId = R.drawable.balloon_dialogue,
            slices = NineSliceInsets(7),
            modifier = Modifier.matchParentSize(),
            scale = 1f,
            tint = bubbleColor
        )

        Text(
            text = message,
            fontFamily = ZpixFont,
            fontSize = 16.sp,
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
            resourceId = R.drawable.icon_jine_ame,
            modifier = Modifier.size(32.dp),
            contentDescription = "Ame-chan"
        )

        Spacer(
            modifier = Modifier.width(2.dp)
        )

        PixelImage(
            resourceId = R.drawable.tail_dialogue,
            modifier = Modifier
                .padding(top = 5.dp)
                .size(6.dp, 10.dp),
            tint = JineBubble
        )

        MessageBubble(
            message = message
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