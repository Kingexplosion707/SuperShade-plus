package com.supershade.plus

import android.app.ActivityOptions
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build

/**
 * Opens what a notification (or one of its buttons) points at. From Android 14 a PendingIntent
 * sent by an app that isn't in front starts no activity unless the sender lends it its own right
 * to (SuperShade has it, as it draws over other apps); without that, tapping a notification in the
 * shade only closed the shade. This sends with that permission, and falls back to opening the app
 * when there's nothing to send or the send fails.
 */
object PendingLaunch {
    fun send(context: Context, intent: PendingIntent?, fillIn: Intent? = null, fallbackPackage: String? = null): Boolean {
        if (intent != null) {
            val sent = runCatching {
                val options = options()
                if (options != null) {
                    intent.send(context, 0, fillIn, null, null, null, options)
                } else if (fillIn != null) {
                    intent.send(context, 0, fillIn)
                } else {
                    intent.send()
                }
            }.isSuccess
            if (sent) return true
        }
        val pkg = fallbackPackage ?: return false
        val launch = context.packageManager.getLaunchIntentForPackage(pkg) ?: return false
        return runCatching {
            context.startActivity(launch.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_RESET_TASK_IF_NEEDED))
        }.isSuccess
    }

    private fun options(): android.os.Bundle? {
        if (Build.VERSION.SDK_INT < 34) return null
        val mode = if (Build.VERSION.SDK_INT >= 36) {
            ActivityOptions.MODE_BACKGROUND_ACTIVITY_START_ALLOW_ALWAYS
        } else {
            @Suppress("DEPRECATION")
            ActivityOptions.MODE_BACKGROUND_ACTIVITY_START_ALLOWED
        }
        return ActivityOptions.makeBasic().setPendingIntentBackgroundActivityStartMode(mode).toBundle()
    }
}
