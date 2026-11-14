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
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import kotlinx.coroutines.launch
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.displayCutoutPadding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Pause
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.PictureInPictureAlt
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
import androidx.compose.ui.input.pointer.pointerInput
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
    val media = org.koin.compose.koinInject<com.supershade.domain.media.MediaRepository>()
    val shade = org.koin.compose.koinInject<com.supershade.viewmodel.ShadeViewModel>()
    val haptic = androidx.compose.ui.platform.LocalHapticFeedback.current
    // The cover and the song open the player (its now-playing screen where it has one).
    val openPlayer: () -> Unit = {
        if (openPlayerApp(context, media, live.player)) shade.close()
    }
    // A tapped lyric, or the progress bar, moves the song there.
    val seek: (Long) -> Unit = { ms ->
        val to = ms.coerceIn(0L, if (live.durationMs > 0) live.durationMs else Long.MAX_VALUE)
        media.seekTo(to)
        SpicyLink.seeked(to)
        haptic.performHapticFeedback(androidx.compose.ui.hapticfeedback.HapticFeedbackType.TextHandleMove)
    }
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
        // On a song change the old cover and Canvas fade into the new ones.
        androidx.compose.animation.Crossfade(art, Modifier.matchParentSize(), animationSpec = tween(SONG_FADE_MS), label = "spicyBackdrop") { shownArt ->
            if (shownArt != null) {
                val blurred = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                    Modifier.blur(36.dp, BlurredEdgeTreatment.Rectangle)
                } else Modifier
                Image(shownArt, null, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize().then(blurred).graphicsLayer { alpha = 0.85f })
            }
        }
        androidx.compose.animation.Crossfade(canvas, Modifier.matchParentSize(), animationSpec = tween(SONG_FADE_MS), label = "spicyCanvas") { url ->
            if (url != null) LoopingVideo(url, Modifier.fillMaxSize(), playing = live.playing, onFailed = { canvasFailed = true })
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
                Box(Modifier.size(54.dp).clip(RoundedCornerShape(14.dp)).background(Color.White.copy(alpha = 0.08f)).clickable(onClickLabel = "Open the player", onClick = openPlayer)) {
                    // The new cover scales in over the old one.
                    AnimatedContent(
                        targetState = art,
                        transitionSpec = {
                            (fadeIn(tween(SONG_FADE_MS)) + androidx.compose.animation.scaleIn(spring(Spring.DampingRatioLowBouncy, Spring.StiffnessLow), initialScale = 0.86f))
                                .togetherWith(fadeOut(tween(SONG_FADE_MS / 2)))
                        },
                        modifier = Modifier.fillMaxSize(),
                        label = "spicyCover",
                    ) { shownArt ->
                        if (shownArt != null) Image(shownArt, null, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
                    }
                    val motion = live.motionUrl?.takeIf { !motionFailed }
                    if (motion != null) LoopingVideo(motion, Modifier.fillMaxSize(), playing = live.playing, onFailed = { motionFailed = true })
                }
                Spacer(Modifier.width(12.dp))
                // The new song's name slides up into place.
                AnimatedContent(
                    targetState = live.title to live.artist,
                    transitionSpec = {
                        (slideInVertically(spring(Spring.DampingRatioLowBouncy, Spring.StiffnessLow)) { it / 2 } + fadeIn(tween(SONG_FADE_MS)))
                            .togetherWith(slideOutVertically(tween(SONG_FADE_MS / 2)) { -it / 2 } + fadeOut(tween(SONG_FADE_MS / 2)))
                    },
                    modifier = Modifier.weight(1f).clickable(onClickLabel = "Open the player", onClick = openPlayer),
                    label = "spicySong",
                ) { (shownTitle, shownArtist) ->
                    Column(Modifier.fillMaxWidth()) {
                        Text(
                            shownTitle, color = Color.White, fontSize = 16.sp, fontWeight = FontWeight.Bold,
                            maxLines = 1, overflow = TextOverflow.Ellipsis,
                        )
                        Text(
                            shownArtist, color = Color.White.copy(alpha = 0.7f), fontSize = 13.sp,
                            maxLines = 1, overflow = TextOverflow.Ellipsis,
                        )
                    }
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
            // A new song's lyrics (or "Finding the lyrics…" while they come) fade in over the last.
            AnimatedContent(
                targetState = live,
                contentKey = { it.key },
                transitionSpec = { fadeIn(tween(SONG_FADE_MS)).togetherWith(fadeOut(tween(SONG_FADE_MS / 2))) },
                modifier = Modifier.fillMaxWidth(),
                label = "spicySongLyrics",
            ) { shown ->
                SpicyLyrics(shown, ink, inkEnd, seek, Modifier.fillMaxWidth().heightIn(min = LYRICS_HEIGHT))
            }
            Spacer(Modifier.height(12.dp))
            SpicyProgress(live, ink, seek)
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

/**
 * The song's progress: a thin bar, running on between Spicy Lyrics' answers, with the time gone
 * and left. Tap or drag along it to move the song. It only ticks while the song plays.
 */
@Composable
private fun SpicyProgress(live: SpicyLink.Live, ink: Color, onSeek: (Long) -> Unit) {
    var now by remember { mutableLongStateOf(live.position()) }
    var dragging by remember { mutableStateOf<Float?>(null) }
    LaunchedEffect(live) {
        now = live.position()
        while (live.playing) {
            kotlinx.coroutines.delay(500L)
            now = live.position()
        }
    }
    val duration = live.durationMs
    val fraction = dragging ?: if (duration > 0) (now.toFloat() / duration).coerceIn(0f, 1f) else 0f
    val currentOnSeek by androidx.compose.runtime.rememberUpdatedState(onSeek)
    Box(
        Modifier
            .fillMaxWidth()
            .height(18.dp)
            .then(
                if (duration <= 0) Modifier else Modifier
                    .pointerInput(duration) {
                        detectTapGestures { p -> currentOnSeek((p.x / size.width * duration).toLong()) }
                    }
                    .pointerInput(duration) {
                        detectHorizontalDragGestures(
                            onDragStart = { p -> dragging = (p.x / size.width).coerceIn(0f, 1f) },
                            onDragEnd = {
                                dragging?.let { currentOnSeek((it * duration).toLong()) }
                                dragging = null
                            },
                            onDragCancel = { dragging = null },
                            onHorizontalDrag = { change, delta ->
                                change.consume()
                                dragging = ((dragging ?: 0f) + delta / size.width).coerceIn(0f, 1f)
                            },
                        )
                    },
            ),
        contentAlignment = Alignment.Center,
    ) {
        val barHeight = if (dragging != null) 7.dp else 4.dp
        Box(Modifier.fillMaxWidth().height(barHeight).clip(CircleShape).background(Color.White.copy(alpha = 0.18f))) {
            Box(Modifier.fillMaxWidth(fraction).height(barHeight).clip(CircleShape).background(Brush.horizontalGradient(listOf(Color.White, ink))))
        }
    }
    if (duration > 0) {
        val shown = dragging?.let { (it * duration).toLong() } ?: now.coerceIn(0L, duration)
        Row(Modifier.fillMaxWidth()) {
            Text(clockText(shown), color = Color.White.copy(alpha = 0.6f), fontSize = 11.sp)
            Spacer(Modifier.weight(1f))
            Text("-" + clockText(duration - shown), color = Color.White.copy(alpha = 0.6f), fontSize = 11.sp)
        }
    }
}

private fun clockText(ms: Long): String {
    val total = (ms / 1000).coerceAtLeast(0)
    return "%d:%02d".format(total / 60, total % 60)
}

/**
 * Opens the player: its own now-playing screen where its media session offers one, else the app.
 * Returns whether something opened.
 */
private fun openPlayerApp(context: android.content.Context, media: com.supershade.domain.media.MediaRepository, player: String?): Boolean {
    val session = media.sessionActivity()
    if (session != null && (player == null || player == media.playerPackage())) {
        val sent = runCatching {
            if (Build.VERSION.SDK_INT >= 34) {
                val options = android.app.ActivityOptions.makeBasic()
                    .setPendingIntentBackgroundActivityStartMode(android.app.ActivityOptions.MODE_BACKGROUND_ACTIVITY_START_ALLOWED)
                session.send(context, 0, null, null, null, null, options.toBundle())
            } else {
                session.send()
            }
        }.isSuccess
        if (sent) return true
    }
    val pkg = player ?: media.playerPackage() ?: return false
    val launch = context.packageManager.getLaunchIntentForPackage(pkg) ?: return false
    return runCatching {
        context.startActivity(launch.addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK or android.content.Intent.FLAG_ACTIVITY_RESET_TASK_IF_NEEDED))
    }.isSuccess
}

/**
 * The lyrics in Spicy Lyrics' style. The line being sung is large, each word filling from dim to
 * bright as it's sung with a soft glow under the sung part; its background vocals fill under it,
 * smaller and dimmer; a second singer's lines sit on the right, as on the lyrics screen; the
 * translation (and romanization) shows under each line, or in its place, as Spicy Lyrics is set.
 * The last line fades out above and the next waits below; lines change with a spring.
 * Line-synced lyrics fill a line at a time; unsynced ones simply show.
 */
@Composable
private fun SpicyLyrics(live: SpicyLink.Live, ink: Color, inkEnd: Color, onSeek: (Long) -> Unit, modifier: Modifier) {
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
    // Each lead line with the background vocals that follow it.
    val groups = remember(lyrics) {
        val out = ArrayList<Group>()
        for (line in lyrics.lines) {
            if (line.words.none { it.text.isNotBlank() }) continue
            if (line.background && out.isNotEmpty()) out[out.lastIndex] = out.last().let { Group(it.lead, it.backing + line) }
            else out += Group(line, emptyList())
        }
        out
    }
    // The clock, read while drawing; only a change of line recomposes. It runs each frame only
    // while the song plays; paused, it holds still and nothing redraws.
    val clock = remember { mutableLongStateOf(live.position()) }
    LaunchedEffect(live) {
        clock.longValue = live.position()
        while (live.playing) withFrameMillis { clock.longValue = live.position() }
    }
    val tapSource = remember { androidx.compose.foundation.interaction.MutableInteractionSource() }
    // Tapping a line moves the song to it (synced lyrics only).
    fun seekTo(ms: Long): Modifier =
        if (lyrics.isStatic) Modifier
        else Modifier.clickable(interactionSource = tapSource, indication = null, onClickLabel = "Play from this line") { onSeek(ms) }
    val index by remember(groups) {
        derivedStateOf {
            if (lyrics.isStatic) 0 else groups.indexOfLast { it.startMs <= clock.longValue + LEAD_MS }.coerceAtLeast(0)
        }
    }
    val alpha = live.lyricsOpacity.coerceIn(0.1f, 1f)
    val scale = (live.fontScale / 0.78f).coerceIn(0.8f, 1.25f)
    val replaceWithTranslation = live.translationMode == "Replace"
    val romanizeReplace = live.romanize && live.romanizationMode == "Replace"
    val romanizeUnder = live.romanize && live.romanizationMode != "Replace"
    AnimatedContent(
        targetState = index,
        transitionSpec = {
            (slideInVertically(spring(Spring.DampingRatioLowBouncy, Spring.StiffnessLow)) { it / 3 } + fadeIn(tween(260)))
                .togetherWith(slideOutVertically(tween(260)) { -it / 3 } + fadeOut(tween(200)))
        },
        modifier = modifier.graphicsLayer { this.alpha = alpha },
        label = "spicyLine",
    ) { i ->
        Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.Center) {
            groups.getOrNull(i - 1)?.takeIf { !lyrics.isStatic }?.let { prev ->
                SideText(
                    shownText(prev.lead, replaceWithTranslation, romanizeReplace), prev.lead.opposite,
                    Color.White.copy(alpha = 0.32f), (15 * scale), FontWeight.SemiBold, maxLines = 1,
                    modifier = seekTo(prev.startMs),
                )
                Spacer(Modifier.height(5.dp))
            }
            val group = groups.getOrNull(i)
            if (group != null) Column(Modifier.fillMaxWidth().then(seekTo(group.startMs))) {
                val lead = group.lead
                if (lyrics.isStatic) {
                    SideText(shownText(lead, replaceWithTranslation, romanizeReplace), lead.opposite, Color.White.copy(alpha = 0.9f), 21 * scale, FontWeight.Bold)
                } else {
                    SungLine(
                        shownLine(lead, replaceWithTranslation), lyrics.wordSynced && !lead.lineTimed && !(replaceWithTranslation && lead.translation != null),
                        clock, ink, inkEnd, live.glow, live.glowStrength, 23 * scale, lead.opposite, romanizeReplace,
                    )
                }
                Extras(lead, replaceWithTranslation, romanizeUnder, scale, 1f)
                // Background vocals: smaller and dimmer, filling as they're sung, on the same side.
                for (backing in group.backing) {
                    Spacer(Modifier.height(4.dp))
                    if (lyrics.isStatic) {
                        SideText(shownText(backing, replaceWithTranslation, romanizeReplace), lead.opposite, Color.White.copy(alpha = 0.6f), 15 * scale, FontWeight.SemiBold)
                    } else {
                        SungLine(
                            shownLine(backing, replaceWithTranslation), lyrics.wordSynced && !backing.lineTimed && !(replaceWithTranslation && backing.translation != null),
                            clock, ink.copy(alpha = 0.85f), inkEnd.copy(alpha = 0.85f), live.glow, live.glowStrength * 0.6f,
                            15.5f * scale, lead.opposite, romanizeReplace, dimAlpha = 0.26f, weight = FontWeight.Bold,
                        )
                    }
                    Extras(backing, replaceWithTranslation, romanizeUnder, scale, 0.75f, lead.opposite)
                }
            }
            groups.getOrNull(i + 1)?.let { next ->
                Spacer(Modifier.height(7.dp))
                SideText(
                    shownText(next.lead, replaceWithTranslation, romanizeReplace), next.lead.opposite,
                    Color.White.copy(alpha = 0.42f), 16 * scale, FontWeight.SemiBold, maxLines = 2,
                    modifier = seekTo(next.startMs),
                )
            }
        }
    }
}

/** A lead line and the background vocals sung with it. */
private class Group(val lead: SpicyLink.Line, val backing: List<SpicyLink.Line>) {
    val startMs: Long get() = minOf(lead.startMs, backing.minOfOrNull { it.startMs } ?: lead.startMs)
}

/** The line as shown: its translation in its place when translations replace the lines. */
private fun shownLine(line: SpicyLink.Line, replace: Boolean): SpicyLink.Line {
    val translation = line.translation
    if (!replace || translation == null) return line
    return SpicyLink.Line(
        line.startMs, line.endMs, line.background, line.opposite, lineTimed = true,
        words = listOf(SpicyLink.Word(translation, line.startMs, line.endMs, attached = false)),
        translation = null, agent = line.agent,
    )
}

private fun shownText(line: SpicyLink.Line, replace: Boolean, romanized: Boolean): String =
    line.translation?.takeIf { replace } ?: if (romanized) line.romanizedText ?: line.text else line.text

/** Under a line: its romanization (when it goes under) and its translation (when it goes under). */
@Composable
private fun Extras(line: SpicyLink.Line, replace: Boolean, romanizeUnder: Boolean, scale: Float, size: Float, opposite: Boolean = line.opposite) {
    if (romanizeUnder) line.romanizedText?.takeIf { it != line.text }?.let {
        Spacer(Modifier.height(2.dp))
        SideText(it, opposite, Color.White.copy(alpha = 0.6f), 13.5f * scale * size, FontWeight.Medium)
    }
    if (!replace) line.translation?.let {
        Spacer(Modifier.height(2.dp))
        SideText(it, opposite, Color.White.copy(alpha = 0.58f), 13.5f * scale * size, FontWeight.Medium, maxLines = 2)
    }
}

/** Text on its singer's side: the right for a second singer, as on the lyrics screen. */
@Composable
private fun SideText(text: String, opposite: Boolean, color: Color, sizeSp: Float, weight: FontWeight, maxLines: Int = 3, modifier: Modifier = Modifier) {
    Text(
        text,
        color = color, fontSize = sizeSp.sp, fontWeight = weight,
        maxLines = maxLines, overflow = TextOverflow.Ellipsis,
        textAlign = if (opposite) TextAlign.End else TextAlign.Start,
        modifier = Modifier.fillMaxWidth().then(modifier),
    )
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
    opposite: Boolean = false,
    romanized: Boolean = false,
    dimAlpha: Float = DIM_ALPHA,
    weight: FontWeight = FontWeight.ExtraBold,
) {
    val measurer = rememberTextMeasurer()
    val density = LocalDensity.current
    val text = remember(line, romanized) { line.joined(romanized) }
    // Where each word sits in the line's text.
    val spans = remember(line, romanized) {
        val out = ArrayList<IntArray>()
        var at = 0
        for ((n, w) in line.words.withIndex()) {
            val word = (if (romanized) w.romanized ?: w.text else w.text).trim()
            if (n > 0 && !w.attached) at += 1
            out += intArrayOf(at, at + word.length)
            at += word.length
        }
        out
    }
    val style = TextStyle(
        fontSize = sizeSp.sp, fontWeight = weight, lineHeight = (sizeSp * 1.18f).sp,
        textAlign = if (opposite) TextAlign.End else TextAlign.Start,
    )
    BoxWithConstraints(Modifier.fillMaxWidth()) {
        val widthPx = constraints.maxWidth
        val layout = remember(text, widthPx, sizeSp, opposite, weight) {
            measurer.measure(text, style, maxLines = 3, overflow = TextOverflow.Ellipsis, constraints = Constraints(minWidth = widthPx, maxWidth = widthPx))
        }
        val heightDp = with(density) { layout.size.height.toDp() }
        val brush = Brush.verticalGradient(listOf(ink, inkEnd))
        Canvas(Modifier.fillMaxWidth().height(heightDp)) {
            val now = clock.longValue
            // Shadow.None: a layout keeps the last shadow drawn with it.
            drawText(layout, color = Color.White.copy(alpha = dimAlpha), shadow = Shadow.None)
            val pieces: List<Pair<IntArray, Float>> = if (wordSynced) {
                line.words.indices.map { k ->
                    val w = line.words[k]
                    spans[k] to ((now - w.startMs).toFloat() / (w.endMs - w.startMs).coerceAtLeast(1)).coerceIn(0f, 1f)
                }
            } else {
                listOf(intArrayOf(0, text.length) to ((now - line.startMs).toFloat() / (line.endMs - line.startMs).coerceAtLeast(1)).coerceIn(0f, 1f))
            }
            val glowShadow = if (glow) Shadow(ink.copy(alpha = (0.55f * glowStrength).coerceIn(0f, 1f)), Offset.Zero, sizeSp * density.density * 0.45f) else Shadow.None
            for ((span, progress) in pieces) {
                if (progress <= 0f || span[1] <= span[0] || span[1] > text.length) continue
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
private const val SONG_FADE_MS = 420
/**
 * After a song change, the live activity stays (with the new song's name and cover, finding its
 * lyrics) this long while Spicy Lyrics catches up, instead of dropping to the plain card and back.
 */
private const val BRIDGE_MS = 6_000L
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
    val matches = enabled && current != null && current.fresh() && SpicyLink.matches(current, title, artist)
    // When the live activity last showed this player's song, so a song change can bridge.
    var lastMatchAt by remember { mutableLongStateOf(0L) }
    var matchedSong by remember { mutableStateOf<Pair<String, String>?>(null) }
    var expired by remember { mutableStateOf(false) }
    val now = android.os.SystemClock.elapsedRealtime()
    if (matches) {
        androidx.compose.runtime.SideEffect {
            lastMatchAt = android.os.SystemClock.elapsedRealtime()
            matchedSong = title to artist
        }
    }
    val bridging = !matches && enabled && current != null && current.fresh() &&
        lastMatchAt > 0L && now - lastMatchAt < BRIDGE_MS && !expired
    LaunchedEffect(bridging, title, artist) {
        expired = false
        if (bridging) {
            kotlinx.coroutines.delay((BRIDGE_MS - (android.os.SystemClock.elapsedRealtime() - lastMatchAt)).coerceAtLeast(0L))
            expired = true
        }
    }
    val shown: SpicyLink.Live? = when {
        matches -> current
        // Spicy Lyrics already moved to the next song and the player's card hasn't yet: show it.
        bridging && current != null && matchedSong == (title to artist) -> current
        // The new song, as the player has it, while Spicy Lyrics finds its lyrics.
        bridging && current != null -> current.copy(
            key = "pending:$title:$artist", title = title, artist = artist,
            lyrics = null, state = "loading", canvasUrl = null, motionUrl = null,
            positionMs = 0L, atElapsed = now, durationMs = 0L,
        )
        else -> null
    }
    val lastShown = remember { mutableStateOf<SpicyLink.Live?>(null) }
    if (shown != null) lastShown.value = shown
    // The live activity and the plain card fade into each other, never jump.
    AnimatedContent(
        targetState = shown != null,
        transitionSpec = {
            (fadeIn(tween(SONG_FADE_MS)) + androidx.compose.animation.scaleIn(tween(SONG_FADE_MS), initialScale = 0.97f))
                .togetherWith(fadeOut(tween(SONG_FADE_MS / 2)))
                .using(androidx.compose.animation.SizeTransform(clip = false))
        },
        label = "spicyOrPlain",
    ) { isLive ->
        val state = shown ?: lastShown.value
        if (isLive && state != null) {
            SpicyLiveActivityCard(state, albumArt, onPlayPause, onSkipNext, onSkipPrevious, modifier)
        } else {
            fallback()
        }
    }
}

/**
 * The media pop-up that shows on a song change, as Spicy Lyrics' live activity when it's sending
 * this song: the Canvas playing behind it (else the cover, blurred), the animated cover, the song,
 * the controls, and the line being sung filling word by word with the next one under it. Else
 * [fallback] (the plain pop-up). Spicy Lyrics is asked to follow while it shows, and told to stop
 * after unless the shade is open. Swipe it up to hide it; the cover opens the player.
 */
@Composable
fun SpicyPopupOr(
    title: String,
    artist: String,
    albumArt: Bitmap?,
    onPlayPause: () -> Unit,
    onSkipNext: () -> Unit,
    onSkipPrevious: () -> Unit,
    onHide: () -> Unit,
    onTouch: () -> Unit,
    pinned: Boolean = false,
    onPin: (Boolean) -> Unit = {},
    fallback: @Composable () -> Unit,
) {
    val context = LocalContext.current
    val enabled = remember { PlusSettings.spicyLiveActivity(context) }
    if (!enabled) {
        fallback()
        return
    }
    androidx.compose.runtime.DisposableEffect(Unit) {
        SpicyLink.popup(context, showing = true)
        onDispose { SpicyLink.popup(context, showing = false) }
    }
    val live by SpicyLink.live.collectAsState()
    val current = live?.takeIf { it.fresh() && SpicyLink.matches(it, title, artist) }
    AnimatedContent(
        targetState = current != null,
        transitionSpec = { fadeIn(tween(SONG_FADE_MS)).togetherWith(fadeOut(tween(SONG_FADE_MS / 2))) },
        label = "spicyPopupOrPlain",
    ) { isLive ->
        val state = SpicyLink.live.value?.takeIf { SpicyLink.matches(it, title, artist) }
        if (isLive && state != null) SpicyPopupCard(state, albumArt, onPlayPause, onSkipNext, onSkipPrevious, onHide, onTouch, pinned, onPin)
        else fallback()
    }
}

@Composable
private fun SpicyPopupCard(
    live: SpicyLink.Live,
    albumArt: Bitmap?,
    onPlayPause: () -> Unit,
    onSkipNext: () -> Unit,
    onSkipPrevious: () -> Unit,
    onHide: () -> Unit,
    onTouch: () -> Unit,
    pinnedAtStart: Boolean,
    onPin: (Boolean) -> Unit,
) {
    val context = LocalContext.current
    var pinned by remember { mutableStateOf(pinnedAtStart) }
    val media = org.koin.compose.koinInject<com.supershade.domain.media.MediaRepository>()
    val scope = androidx.compose.runtime.rememberCoroutineScope()
    val density = LocalDensity.current
    val hidePx = with(density) { 36.dp.toPx() }
    val offsetY = remember { androidx.compose.animation.core.Animatable(0f) }
    var canvasFailed by remember(live.canvasUrl) { mutableStateOf(false) }
    var motionFailed by remember(live.motionUrl) { mutableStateOf(false) }
    val art = remember(albumArt) { albumArt?.asImageBitmap() }
    val ink = Color(live.ink)
    val inkEnd = Color(live.inkEnd)
    val shape = RoundedCornerShape(26.dp)
    Box(
        Modifier
            .fillMaxWidth()
            .displayCutoutPadding()
            .statusBarsPadding()
            .padding(horizontal = 14.dp, vertical = 6.dp)
            .graphicsLayer {
                translationY = offsetY.value
                alpha = 1f - (kotlin.math.abs(offsetY.value) / (hidePx * 2f)).coerceIn(0f, 1f)
            }
            .pointerInput(Unit) {
                // Any touch keeps it up; a swipe up hides it.
                awaitEachGesture {
                    val down = awaitFirstDown(requireUnconsumed = false)
                    onTouch()
                    var dragging = false
                    while (true) {
                        val event = awaitPointerEvent()
                        val change = event.changes.firstOrNull { it.id == down.id } ?: break
                        if (!change.pressed) {
                            if (dragging) {
                                if (offsetY.value < -hidePx) {
                                    scope.launch {
                                        offsetY.animateTo(-hidePx * 3.5f, tween(160))
                                        onHide()
                                    }
                                } else {
                                    scope.launch { offsetY.animateTo(0f, spring(0.65f, 800f)) }
                                }
                            }
                            break
                        }
                        val dy = change.position.y - down.position.y
                        if (kotlin.math.abs(dy) > 12f) dragging = true
                        if (dragging) {
                            change.consume()
                            scope.launch { offsetY.snapTo(dy.coerceAtMost(20f)) }
                        }
                    }
                }
            },
    ) {
        Box(
            Modifier
                .fillMaxWidth()
                .clip(shape)
                .background(Color(0xFF101014))
                .border(BorderStroke(1.dp, Brush.linearGradient(listOf(Color.White.copy(alpha = 0.4f), Color.White.copy(alpha = 0.06f), ink.copy(alpha = 0.35f)))), shape),
        ) {
            androidx.compose.animation.Crossfade(art, Modifier.matchParentSize(), animationSpec = tween(SONG_FADE_MS), label = "popupBackdrop") { shownArt ->
                if (shownArt != null) {
                    val blurred = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) Modifier.blur(30.dp, BlurredEdgeTreatment.Rectangle) else Modifier
                    Image(shownArt, null, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize().then(blurred).graphicsLayer { alpha = 0.85f })
                }
            }
            val canvas = live.canvasUrl?.takeIf { !canvasFailed }
            androidx.compose.animation.Crossfade(canvas, Modifier.matchParentSize(), animationSpec = tween(SONG_FADE_MS), label = "popupCanvas") { url ->
                if (url != null) LoopingVideo(url, Modifier.fillMaxSize(), playing = live.playing, onFailed = { canvasFailed = true })
            }
            val veil = (0.3f + live.backdropOpacity * 0.5f).coerceIn(0.3f, 0.8f)
            Box(Modifier.matchParentSize().background(Brush.verticalGradient(listOf(Color.Black.copy(alpha = veil * 0.6f), Color.Black.copy(alpha = veil)))))
            Column(Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 12.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        Modifier.size(46.dp).clip(RoundedCornerShape(13.dp)).background(Color.White.copy(alpha = 0.08f))
                            .clickable(onClickLabel = "Open the player") { if (openPlayerApp(context, media, live.player)) onHide() },
                    ) {
                        if (art != null) Image(art, null, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
                        val motion = live.motionUrl?.takeIf { !motionFailed }
                        if (motion != null) LoopingVideo(motion, Modifier.fillMaxSize(), playing = live.playing, onFailed = { motionFailed = true })
                    }
                    Spacer(Modifier.width(12.dp))
                    Column(Modifier.weight(1f)) {
                        Text(live.title, color = Color.White, fontSize = 15.sp, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        Text(live.artist, color = Color.White.copy(alpha = 0.7f), fontSize = 12.5.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    }
                    RoundButton(Icons.Rounded.SkipPrevious, "Previous", 20.dp, { onTouch(); onSkipPrevious() })
                    RoundButton(if (live.playing) Icons.Rounded.Pause else Icons.Rounded.PlayArrow, if (live.playing) "Pause" else "Play", 22.dp, { onTouch(); onPlayPause() }, filled = true)
                    RoundButton(Icons.Rounded.SkipNext, "Next", 20.dp, { onTouch(); onSkipNext() })
                }
                Row(Modifier.fillMaxWidth().padding(top = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                    PopupLyric(live, ink, inkEnd, Modifier.weight(1f))
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        // Keep the pop-up up (through song changes) until unpinned or swiped away.
                        SmallToggle(
                            on = pinned,
                            icon = if (pinned) Icons.Rounded.PushPin else Icons.Outlined.PushPin,
                            label = if (pinned) "Let the pop-up go" else "Keep the pop-up up",
                            ink = ink,
                        ) {
                            pinned = !pinned
                            onPin(pinned)
                        }
                        Spacer(Modifier.height(6.dp))
                        // Float the lyrics out as Spicy Lyrics' overlay, or back.
                        SmallToggle(
                            on = live.overlayOn,
                            icon = Icons.Rounded.PictureInPictureAlt,
                            label = if (live.overlayOn) "Bring the lyrics back" else "Float the lyrics as an overlay",
                            ink = ink,
                        ) {
                            onTouch()
                            SpicyLink.setOverlay(context, !live.overlayOn)
                        }
                    }
                }
            }
        }
    }
}

/** The line being sung (filling word by word) and the next one, or nothing without lyrics. */
@Composable
private fun PopupLyric(live: SpicyLink.Live, ink: Color, inkEnd: Color, modifier: Modifier) {
    val lyrics = live.lyrics?.takeIf { !it.isStatic && it.lines.isNotEmpty() }
    if (lyrics == null) {
        Spacer(modifier)
        return
    }
    val groups = remember(lyrics) {
        val out = ArrayList<Group>()
        for (line in lyrics.lines) {
            if (line.words.none { it.text.isNotBlank() }) continue
            if (line.background && out.isNotEmpty()) out[out.lastIndex] = out.last().let { Group(it.lead, it.backing + line) }
            else out += Group(line, emptyList())
        }
        out
    }
    if (groups.isEmpty()) {
        Spacer(modifier)
        return
    }
    val clock = remember { mutableLongStateOf(live.position()) }
    LaunchedEffect(live) {
        clock.longValue = live.position()
        while (live.playing) withFrameMillis { clock.longValue = live.position() }
    }
    val index by remember(groups) { derivedStateOf { groups.indexOfLast { it.startMs <= clock.longValue + LEAD_MS }.coerceAtLeast(0) } }
    val replace = live.translationMode == "Replace"
    val romanizeReplace = live.romanize && live.romanizationMode == "Replace"
    AnimatedContent(
        targetState = index,
        transitionSpec = {
            (slideInVertically(spring(Spring.DampingRatioLowBouncy, Spring.StiffnessLow)) { it / 3 } + fadeIn(tween(240)))
                .togetherWith(slideOutVertically(tween(240)) { -it / 3 } + fadeOut(tween(180)))
        },
        modifier = modifier.graphicsLayer { alpha = live.lyricsOpacity.coerceIn(0.1f, 1f) },
        label = "popupLine",
    ) { i ->
        Column(Modifier.fillMaxWidth()) {
            val group = groups[i.coerceIn(0, groups.lastIndex)]
            val lead = group.lead
            SungLine(
                shownLine(lead, replace), lyrics.wordSynced && !lead.lineTimed && !(replace && lead.translation != null),
                clock, ink, inkEnd, live.glow, live.glowStrength, 18f, lead.opposite, romanizeReplace,
            )
            groups.getOrNull(i + 1)?.let { next ->
                Spacer(Modifier.height(3.dp))
                SideText(shownText(next.lead, replace, romanizeReplace), next.lead.opposite, Color.White.copy(alpha = 0.42f), 13.5f, FontWeight.SemiBold, maxLines = 1)
            }
        }
    }
}

@Composable
private fun SmallToggle(on: Boolean, icon: androidx.compose.ui.graphics.vector.ImageVector, label: String, ink: Color, onClick: () -> Unit) {
    Box(
        Modifier.size(32.dp).clip(CircleShape)
            .background(if (on) ink.copy(alpha = 0.9f) else Color.White.copy(alpha = 0.14f))
            .clickable(onClickLabel = label, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Icon(icon, label, tint = if (on) Color.Black else Color.White, modifier = Modifier.size(16.dp))
    }
}
