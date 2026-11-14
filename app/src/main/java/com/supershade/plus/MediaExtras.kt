package com.supershade.plus

import android.content.Context
import android.graphics.Bitmap
import android.graphics.drawable.BitmapDrawable
import android.media.Rating
import android.media.session.PlaybackState
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Favorite
import androidx.compose.material.icons.rounded.FavoriteBorder
import androidx.compose.material.icons.rounded.Repeat
import androidx.compose.material.icons.rounded.Shuffle
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.supershade.domain.media.MediaRepository

/**
 * The player's own extra buttons, as its media session offers them: like (Spotify's "Add to Liked
 * Songs", a heart or thumbs-up elsewhere), shuffle and repeat. Each shows the player's own icon,
 * which changes with the state (a filled heart once liked), and is sent back to the player as its
 * custom action. A player without a like action but with heart ratings gets a heart that sets the
 * rating. A button the player doesn't offer isn't shown.
 */
object MediaExtras {
    enum class Kind { Like, Shuffle, Repeat }

    class Button(val kind: Kind, val label: String, val icon: ImageBitmap?, val on: Boolean?, val press: () -> Unit)

    private val likeWords = listOf("like", "heart", "favorite", "favourite", "collection", "save", "thumb")
    private val iconCache = HashMap<String, ImageBitmap?>()

    fun kindOf(action: PlaybackState.CustomAction): Kind? {
        val text = (action.action + " " + action.name).lowercase()
        return when {
            "dislike" in text || "thumbs_down" in text || "thumb down" in text -> null
            "shuffle" in text -> Kind.Shuffle
            "repeat" in text -> Kind.Repeat
            likeWords.any { it in text } -> Kind.Like
            else -> null
        }
    }

    fun buttons(context: Context, media: MediaRepository): List<Button> {
        val pkg = media.playerPackage() ?: return emptyList()
        val found = LinkedHashMap<Kind, Button>()
        for (action in media.customActions()) {
            val kind = kindOf(action) ?: continue
            if (kind in found) continue
            found[kind] = Button(kind, action.name.toString(), icon(context, pkg, action.icon), null) { media.sendCustomAction(action) }
        }
        if (Kind.Like !in found && media.ratingType() == Rating.RATING_HEART) {
            val liked = media.liked() == true
            found[Kind.Like] = Button(Kind.Like, if (liked) "Unlike" else "Like", null, liked) { media.setHeart(!liked) }
        }
        return Kind.entries.mapNotNull { found[it] }
    }

    private fun icon(context: Context, pkg: String, res: Int): ImageBitmap? {
        if (res == 0) return null
        val key = "$pkg/$res"
        return iconCache.getOrPut(key) {
            runCatching {
                val drawable = context.packageManager.getResourcesForApplication(pkg).getDrawable(res, null)
                val bitmap = (drawable as? BitmapDrawable)?.bitmap ?: run {
                    val size = 72
                    Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888).also {
                        val canvas = android.graphics.Canvas(it)
                        drawable.setBounds(0, 0, size, size)
                        drawable.draw(canvas)
                    }
                }
                bitmap.asImageBitmap()
            }.getOrNull()
        }
    }
}

/** The player's extra buttons ([MediaExtras]) for the controls, following the player's state. */
@Composable
fun rememberMediaExtras(): List<MediaExtras.Button> {
    val context = LocalContext.current
    val media = org.koin.compose.koinInject<MediaRepository>()
    // Recomputed whenever the player's state changes (its icons follow, e.g. a heart once liked).
    val state by media.media.collectAsState()
    return remember(state) { MediaExtras.buttons(context, media) }
}

@Composable
fun MediaExtraButton(button: MediaExtras.Button, size: Dp, tint: Color = Color.White, onPressed: () -> Unit = {}) {
    val haptic = LocalHapticFeedback.current
    Box(
        Modifier.size(size + 16.dp).clip(CircleShape)
            .background(if (button.on == true) tint.copy(alpha = 0.18f) else Color.Transparent)
            .clickable(onClickLabel = button.label) {
                haptic.performHapticFeedback(androidx.compose.ui.hapticfeedback.HapticFeedbackType.TextHandleMove)
                onPressed()
                button.press()
            },
        contentAlignment = Alignment.Center,
    ) {
        val image = button.icon
        if (image != null) {
            Image(image, button.label, colorFilter = ColorFilter.tint(tint), modifier = Modifier.size(size))
        } else {
            val vector = when (button.kind) {
                MediaExtras.Kind.Like -> if (button.on == true) Icons.Rounded.Favorite else Icons.Rounded.FavoriteBorder
                MediaExtras.Kind.Shuffle -> Icons.Rounded.Shuffle
                MediaExtras.Kind.Repeat -> Icons.Rounded.Repeat
            }
            Icon(vector, button.label, tint = tint, modifier = Modifier.size(size))
        }
    }
}
