package com.supershade.plus

import android.graphics.RenderEffect
import android.graphics.RuntimeShader
import android.os.Build
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.spring
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.asComposeRenderEffect
import androidx.compose.ui.graphics.graphicsLayer

/**
 * A pop-up coming out from behind the camera, like a window restored from the macOS Dock: it
 * pours out of a narrow neck at the top centre and widens into its shape as it drops into place.
 * From Android 13 it's a real funnel warp (an AGSL shader); before that, a zoom from the top
 * centre that widens before it lengthens. It runs once, when the pop-up appears.
 */
fun Modifier.genieReveal(): Modifier = composed {
    val progress = remember { Animatable(0f) }
    LaunchedEffect(Unit) {
        progress.animateTo(1f, spring(dampingRatio = 0.86f, stiffness = 170f))
    }
    // A shader the device can't compile falls back to the zoom rather than failing the pop-up.
    val shader = remember { if (Build.VERSION.SDK_INT >= 33) runCatching { GenieShader.create() }.getOrNull() else null }
    graphicsLayer {
        val p = progress.value
        alpha = (p * 3.5f).coerceIn(0f, 1f)
        if (p >= 0.999f) {
            renderEffect = null
            return@graphicsLayer
        }
        if (Build.VERSION.SDK_INT >= 33 && shader != null && size.width > 0f && size.height > 0f) {
            val s = shader as RuntimeShader
            renderEffect = runCatching {
                s.setFloatUniform("size", size.width, size.height)
                s.setFloatUniform("progress", p)
                RenderEffect.createRuntimeShaderEffect(s, "content").asComposeRenderEffect()
            }.getOrNull()
        } else {
            transformOrigin = TransformOrigin(0.5f, 0f)
            val wide = (p * 1.5f).coerceIn(0f, 1f)
            scaleX = 0.12f + 0.88f * (1f - (1f - wide) * (1f - wide))
            scaleY = 0.04f + 0.96f * p
        }
    }
}

private object GenieShader {
    // Each row is squeezed toward the centre by how recently it came out of the top: the rows
    // still near the neck are narrow, those further down have opened up; the whole picture slides
    // down out of the top edge as it goes.
    private const val SOURCE = """
        uniform shader content;
        uniform float2 size;
        uniform float progress;

        half4 main(float2 p) {
            float t = clamp(progress, 0.0, 1.0);
            float drop = (1.0 - t) * size.y;
            float sy = p.y - drop * 0.92;
            if (sy < 0.0) { return half4(0.0); }
            float below = clamp(p.y / max(size.y, 1.0), 0.0, 1.0);
            float open = smoothstep(0.0, 1.0, clamp(t * 1.35 + below * 0.55 - 0.25, 0.0, 1.0));
            float width = mix(0.07, 1.0, open);
            float cx = size.x * 0.5;
            float sx = cx + (p.x - cx) / width;
            if (sx < 0.0 || sx > size.x || sy > size.y) { return half4(0.0); }
            return content.eval(float2(sx, sy));
        }
    """

    fun create(): Any = RuntimeShader(SOURCE)
}
