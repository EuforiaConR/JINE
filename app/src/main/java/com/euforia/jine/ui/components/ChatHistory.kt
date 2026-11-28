package com.euforia.jine.ui.components

import androidx.compose.foundation.gestures.scrollBy
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.items
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.getScrollViewportLength
import androidx.compose.ui.semantics.scrollToIndex
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.testTag
import androidx.compose.ui.semantics.testTagsAsResourceId
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import com.euforia.jine.core.audio.ChatSoundEvents
import com.euforia.jine.core.localization.LocalizationSnapshot
import com.euforia.jine.data.model.Message
import com.euforia.jine.data.model.MessageSender
import com.euforia.jine.data.model.Sticker
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

internal const val CHAT_HISTORY_TAG = "chat-history"

@Composable
internal fun ChatHistory(
    messages: List<Message>,
    localization: LocalizationSnapshot,
    stickersById: Map<String, Sticker>,
    state: LazyListState,
    viewport: IntSize,
    panelHeightPx: Int?,
    sounds: ChatSoundEvents? = null
) {
    val appearances = rememberMessageAppearances(
        messages,
        onArrived = sounds?.let { it::onMessageArrived },
        onFinished = sounds?.let { it::onAppearanceFinished }
    )
    val density = LocalDensity.current
    val bottomPadding = with(density) { (panelHeightPx ?: 0).toDp() } + 12.dp
    val afterPaddingPx = with(density) { bottomPadding.roundToPx() }
    var followTail by rememberSaveable { mutableStateOf(true) }
    val latestOutgoingId = remember(messages) { messages.lastOrNull { it.sender == MessageSender.PCHAN }?.id }
    var seenOutgoingId by rememberSaveable { mutableStateOf(latestOutgoingId) }
    val scope = rememberCoroutineScope()
    val manualScroll = remember(state) {
        object : NestedScrollConnection {
            override fun onPostScroll(consumed: Offset, available: Offset, source: NestedScrollSource): Offset {
                // Gestures, flings and accessibility scroll actions dispatch here. Direct list-state jumps do not.
                if (consumed.y != 0f) followTail = !state.canScrollForward
                return Offset.Zero
            }
        }
    }

    LaunchedEffect(messages, localization.language, viewport, density, panelHeightPx, followTail) {
        if (latestOutgoingId != seenOutgoingId) {
            seenOutgoingId = latestOutgoingId
            followTail = true
        }
        if (messages.isEmpty()) {
            followTail = true
            return@LaunchedEffect
        }
        if (panelHeightPx == null) return@LaunchedEffect

        // Wait for the measured panel, padding and item count to reach the lazy list.
        snapshotFlow { state.layoutInfo }.first {
            it.totalItemsCount == messages.size && it.viewportSize == viewport &&
                it.afterContentPadding == afterPaddingPx
        }
        if (!followTail) {
            if (!state.canScrollBackward && !state.canScrollForward) followTail = true
            return@LaunchedEffect
        }
        snapshotFlow { state.isScrollInProgress }.first { !it }
        if (followTail) state.scrollToHistoryEnd(messages.lastIndex)
    }

    LazyColumn(
        modifier = Modifier.fillMaxSize().nestedScroll(manualScroll).semantics {
            testTagsAsResourceId = true
            testTag = CHAT_HISTORY_TAG
            getScrollViewportLength { (viewport.height - (panelHeightPx ?: 0)).coerceAtLeast(0).toFloat() }
            scrollToIndex { index ->
                if (index !in messages.indices) {
                    false
                } else {
                    // Accessibility's direct indexed jump bypasses nested scroll.
                    followTail = false
                    scope.launch {
                        state.scrollToItem(index)
                        followTail = !state.canScrollForward
                    }
                    true
                }
            }
        },
        state = state,
        contentPadding = PaddingValues(start = 10.dp, top = 10.dp, end = 14.dp, bottom = bottomPadding),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        items(messages, key = { it.id }) { message ->
            val modifier = if (appearances != null && appearances.needsLayer(message.id)) {
                Modifier.messageAppearance { appearances.alpha(message.id) }
            } else {
                Modifier
            }
            ChatMessageItem(message, localization, stickersById, modifier)
        }
    }
}

private suspend fun LazyListState.scrollToHistoryEnd(lastIndex: Int) {
    scrollToItem(lastIndex)
    if (!canScrollForward) return

    // A tall last row may still extend past the reading area after aligning its top.
    val layout = layoutInfo
    val last = layout.visibleItemsInfo.lastOrNull { it.index == lastIndex } ?: return
    val remaining = last.offset + last.size + layout.afterContentPadding - layout.viewportEndOffset
    if (remaining > 0) scrollBy(remaining.toFloat())
}
