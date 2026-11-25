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
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.euforia.jine.R
import com.euforia.jine.core.assets.JineAssets
import com.euforia.jine.core.localization.JineLanguageManager
import com.euforia.jine.core.localization.ProvideLocalizedResources
import com.euforia.jine.core.localization.rememberLocalization
import com.euforia.jine.data.model.Sticker
import com.euforia.jine.data.repository.StickerCatalogException
import com.euforia.jine.data.repository.StickerRepository
import com.euforia.jine.ui.components.ChatHeader
import com.euforia.jine.ui.components.JINE_UI_SCALE
import com.euforia.jine.ui.components.JineWindow
import com.euforia.jine.ui.components.PixelTiledImage
import com.euforia.jine.ui.components.ReceivedMessage
import com.euforia.jine.ui.components.StickerPanelStatus
import com.euforia.jine.ui.components.StickerPicker

private val previewMessageKeys = listOf(
    "dialogue.preview.greeting",
    "dialogue.preview.question",
    "dialogue.preview.long_message"
)

@Composable
fun ChatScreen(
    language: String = JineLanguageManager.DEFAULT_LANGUAGE,
    onStickerSelected: (Sticker) -> Unit = {}
) {
    val context = LocalContext.current
    val localization = rememberLocalization(language)
    val messages = remember(localization) {
        previewMessageKeys.map(localization::translate)
    }
    val stickers = remember(context) {
        try {
            StickerRepository.load(context)
        } catch (error: StickerCatalogException) {
            Log.e("JineChat", "Could not load the sticker panel", error)
            null
        }
    }
    var selectedStickerId by rememberSaveable { mutableStateOf<String?>(null) }

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
                        items(messages) { message ->
                            ReceivedMessage(message = message)
                        }
                    }

                    if (stickers == null) {
                        StickerPanelStatus(R.string.sticker_catalog_error, panelModifier)
                    } else {
                        StickerPicker(
                            stickers = stickers,
                            selectedStickerId = selectedStickerId,
                            onStickerSelected = { sticker ->
                                selectedStickerId = sticker.id
                                onStickerSelected(sticker)
                            },
                            descriptionFor = { sticker -> localization.translate(sticker.descriptionKey) },
                            modifier = panelModifier
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
    ChatScreen()
}

@Preview(name = "English", showBackground = true, widthDp = 360, heightDp = 780)
@Composable
private fun ChatScreenEnglishPreview() {
    ChatScreen(language = JineLanguageManager.ENGLISH)
}
