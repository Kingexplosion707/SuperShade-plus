package com.supershade.plus

import android.graphics.Bitmap
import android.os.Build
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Pause
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.PushPin
import androidx.compose.material.icons.rounded.SkipNext
import androidx.compose.material.icons.rounded.SkipPrevious
import androidx.compose.material.icons.outlined.PushPin
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameMillis
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.BlurredEdgeTreatment
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * Spicy Lyrics as a live activity, in place of the media card while Spicy Lyrics Plus sends this
 * song's lyrics ([SpicyLink]): the Canvas playing behind it (else the cover, blurred), the
 * animated cover beside the song, the lyrics word-synced in Spicy Lyrics' style (each word filling
 * as it's sung, with its glow, in the colour, size and opacity of its overlay), the song's
 * progress and the controls. Its pin floats the lyrics out as Spicy Lyrics' overlay, and back.
 */
@Composable
fun SpicyLiveActivityCard(
    live: SpicyLink.Live,
    albumArt: Bitmap?,
    onPlayPause: () -> Unit,
    onSkipNext: () -> Unit,
    onSkipPrevious: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val shape = RoundedCornerShape(30.dp)
    var canvasFailed by remember(live.canvasUrl) { mutableStateOf(false) }
    var motionFailed by remember(live.motionUrl) { mutableStateOf(false) }
    val art = remember(albumArt) { albumArt?.asImageBitmap() }
    val ink = Color(live.ink)
    val inkEnd = Color(live.inkEnd)
    Box(
        modifier
            .fillMaxWidth()
            .clip(shape)
            .background(Color(0xFF101014))
            .border(
                BorderStroke(
                    1.dp,
                    Brush.linearGradient(listOf(Color.White.copy(alpha = 0.45f), Color.White.copy(alpha = 0.06f), ink.copy(alpha = 0.35f))),
                ),
                shape,
            ),
    ) {
        // Behind it all: the Canvas, else the cover blurred.
        val canvas = live.canvasUrl?.takeIf { !canvasFailed }
        if (art != null) {
            val blurred = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                Modifier.blur(36.dp, BlurredEdgeTreatment.Rectangle)
            } else Modifier
            Image(art, null, contentScale = ContentScale.Crop, modifier = Modifier.matchParentSize().then(blurred).graphicsLayer { alpha = 0.85f })
        }
        if (canvas != null) {
            LoopingVideo(canvas, Modifier.matchParentSize(), onFailed = { canvasFailed = true })
        }
        // A veil for the words to read against, darker at the bottom, as the overlay's backdrop.
        val veil = (0.25f + live.backdropOpacity * 0.5f).coerceIn(0.25f, 0.8f)
        Box(
            Modifier.matchParentSize().background(
                Brush.verticalGradient(listOf(Color.Black.copy(alpha = veil * 0.55f), Color.Black.copy(alpha = veil), Color.Black.copy(alpha = veil * 1.15f))),
            ),
        )
        // A glassy highlight across the top.
        Box(
            Modifier.matchParentSize().background(
                Brush.verticalGradient(0f to Color.White.copy(alpha = 0.10f), 0.25f to Color.Transparent),
            ),
        )
        Column(Modifier.fillMaxWidth().padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.size(54.dp).clip(RoundedCornerShape(14.dp)).background(Color.White.copy(alpha = 0.08f))) {
                    if (art != null) Image(art, null, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
                    val motion = live.motionUrl?.takeIf { !motionFailed }
                    if (motion != null) LoopingVideo(motion, Modifier.fillMaxSize(), onFailed = { motionFailed = true })
                }
                Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f)) {
                    Text(
                        live.title, color = Color.White, fontSize = 16.sp, fontWeight = FontWeight.Bold,
                        maxLines = 1, overflow = TextOverflow.Ellipsis,
                    )
                    Text(
                        live.artist, color = Color.White.copy(alpha = 0.7f), fontSize = 13.sp,
                        maxLines = 1, overflow = TextOverflow.Ellipsis,
                    )
                }
                // Float the lyrics out as Spicy Lyrics' overlay, or back.
                val pinned = live.overlayOn
                Box(
                    Modifier
                        .size(38.dp)
                        .clip(CircleShape)
                        .background(if (pinned) ink.copy(alpha = 0.9f) else Color.White.copy(alpha = 0.14f))
                        .clickable { SpicyLink.setOverlay(context, !pinned) },
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        if (pinned) Icons.Rounded.PushPin else Icons.Outlined.PushPin,
                        contentDescription = if (pinned) "Bring the lyrics back" else "Float the lyrics as an overlay",
                        tint = if (pinned) Color.Black else Color.White,
                        modifier = Modifier.size(19.dp),
                    )
                }
            }
            Spacer(Modifier.height(14.dp))
            SpicyLyrics(live, ink, inkEnd, Modifier.fillMaxWidth().height(LYRICS_HEIGHT))
            Spacer(Modifier.height(12.dp))
            SpicyProgress(live, ink)
            Row(
                Modifier.fillMaxWidth().padding(top = 6.dp),
                horizontalArrangement = Arrangement.Center,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                RoundButton(Icons.Rounded.SkipPrevious, "Previous", 26.dp, onSkipPrevious)
                Spacer(Modifier.width(22.dp))
                RoundButton(if (live.playing) Icons.Rounded.Pause else Icons.Rounded.PlayArrow, if (live.playing) "Pause" else "Play", 34.dp, onPlayPause, filled = true)
                Spacer(Modifier.width(22.dp))
                RoundButton(Icons.Rounded.SkipNext, "Next", 26.dp, onSkipNext)
            }
        }
    }
}

