package com.euforia.jine.core.localization

import android.content.res.Configuration
import android.content.res.Resources
import androidx.annotation.StringRes
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import java.util.Locale

private val LocalLocalizedResources = compositionLocalOf<Resources?> { null }

@Composable
fun rememberLocalization(language: String): LocalizationSnapshot {
    val context = LocalContext.current
    val manager = remember(context) { JineLanguageManager() }
    return remember(context, language) { manager.load(context, language) }
}

@Composable
fun ProvideLocalizedResources(language: String, content: @Composable () -> Unit) {
    val context = LocalContext.current
    val configuration = LocalConfiguration.current
    val resources = remember(context, configuration, language) {
        val localizedConfiguration = Configuration(configuration).apply {
            setLocale(Locale.forLanguageTag(language))
        }
        context.createConfigurationContext(localizedConfiguration).resources
    }

    // LocalContext stays the same, so textures keep their existing Compose cache keys.
    CompositionLocalProvider(LocalLocalizedResources provides resources, content = content)
}

@Composable
fun localizedStringResource(@StringRes id: Int): String {
    return LocalLocalizedResources.current?.getString(id) ?: stringResource(id)
}
