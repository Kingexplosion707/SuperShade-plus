package com.supershade.plus

import android.content.Context
import android.content.SharedPreferences
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow

/**
 * SuperShade Plus's own settings, kept apart from the original app's so its updates merge
 * cleanly. Plain preferences: the gesture windows read them synchronously when they're laid out.
 */
object PlusSettings {
    private const val FILE = "supershade_plus"

    const val SPICY_LIVE_ACTIVITY = "spicy_live_activity"
    const val ZONE_START = "gesture_zone_start"
    const val ZONE_END = "gesture_zone_end"
    const val ISLAND_GAP = "gesture_island_gap"
    const val ZONE_EXTRA_DP = "gesture_zone_extra_dp"
    const val STRICT_BLOCK = "strict_block_system_shade"
    const val POPUP_BLUR = "popup_blur_percent"
    const val SWIPE_SENSITIVITY = "swipe_dismiss_sensitivity"
    const val NOTIFICATION_VIBRATE = "notification_vibrate"
    const val POPUP_ANIMATION = "popup_genie_animation"

    fun prefs(context: Context): SharedPreferences =
        context.applicationContext.getSharedPreferences(FILE, Context.MODE_PRIVATE)

    /** Spicy Lyrics' live activity in the shade (with Spicy Lyrics Plus installed). */
    fun spicyLiveActivity(context: Context) = prefs(context).getBoolean(SPICY_LIVE_ACTIVITY, true)

    /** Where across the top the swipe-down zone starts and ends, in percent of the width. */
    fun zoneStart(context: Context) = prefs(context).getInt(ZONE_START, 0).coerceIn(0, 95)
    fun zoneEnd(context: Context) = prefs(context).getInt(ZONE_END, 100).coerceIn(5, 100)

    /**
     * A gap in the zone, in percent of the width, centred on the camera cutout (or the middle):
     * taps there reach what's under it, such as a dynamic island. 0 leaves no gap.
     */
    fun islandGap(context: Context) = prefs(context).getInt(ISLAND_GAP, 0).coerceIn(0, 70)

    /** How far below the status bar the zone reaches, in dp. */
    fun zoneExtraDp(context: Context) = prefs(context).getInt(ZONE_EXTRA_DP, 20).coerceIn(0, 80)

    /** Shizuku keeps the system's own shade from opening at all, re-asserting it and closing it if it slips open. */
    fun strictBlock(context: Context) = prefs(context).getBoolean(STRICT_BLOCK, false)

    /**
     * How much a notification or media pop-up blurs the screen behind it, in percent of the
     * original strength. Android blurs the whole screen behind such a window, so it's off by default.
     */
    fun popupBlur(context: Context) = prefs(context).getInt(POPUP_BLUR, 0).coerceIn(0, 100)

    /** Sets a pop-up window's blur from [popupBlur]; [maxRadiusPx] is the original's full strength. */
    fun applyPopupBlur(context: Context, params: android.view.WindowManager.LayoutParams, maxRadiusPx: Int) {
        if (android.os.Build.VERSION.SDK_INT < android.os.Build.VERSION_CODES.S) return
        val radius = maxRadiusPx * popupBlur(context) / 100
        @Suppress("DEPRECATION")
        val flag = android.view.WindowManager.LayoutParams.FLAG_BLUR_BEHIND
        params.flags = if (radius > 0) params.flags or flag else params.flags and flag.inv()
        params.blurBehindRadius = radius
    }

    /**
     * How readily a notification swipes away, 0 (a long, deliberate swipe) to 100 (the original's
     * light touch). It sets how far a release must go, how fast a fling must be, and how far the
     * finger must move sideways before it's a swipe and not a scroll.
     */
    fun swipeSensitivity(context: Context) = prefs(context).getInt(SWIPE_SENSITIVITY, 35).coerceIn(0, 100)

    /** A buzz for each new notification, in vibrate mode or with the sound on (never in silent). */
    fun notificationVibrate(context: Context) = prefs(context).getBoolean(NOTIFICATION_VIBRATE, true)

    /** Pop-ups come out from behind the camera (else they slide down as in the original). */
    fun popupAnimation(context: Context) = prefs(context).getBoolean(POPUP_ANIMATION, true)

    fun put(context: Context, key: String, value: Any) {
        prefs(context).edit().apply {
            when (value) {
                is Boolean -> putBoolean(key, value)
                is Int -> putInt(key, value)
                else -> putString(key, value.toString())
            }
        }.apply()
    }

    /** Emits the key of each change. */
    fun changes(context: Context): Flow<String?> = callbackFlow {
        val prefs = prefs(context)
        val listener = SharedPreferences.OnSharedPreferenceChangeListener { _, key -> trySend(key) }
        prefs.registerOnSharedPreferenceChangeListener(listener)
        awaitClose { prefs.unregisterOnSharedPreferenceChangeListener(listener) }
    }
}

/** The settings that change the swipe zone's layout. */
val PlusZoneKeys = setOf(PlusSettings.ZONE_START, PlusSettings.ZONE_END, PlusSettings.ISLAND_GAP, PlusSettings.ZONE_EXTRA_DP)
