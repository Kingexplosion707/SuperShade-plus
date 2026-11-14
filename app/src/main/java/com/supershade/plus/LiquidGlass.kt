package com.supershade.plus

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.compositeOver
import androidx.compose.ui.graphics.luminance

/**
 * The Liquid Glass look (Appearance → Backdrop → Liquid Glass): every card and tile surface turns
 * to tinted glass over the blurred backdrop (their borders already catch the light as a specular
 * rim), with the accent bleeding faintly into the glass. [on] false leaves the theme as it is.
 */
@Composable
fun LiquidGlass(on: Boolean, content: @Composable () -> Unit) {
    if (!on) {
        content()
        return
    }
    val cs = MaterialTheme.colorScheme
    val dark = cs.surface.luminance() < 0.5f
    val glass = if (dark) Color.White else Color.White
    val tint = cs.primary
    fun pane(alpha: Float, tintShare: Float) =
        tint.copy(alpha = tintShare).compositeOver(glass.copy(alpha = alpha)).copy(alpha = alpha + tintShare * 0.5f)
    val scheme = if (dark) {
        cs.copy(
            surfaceContainerLowest = pane(0.05f, 0.02f),
            surfaceContainerLow = pane(0.07f, 0.03f),
            surfaceContainer = pane(0.10f, 0.03f),
            surfaceContainerHigh = pane(0.13f, 0.04f),
            surfaceContainerHighest = pane(0.16f, 0.05f),
            surfaceVariant = pane(0.12f, 0.04f),
            secondaryContainer = cs.secondaryContainer.copy(alpha = 0.55f),
            outlineVariant = Color.White.copy(alpha = 0.22f),
        )
    } else {
        cs.copy(
            surfaceContainerLowest = Color.White.copy(alpha = 0.40f),
            surfaceContainerLow = Color.White.copy(alpha = 0.46f),
            surfaceContainer = Color.White.copy(alpha = 0.52f),
            surfaceContainerHigh = Color.White.copy(alpha = 0.58f),
            surfaceContainerHighest = Color.White.copy(alpha = 0.64f),
            surfaceVariant = Color.White.copy(alpha = 0.50f),
            secondaryContainer = cs.secondaryContainer.copy(alpha = 0.65f),
            outlineVariant = Color.White.copy(alpha = 0.7f),
        )
    }
    MaterialTheme(colorScheme = scheme, typography = MaterialTheme.typography, shapes = MaterialTheme.shapes, content = content)
}
