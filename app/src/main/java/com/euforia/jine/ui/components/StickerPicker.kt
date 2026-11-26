package com.euforia.jine.ui.components

import androidx.annotation.StringRes
import androidx.compose.foundation.background
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
import androidx.compose.foundation.selection.selectable
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
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
private const val STICKER_DIMMED_ALPHA = 0.4f
private const val STICKER_HIGHLIGHTED_ALPHA = 1f
private val cellHeight = 79.dp
private val stickerSize = 72.dp
private val cellSpacing = 6.dp
private val panelPadding = 8.dp
private val itemInnerPadding = 2.dp

@Composable
fun StickerPicker(
    stickers: List<Sticker>,
    selectedStickerId: String?,
    onStickerSelected: (Sticker) -> Unit,
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
    val selectedDescription = localizedStringResource(R.string.sticker_selected)
    val notSelectedDescription = localizedStringResource(R.string.sticker_not_selected)

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
            val isSelected = sticker.id == selectedStickerId
            val stickerAlpha = if (isSelected) STICKER_HIGHLIGHTED_ALPHA else STICKER_DIMMED_ALPHA
            val description = descriptionFor(sticker)

            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(cellHeight)
                    .selectable(
                        selected = isSelected,
                        interactionSource = null,
                        indication = null,
                        enabled = enabled,
                        role = Role.Button
                    ) {
                        onStickerSelected(sticker)
                    }
                    .semantics {
                        contentDescription = description
                        stateDescription = if (isSelected) selectedDescription else notSelectedDescription
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
