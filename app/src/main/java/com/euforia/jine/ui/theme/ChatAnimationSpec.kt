package com.euforia.jine.ui.theme

import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.MotionDurationScale
import kotlinx.coroutines.CoroutineScope

internal class ChatAnimationSpec(
    val messageFadeMillis: Int = 200,
    val choiceHalfPeriodMillis: Int = 800,
    val nowNanos: () -> Long = System::nanoTime
) {
    companion object {
        val Static = ChatAnimationSpec(messageFadeMillis = 0, choiceHalfPeriodMillis = 0)
    }
}

internal val LocalChatAnimationSpec = staticCompositionLocalOf { ChatAnimationSpec() }

// Android's window recomposer supplies the observable system animator duration scale.
internal fun CoroutineScope.chatMotionScale(): Float =
    coroutineContext[MotionDurationScale]?.scaleFactor ?: 1f
