package com.euforia.jine.ui.screens

import android.util.Log
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.tooling.preview.Preview
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
import com.euforia.jine.ui.components.ChatMessageItem
import com.euforia.jine.ui.components.JINE_UI_SCALE
import com.euforia.jine.ui.components.JineWindow
import com.euforia.jine.ui.components.PixelTiledImage
import com.euforia.jine.ui.components.StickerPanelStatus
import com.euforia.jine.ui.components.StickerPicker

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
private fun ChatContent(
    uiState: ChatUiState,
    language: String,
    stickers: List<Sticker>?,
    onStickerPressed: (Sticker) -> Unit = {}
) {
    val localization = rememberLocalization(language)
    val stickersById = remember(stickers) { stickers.orEmpty().associateBy { it.id } }

    // The resolved language may be Spanish if the requested catalog was unavailable.
    ProvideLocalizedResources(localization.language) {
        JineWindow(header = { ChatHeader() }) {
            PixelTiledImage(
                path = JineAssets.Backgrounds.JINE,
                modifier = Modifier.matchParentSize(),
                scale = JINE_UI_SCALE
            )

            BoxWithConstraints(Modifier.fillMaxSize()) {
                val panelModifier = Modifier.heightIn(max = maxHeight * 0.45f)
                Column(Modifier.fillMaxSize()) {
                    LazyColumn(
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxWidth(),
                        contentPadding = PaddingValues(
                            start = 10.dp,
                            top = 10.dp,
                            end = 14.dp,
                            bottom = 12.dp
                        ),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        items(uiState.messages, key = { it.id }) { message ->
                            ChatMessageItem(message, localization, stickersById)
                        }
                    }

                    if (stickers == null) {
                        StickerPanelStatus(R.string.sticker_catalog_error, panelModifier)
                    } else {
                        StickerPicker(
                            stickers = stickers,
                            selectedStickerId = uiState.selectedStickerId?.takeIf { it in stickersById },
                            onStickerSelected = onStickerPressed,
                            descriptionFor = { sticker -> localization.translate(sticker.descriptionKey) },
                            modifier = panelModifier,
                            enabled = uiState.canSendSticker,
                            language = localization.language
                        )
                    }
                }
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
    ChatContent(ChatUiState(initialMessages(0L)), JineLanguageManager.DEFAULT_LANGUAGE, rememberStickerCatalog())
}

@Preview(name = "English", showBackground = true, widthDp = 360, heightDp = 780)
@Composable
private fun ChatScreenEnglishPreview() {
    ChatContent(ChatUiState(initialMessages(0L)), JineLanguageManager.ENGLISH, rememberStickerCatalog())
}

@Preview(name = "Ame respondiendo", showBackground = true, widthDp = 360, heightDp = 780)
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
            messages = initialMessages(0L) + listOf(read, unread),
            communicationState = CommunicationState.RESPONDING
        ),
        language = JineLanguageManager.DEFAULT_LANGUAGE,
        stickers = rememberStickerCatalog()
    )
}
