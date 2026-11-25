package com.euforia.jine.ui.components

import androidx.annotation.StringRes
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.selection.selectable
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.euforia.jine.R
import com.euforia.jine.core.localization.localizedStringResource
import com.euforia.jine.data.model.Sticker
import com.euforia.jine.ui.theme.JineStickerPanel
import com.euforia.jine.ui.theme.JineText
import com.euforia.jine.ui.theme.ZpixFont
import com.euforia.jine.ui.theme.pixelFontSize

private const val COLUMN_COUNT = 4
private const val MAX_VISIBLE_ROWS = 2
private val cellHeight = 56.dp
private val cellSpacing = 6.dp

@Composable
fun StickerPicker(
    stickers: List<Sticker>,
    selectedStickerId: String?,
    onStickerSelected: (Sticker) -> Unit,
    descriptionFor: (Sticker) -> String,
    modifier: Modifier = Modifier,
    enabled: Boolean = true
) {
    if (stickers.isEmpty()) {
        StickerPanelStatus(R.string.sticker_catalog_empty, modifier)
        return
    }

    val rows = minOf(MAX_VISIBLE_ROWS, (stickers.size - 1) / COLUMN_COUNT + 1)
    val gridHeight = cellHeight * rows + cellSpacing * (rows + 1)
    val selectedDescription = localizedStringResource(R.string.sticker_selected)
    val notSelectedDescription = localizedStringResource(R.string.sticker_not_selected)

    LazyVerticalGrid(
        columns = GridCells.Fixed(COLUMN_COUNT),
        modifier = modifier
            .fillMaxWidth()
            .height(gridHeight)
            .background(JineStickerPanel),
        contentPadding = PaddingValues(horizontal = 8.dp, vertical = cellSpacing),
        horizontalArrangement = Arrangement.spacedBy(cellSpacing),
        verticalArrangement = Arrangement.spacedBy(cellSpacing)
    ) {
        items(stickers, key = { it.id }) { sticker ->
            val isSelected = sticker.id == selectedStickerId
            val description = descriptionFor(sticker)

            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(cellHeight)
                    .alpha(if (enabled) 1f else 0.55f)
                    .border(1.dp, if (isSelected) JineText else Color.Transparent)
                    .selectable(selected = isSelected, enabled = enabled, role = Role.Button) {
                        onStickerSelected(sticker)
                    }
                    .semantics {
                        contentDescription = description
                        stateDescription = if (isSelected) selectedDescription else notSelectedDescription
                    }
            ) {
                PixelImage(
                    path = sticker.texture,
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(5.dp)
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
            .background(JineStickerPanel)
            .padding(8.dp),
        color = JineText,
        fontFamily = ZpixFont,
        fontSize = pixelFontSize(14.sp),
        letterSpacing = 0.sp
    )
}
