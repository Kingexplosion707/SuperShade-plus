package com.supershade.plus

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import androidx.core.content.ContextCompat
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import org.json.JSONObject

/**
 * The link to Spicy Lyrics Plus, for its live activity in the shade. While the shade is open this
 * asks Spicy Lyrics for the lyrics ([ACTION_REQUEST], again every [REASK_MS]); it answers with
 * [ACTION_LIVE] broadcasts: the song, its lyrics with word timing, where the song is (on the
 * elapsed-realtime clock both apps share, so the clock runs here between answers), the Canvas and
 * animated cover streams, and the lyrics overlay's look and whether it's floating. The pin asks
 * Spicy Lyrics to float the lyrics out as its overlay, or bring them back ([setOverlay]).
 */
object SpicyLink {
    private val SPICY_PACKAGES = listOf("com.behnoud.spicylyrics", "com.behnoud.spicylyrics.debug")
    private const val ACTION_REQUEST = "com.behnoud.spicylyrics.action.SHADE_REQUEST"
    private const val ACTION_STOP = "com.behnoud.spicylyrics.action.SHADE_STOP"
    private const val ACTION_OVERLAY = "com.behnoud.spicylyrics.action.SHADE_OVERLAY"
    private const val ACTION_LIVE = "com.behnoud.spicylyrics.action.LIVE_LYRICS"
    private const val REASK_MS = 45_000L
    /** An answer older than this (Spicy Lyrics gone quiet) no longer shows. */
    const val STALE_MS = 20_000L

    class Word(val text: String, val startMs: Long, val endMs: Long, val attached: Boolean, val romanized: String? = null)

    class Line(
        val startMs: Long,
        val endMs: Long,
        val background: Boolean,
        val opposite: Boolean,
        val lineTimed: Boolean,
        val words: List<Word>,
        val translation: String?,
        /** Who sings it, where the lyrics say (duets). */
        val agent: String? = null,
    ) {
        val text: String by lazy { joined(romanized = false) }
        /** The line romanized, where its words came romanized; null where none did. */
        val romanizedText: String? by lazy { if (words.any { it.romanized != null }) joined(romanized = true) else null }

        /** The line's words joined, each as itself or romanized. */
        fun joined(romanized: Boolean): String = buildString {
            for (w in words) {
                if (isNotEmpty() && !w.attached) append(' ')
                append((if (romanized) w.romanized ?: w.text else w.text).trim())
            }
        }
    }

    class Lyrics(val type: String, val lines: List<Line>) {
        val wordSynced get() = type == "Syllable"
        val isStatic get() = type == "Static"
    }

    data class Live(
        val key: String,
        val title: String,
        val artist: String,
        val album: String,
        val durationMs: Long,
        val player: String?,
        val playing: Boolean,
        val positionMs: Long,
        val atElapsed: Long,
        val state: String,
        val lyrics: Lyrics?,
        val canvasUrl: String?,
        val motionUrl: String?,
        val overlayOn: Boolean,
        val ink: Int,
        val inkEnd: Int,
        val glow: Boolean,
        val glowStrength: Float,
        val lyricsOpacity: Float,
        val backdropOpacity: Float,
        val fontScale: Float,
        val receivedAt: Long,
        /** "UnderLine" or "Replace", as on Spicy Lyrics' lyrics screen. */
        val translationMode: String = "UnderLine",
        val romanize: Boolean = false,
        val romanizationMode: String = "Replace",
    ) {
        /** Where the song is now, run on from the last answer while it plays. */
        fun position(): Long =
            if (playing) positionMs + (SystemClock.elapsedRealtime() - atElapsed) else positionMs

        fun fresh(): Boolean = SystemClock.elapsedRealtime() - receivedAt < STALE_MS
    }

    private val _live = MutableStateFlow<Live?>(null)
    val live: StateFlow<Live?> = _live

    /** The song was just moved to [positionMs] from the shade: show it there until Spicy Lyrics answers. */
    fun seeked(positionMs: Long) {
        val current = _live.value ?: return
        _live.value = current.copy(positionMs = positionMs.coerceAtLeast(0L), atElapsed = SystemClock.elapsedRealtime())
    }

    private val main = Handler(Looper.getMainLooper())
    private var receiver: BroadcastReceiver? = null
    private var open = false
    private var app: Context? = null

    fun start(context: Context) {
        if (receiver != null) return
        val appContext = context.applicationContext
        app = appContext
        val created = object : BroadcastReceiver() {
            override fun onReceive(context: Context, intent: Intent) {
                if (intent.action == ACTION_LIVE) onLive(intent)
            }
        }
        receiver = created
        ContextCompat.registerReceiver(appContext, created, IntentFilter(ACTION_LIVE), ContextCompat.RECEIVER_EXPORTED)
    }

    fun stop(context: Context) {
        receiver?.let { runCatching { context.applicationContext.unregisterReceiver(it) } }
        receiver = null
        main.removeCallbacks(reask)
    }

