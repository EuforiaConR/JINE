package com.euforia.jine.ui.screens

import android.util.Log
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.testTag
import androidx.compose.ui.semantics.testTagsAsResourceId
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.findViewTreeViewModelStoreOwner
import com.euforia.jine.R
import com.euforia.jine.core.assets.JineAssets
import com.euforia.jine.core.audio.ChatSoundEvents
import com.euforia.jine.core.audio.JineAudioManager
import com.euforia.jine.core.localization.JineLanguageManager
import com.euforia.jine.core.localization.ProvideLocalizedResources
import com.euforia.jine.core.localization.rememberLocalization
import com.euforia.jine.data.model.Message
import com.euforia.jine.data.model.MessageContent
import com.euforia.jine.data.model.MessageSender
import com.euforia.jine.data.model.PCHAN_PACK_ID
import com.euforia.jine.data.model.Sticker
import com.euforia.jine.data.model.StickerCatalogs
import com.euforia.jine.data.model.StickerPack
import com.euforia.jine.data.repository.StickerCatalogException
import com.euforia.jine.data.repository.StickerRepository
import com.euforia.jine.ui.components.ChatHeader
import com.euforia.jine.ui.components.ChatHistory
import com.euforia.jine.ui.components.ChatScrollbar
import com.euforia.jine.ui.components.DialogueChoicePanel
import com.euforia.jine.ui.components.JINE_UI_SCALE
import com.euforia.jine.ui.components.JineWindow
import com.euforia.jine.ui.components.PixelTiledImage
import com.euforia.jine.ui.components.StickerPanelStatus
import com.euforia.jine.ui.components.StickerPicker
import com.euforia.jine.ui.theme.ChatAnimationSpec
import com.euforia.jine.ui.theme.LocalChatAnimationSpec

internal const val CHAT_PANEL_TAG = "chat-sticker-panel"

@Composable
fun ChatScreen(
    language: String = JineLanguageManager.DEFAULT_LANGUAGE,
    onStickerSent: (Sticker) -> Unit = {}
) {
    val owner = checkNotNull(LocalView.current.findViewTreeViewModelStoreOwner()) {
        "ChatScreen requires a ViewModelStoreOwner"
    }
    val provider = remember(owner) { ViewModelProvider(owner) }
    ChatScreen(provider[ChatViewModel::class.java], language, onStickerSent = onStickerSent)
}

@Composable
internal fun ChatScreen(
    viewModel: ChatViewModel,
    language: String = JineLanguageManager.DEFAULT_LANGUAGE,
    audioHost: JineAudioManager.Host? = null,
    catalogs: StickerCatalogs = rememberStickerCatalogs(),
    onStickerSent: (Sticker) -> Unit = {}
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val validStickerIds = remember(catalogs.pchan) { catalogs.pchan.orEmpty().map { it.id }.toSet() }

    key(viewModel) {
        val sounds = rememberChatSounds(audioHost) { viewModel.uiState.value.messages }
        ChatContent(
            uiState,
            language,
            catalogs,
            onStickerPressed = { sticker ->
                if (viewModel.sendSticker(sticker.id, validStickerIds)) {
                    sounds?.onInteractionAccepted()
                    onStickerSent(sticker)
                }
            },
            onChoicePressed = { promptId, choiceId ->
                if (viewModel.chooseDialogueOption(promptId, choiceId)) sounds?.onInteractionAccepted()
            },
            sounds = sounds
        )
    }
}

@Composable
internal fun rememberChatSounds(
    audioHost: JineAudioManager.Host?,
    history: () -> List<Message>
): ChatSoundEvents? {
    if (audioHost == null) return null
    val currentHistory by rememberUpdatedState(history)
    val sounds = remember(audioHost) { audioHost.createChat { currentHistory() } }
    DisposableEffect(sounds) {
        sounds.attach()
        onDispose(sounds::detach)
    }
    return sounds
}

@Composable
internal fun rememberStickerCatalogs(): StickerCatalogs {
    val context = LocalContext.current.applicationContext
    return remember(context) { loadStickerCatalogs { pack -> StickerRepository.load(context, pack) } }
}

internal fun loadStickerCatalogs(load: (StickerPack) -> List<Sticker>): StickerCatalogs {
    fun tryLoad(pack: StickerPack): List<Sticker>? = try {
        load(pack)
    } catch (error: StickerCatalogException) {
        Log.e("JineChat", "Could not load ${pack.id} stickers from ${pack.catalogPath}", error)
        null
    }

    return StickerCatalogs(pchan = tryLoad(StickerPack.PCHAN), ame = tryLoad(StickerPack.AME))
}

