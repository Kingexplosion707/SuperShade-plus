package com.supershade.plus

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import androidx.core.content.ContextCompat
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

/**
 * The strict block ([PlusSettings.strictBlock]): through Shizuku, the system's own shade is kept
 * from expanding at all. The block is asserted at once, again every [REASSERT_MS] and whenever
 * the screen comes on or the phone unlocks (SystemUI restarting, or another app, can lift it),
 * and the accessibility service closes the system shade the moment it shows anyway.
 */
object StrictBlock {
    private const val REASSERT_MS = 20_000L
    private var loop: Job? = null
    private var receiver: BroadcastReceiver? = null

    fun start(context: Context, scope: CoroutineScope, assert: suspend () -> Unit) {
        val app = context.applicationContext
        fun apply() {
            loop?.cancel()
            loop = null
            if (!PlusSettings.strictBlock(app)) return
            loop = scope.launch {
                while (isActive) {
                    runCatching { assert() }
                    delay(REASSERT_MS)
                }
            }
        }
        apply()
        PlusSettings.changes(app).onEach { key -> if (key == PlusSettings.STRICT_BLOCK) apply() }.launchIn(scope)
        val onWake = object : BroadcastReceiver() {
            override fun onReceive(context: Context, intent: Intent) {
                if (PlusSettings.strictBlock(app)) scope.launch { runCatching { assert() } }
            }
        }
        receiver = onWake
        ContextCompat.registerReceiver(
            app, onWake,
            IntentFilter(Intent.ACTION_SCREEN_ON).apply { addAction(Intent.ACTION_USER_PRESENT) },
            ContextCompat.RECEIVER_NOT_EXPORTED,
        )
    }

    fun stop(context: Context) {
        loop?.cancel()
        loop = null
        receiver?.let { runCatching { context.applicationContext.unregisterReceiver(it) } }
        receiver = null
    }
}
