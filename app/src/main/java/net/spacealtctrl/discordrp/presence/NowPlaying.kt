package net.spacealtctrl.discordrp.presence

import android.graphics.Bitmap
import androidx.compose.runtime.Immutable

internal fun distinctMetadataText(excluded: String, vararg candidates: String?): String? =
    candidates.firstNotNullOfOrNull { candidate ->
        candidate?.trim()?.takeIf {
            it.isNotEmpty() && !it.equals(excluded.trim(), ignoreCase = true)
        }
    }

@Immutable
data class NowPlaying(
    val title: String,
    val artist: String? = null,
    val album: String? = null,
    val albumArtist: String? = null,
    val appLabel: String,
    val appPackage: String,
    val artwork: Bitmap? = null,
    val artworkUrl: String? = null,
    val playing: Boolean = false,
    val stopped: Boolean = false,
    val startedAtMillis: Long? = null,
    val endsAtMillis: Long? = null,
    val durationMillis: Long? = null,
) {
    val hasTimeline: Boolean get() = startedAtMillis != null

    fun isSameSong(other: NowPlaying?): Boolean =
        other != null &&
            appPackage == other.appPackage &&
            title == other.title &&
            artist == other.artist &&
            album == other.album &&
            artworkUrl == other.artworkUrl
}

@Immutable
data class BridgeSnapshot(
    val linked: Boolean = false,
    val track: NowPlaying? = null,
    val art: PublishedArt? = null,
)