@Composable
internal fun ChatContent(
    uiState: ChatUiState,
    language: String,
    catalogs: StickerCatalogs,
    onStickerPressed: (Sticker) -> Unit = {},
    onChoicePressed: (promptId: String, choiceId: String) -> Unit = { _, _ -> },
    sounds: ChatSoundEvents? = null
) {
    val localization = rememberLocalization(language)
    val historyState = rememberLazyListState()

    // The resolved language may be Spanish if the requested catalog was unavailable.
    ProvideLocalizedResources(localization.language) {
        JineWindow(header = { ChatHeader() }) {
            PixelTiledImage(
                path = JineAssets.Backgrounds.JINE,
                modifier = Modifier.matchParentSize(),
                scale = JINE_UI_SCALE
            )

            BoxWithConstraints(Modifier.fillMaxSize()) {
                val mode = uiState.inputMode
                val panelIdentity = when (mode) {
                    ChatInputMode.Stickers, ChatInputMode.Hidden -> mode
                    is ChatInputMode.Choices -> mode.promptId
                }
                val activePanelHeight = remember(panelIdentity) { mutableStateOf<Int?>(null) }
                val panelHeightPx = if (mode == ChatInputMode.Hidden) 0 else activePanelHeight.value
                ChatHistory(
                    messages = uiState.messages,
                    localization = localization,
                    catalogs = catalogs,
                    state = historyState,
                    viewport = IntSize(constraints.maxWidth, constraints.maxHeight),
                    panelHeightPx = panelHeightPx,
                    sounds = sounds
                )
                panelHeightPx?.let { measuredHeight ->
                    val readingHeight = with(LocalDensity.current) {
                        (constraints.maxHeight - measuredHeight).coerceAtLeast(0).toDp()
                    }
                    ChatScrollbar(
                        listState = historyState,
                        panelHeightPx = measuredHeight,
                        modifier = Modifier.align(Alignment.TopEnd).height(readingHeight)
                    )
                }
                if (mode != ChatInputMode.Hidden) {
                    // A new panel reports its own size even when it matches the previous panel.
                    // Measurement callbacks keep the state of the panel that created them.
                    key(panelIdentity) {
                        Box(
                            Modifier
                                .align(Alignment.BottomCenter)
                                .fillMaxWidth()
                                .heightIn(max = maxHeight * 0.45f)
                                .onSizeChanged { size -> activePanelHeight.value = size.height }
                                .semantics {
                                    if (mode == ChatInputMode.Stickers) {
                                        testTagsAsResourceId = true
                                        testTag = CHAT_PANEL_TAG
                                    }
                                }
                                .pointerInput(Unit) {
                                    // Hit-test gaps without consuming the panel's own gestures.
                                    awaitPointerEventScope { while (true) awaitPointerEvent() }
                                }
                        ) {
                            when (mode) {
                                ChatInputMode.Hidden -> Unit
                                ChatInputMode.Stickers -> {
                                    if (catalogs.pchan == null) {
                                        StickerPanelStatus(R.string.sticker_catalog_error)
                                    } else {
                                        StickerPicker(
                                            stickers = catalogs.pchan,
                                            onStickerPressed = onStickerPressed,
                                            descriptionFor = { sticker ->
                                                localization.translate(sticker.descriptionKey)
                                            },
                                            enabled = uiState.canSendSticker,
                                            language = localization.language
                                        )
                                    }
                                }
                                is ChatInputMode.Choices -> DialogueChoicePanel(mode, localization, onChoicePressed)
                            }
                        }
                    }
                }
            }
        }
    }
}

@Preview(name = "Historial largo", showBackground = true, widthDp = 360, heightDp = 780)
@Preview(name = "Historial compacto", showBackground = true, widthDp = 320, heightDp = 260)
@Composable
private fun ChatHistoryPreview() {
    val texts = initialMessages(0L)
    val history = List(12) { index ->
        if (index % 3 == 1) {
            Message(
                id = "preview_history_$index",
                sender = MessageSender.PCHAN,
                content = MessageContent.Sticker(PCHAN_PACK_ID, "love"),
                sentAt = index.toLong(),
                readAt = index.toLong() + 1
            )
        } else {
            val text = if (index % 3 == 0) texts.first() else texts.last()
            text.copy(id = "preview_history_$index", sentAt = index.toLong())
        }
    }
    ChatContent(ChatUiState(history), JineLanguageManager.DEFAULT_LANGUAGE, rememberStickerCatalogs())
}

