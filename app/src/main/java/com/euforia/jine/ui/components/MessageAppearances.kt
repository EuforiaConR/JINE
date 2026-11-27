package com.euforia.jine.ui.components

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.snapshotFlow
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Paint
import androidx.compose.ui.unit.dp
import com.euforia.jine.data.model.Message
import com.euforia.jine.ui.theme.LocalChatAnimationSpec
import com.euforia.jine.ui.theme.chatMotionScale
import kotlinx.coroutines.flow.first

internal class MessageArrivalTracker(initialIds: List<String>) {
    private val seen = initialIds.toHashSet()
    private var observedIds = initialIds

    fun knows(id: String): Boolean = id in seen

    fun register(ids: List<String>): List<String> {
        if (ids === observedIds) return emptyList()
        observedIds = ids
        return ids.filter(seen::add)
    }
}

internal class MessageFadeWindow(private val startNanos: Long, private val durationNanos: Long) {
    fun alphaAt(nowNanos: Long): Float {
        if (durationNanos <= 0L) return 1f
        val elapsed = (nowNanos - startNanos).coerceIn(0L, durationNanos)
        return (elapsed.toDouble() / durationNanos).toFloat()
    }
}

internal data class AppearanceUpdate(
    val arrivedIds: List<String>,
    val finishedIds: List<String> = emptyList()
)

internal class MessageAppearances(initialIds: List<String>) {
    private class ActiveFade(val window: MessageFadeWindow) {
        val alpha = mutableFloatStateOf(0f)
    }

    private val arrivals = MessageArrivalTracker(initialIds)
    private val active = mutableStateMapOf<String, ActiveFade>()
    val hasActiveFades get() = active.isNotEmpty()

    fun needsLayer(id: String): Boolean = active.containsKey(id) || !arrivals.knows(id)

    // A lazy row composed before SideEffect sees zero on its first draw, never an opaque flash.
    fun alpha(id: String): Float = active[id]?.alpha?.floatValue ?: if (arrivals.knows(id)) 1f else 0f

    fun observe(ids: List<String>, nowNanos: Long, durationNanos: Long): AppearanceUpdate {
        val newIds = arrivals.register(ids)
        if (durationNanos <= 0L) {
            val finished = active.keys.toList() + newIds
            active.clear()
            return AppearanceUpdate(newIds, finished)
        }
        for (id in newIds) {
            active[id] = ActiveFade(MessageFadeWindow(nowNanos, durationNanos))
        }
        return AppearanceUpdate(newIds)
    }

    fun advance(nowNanos: Long): List<String> {
        val finished = mutableListOf<String>()
        for ((id, fade) in active) {
            val alpha = fade.window.alphaAt(nowNanos)
            if (alpha >= 1f) finished.add(id) else fade.alpha.floatValue = alpha
        }
        finished.forEach(active::remove)
        return finished
    }
}

@Composable
internal fun rememberMessageAppearances(
    messages: List<Message>,
    onArrived: ((Message) -> Unit)? = null,
    onFinished: ((String) -> Unit)? = null
): MessageAppearances? {
    val ids = remember(messages) { messages.map(Message::id) }
    val appearances = remember { MessageAppearances(ids) }
    val spec = LocalChatAnimationSpec.current
    val scale = rememberCoroutineScope().chatMotionScale()
    val durationNanos = (spec.messageFadeMillis * 1_000_000.0 * scale).toLong()
    val latestFinished = rememberUpdatedState(onFinished)

    SideEffect {
        val update = appearances.observe(ids, spec.nowNanos(), durationNanos)
        if (onArrived != null && update.arrivedIds.isNotEmpty()) {
            val newIds = update.arrivedIds.toHashSet()
            for (message in messages) {
                if (message.id in newIds) onArrived(message)
            }
        }
        onFinished?.let { update.finishedIds.forEach(it) }
    }

    if (durationNanos > 0L) {
        LaunchedEffect(appearances, spec.nowNanos) {
            while (true) {
                snapshotFlow { appearances.hasActiveFades }.first { it }
                while (appearances.hasActiveFades) {
                    // Frames invalidate active layers. Monotonic deadlines also expire offscreen.
                    val finished = withFrameNanos { appearances.advance(spec.nowNanos()) }
                    latestFinished.value?.let { finished.forEach(it) }
                }
            }
        }
    }
    return appearances.takeIf { durationNanos > 0L }
}

internal fun Modifier.messageAppearance(alpha: () -> Float): Modifier = drawWithCache {
    val paint = Paint()
    // Leave room for the existing bubble tail offset, without changing the row's layout.
    val bounds = Rect(0f, -2.dp.toPx(), size.width, size.height)
    onDrawWithContent {
        val opacity = alpha()
        when {
            opacity >= 1f -> drawContent()
            opacity > 0f -> {
                // Composite the entire row together. Applying zero alpha to a layout layer
                // would also mark the row hidden for Android accessibility.
                paint.alpha = opacity
                val canvas = drawContext.canvas
                canvas.saveLayer(bounds, paint)
                try {
                    drawContent()
                } finally {
                    canvas.restore()
                }
            }
        }
    }
}
