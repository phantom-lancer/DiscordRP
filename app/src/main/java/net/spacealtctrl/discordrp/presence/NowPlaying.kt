package net.spacealtctrl.discordrp.presence

import android.graphics.Bitmap
import androidx.compose.runtime.Immutable
import java.text.Normalizer
import java.util.Locale

private val invisibleMetadataCharacters = Regex("[\\p{Cf}]")
private val metadataWhitespace = Regex("\\s+")

private fun metadataIdentity(value: String): String =
    metadataWhitespace.replace(
        invisibleMetadataCharacters.replace(
            Normalizer.normalize(value, Normalizer.Form.NFKC),
            "",
        ),
        " ",
    ).trim().lowercase(Locale.ROOT)

internal fun distinctMetadataText(excluded: String, vararg candidates: String?): String? {
    val excludedIdentity = metadataIdentity(excluded)
    return candidates.firstNotNullOfOrNull { candidate ->
        val value = candidate?.trim()?.takeIf { it.isNotEmpty() } ?: return@firstNotNullOfOrNull null
        value.takeIf { metadataIdentity(it) != excludedIdentity }
    }
}

internal fun resolveArtistMetadata(
    title: String,
    artist: String?,
    author: String?,
    albumArtist: String?,
    displaySubtitle: String?,
): String? = distinctMetadataText(title, artist, author, albumArtist, displaySubtitle)

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
