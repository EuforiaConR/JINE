package com.euforia.jine

import androidx.compose.runtime.Composable
import com.euforia.jine.core.audio.JineAudioManager
import com.euforia.jine.ui.screens.ChatScreen
import com.euforia.jine.ui.screens.ChatViewModel

@Composable
internal fun JineApp(chat: ChatViewModel, audioHost: JineAudioManager.Host? = null) {
    ChatScreen(chat, audioHost = audioHost)
}
