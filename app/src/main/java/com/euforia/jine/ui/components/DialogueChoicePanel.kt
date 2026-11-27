package com.euforia.jine.ui.components

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.runtime.Composable
import androidx.compose.runtime.key
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.testTag
import androidx.compose.ui.semantics.testTagsAsResourceId
import androidx.compose.ui.unit.dp
import com.euforia.jine.core.localization.LocalizationSnapshot
import com.euforia.jine.ui.screens.ChatInputMode
import com.euforia.jine.ui.theme.JINE_PANEL_OVERLAY_ALPHA
import com.euforia.jine.ui.theme.JineSentBubble
import com.euforia.jine.ui.theme.JineStickerOverlay
import com.euforia.jine.ui.theme.LocalChatAnimationSpec
import com.euforia.jine.ui.theme.chatMotionScale

internal const val CHAT_CHOICE_PANEL_TAG = "chat-choice-panel"
internal const val CHAT_CHOICE_TAG_PREFIX = "chat-choice-"
private val preferredPanelHeight = 180.dp

@Composable
internal fun DialogueChoicePanel(
    prompt: ChatInputMode.Choices,
    localization: LocalizationSnapshot,
    onChoicePressed: (promptId: String, choiceId: String) -> Unit
) {
    val halfPeriod = LocalChatAnimationSpec.current.choiceHalfPeriodMillis
    val scale = rememberCoroutineScope().chatMotionScale()
    val backgroundAlpha = if (halfPeriod > 0 && scale > 0f) {
        key(prompt.promptId) {
            val transition = rememberInfiniteTransition(label = "Dialogue choice backgrounds")
            val alpha = transition.animateFloat(
                initialValue = 0f,
                targetValue = 1f,
                animationSpec = infiniteRepeatable(tween(halfPeriod, easing = LinearEasing), RepeatMode.Reverse),
                label = "Choice background opacity"
            )
            remember(alpha) { { alpha.value } }
        }
    } else {
        null
    }
    LazyColumn(
        modifier = Modifier
            .fillMaxWidth()
            .height(preferredPanelHeight)
            .background(JineStickerOverlay.copy(alpha = JINE_PANEL_OVERLAY_ALPHA))
            .semantics {
                testTagsAsResourceId = true
                testTag = CHAT_CHOICE_PANEL_TAG
            },
        contentPadding = PaddingValues(8.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp),
        horizontalAlignment = Alignment.End
    ) {
        items(prompt.options, key = { it.id }) { choice ->
            MessageBubble(
                message = localization.translate(choice.textKey),
                modifier = Modifier
                    // Keep the touch target inside the bubble, away from the gaps between choices.
                    .defaultMinSize(minWidth = 48.dp, minHeight = 48.dp)
                    .clickable(role = Role.Button, interactionSource = null, indication = null) {
                        onChoicePressed(prompt.promptId, choice.id)
                    }
                    .semantics { testTag = "$CHAT_CHOICE_TAG_PREFIX${choice.id}" },
                bubbleColor = JineSentBubble,
                backgroundAlpha = backgroundAlpha
            )
        }
    }
}