@Composable
private fun RoundButton(icon: androidx.compose.ui.graphics.vector.ImageVector, label: String, size: androidx.compose.ui.unit.Dp, onClick: () -> Unit, filled: Boolean = false) {
    Box(
        Modifier
            .size(size + 20.dp)
            .clip(CircleShape)
            .background(if (filled) Color.White.copy(alpha = 0.92f) else Color.Transparent)
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Icon(icon, label, tint = if (filled) Color.Black else Color.White, modifier = Modifier.size(size))
    }
}

/** The song's progress: a thin bar, running on between Spicy Lyrics' answers. */
@Composable
private fun SpicyProgress(live: SpicyLink.Live, ink: Color) {
    var now by remember { mutableLongStateOf(live.position()) }
    LaunchedEffect(live) {
        while (true) {
            now = live.position()
            kotlinx.coroutines.delay(500L)
        }
    }
    val fraction = if (live.durationMs > 0) (now.toFloat() / live.durationMs).coerceIn(0f, 1f) else 0f
    Box(Modifier.fillMaxWidth().height(4.dp).clip(CircleShape).background(Color.White.copy(alpha = 0.18f))) {
        Box(Modifier.fillMaxWidth(fraction).height(4.dp).clip(CircleShape).background(Brush.horizontalGradient(listOf(Color.White, ink))))
    }
}

/**
 * The lyrics in Spicy Lyrics' style: the line being sung large, each word filling from dim to
 * bright as it's sung with a soft glow under the sung part, the last line fading out above and
 * the next ones waiting below; lines change with a spring. Line-synced lyrics fill a line at a
 * time; unsynced ones simply show.
 */
