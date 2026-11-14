package com.supershade.plus

import android.app.NotificationManager
import android.content.Context
import android.media.AudioAttributes
import android.media.AudioManager
import android.os.Build
import android.os.SystemClock
import android.os.VibrationAttributes
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import com.supershade.domain.notification.model.ShadeNotification

/**
 * A short buzz when a notification arrives, as the phone would give: in vibrate mode and with the
 * sound on, never in silent mode or while Do Not Disturb holds notifications back. Ongoing ones
 * (music, downloads) don't buzz, and a burst of notifications buzzes once.
 * Settings → SuperShade Plus → Vibrate for notifications (on by default).
 */
object NotificationBuzz {
    private const val MIN_GAP_MS = 1_500L
    private var lastAt = 0L

    fun onNew(context: Context, notification: ShadeNotification) {
        if (!PlusSettings.notificationVibrate(context)) return
        if (notification.isOngoing || notification.packageName == context.packageName) return
        val audio = context.getSystemService(AudioManager::class.java) ?: return
        if (audio.ringerMode == AudioManager.RINGER_MODE_SILENT) return
        val nm = context.getSystemService(NotificationManager::class.java)
        if (nm != null && nm.currentInterruptionFilter > NotificationManager.INTERRUPTION_FILTER_ALL) return
        val now = SystemClock.elapsedRealtime()
        if (now - lastAt < MIN_GAP_MS) return
        lastAt = now
        val vibrator = if (Build.VERSION.SDK_INT >= 31) {
            context.getSystemService(VibratorManager::class.java)?.defaultVibrator
        } else {
            @Suppress("DEPRECATION")
            context.getSystemService(Vibrator::class.java)
        } ?: return
        if (!vibrator.hasVibrator()) return
        val effect = VibrationEffect.createWaveform(longArrayOf(0, 45, 80, 70), intArrayOf(0, 180, 0, 255), -1)
        runCatching {
            if (Build.VERSION.SDK_INT >= 33) {
                vibrator.vibrate(effect, VibrationAttributes.createForUsage(VibrationAttributes.USAGE_NOTIFICATION))
            } else {
                @Suppress("DEPRECATION")
                vibrator.vibrate(effect, AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_NOTIFICATION).build())
            }
        }
    }
}
