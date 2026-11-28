package com.euforia.jine

import android.content.Context
import android.util.Log
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
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveableStateHolder
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.euforia.jine.core.assets.JineAssets
import com.euforia.jine.core.audio.JineAudioManager
import com.euforia.jine.data.model.dialogue.DialogueDefinition
import com.euforia.jine.data.repository.DialogueContentException
import com.euforia.jine.data.repository.DialogueRepository
import com.euforia.jine.ui.screens.ChatScreen
import com.euforia.jine.ui.screens.ChatUiState
import com.euforia.jine.ui.screens.ChatViewModel
import com.euforia.jine.ui.screens.CommunicationState
import com.euforia.jine.ui.screens.rememberStickerCatalogs
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

@Composable
internal fun JineApp(
    chat: ChatViewModel,
    audioHost: JineAudioManager.Host? = null,
    loadDialogue: (Context) -> DialogueDefinition = { DialogueRepository.load(it, JineAssets.Dialogues.STREAM_DEMO) }
) {
    val context = LocalContext.current.applicationContext
    val catalogs = rememberStickerCatalogs()
    val chatScreenState = rememberSaveableStateHolder()
    var showAmeDemo by remember { mutableStateOf(false) }
    var attempt by remember { mutableIntStateOf(0) }
    val result by produceState<Result<DialogueDefinition>?>(null, context, attempt) {
        value = null
        value = withContext(Dispatchers.IO) {
            try {
                Result.success(loadDialogue(context))
            } catch (error: DialogueContentException) {
                Log.e("JineDialogue", "Could not load the debug dialogue", error)
                Result.failure(error)
            }
        }
    }
    val current = result
    val definition = current?.getOrNull()

    if (showAmeDemo) {
        AmeStickerDemo(catalogs, audioHost, onClose = { showAmeDemo = false })
        return
    }

    val chatState by chat.uiState.collectAsStateWithLifecycle()
    Column(Modifier.fillMaxSize().navigationBarsPadding()) {
        Box(Modifier.weight(1f)) {
            chatScreenState.SaveableStateProvider("real-chat") {
                ChatScreen(chat, audioHost = audioHost, catalogs = catalogs)
            }
        }
        Row(Modifier.fillMaxWidth()) {
            TextButton(
                onClick = {
                    if (definition != null) {
                        chat.requestDialogue(definition)
                    } else {
                        attempt++
                    }
                },
                enabled = current != null,
                modifier = Modifier.weight(1f)
            ) {
                Text(when {
                    current == null -> "Debug · Cargando demo…"
                    definition == null -> "Debug · Error al cargar la demo. Reintentar"
                    else -> "Debug · Demo narrativa"
                })
            }
            TextButton(
                onClick = { if (chat.uiState.value.canOpenAmeDemo) showAmeDemo = true },
                enabled = chatState.canOpenAmeDemo,
                modifier = Modifier.weight(1f)
            ) {
                Text("Debug · Stickers de Ame")
            }
        }
    }
}

private val ChatUiState.canOpenAmeDemo: Boolean
    get() = canSendSticker && communicationState == CommunicationState.AVAILABLE
