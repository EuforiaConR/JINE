package com.euforia.jine

import androidx.compose.runtime.Composable
import com.euforia.jine.core.audio.ChatSoundCoordinator
import com.euforia.jine.ui.screens.ChatScreen
import com.euforia.jine.ui.screens.ChatViewModel

@Composable
internal fun JineApp(chat: ChatViewModel, sounds: ChatSoundCoordinator? = null) {
    ChatScreen(chat, sounds = sounds)
}
