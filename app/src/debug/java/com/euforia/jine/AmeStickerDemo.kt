package com.euforia.jine

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import com.euforia.jine.core.audio.JineAudioManager
import com.euforia.jine.core.localization.JineLanguageManager
import com.euforia.jine.data.model.AME_PACK_ID
import com.euforia.jine.data.model.Message
import com.euforia.jine.data.model.MessageContent
import com.euforia.jine.data.model.MessageSender
import com.euforia.jine.data.model.PCHAN_PACK_ID
import com.euforia.jine.data.model.StickerCatalogs
import com.euforia.jine.ui.screens.ChatContent
import com.euforia.jine.ui.screens.ChatInputMode
import com.euforia.jine.ui.screens.ChatUiState
import com.euforia.jine.ui.screens.rememberChatSounds
import java.util.UUID

@Composable
internal fun AmeStickerDemo(
    catalogs: StickerCatalogs,
    audioHost: JineAudioManager.Host?,
    onClose: () -> Unit
) {
    val demoId = remember { "ame-demo-${UUID.randomUUID()}" }
    val sampleHistory = remember(demoId) {
        listOf(
            Message("$demoId-greeting", MessageSender.AME, MessageContent.Text("dialogue.preview.greeting"), 0L),
            Message("$demoId-love", MessageSender.PCHAN, MessageContent.Sticker(PCHAN_PACK_ID, "love"), 1L, readAt = 2L)
        )
    }
    var messages by remember { mutableStateOf(sampleHistory) }
    var language by remember { mutableStateOf(JineLanguageManager.DEFAULT_LANGUAGE) }
    val sounds = rememberChatSounds(audioHost) { messages }
    val nextIndex = messages.size - sampleHistory.size

    BackHandler(onBack = onClose)
    Column(Modifier.fillMaxSize().navigationBarsPadding()) {
        Box(Modifier.weight(1f)) {
            ChatContent(ChatUiState(messages, inputMode = ChatInputMode.Hidden), language, catalogs, sounds = sounds)
        }
        if (catalogs.ame == null) Text("No se pudo cargar el catálogo de Ame.")
        TextButton(
            modifier = Modifier.fillMaxWidth(),
            enabled = nextIndex < catalogs.ame.orEmpty().size,
            onClick = {
                val index = messages.size - sampleHistory.size
                catalogs.ame?.getOrNull(index)?.let { sticker ->
                    messages = messages + Message(
                        id = "$demoId-$index",
                        sender = MessageSender.AME,
                        content = MessageContent.Sticker(AME_PACK_ID, sticker.id),
                        sentAt = System.currentTimeMillis()
                    )
                }
            }
        ) {
            Text("Añadir sticker de Ame")
        }
        Row(Modifier.fillMaxWidth()) {
            TextButton(onClick = onClose, modifier = Modifier.weight(1f)) { Text("Volver al chat") }
            TextButton(
                onClick = {
                    language = if (language == JineLanguageManager.ENGLISH) JineLanguageManager.DEFAULT_LANGUAGE
                    else JineLanguageManager.ENGLISH
                },
                modifier = Modifier.weight(1f)
            ) {
                Text(if (language == JineLanguageManager.ENGLISH) "Español" else "English")
            }
        }
    }
}