@Composable
private fun SpicyLyrics(live: SpicyLink.Live, ink: Color, inkEnd: Color, modifier: Modifier) {
    val lyrics = live.lyrics
    if (lyrics == null || lyrics.lines.isEmpty()) {
        Box(modifier, contentAlignment = Alignment.Center) {
            Text(
                when (live.state) {
                    "loading" -> "Finding the lyrics…"
                    else -> "No lyrics for this song"
                },
                color = Color.White.copy(alpha = 0.55f), fontSize = 15.sp,
            )
        }
        return
    }
    val lines = remember(lyrics) { lyrics.lines.filter { !it.background && it.words.any { w -> w.text.isNotBlank() } } }
    // The clock, read while drawing; only a change of line recomposes.
    val clock = remember { mutableLongStateOf(live.position()) }
    LaunchedEffect(live) {
        while (true) withFrameMillis { clock.longValue = live.position() }
    }
    val index by remember(lines) {
        derivedStateOf {
            if (lyrics.isStatic) 0 else lines.indexOfLast { it.startMs <= clock.longValue + LEAD_MS }.coerceAtLeast(0)
        }
    }
    val alpha = live.lyricsOpacity.coerceIn(0.1f, 1f)
    val scale = (live.fontScale / 0.78f).coerceIn(0.8f, 1.25f)
    AnimatedContent(
        targetState = index,
        transitionSpec = {
            (slideInVertically(spring(Spring.DampingRatioLowBouncy, Spring.StiffnessLow)) { it / 3 } + fadeIn(tween(260)))
                .togetherWith(slideOutVertically(tween(260)) { -it / 3 } + fadeOut(tween(200)))
        },
        modifier = modifier.graphicsLayer { this.alpha = alpha },
        label = "spicyLine",
    ) { i ->
        Column(Modifier.fillMaxSize(), verticalArrangement = Arrangement.Center) {
            lines.getOrNull(i - 1)?.takeIf { !lyrics.isStatic }?.let {
                Text(
                    it.text, color = Color.White.copy(alpha = 0.32f), fontSize = (15 * scale).sp, fontWeight = FontWeight.SemiBold,
                    maxLines = 1, overflow = TextOverflow.Ellipsis,
                )
                Spacer(Modifier.height(4.dp))
            }
            val line = lines.getOrNull(i)
            if (line != null) {
                if (lyrics.isStatic) {
                    Text(line.text, color = Color.White.copy(alpha = 0.9f), fontSize = (21 * scale).sp, fontWeight = FontWeight.Bold)
                } else {
                    SungLine(line, lyrics.wordSynced && !line.lineTimed, clock, ink, inkEnd, live.glow, live.glowStrength, (23 * scale))
                }
            }
            lines.getOrNull(i + 1)?.let {
                Spacer(Modifier.height(6.dp))
                Text(
                    it.text, color = Color.White.copy(alpha = 0.42f), fontSize = (16 * scale).sp, fontWeight = FontWeight.SemiBold,
                    maxLines = 2, overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}

/** One line filling word by word ([wordSynced]) or as a whole, drawn by hand for the fill and glow. */
@Composable
private fun SungLine(
    line: SpicyLink.Line,
    wordSynced: Boolean,
    clock: androidx.compose.runtime.MutableLongState,
    ink: Color,
    inkEnd: Color,
    glow: Boolean,
    glowStrength: Float,
    sizeSp: Float,
) {
    val measurer = rememberTextMeasurer()
    val density = LocalDensity.current
    // Where each word sits in the line's text.
    val spans = remember(line) {
        val out = ArrayList<IntArray>()
        var at = 0
        for ((n, w) in line.words.withIndex()) {
            val text = w.text.trim()
            if (n > 0 && !w.attached) at += 1
            out += intArrayOf(at, at + text.length)
            at += text.length
        }
        out
    }
    val style = TextStyle(fontSize = sizeSp.sp, fontWeight = FontWeight.ExtraBold, lineHeight = (sizeSp * 1.18f).sp, textAlign = TextAlign.Start)
    BoxWithConstraints(Modifier.fillMaxWidth()) {
        val widthPx = constraints.maxWidth
        val layout = remember(line, widthPx, sizeSp) {
            measurer.measure(line.text, style, maxLines = 3, overflow = TextOverflow.Ellipsis, constraints = Constraints(maxWidth = widthPx))
        }
        val heightDp = with(density) { layout.size.height.toDp() }
        val brush = Brush.verticalGradient(listOf(ink, inkEnd))
        Canvas(Modifier.fillMaxWidth().height(heightDp)) {
            val now = clock.longValue
            // Shadow.None: a layout keeps the last shadow drawn with it.
            drawText(layout, color = Color.White.copy(alpha = DIM_ALPHA), shadow = Shadow.None)
            val pieces: List<Pair<IntArray, Float>> = if (wordSynced) {
                line.words.indices.map { k ->
                    val w = line.words[k]
                    spans[k] to ((now - w.startMs).toFloat() / (w.endMs - w.startMs).coerceAtLeast(1)).coerceIn(0f, 1f)
                }
            } else {
                listOf(intArrayOf(0, line.text.length) to ((now - line.startMs).toFloat() / (line.endMs - line.startMs).coerceAtLeast(1)).coerceIn(0f, 1f))
            }
            val glowShadow = if (glow) Shadow(ink.copy(alpha = (0.55f * glowStrength).coerceIn(0f, 1f)), Offset.Zero, sizeSp * density.density * 0.45f) else Shadow.None
            for ((span, progress) in pieces) {
                if (progress <= 0f || span[1] <= span[0] || span[1] > line.text.length) continue
                val first = layout.getBoundingBox(span[0])
                val last = layout.getBoundingBox(span[1] - 1)
                if (layout.getLineForOffset(span[0]) == layout.getLineForOffset(span[1] - 1)) {
                    val right = first.left + (last.right - first.left) * progress
                    clipRect(first.left, first.top - 8f, right, first.bottom + 8f) {
                        drawText(layout, brush = brush, shadow = glowShadow)
                    }
                } else {
                    // A whole-line fill that wraps: fill each visual line in turn.
                    val firstLine = layout.getLineForOffset(span[0])
                    val lastLine = layout.getLineForOffset(span[1] - 1)
                    val total = (firstLine..lastLine).sumOf { (layout.getLineRight(it) - layout.getLineLeft(it)).toDouble() }.toFloat()
                    var remaining = total * progress
                    for (l in firstLine..lastLine) {
                        val left = layout.getLineLeft(l)
                        val width = layout.getLineRight(l) - left
                        val take = remaining.coerceIn(0f, width)
                        if (take > 0f) {
                            clipRect(left, layout.getLineTop(l), left + take, layout.getLineBottom(l)) {
                                drawText(layout, brush = brush, shadow = glowShadow)
                            }
                        }
                        remaining -= width
                        if (remaining <= 0f) break
                    }
                }
            }
        }
    }
}

private val LYRICS_HEIGHT = 136.dp
/** A line counts as begun this long before its first word, so the change lands on time. */
private const val LEAD_MS = 150L
private const val DIM_ALPHA = 0.38f

/**
 * The live activity in place of [fallback] when the setting is on and Spicy Lyrics is sending
 * this song ([title] by [artist]).
 */
@Composable
fun SpicyLiveActivityOr(
    title: String,
    artist: String,
    albumArt: Bitmap?,
    onPlayPause: () -> Unit,
    onSkipNext: () -> Unit,
    onSkipPrevious: () -> Unit,
    modifier: Modifier = Modifier,
    fallback: @Composable () -> Unit,
) {
    val context = LocalContext.current
    val live by SpicyLink.live.collectAsState()
    val current = live
    val enabled = remember { PlusSettings.spicyLiveActivity(context) }
    if (enabled && current != null && current.fresh() && SpicyLink.matches(current, title, artist)) {
        SpicyLiveActivityCard(current, albumArt, onPlayPause, onSkipNext, onSkipPrevious, modifier)
    } else {
        fallback()
    }
}
