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
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
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
import com.euforia.jine.core.localization.JineLanguageManager
import com.euforia.jine.core.localization.ProvideLocalizedResources
import com.euforia.jine.core.localization.rememberLocalization
import com.euforia.jine.data.model.Message
import com.euforia.jine.data.model.MessageContent
import com.euforia.jine.data.model.MessageSender
import com.euforia.jine.data.model.PCHAN_PACK_ID
import com.euforia.jine.data.model.Sticker
import com.euforia.jine.data.repository.StickerCatalogException
import com.euforia.jine.data.repository.StickerRepository
import com.euforia.jine.ui.components.ChatHeader
import com.euforia.jine.ui.components.ChatHistory
import com.euforia.jine.ui.components.ChatScrollbar
import com.euforia.jine.ui.components.JINE_UI_SCALE
import com.euforia.jine.ui.components.JineWindow
import com.euforia.jine.ui.components.PixelTiledImage
import com.euforia.jine.ui.components.StickerPanelStatus
import com.euforia.jine.ui.components.StickerPicker

internal const val CHAT_PANEL_TAG = "chat-sticker-panel"

@Composable
fun ChatScreen(
    language: String = JineLanguageManager.DEFAULT_LANGUAGE,
    onStickerSelected: (Sticker) -> Unit = {}
) {
    val owner = checkNotNull(LocalView.current.findViewTreeViewModelStoreOwner()) {
        "ChatScreen requires a ViewModelStoreOwner"
    }
    val provider = remember(owner) { ViewModelProvider(owner) }
    ChatScreen(provider[ChatViewModel::class.java], language, onStickerSelected)
}

@Composable
internal fun ChatScreen(
    viewModel: ChatViewModel,
    language: String = JineLanguageManager.DEFAULT_LANGUAGE,
    onStickerSelected: (Sticker) -> Unit = {}
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val stickers = rememberStickerCatalog()
    val validStickerIds = remember(stickers) { stickers.orEmpty().map { it.id }.toSet() }
    LaunchedEffect(viewModel, validStickerIds) {
        viewModel.reconcileSelection(validStickerIds)
    }

    ChatContent(uiState, language, stickers) { sticker ->
        if (viewModel.onStickerPressed(sticker.id, validStickerIds)) onStickerSelected(sticker)
    }
}

@Composable
private fun rememberStickerCatalog(): List<Sticker>? {
    val context = LocalContext.current
    return remember(context) {
        try {
            StickerRepository.load(context)
        } catch (error: StickerCatalogException) {
            Log.e("JineChat", "Could not load the sticker panel", error)
            null
        }
    }
}

@Composable
internal fun ChatContent(
    uiState: ChatUiState,
    language: String,
    stickers: List<Sticker>?,
    onStickerPressed: (Sticker) -> Unit = {}
) {
    val localization = rememberLocalization(language)
    val stickersById = remember(stickers) { stickers.orEmpty().associateBy { it.id } }
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
                var panelHeightPx by remember { mutableStateOf<Int?>(null) }
                ChatHistory(
                    messages = uiState.messages,
                    localization = localization,
                    stickersById = stickersById,
                    state = historyState,
                    viewport = IntSize(constraints.maxWidth, constraints.maxHeight),
                    panelHeightPx = panelHeightPx
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
                Box(
                    Modifier
                        .align(Alignment.BottomCenter)
                        .fillMaxWidth()
                        .heightIn(max = maxHeight * 0.45f)
                        .onSizeChanged { size -> panelHeightPx = size.height }
                        .semantics {
                            testTagsAsResourceId = true
                            testTag = CHAT_PANEL_TAG
                        }
                        .pointerInput(Unit) {
                            // Hit-test the whole panel, including the gaps, without consuming its grid's gestures.
                            awaitPointerEventScope { while (true) awaitPointerEvent() }
                        }
                ) {
                    if (stickers == null) {
                        StickerPanelStatus(R.string.sticker_catalog_error)
                    } else {
                        StickerPicker(
                            stickers = stickers,
                            selectedStickerId = uiState.selectedStickerId?.takeIf { it in stickersById },
                            onStickerSelected = onStickerPressed,
                            descriptionFor = { sticker -> localization.translate(sticker.descriptionKey) },
                            enabled = uiState.canSendSticker,
                            language = localization.language
                        )
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
    ChatContent(ChatUiState(history), JineLanguageManager.DEFAULT_LANGUAGE, rememberStickerCatalog())
}

@Preview(
    showBackground = true,
    widthDp = 360,
    heightDp = 780
)
@Composable
fun ChatScreenPreview() {
    ChatContent(ChatUiState(initialMessages(0L)), JineLanguageManager.DEFAULT_LANGUAGE, rememberStickerCatalog())
}

@Preview(name = "English", showBackground = true, widthDp = 360, heightDp = 780)
@Composable
private fun ChatScreenEnglishPreview() {
    ChatContent(ChatUiState(initialMessages(0L)), JineLanguageManager.ENGLISH, rememberStickerCatalog())
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
            selectedStickerId = "gomen",
            communicationState = CommunicationState.RESPONDING
        ),
        language = JineLanguageManager.DEFAULT_LANGUAGE,
        stickers = rememberStickerCatalog()
    )
}
