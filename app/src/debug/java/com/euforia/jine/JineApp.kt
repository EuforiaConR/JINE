package com.euforia.jine

import android.content.Context
import android.util.Log
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import com.euforia.jine.core.assets.JineAssets
import com.euforia.jine.data.model.dialogue.DialogueDefinition
import com.euforia.jine.data.repository.DialogueContentException
import com.euforia.jine.data.repository.DialogueRepository
import com.euforia.jine.ui.screens.ChatScreen
import com.euforia.jine.ui.screens.ChatViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

@Composable
internal fun JineApp(
    chat: ChatViewModel,
    loadDialogue: (Context) -> DialogueDefinition = { DialogueRepository.load(it, JineAssets.Dialogues.STREAM_DEMO) }
) {
    val context = LocalContext.current.applicationContext
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

    Column(Modifier.fillMaxSize().navigationBarsPadding()) {
        Box(Modifier.weight(1f)) {
            ChatScreen(chat)
        }
        TextButton(
            onClick = {
                if (definition != null) {
                    chat.requestDialogue(definition)
                } else {
                    attempt++
                }
            },
            enabled = current != null,
            modifier = Modifier.fillMaxWidth()
        ) {
            Text(when {
                current == null -> "Debug · Cargando demo…"
                definition == null -> "Debug · Error al cargar la demo. Reintentar"
                else -> "Debug · Demo narrativa"
            })
        }
    }
}
