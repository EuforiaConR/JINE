package com.euforia.jine.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.euforia.jine.core.assets.JineAssets
import com.euforia.jine.ui.components.ChatHeader
import com.euforia.jine.ui.components.JineWindow
import com.euforia.jine.ui.components.PixelTiledImage
import com.euforia.jine.ui.components.ReceivedMessage
import com.euforia.jine.ui.components.JINE_UI_SCALE

@Composable
fun ChatScreen() {

    val messages = listOf(
        "p-chaaaan!!",
        "¿Estás ahí?",
        "Tengo muchas cosas que contarte hoy. " +
                "Espero que podamos hablar un rato."
    )

    JineWindow(
        header = {
            ChatHeader()
        }
    ) {

        PixelTiledImage(
            path = JineAssets.Backgrounds.JINE,
            modifier = Modifier.matchParentSize(),
            scale = JINE_UI_SCALE
        )

        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(
                start = 10.dp,
                top = 10.dp,
                end = 14.dp,
                bottom = 12.dp
            ),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {

            items(messages) { message ->
                ReceivedMessage(
                    message = message
                )
            }
        }
    }
}

@Preview(
    showBackground = true,
    widthDp = 360,
    heightDp = 780
)
@Composable
fun ChatScreenPreview() {
    ChatScreen()
}
