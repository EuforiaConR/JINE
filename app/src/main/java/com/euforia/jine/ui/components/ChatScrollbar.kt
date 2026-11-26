package com.euforia.jine.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.testTag
import androidx.compose.ui.semantics.testTagsAsResourceId
import androidx.compose.ui.unit.dp
import com.euforia.jine.ui.theme.JineScrollbarThumb
import com.euforia.jine.ui.theme.JineScrollbarTrack

internal const val CHAT_SCROLLBAR_TAG = "chat-scrollbar"
private val scrollbarWidth = 6.dp
private val minimumThumbHeight = 16.dp

internal data class ScrollbarGeometry(val top: Float, val height: Float)

internal fun calculateScrollbarGeometry(
    trackHeight: Float,
    minThumbHeight: Float,
    estimatedContent: Float,
    estimatedOffset: Float,
    canScrollBackward: Boolean,
    canScrollForward: Boolean
): ScrollbarGeometry {
    if (!trackHeight.isFinite() || trackHeight <= 0f) return ScrollbarGeometry(0f, 0f)
    if (!canScrollBackward && !canScrollForward) return ScrollbarGeometry(0f, trackHeight)

    val minimum = if (minThumbHeight.isFinite()) minThumbHeight.coerceIn(0f, trackHeight) else trackHeight
    val scrollable = estimatedContent - trackHeight
    val hasEstimate = scrollable.isFinite() && scrollable > 0f
    val height = if (hasEstimate) {
        (trackHeight * (trackHeight / estimatedContent)).coerceIn(minimum, trackHeight)
    } else {
        minimum
    }
    val progress = when {
        !canScrollBackward -> 0f
        !canScrollForward -> 1f
        hasEstimate && estimatedOffset.isFinite() -> (estimatedOffset / scrollable).coerceIn(0f, 1f)
        else -> 0.5f
    }
    return ScrollbarGeometry((trackHeight - height) * progress, height)
}

@Composable
internal fun ChatScrollbar(
    listState: LazyListState,
    panelHeightPx: Int,
    modifier: Modifier = Modifier
) {
    Canvas(
        modifier.width(scrollbarWidth).semantics {
            testTagsAsResourceId = true
            testTag = CHAT_SCROLLBAR_TAG
        }
    ) {
        if (size.height <= 0f) return@Canvas

        // Sample only rows intersecting the part of the history above the panel.
        val layout = listState.layoutInfo
        val readingEnd = layout.viewportEndOffset - panelHeightPx
        val visible = layout.visibleItemsInfo.filter {
            it.offset < readingEnd && it.offset + it.size > layout.viewportStartOffset
        }
        val average = if (visible.isEmpty()) 0f else visible.sumOf { it.size }.toFloat() / visible.size
        val spacing = layout.mainAxisItemSpacing
        val count = layout.totalItemsCount
        val estimatedContent = average * count + spacing * (count - 1).coerceAtLeast(0) +
            layout.beforeContentPadding + (layout.afterContentPadding - panelHeightPx).coerceAtLeast(0)
        val estimatedOffset = visible.firstOrNull()?.let { it.index * (average + spacing) - it.offset } ?: 0f
        val thumb = calculateScrollbarGeometry(
            trackHeight = size.height,
            minThumbHeight = minimumThumbHeight.toPx(),
            estimatedContent = estimatedContent,
            estimatedOffset = estimatedOffset,
            canScrollBackward = listState.canScrollBackward,
            canScrollForward = listState.canScrollForward
        )
        drawRect(JineScrollbarTrack)
        drawRect(
            color = JineScrollbarThumb,
            topLeft = Offset(0f, thumb.top),
            size = Size(size.width, thumb.height)
        )
    }
}
