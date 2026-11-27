package com.euforia.jine

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.euforia.jine.data.model.dialogue.DemoDialogues
import com.euforia.jine.ui.screens.ChatScreen
import com.euforia.jine.ui.screens.ChatViewModel

@Composable
internal fun JineApp(chat: ChatViewModel) {
    Column(Modifier.fillMaxSize().navigationBarsPadding()) {
        Box(Modifier.weight(1f)) {
            ChatScreen(chat)
        }
        TextButton(
            onClick = { chat.requestDialogue(DemoDialogues.stream) },
            modifier = Modifier.fillMaxWidth()
        ) {
            Text("Debug · Demo narrativa")
        }
    }
}
