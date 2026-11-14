package com.supershade.plus

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import kotlin.math.roundToInt
import kotlinx.coroutines.launch

/**
 * SuperShade Plus's own settings: Spicy Lyrics' live activity, where along the top a swipe opens
 * the shade (with a gap for a dynamic island), how deep that reaches, and the strict block of
 * the system's own shade.
 */
@Composable
fun PlusSettingsSection(shizukuConnected: Boolean) {
    val context = LocalContext.current
    Text(
        "SuperShade Plus",
        style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
        modifier = Modifier.padding(start = 8.dp, top = 20.dp, bottom = 8.dp),
    )
    Surface(
        shape = RoundedCornerShape(24.dp),
        color = MaterialTheme.colorScheme.surfaceContainer,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.35f)),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(Modifier.padding(16.dp)) {
            var spicy by remember { mutableStateOf(PlusSettings.spicyLiveActivity(context)) }
            SwitchRow(
                "Spicy Lyrics live activity",
                "With Spicy Lyrics Plus installed, the media card becomes its live activity: word-synced lyrics, the animated cover and the Canvas. The pin floats the lyrics out as its overlay.",
                spicy,
            ) {
                spicy = it
                PlusSettings.put(context, PlusSettings.SPICY_LIVE_ACTIVITY, it)
            }
            HorizontalDivider(Modifier.padding(vertical = 10.dp), color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f))
            var start by remember { mutableIntStateOf(PlusSettings.zoneStart(context)) }
            var end by remember { mutableIntStateOf(PlusSettings.zoneEnd(context)) }
            SliderRow("Swipe zone starts", "$start% from the left", start, 0..95) {
                start = it.coerceAtMost(end - 5)
                PlusSettings.put(context, PlusSettings.ZONE_START, start)
            }
            SliderRow("Swipe zone ends", "$end% from the left", end, 5..100) {
                end = it.coerceAtLeast(start + 5)
                PlusSettings.put(context, PlusSettings.ZONE_END, end)
            }
            var gap by remember { mutableIntStateOf(PlusSettings.islandGap(context)) }
            SliderRow(
                "Gap for the dynamic island",
                if (gap == 0) "None: the whole zone opens the shade" else "$gap% of the width over the camera stays free to tap",
                gap, 0..70,
            ) {
                gap = it
                PlusSettings.put(context, PlusSettings.ISLAND_GAP, it)
            }
            var depth by remember { mutableIntStateOf(PlusSettings.zoneExtraDp(context)) }
            SliderRow("Swipe zone depth", "Status bar plus $depth dp", depth, 0..80) {
                depth = it
                PlusSettings.put(context, PlusSettings.ZONE_EXTRA_DP, it)
            }
            HorizontalDivider(Modifier.padding(vertical = 10.dp), color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f))
            var swipe by remember { mutableIntStateOf(PlusSettings.swipeSensitivity(context)) }
            SliderRow(
                "Swipe-to-dismiss sensitivity",
                when {
                    swipe < 25 -> "Low: a long, deliberate swipe dismisses"
                    swipe < 60 -> "Medium: about half the card's width"
                    else -> "High: a light flick dismisses"
                },
                swipe, 0..100,
            ) {
                swipe = it
                PlusSettings.put(context, PlusSettings.SWIPE_SENSITIVITY, it)
            }
            var blur by remember { mutableIntStateOf(PlusSettings.popupBlur(context)) }
            SliderRow(
                "Pop-up background blur",
                if (blur == 0) "Off: notification and media pop-ups leave the screen sharp" else "$blur% blur behind notification and media pop-ups",
                blur, 0..100,
            ) {
                blur = it
                PlusSettings.put(context, PlusSettings.POPUP_BLUR, it)
            }
            HorizontalDivider(Modifier.padding(vertical = 10.dp), color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f))
            // Every permission at once, through Shizuku.
            val governor = org.koin.compose.koinInject<com.supershade.shizuku.StatusBarGovernor>()
            val scope = androidx.compose.runtime.rememberCoroutineScope()
            var grantStatus by remember { mutableStateOf<String?>(null) }
            Row(Modifier.fillMaxWidth().padding(vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text("Grant every permission", style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.SemiBold))
                    Text(
                        grantStatus ?: "Through Shizuku: notification access, accessibility, drawing over apps, settings access, Do Not Disturb, phone and Bluetooth, and no battery limits.",
                        style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Spacer(Modifier.padding(start = 12.dp))
                androidx.compose.material3.FilledTonalButton(
                    enabled = shizukuConnected,
                    onClick = {
                        grantStatus = "Granting…"
                        scope.launch {
                            val result = ShizukuGrants.grantAll(context, governor)
                            grantStatus = if (result.failed.isEmpty()) "Granted ${result.granted.size} permissions."
                            else "Granted ${result.granted.size}; couldn't grant: ${result.failed.joinToString()}."
                        }
                    },
                ) { Text("Grant") }
            }
            HorizontalDivider(Modifier.padding(vertical = 10.dp), color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f))
            var strict by remember { mutableStateOf(PlusSettings.strictBlock(context)) }
            SwitchRow(
                "Never let the system shade open",
                if (shizukuConnected) "Through Shizuku the system's shade is blocked and kept blocked, and closed at once if it slips open."
                else "Needs Shizuku running. The system's shade is blocked and kept blocked, and closed at once if it slips open.",
                strict,
            ) {
                strict = it
                PlusSettings.put(context, PlusSettings.STRICT_BLOCK, it)
            }
            HorizontalDivider(Modifier.padding(vertical = 10.dp), color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f))
            var buzz by remember { mutableStateOf(PlusSettings.notificationVibrate(context)) }
            SwitchRow(
                "Vibrate for notifications",
                "A short buzz when a notification arrives, in vibrate mode or with the sound on. Never in silent mode or Do Not Disturb.",
                buzz,
            ) {
                buzz = it
                PlusSettings.put(context, PlusSettings.NOTIFICATION_VIBRATE, it)
            }
            var genie by remember { mutableStateOf(PlusSettings.popupAnimation(context)) }
            SwitchRow(
                "Pop-ups come out of the camera",
                "Notification and media pop-ups pour out from behind the camera, like a window coming out of the macOS Dock. Off: they slide down.",
                genie,
            ) {
                genie = it
                PlusSettings.put(context, PlusSettings.POPUP_ANIMATION, it)
            }
        }
    }
}

@Composable
private fun SwitchRow(title: String, subtitle: String, checked: Boolean, onChange: (Boolean) -> Unit) {
    Row(Modifier.fillMaxWidth().padding(vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.SemiBold))
            Text(subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Spacer(Modifier.padding(start = 12.dp))
        Switch(checked = checked, onCheckedChange = onChange)
    }
}

@Composable
private fun SliderRow(title: String, subtitle: String, value: Int, range: IntRange, onChange: (Int) -> Unit) {
    Column(Modifier.fillMaxWidth().padding(vertical = 2.dp)) {
        Text(title, style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.SemiBold))
        Text(subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Spacer(Modifier.height(2.dp))
        Slider(
            value = value.toFloat(),
            onValueChange = { onChange(it.roundToInt()) },
            valueRange = range.first.toFloat()..range.last.toFloat(),
        )
    }
}
