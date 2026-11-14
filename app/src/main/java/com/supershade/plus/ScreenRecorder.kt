package com.supershade.plus

import android.content.Context
import android.media.MediaScannerConnection
import android.os.Environment
import android.widget.Toast
import com.supershade.shizuku.StatusBarGovernor
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.withContext

/**
 * The Screen Record tile. Android's own recorder lives inside SystemUI's tile and can't be clicked
 * from outside it, so through Shizuku this records with Android's `screenrecord` instead: the
 * screen, without sound, into Movies/SuperShade. That tool stops each file at three minutes, so
 * recording carries on in a new file until the tile is tapped again.
 */
object ScreenRecorder {
    private const val FLAG = "/data/local/tmp/supershade_recording"
    private const val FOLDER = "/sdcard/Movies/SuperShade"

    private val _recording = MutableStateFlow(false)
    val recording: StateFlow<Boolean> = _recording

    /** Starts or stops recording; the new state. */
    suspend fun toggle(context: Context, governor: StatusBarGovernor): Boolean {
        if (!governor.canRunPrivileged) {
            toast(context, "Screen recording needs Shizuku running")
            return false
        }
        // Recording already, whether this started it or not.
        val running = _recording.value || governor.runShellOutput("sh", "-c", "pidof screenrecord || true").isNotBlank()
        return if (running) stop(context, governor) else start(context, governor)
    }

    private suspend fun start(context: Context, governor: StatusBarGovernor): Boolean {
        val script = "mkdir -p $FOLDER; touch $FLAG; " +
            "nohup sh -c 'while [ -e $FLAG ]; do screenrecord --bit-rate 16000000 $FOLDER/Screen_\$(date +%Y%m%d_%H%M%S).mp4; done' " +
            "> /dev/null 2>&1 &"
        val ok = governor.runShell("sh", "-c", script)
        if (ok) {
            _recording.value = true
            toast(context, "Recording the screen. Tap Screen Record again to stop")
        } else {
            toast(context, "Couldn't start recording")
        }
        return ok
    }

    private suspend fun stop(context: Context, governor: StatusBarGovernor): Boolean {
        governor.runShell("sh", "-c", "rm -f $FLAG; for p in \$(pidof screenrecord); do kill -2 \$p; done")
        _recording.value = false
        // Let the last file finish writing, then make sure it shows in the gallery.
        delay(1_200L)
        withContext(Dispatchers.IO) {
            val dir = java.io.File(Environment.getExternalStorageDirectory(), "Movies/SuperShade")
            val files = dir.listFiles()?.map { it.absolutePath }?.toTypedArray() ?: arrayOf("$FOLDER")
            runCatching { MediaScannerConnection.scanFile(context, files, null, null) }
        }
        toast(context, "Saved to Movies/SuperShade")
        return false
    }

    private suspend fun toast(context: Context, text: String) = withContext(Dispatchers.Main) {
        Toast.makeText(context.applicationContext, text, Toast.LENGTH_SHORT).show()
    }
}
