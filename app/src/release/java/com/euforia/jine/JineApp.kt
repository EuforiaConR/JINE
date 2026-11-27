package com.euforia.jine

import androidx.compose.runtime.Composable
import com.euforia.jine.ui.screens.ChatScreen
import com.euforia.jine.ui.screens.ChatViewModel

@Composable
internal fun JineApp(chat: ChatViewModel) {
    ChatScreen(chat)
}