    /** The shade opened or closed: ask Spicy Lyrics to follow along while it's open. */
    fun shadeOpen(context: Context, isOpen: Boolean) {
        open = isOpen
        main.removeCallbacks(reask)
        if (isOpen && PlusSettings.spicyLiveActivity(context)) {
            send(context, Intent(ACTION_REQUEST))
            main.postDelayed(reask, REASK_MS)
        } else if (!isOpen) {
            send(context, Intent(ACTION_STOP))
        }
    }

    /**
     * The media pop-up showed or went: ask Spicy Lyrics for this song while it shows, and let it
     * stop after unless the shade is open (it also stops by itself when no one asks).
     */
    fun popup(context: Context, showing: Boolean) {
        if (showing) {
            if (PlusSettings.spicyLiveActivity(context)) send(context, Intent(ACTION_REQUEST))
        } else if (!open) {
            send(context, Intent(ACTION_STOP))
        }
    }

    private val reask: Runnable = object : Runnable {
        override fun run() {
            val context = app ?: return
            if (!open) return
            send(context, Intent(ACTION_REQUEST))
            main.postDelayed(this, REASK_MS)
        }
    }

    /** Floats the lyrics out as Spicy Lyrics' overlay, or brings them back into the shade. */
    fun setOverlay(context: Context, on: Boolean) {
        _live.value = _live.value?.copy(overlayOn = on)
        send(context, Intent(ACTION_OVERLAY).putExtra("on", on))
    }

    private fun send(context: Context, intent: Intent) {
        for (pkg in SPICY_PACKAGES) runCatching { context.sendBroadcast(Intent(intent).setPackage(pkg)) }
    }

    private fun onLive(intent: Intent) {
        if (!intent.getBooleanExtra("has_track", false)) {
            _live.value = null
            return
        }
        val key = intent.getStringExtra("key") ?: return
        val previous = _live.value
        // The lyrics come only when new; otherwise the last ones for this song stay.
        val lyrics = intent.getStringExtra("lyrics")?.let(::parseLyrics)
            ?: previous?.takeIf { it.key == key }?.lyrics
        _live.value = Live(
            key = key,
            title = intent.getStringExtra("title").orEmpty(),
            artist = intent.getStringExtra("artist").orEmpty(),
            album = intent.getStringExtra("album").orEmpty(),
            durationMs = intent.getLongExtra("duration", 0L),
            player = intent.getStringExtra("player"),
            playing = intent.getBooleanExtra("playing", false),
            positionMs = intent.getLongExtra("position", 0L),
            atElapsed = intent.getLongExtra("at", SystemClock.elapsedRealtime()),
            state = intent.getStringExtra("state") ?: "none",
            lyrics = lyrics,
            canvasUrl = intent.getStringExtra("canvas_url"),
            motionUrl = intent.getStringExtra("motion_url"),
            overlayOn = intent.getBooleanExtra("overlay_on", false),
            ink = intent.getIntExtra("ink", 0xFFFFFFFF.toInt()),
            inkEnd = intent.getIntExtra("ink_end", 0xFFFFFFFF.toInt()),
            glow = intent.getBooleanExtra("glow", true),
            glowStrength = intent.getFloatExtra("glow_strength", 1f),
            lyricsOpacity = intent.getFloatExtra("lyrics_opacity", 1f),
            backdropOpacity = intent.getFloatExtra("backdrop_opacity", 0.45f),
            fontScale = intent.getFloatExtra("font_scale", 0.78f),
            receivedAt = SystemClock.elapsedRealtime(),
            translationMode = intent.getStringExtra("translation_mode") ?: "UnderLine",
            romanize = intent.getBooleanExtra("romanize", false),
            romanizationMode = intent.getStringExtra("romanization_mode") ?: "Replace",
        )
    }

    private fun parseLyrics(json: String): Lyrics? = runCatching {
        val root = JSONObject(json)
        val lines = root.getJSONArray("lines")
        val parsed = ArrayList<Line>(lines.length())
        for (i in 0 until lines.length()) {
            val o = lines.getJSONObject(i)
            val w = o.getJSONArray("w")
            val words = ArrayList<Word>(w.length())
            for (j in 0 until w.length()) {
                val a = w.getJSONArray(j)
                words += Word(a.getString(0), a.getLong(1), a.getLong(2), a.optInt(3) == 1, a.optString(4).takeIf { a.length() > 4 && it.isNotBlank() })
            }
            parsed += Line(
                startMs = o.getLong("s"),
                endMs = o.getLong("e"),
                background = o.optBoolean("bg"),
                opposite = o.optBoolean("op"),
                lineTimed = o.optBoolean("lt"),
                words = words,
                translation = o.optString("tr").takeIf { it.isNotBlank() },
                agent = o.optString("ag").takeIf { it.isNotBlank() },
            )
        }
        Lyrics(root.optString("type", "Syllable"), parsed)
    }.getOrNull()

    /** Whether [live] is the song [title] by [artist] shown by the media card. */
    fun matches(live: Live, title: String, artist: String): Boolean {
        fun norm(s: String) = s.lowercase().replace(Regex("""\s*[(\[].*?[)\]]"""), "").replace(Regex("[^\\p{L}\\p{N}]"), "")
        val t = norm(title)
        val lt = norm(live.title)
        return t.isNotEmpty() && (t == lt || t.startsWith(lt) || lt.startsWith(t))
    }
}