@Preview(name = "Hilo sin panel · textos de P-chan", showBackground = true, widthDp = 360, heightDp = 780)
@Composable
private fun HiddenPanelPreview() {
    val messages = initialMessages(0L) + listOf(
        Message("preview_narrative_short", MessageSender.PCHAN, MessageContent.Text(narrativeChoices[0].textKey), 1L, 2L),
        Message("preview_narrative_reply", MessageSender.AME, MessageContent.Text("dialogue.preview.narrative.reply"), 3L),
        Message("preview_narrative_sticker", MessageSender.PCHAN, MessageContent.Sticker(PCHAN_PACK_ID, "love"), 4L),
        Message("preview_narrative_long", MessageSender.PCHAN, MessageContent.Text(narrativeLongChoice.textKey), 5L)
    )
    ChatContent(
        ChatUiState(messages, inputMode = ChatInputMode.Hidden),
        JineLanguageManager.DEFAULT_LANGUAGE,
        rememberStickerCatalogs()
    )
}

@Preview(name = "Opciones · Español", group = "Narrativa", showBackground = true, widthDp = 360, heightDp = 780)
@Composable
private fun SpanishChoicesPreview() {
    ChoicesPreview(JineLanguageManager.DEFAULT_LANGUAGE, narrativeChoices)
}

@Preview(name = "Choices · English", group = "Narrativa", showBackground = true, widthDp = 360, heightDp = 780)
@Composable
private fun EnglishChoicesPreview() {
    ChoicesPreview(JineLanguageManager.ENGLISH, narrativeChoices)
}

@Preview(name = "Opciones largas · ventana compacta", group = "Narrativa", showBackground = true, widthDp = 320, heightDp = 260)
@Composable
private fun CompactChoicesPreview() {
    ChoicesPreview(
        JineLanguageManager.DEFAULT_LANGUAGE,
        listOf(narrativeLongChoice) + narrativeChoices + narrativeLongChoice.copy(id = "another-long")
    )
}

private val narrativeChoices = listOf(
    DialogueChoice("gaming", "dialogue.preview.narrative.choice.gaming"),
    DialogueChoice("talking", "dialogue.preview.narrative.choice.talking")
)
private val narrativeLongChoice = DialogueChoice("long", "dialogue.preview.narrative.choice.long")

@Composable
private fun ChoicesPreview(language: String, choices: List<DialogueChoice>) {
    val messages = initialMessages(0L) + listOf(
        Message("preview_narrative_intro", MessageSender.AME, MessageContent.Text("dialogue.preview.narrative.intro"), 1L),
        Message("preview_narrative_question", MessageSender.AME, MessageContent.Text("dialogue.preview.narrative.question"), 2L)
    )
    CompositionLocalProvider(LocalChatAnimationSpec provides ChatAnimationSpec.Static) {
        ChatContent(
            ChatUiState(messages, inputMode = ChatInputMode.Choices("preview-stream", choices)),
            language,
            rememberStickerCatalogs()
        )
    }
}

@Preview(
    showBackground = true,
    widthDp = 360,
    heightDp = 780
)
@Composable
fun ChatScreenPreview() {
    ChatContent(ChatUiState(initialMessages(0L)), JineLanguageManager.DEFAULT_LANGUAGE, rememberStickerCatalogs())
}

@Preview(name = "English", showBackground = true, widthDp = 360, heightDp = 780)
@Composable
private fun ChatScreenEnglishPreview() {
    ChatContent(ChatUiState(initialMessages(0L)), JineLanguageManager.ENGLISH, rememberStickerCatalogs())
}

@Preview(name = "Ame respondiendo · selector activo", showBackground = true, widthDp = 360, heightDp = 780)
@Composable
private fun ChatRespondingPreview() {
    val read = Message(
        id = "preview_batch_read",
        sender = MessageSender.PCHAN,
        content = MessageContent.Sticker(PCHAN_PACK_ID, "love"),
        sentAt = 1L,
        readAt = 2L
    )
    val unread = read.copy(
        id = "preview_batch_unread",
        content = MessageContent.Sticker(PCHAN_PACK_ID, "ok"),
        sentAt = 2L,
        readAt = null
    )
    ChatContent(
        uiState = ChatUiState(
            messages = initialMessages(0L) + listOf(
                read,
                Message("preview_batch_reply", MessageSender.AME, MessageContent.Text("dialogue.demo.reply.love.01"), 3L),
                unread.copy(sentAt = 4L)
            ),
            communicationState = CommunicationState.RESPONDING
        ),
        language = JineLanguageManager.DEFAULT_LANGUAGE,
        catalogs = rememberStickerCatalogs()
    )
}
