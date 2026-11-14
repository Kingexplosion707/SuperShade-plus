package com.supershade.ui.shade

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.util.VelocityTracker
import androidx.compose.ui.input.pointer.util.addPointerInputChange
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import com.supershade.haptics.LocalSuperHaptics
import com.supershade.haptics.SuperHaptics
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.launch
import kotlin.math.abs
import kotlin.math.sign

/**
 * Finger-tracked horizontal swipe with release + fling physics:
 *  - 1:1 tracking while dragging, subtle tilt, rubber-band when a direction is disabled
 *  - fling velocity or distance past the threshold commits the dismissal, carrying the
 *    release velocity into the exit spring
 *  - otherwise springs back with a lively overshoot, again seeded with the release velocity
 *  - haptic tick when crossing the commit threshold in either direction
 *  - tapping the exposed Dismiss badge behind the card commits the dismissal too
 *
 * Plus: the finger's position is kept in plain state while dragging and handed to the spring
 * only once, on release (or when the gesture is cancelled). Snapping the spring per move event
 * from launched coroutines could land after the release and cancel the settle, leaving the card
 * stuck half-swiped.
 */
@Stable
class FluidSwipeState internal constructor(
    internal val scope: CoroutineScope,
) {
    internal val offset = Animatable(0f)
    internal var widthPx by mutableFloatStateOf(1f)
    internal var dragging by mutableStateOf(false)
    internal var dragOffset by mutableFloatStateOf(0f)
    /** Bumped at each gesture start, so a settle from an older gesture leaves the new one alone. */
    internal var gesture by mutableIntStateOf(0)

    /** Current horizontal translation in px (positive = to the right). */
    val offsetPx: Float get() = if (dragging) dragOffset else offset.value

    /** Distance after which a release commits the dismissal. */
    val thresholdPx: Float get() = widthPx * 0.28f

    /** 0..1 progress towards the commit threshold-ish range, used for background effects. */
    val progress: Float get() = (abs(offsetPx) / (widthPx * 0.5f)).coerceIn(0f, 1f)

    val isAtRest: Boolean get() = abs(offsetPx) < 4f
}

@Composable
fun rememberFluidSwipeState(key: Any?): FluidSwipeState {
    val scope = rememberCoroutineScope()
    return remember(key) { FluidSwipeState(scope) }
}

@Composable
fun FluidSwipeToDismiss(
    state: FluidSwipeState,
    onDismissed: (towardsEnd: Boolean) -> Unit,
    modifier: Modifier = Modifier,
    enableStartToEnd: Boolean = true,
    enableEndToStart: Boolean = true,
    backgroundContent: @Composable BoxScope.() -> Unit,
    content: @Composable BoxScope.() -> Unit,
) {
    val context = LocalContext.current
    val haptics: SuperHaptics = LocalSuperHaptics.current ?: remember(context) { SuperHaptics(context) }
    val density = LocalDensity.current
    val flingVelocityPx = with(density) { 900.dp.toPx() }
    val currentOnDismissed by rememberUpdatedState(onDismissed)
    val allowStart by rememberUpdatedState(enableStartToEnd)
    val allowEnd by rememberUpdatedState(enableEndToStart)

    LaunchedEffect(state) {
        snapshotFlow { abs(state.offsetPx) > state.thresholdPx }
            .distinctUntilChanged()
            .collect { past -> if (past && !state.isAtRest) haptics.notificationDismissTick() }
    }

    fun allowed(dir: Float) = if (dir > 0f) allowStart else allowEnd

    /** Hands the finger's position to the spring and commits or springs back. */
    fun settle(velocity: Float, forceCommit: Boolean = false) {
        val gesture = state.gesture
        val from = state.offsetPx
        state.scope.launch {
            state.offset.snapTo(from)
            if (state.gesture == gesture) state.dragging = false
            val dir = sign(from)
            val flung = abs(velocity) > flingVelocityPx && sign(velocity) == dir && abs(from) > 8f
            val commit = dir != 0f && allowed(dir) && (forceCommit || abs(from) > state.thresholdPx || flung)
            if (commit) {
                haptics.notificationDismissCommit()
                state.offset.animateTo(
                    dir * state.widthPx * 1.2f,
                    spring(Spring.DampingRatioNoBouncy, 420f),
                    initialVelocity = velocity,
                )
                currentOnDismissed(dir > 0f)
                // If the item survives (e.g. dismissal rejected), glide back.
                state.offset.animateTo(0f, spring(0.8f, 300f))
            } else {
                state.offset.animateTo(0f, spring(0.62f, 450f), initialVelocity = velocity)
            }
        }
    }

    Box(modifier = modifier) {
        Box(
            modifier = Modifier
                .matchParentSize()
                .pointerInput(state) {
                    // The exposed badge behind a swiped card: tapping it dismisses.
                    detectTapGestures {
                        if (!state.isAtRest && !state.dragging) settle(0f, forceCommit = true)
                    }
                },
        ) { backgroundContent() }
        Box(
            modifier = Modifier
                .onSizeChanged { state.widthPx = it.width.toFloat().coerceAtLeast(1f) }
                .graphicsLayer {
                    val o = state.offsetPx
                    translationX = o
                    rotationZ = (o / state.widthPx) * 3.5f
                }
                .pointerInput(state) {
                    val tracker = VelocityTracker()
                    detectHorizontalDragGestures(
                        onDragStart = {
                            tracker.resetTracking()
                            state.gesture++
                            state.dragOffset = state.offsetPx
                            state.dragging = true
                            state.scope.launch { state.offset.stop() }
                        },
                        onDragEnd = {
                            val v = runCatching { tracker.calculateVelocity().x }.getOrDefault(0f)
                            settle(v)
                        },
                        onDragCancel = { settle(0f) },
                        onHorizontalDrag = { change, delta ->
                            tracker.addPointerInputChange(change)
                            change.consume()
                            val cur = state.dragOffset
                            val next = cur + delta
                            val dir = if (next != 0f) sign(next) else sign(delta)
                            // Rubber-band when the direction is disabled.
                            val effective = if (allowed(dir)) delta else delta * 0.18f
                            state.dragOffset = cur + effective
                        },
                    )
                },
        ) { content() }
    }

    // Never leave a card parked off its rest position with no finger on it.
    LaunchedEffect(state) {
        snapshotFlow { !state.dragging && !state.offset.isRunning && !state.isAtRest }
            .distinctUntilChanged()
            .collect { stuck -> if (stuck) state.offset.animateTo(0f, spring(0.8f, 300f)) }
    }
}
