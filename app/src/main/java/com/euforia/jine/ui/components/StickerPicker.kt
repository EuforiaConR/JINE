package com.euforia.jine.ui.components

import androidx.annotation.StringRes
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.testTag
import androidx.compose.ui.semantics.testTagsAsResourceId
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.euforia.jine.R
import com.euforia.jine.core.localization.JineLanguageManager
import com.euforia.jine.core.localization.localizedStringResource
import com.euforia.jine.data.model.Sticker
import com.euforia.jine.ui.theme.JineStickerOverlay
import com.euforia.jine.ui.theme.JineText
import com.euforia.jine.ui.theme.ZpixFont
import com.euforia.jine.ui.theme.pixelFontSize

internal const val PICKER_STICKER_TAG_PREFIX = "sticker-picker-"
private const val COLUMN_COUNT = 4
private const val MAX_VISIBLE_ROWS = 2
private const val PANEL_ALPHA = 0.45f
private const val STICKER_IDLE_ALPHA = 0.4f
private const val STICKER_PRESSED_ALPHA = 1f
private val cellHeight = 79.dp
private val stickerSize = 72.dp
private val cellSpacing = 6.dp
private val panelPadding = 8.dp
private val itemInnerPadding = 2.dp

@Composable
fun StickerPicker(
    stickers: List<Sticker>,
    onStickerPressed: (Sticker) -> Unit,
    descriptionFor: (Sticker) -> String,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    language: String = JineLanguageManager.DEFAULT_LANGUAGE
) {
    if (stickers.isEmpty()) {
        StickerPanelStatus(R.string.sticker_catalog_empty, modifier)
        return
    }

    val rows = minOf(MAX_VISIBLE_ROWS, (stickers.size - 1) / COLUMN_COUNT + 1)
    val gridHeight = cellHeight * rows + cellSpacing * (rows - 1) + panelPadding * 2

    LazyVerticalGrid(
        columns = GridCells.Fixed(COLUMN_COUNT),
        modifier = modifier
            .fillMaxWidth()
            .height(gridHeight)
            .background(JineStickerOverlay.copy(alpha = PANEL_ALPHA)),
        contentPadding = PaddingValues(panelPadding),
        horizontalArrangement = Arrangement.spacedBy(cellSpacing),
        verticalArrangement = Arrangement.spacedBy(cellSpacing)
    ) {
        items(stickers, key = { it.id }) { sticker ->
            val interactionSource = remember { MutableInteractionSource() }
            val isPressed by interactionSource.collectIsPressedAsState()
            val stickerAlpha = if (enabled && isPressed) STICKER_PRESSED_ALPHA else STICKER_IDLE_ALPHA
            val description = descriptionFor(sticker)

            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(cellHeight)
                    .clickable(
                        interactionSource = interactionSource,
                        indication = null,
                        enabled = enabled,
                        role = Role.Button
                    ) {
                        onStickerPressed(sticker)
                    }
                    .semantics {
                        contentDescription = description
                        testTagsAsResourceId = true
                        testTag = "$PICKER_STICKER_TAG_PREFIX${sticker.id}"
                    },
                contentAlignment = Alignment.Center
            ) {
                PixelImage(
                    path = sticker.getTexture(language),
                    modifier = Modifier
                        .padding(itemInnerPadding)
                        .size(stickerSize)
                        .alpha(stickerAlpha),
                    contentScale = ContentScale.Fit
                )
            }
        }
    }
}

@Composable
internal fun StickerPanelStatus(@StringRes messageId: Int, modifier: Modifier = Modifier) {
    Text(
        text = localizedStringResource(messageId),
        modifier = modifier
            .fillMaxWidth()
            .background(JineStickerOverlay.copy(alpha = PANEL_ALPHA))
            .padding(panelPadding),
        color = JineText,
        fontFamily = ZpixFont,
        fontSize = pixelFontSize(14.sp),
        letterSpacing = 0.sp
    )
}
