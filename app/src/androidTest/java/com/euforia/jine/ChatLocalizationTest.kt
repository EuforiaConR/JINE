package com.euforia.jine

import android.content.res.Configuration
import android.graphics.Bitmap
import android.graphics.Rect
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.view.MotionEvent
import android.view.PixelCopy
import android.view.accessibility.AccessibilityNodeInfo
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.euforia.jine.core.localization.JineLanguageManager
import com.euforia.jine.core.localization.ProvideLocalizedResources
import com.euforia.jine.data.model.Sticker
import com.euforia.jine.data.repository.StickerRepository
import com.euforia.jine.ui.components.StickerPicker
import com.euforia.jine.ui.screens.ChatScreen
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import java.util.Locale
import java.util.concurrent.CompletableFuture
import java.util.concurrent.TimeUnit

@RunWith(AndroidJUnit4::class)
class ChatLocalizationTest {
    private val instrumentation get() = InstrumentationRegistry.getInstrumentation()
    private val context get() = instrumentation.targetContext

    @Test
    fun startsInSpanishOnEnglishDeviceAndChangesLanguageWithSelectionIntact() {
        val englishConfig = Configuration(context.resources.configuration).apply { setLocale(Locale.ENGLISH) }
        val englishContext = context.createConfigurationContext(englishConfig)
        val requestedLanguage = mutableStateOf(JineLanguageManager.DEFAULT_LANGUAGE)
        val selected = mutableListOf<Sticker>()
        val stickers = StickerRepository.load(context)

        ActivityScenario.launch(ComponentActivity::class.java).use { scenario ->
            scenario.onActivity { activity ->
                activity.setContent {
                    CompositionLocalProvider(
                        LocalContext provides englishContext,
                        LocalConfiguration provides englishConfig
                    ) {
                        ChatScreen(language = requestedLanguage.value, onStickerSelected = selected::add)
                    }
                }
            }

            awaitNode("¿Estás ahí?")
            awaitNode("Minimizar")

            val spanish = JineLanguageManager().load(context, "es")
            val positions = stickers.map { sticker ->
                val node = awaitNode(spanish.translate(sticker.descriptionKey))
                Rect().also(node::getBoundsInScreen)
            }
            assertEquals(4, positions.take(4).map { it.left }.distinct().size)
            assertEquals(1, positions.take(4).map { it.top }.distinct().size)
            assertEquals(1, positions.drop(4).map { it.top }.distinct().size)
            assertTrue(positions[4].top > positions[0].top)

            val firstSticker = awaitNode("De acuerdo")
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                assertEquals("Sin seleccionar", firstSticker.stickerControl().stateDescription.toString())
            }
            tap(firstSticker)
            awaitNode("De acuerdo", selected = true)
            val spanishLove = boundsOf(awaitNode("Que viva el amor"))
            val spanishLovePixels = capturePixels(scenario, spanishLove)
            scenario.onActivity { requestedLanguage.value = JineLanguageManager.ENGLISH }

            awaitNode("Are you there?")
            awaitNode("Minimize")
            val englishSticker = awaitNode("Okay", selected = true)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                assertEquals("Sticker selected", englishSticker.stickerControl().stateDescription.toString())
            }
            val englishLove = boundsOf(awaitNode("Love forever"))
            assertEquals(spanishLove, englishLove)
            assertFalse(
                "The love sticker should change visually with the language",
                spanishLovePixels.contentEquals(capturePixels(scenario, englishLove))
            )
            scenario.onActivity { assertEquals(listOf(stickers.first()), selected) }
        }
    }

    @Test
    fun compactChatAndStickerGridScrollIndependently() {
        val lastMessage = "Tengo muchas cosas que contarte hoy. Espero que podamos hablar un rato."
        ActivityScenario.launch(ComponentActivity::class.java).use { scenario ->
            scenario.onActivity { activity ->
                activity.setContent {
                    Box(Modifier.size(width = 320.dp, height = 260.dp)) {
                        ChatScreen()
                    }
                }
            }

            scrollForwardFrom(awaitNode("De acuerdo"))
            val lastSticker = awaitNode("Esto.").stickerControl()
            assertTrue(lastSticker.performAction(AccessibilityNodeInfo.ACTION_CLICK))
            awaitNode("Esto.", selected = true)

            scrollForwardFrom(awaitNode("p-chaaaan!!"))
            awaitNode(lastMessage)
            awaitNode("Esto.", selected = true)
        }
    }

    @Test
    fun disabledPickerDoesNotEmitAnEvent() {
        val stickers = StickerRepository.load(context).take(1)
        val selected = mutableListOf<Sticker>()
        ActivityScenario.launch(ComponentActivity::class.java).use { scenario ->
            scenario.onActivity { activity ->
                activity.setContent {
                    ProvideLocalizedResources("en") {
                        StickerPicker(
                            stickers = stickers,
                            selectedStickerId = stickers.single().id,
                            onStickerSelected = selected::add,
                            descriptionFor = { "Okay" },
                            enabled = false,
                            language = JineLanguageManager.ENGLISH
                        )
                    }
                }
            }
            val label = awaitNode("Okay", selected = true)
            assertFalse(label.stickerControl().isEnabled)
            tap(label)
            scenario.onActivity { assertTrue(selected.isEmpty()) }
        }
    }

    @Test
    fun emptyPickerShowsLocalizedStatus() {
        ActivityScenario.launch(ComponentActivity::class.java).use { scenario ->
            scenario.onActivity { activity ->
                activity.setContent {
                    ProvideLocalizedResources("en") {
                        StickerPicker(emptyList(), null, {}, { it.descriptionKey })
                    }
                }
            }
            awaitNode("No stickers available.")
        }
    }

    // The platform accessibility tree lets the test verify labels, bounds and real taps.
    private fun awaitNode(label: String, selected: Boolean = false): AccessibilityNodeInfo {
        val deadline = SystemClock.uptimeMillis() + 5_000
        do {
            val root = instrumentation.uiAutomation.rootInActiveWindow
            val found = root?.findNode(label, selected)
            if (found != null) return found
            SystemClock.sleep(50)
        } while (SystemClock.uptimeMillis() < deadline)

        throw AssertionError("No visible accessibility node for '$label' (selected required: $selected)")
    }

    private fun AccessibilityNodeInfo.findNode(label: String, selected: Boolean): AccessibilityNodeInfo? {
        val matches = text?.toString() == label || contentDescription?.toString() == label
        if (matches && isVisibleToUser && (!selected || isStickerSelected())) return this
        for (index in 0 until childCount) {
            val found = getChild(index)?.findNode(label, selected)
            if (found != null) return found
        }
        return null
    }

    // Compose can expose the description as a child of the checkable selection control.
    private fun AccessibilityNodeInfo.stickerControl(): AccessibilityNodeInfo {
        return if (isCheckable) this else parent ?: this
    }

    @Suppress("DEPRECATION") // Android maps selected controls other than tabs to checked.
    private fun AccessibilityNodeInfo.isStickerSelected() = stickerControl().isChecked

    private fun scrollForwardFrom(node: AccessibilityNodeInfo) {
        var ancestor = node.parent
        while (ancestor != null && !ancestor.isScrollable) {
            ancestor = ancestor.parent
        }
        assertTrue("Expected a scrollable container", ancestor != null)
        assertTrue(ancestor?.performAction(AccessibilityNodeInfo.ACTION_SCROLL_FORWARD) == true)
        instrumentation.uiAutomation.waitForIdle(250, 5_000)
    }

    private fun tap(node: AccessibilityNodeInfo) {
        val bounds = boundsOf(node)
        val start = SystemClock.uptimeMillis()
        for (action in listOf(MotionEvent.ACTION_DOWN, MotionEvent.ACTION_UP)) {
            val event = MotionEvent.obtain(
                start, SystemClock.uptimeMillis(), action, bounds.exactCenterX(), bounds.exactCenterY(), 0
            )
            try {
                instrumentation.sendPointerSync(event)
            } finally {
                event.recycle()
            }
        }
    }

    private fun boundsOf(node: AccessibilityNodeInfo): Rect = Rect().also(node::getBoundsInScreen)

    private fun capturePixels(scenario: ActivityScenario<ComponentActivity>, bounds: Rect): IntArray {
        instrumentation.uiAutomation.waitForIdle(250, 5_000)
        val width = bounds.width()
        val height = bounds.height()
        val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        try {
            val result = CompletableFuture<Int>()
            scenario.onActivity { activity ->
                val location = IntArray(2)
                activity.window.decorView.getLocationOnScreen(location)
                val windowBounds = Rect(bounds).apply { offset(-location[0], -location[1]) }
                PixelCopy.request(
                    activity.window,
                    windowBounds,
                    bitmap,
                    { result.complete(it) },
                    Handler(Looper.getMainLooper())
                )
            }
            assertEquals(PixelCopy.SUCCESS, result.get(5, TimeUnit.SECONDS).toInt())
            return IntArray(width * height).also { pixels ->
                bitmap.getPixels(pixels, 0, width, 0, 0, width, height)
            }
        } finally {
            bitmap.recycle()
        }
    }
}
