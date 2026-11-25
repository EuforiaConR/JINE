package com.euforia.jine.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.unit.dp
import com.euforia.jine.core.assets.JineAssets
import com.euforia.jine.ui.theme.JineWindowOuter

internal const val JINE_UI_SCALE = 1f

@Composable
fun JineWindow(
    modifier: Modifier = Modifier,
    header: @Composable () -> Unit,
    content: @Composable BoxScope.() -> Unit
) {
    Box(
        modifier = modifier
            .fillMaxSize()
            .background(JineWindowOuter)
            .safeDrawingPadding()
            .padding(8.dp)
    ) {

        // Marco original de la ventana.
        NineSliceImage(
            path = JineAssets.Ui.WINDOW_BASE,
            slices = NineSliceInsets(
                left = 8,
                top = 42,
                right = 12,
                bottom = 22
            ),
            modifier = Modifier.matchParentSize(),
            scale = JINE_UI_SCALE
        )

        // Área independiente de la cabecera.
        Box(
            modifier = Modifier
                .align(Alignment.TopStart)
                .fillMaxWidth()
                .padding(
                    start = 8.dp * JINE_UI_SCALE,
                    top = 8.dp * JINE_UI_SCALE,
                    end = 12.dp * JINE_UI_SCALE
                )
                .height(26.dp * JINE_UI_SCALE),
            contentAlignment = Alignment.CenterStart
        ) {
            header()
        }

        // Área interior de la ventana.
        Box(
            modifier = Modifier
                .matchParentSize()
                .padding(
                    start = 8.dp * JINE_UI_SCALE,
                    top = 42.dp * JINE_UI_SCALE,
                    end = 12.dp * JINE_UI_SCALE,
                    bottom = 22.dp * JINE_UI_SCALE
                )
                .clipToBounds(),
            content = content
        )
    }
}
