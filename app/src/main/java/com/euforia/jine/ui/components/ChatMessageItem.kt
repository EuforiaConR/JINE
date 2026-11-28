package com.euforia.jine.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.testTag
import androidx.compose.ui.semantics.testTagsAsResourceId
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.euforia.jine.R
import com.euforia.jine.core.localization.JineLanguageManager
import com.euforia.jine.core.localization.LocalizationSnapshot
import com.euforia.jine.core.localization.ProvideLocalizedResources
import com.euforia.jine.core.localization.localizedStringResource
import com.euforia.jine.core.localization.rememberLocalization
import com.euforia.jine.data.model.Message
import com.euforia.jine.data.model.MessageContent
import com.euforia.jine.data.model.MessageSender
import com.euforia.jine.data.model.PCHAN_PACK_ID
import com.euforia.jine.data.model.StickerCatalogs
import com.euforia.jine.data.repository.StickerRepository
import com.euforia.jine.ui.theme.DinkieBitmapFont
import com.euforia.jine.ui.theme.JineChatBackground
import com.euforia.jine.ui.theme.JineReadReceipt
import com.euforia.jine.ui.theme.JineText
import com.euforia.jine.ui.theme.ZpixFont
import com.euforia.jine.ui.theme.pixelFontSize

internal const val CHAT_MESSAGE_TAG_PREFIX = "chat-message-"
private val messageStickerSize = 72.dp
private val receivedStickerStartInset = 40.dp

@Composable
internal fun ChatMessageItem(
    message: Message,
    localization: LocalizationSnapshot,
    catalogs: StickerCatalogs,
    modifier: Modifier = Modifier
) {
    val alignment = when (message.sender) {
        MessageSender.AME -> Alignment.TopStart
        MessageSender.PCHAN -> Alignment.TopEnd
    }
    Box(
        modifier = modifier
            .fillMaxWidth()
            .semantics(mergeDescendants = true) {
                testTagsAsResourceId = true
                testTag = "$CHAT_MESSAGE_TAG_PREFIX${message.id}"
            },
        contentAlignment = alignment
    ) {
        when (val content = message.content) {
            is MessageContent.Text -> when (message.sender) {
                MessageSender.AME -> ReceivedMessage(message = localization.translate(content.textKey))
                MessageSender.PCHAN -> SentMessage(
                    message = localization.translate(content.textKey),
                    isRead = message.isRead
                )
            }
            is MessageContent.Sticker -> {
                val sticker = catalogs.resolve(message.sender, content)
                if (sticker == null) {
                    UnavailableMessage()
                } else {
                    val path = sticker.getTexture(localization.language)
                    val description = localization.translate(sticker.descriptionKey)
                    when (message.sender) {
                        MessageSender.AME -> ReceivedSticker(path, description)
                        MessageSender.PCHAN -> SentSticker(path, description, message.isRead)
                    }
                }
            }
        }
    }
}

@Composable
private fun ReceivedSticker(path: String, description: String) {
    Box(Modifier.padding(start = receivedStickerStartInset)) {
        PixelImage(
            path = path,
            modifier = Modifier.size(messageStickerSize),
            contentDescription = description
        )
    }
}

@Composable
private fun SentSticker(path: String, description: String, isRead: Boolean) {
    Column(horizontalAlignment = Alignment.Start) {
        PixelImage(
            path = path,
            modifier = Modifier.size(messageStickerSize),
            contentDescription = description
        )
        if (isRead) MessageReadReceipt()
    }
}

@Composable
internal fun MessageReadReceipt() {
    Text(
        text = localizedStringResource(R.string.chat_message_read),
        modifier = Modifier.padding(top = 2.dp),
        color = JineReadReceipt,
        fontFamily = DinkieBitmapFont,
        fontSize = pixelFontSize(10.sp, baseSize = 7),
        letterSpacing = 0.sp
    )
}

@Composable
private fun UnavailableMessage() {
    Text(
        text = localizedStringResource(R.string.chat_message_unavailable),
        color = JineText,
        fontFamily = ZpixFont,
        fontSize = pixelFontSize(12.sp),
        letterSpacing = 0.sp
    )
}

@Preview(name = "Sticker leído", showBackground = true, widthDp = 360)
@Composable
private fun ReadStickerMessagePreview() {
    val context = LocalContext.current
    val localization = rememberLocalization(JineLanguageManager.DEFAULT_LANGUAGE)
    val catalogs = remember(context) { StickerCatalogs(pchan = StickerRepository.load(context)) }
    val message = Message(
        id = "preview_read_sticker",
        sender = MessageSender.PCHAN,
        content = MessageContent.Sticker(PCHAN_PACK_ID, "love"),
        sentAt = 0L,
        readAt = 1L
    )

    ProvideLocalizedResources(localization.language) {
        Box(Modifier.background(JineChatBackground).padding(12.dp)) {
            ChatMessageItem(message, localization, catalogs)
        }
    }
}
