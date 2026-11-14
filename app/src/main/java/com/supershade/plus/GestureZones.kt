package com.supershade.plus

import android.content.Context
import android.graphics.PixelFormat
import android.os.Build
import android.view.Gravity
import android.view.View
import android.view.WindowManager

/**
 * The swipe-down zone along the top of the screen ([PlusSettings]): from a start to an end across
 * the width, as deep as the status bar plus some, with an optional gap over the camera cutout so
 * a dynamic island under it can be tapped. A window takes every touch inside it, so the gap is
 * made by laying the zone out as separate windows either side of it.
 */
object GestureZones {
    /** The zone's pieces across the screen, in pixels. */
    fun segments(context: Context, widthPx: Int): List<IntRange> {
        val start = widthPx * PlusSettings.zoneStart(context) / 100
        val end = (widthPx * PlusSettings.zoneEnd(context) / 100).coerceAtLeast(start + 1)
        val gapShare = PlusSettings.islandGap(context)
        if (gapShare <= 0) return listOf(start until end)
        val center = cutoutCenter(context) ?: (widthPx / 2)
        val half = widthPx * gapShare / 200
        val gapStart = (center - half).coerceAtLeast(0)
        val gapEnd = (center + half).coerceAtMost(widthPx)
        return listOfNotNull(
            (start until minOf(end, gapStart)).takeIf { !it.isEmpty() && it.last - it.first > 8 },
            (maxOf(start, gapEnd) until end).takeIf { !it.isEmpty() && it.last - it.first > 8 },
        )
    }

    /** How deep the zone reaches: the status bar and [PlusSettings.zoneExtraDp] below it. */
    fun height(context: Context, statusBarPx: Int): Int =
        statusBarPx + (PlusSettings.zoneExtraDp(context) * context.resources.displayMetrics.density).toInt()

    private fun cutoutCenter(context: Context): Int? {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.R) return null
        return runCatching {
            val wm = context.getSystemService(WindowManager::class.java) ?: return null
            val rect = wm.currentWindowMetrics.windowInsets.displayCutout?.boundingRectTop
            if (rect == null || rect.isEmpty) null else rect.centerX()
        }.getOrNull()
    }

    /**
     * Adds [main] (with its own touch handling) as the first piece of the zone and a window for
     * each other piece that hands its touches to [main]; returns every window added.
     */
    fun addWindows(context: Context, wm: WindowManager, main: View, template: WindowManager.LayoutParams, statusBarPx: Int): List<View> {
        val width = context.resources.displayMetrics.widthPixels
        val pieces = segments(context, width).ifEmpty { listOf(0 until width) }
        val added = ArrayList<View>()
        pieces.forEachIndexed { i, piece ->
            val view = if (i == 0) main else View(context).apply { setOnTouchListener { _, e -> main.dispatchTouchEvent(e) } }
            val params = WindowManager.LayoutParams().apply {
                copyFrom(template)
                this.width = piece.last - piece.first + 1
                this.height = height(context, statusBarPx)
                x = piece.first
                y = 0
                gravity = Gravity.TOP or Gravity.START
                format = PixelFormat.TRANSLUCENT
            }
            runCatching { wm.addView(view, params) }.onSuccess { added += view }
        }
        return added
    }

    fun removeWindows(wm: WindowManager?, views: List<View>) {
        for (view in views) runCatching { if (view.isAttachedToWindow) wm?.removeViewImmediate(view) }
    }
}
