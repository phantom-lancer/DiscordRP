package net.spacealtctrl.discordrp.presence

import android.graphics.Bitmap
import androidx.compose.runtime.Immutable
import java.text.Normalizer
import java.util.Locale

private val invisibleMetadataCharacters = Regex("[\\p{Cf}]")
private val metadataWhitespace = Regex("\\s+")
private val metadataSeparators = Regex(
    "\\s+(?:[-–—|•·])\\s+|\\s*[:：]\\s*|\\s+by\\s+",
    RegexOption.IGNORE_CASE,
)

private fun cleanMetadataText(value: String?): String? {
    val raw = value?.let { Normalizer.normalize(it, Normalizer.Form.NFKC) } ?: return null
    return metadataWhitespace.replace(invisibleMetadataCharacters.replace(raw, ""), " ")
        .trim()
        .takeIf(String::isNotEmpty)
}

internal fun metadataIdentity(value: String): String =
    metadataWhitespace.replace(
        invisibleMetadataCharacters.replace(
            Normalizer.normalize(value, Normalizer.Form.NFKC),
            "",
        ),
        " ",
    ).trim().lowercase(Locale.ROOT)

/**
 * MediaSession subtitle fields are sometimes formatted as "Track - Artist" instead
 * of containing only the performer. Remove the exact track-title segment before
 * treating the remaining segment as an artist. Never guess from partial substrings.
 */
internal fun normalizeArtistMetadataCandidate(title: String, candidate: String?): String? {
    val value = cleanMetadataText(candidate) ?: return null
    val titleKey = metadataIdentity(title)
    if (titleKey.isEmpty()) return value

    val parts = metadataSeparators.split(value)
        .map(String::trim)
        .filter(String::isNotEmpty)
    val titleIndex = parts.indexOfFirst { metadataIdentity(it) == titleKey }
    if (titleIndex >= 0 && parts.size > 1) {
        val adjacentArtist = when (titleIndex) {
            0 -> parts.getOrNull(1)
            parts.lastIndex -> parts.getOrNull(parts.lastIndex - 1)
            else -> parts.getOrNull(titleIndex + 1)
        }
        return adjacentArtist?.takeIf { metadataIdentity(it) != titleKey }
    }

    return value.takeIf { metadataIdentity(it) != titleKey }
}

internal fun distinctMetadataText(excluded: String, vararg candidates: String?): String? {
    val excludedIdentity = metadataIdentity(excluded)
    return candidates.firstNotNullOfOrNull { candidate ->
        val value = cleanMetadataText(candidate) ?: return@firstNotNullOfOrNull null
        value.takeIf { metadataIdentity(it) != excludedIdentity }
    }
}

internal fun preferredTrackTitle(displayTitle: String?, title: String?): String? =
    cleanMetadataText(displayTitle) ?: cleanMetadataText(title)

/**
 * Some players publish a formatted title ("Artist - Track") and the same artist
 * separately. Strip a complete, known artist segment from either end of the title.
 */
internal fun normalizeTrackTitle(title: String, vararg artists: String?): String {
    val cleanTitle = cleanMetadataText(title) ?: return ""
    val parts = metadataSeparators.split(cleanTitle).map(String::trim).filter(String::isNotEmpty)
    if (parts.size < 2) return cleanTitle

    val identities = artists.filterNotNull()
        .mapNotNull { normalizeArtistMetadataCandidate(cleanTitle, it) }
        .map(::metadataIdentity)
        .filter(String::isNotEmpty)
        .toSet()
    if (identities.isEmpty()) return cleanTitle

    return when {
        metadataIdentity(parts.first()) in identities -> parts.drop(1).joinToString(" - ").trim()
        metadataIdentity(parts.last()) in identities -> parts.dropLast(1).joinToString(" - ").trim()
        else -> cleanTitle
    }.ifEmpty { cleanTitle }
}

/**
 * DISPLAY_SUBTITLE is Android's preferred second line for media metadata. Prefer it
 * over less presentation-specific ARTIST/AUTHOR values, which some players format
 * with the track title. DISPLAY_DESCRIPTION is deliberately not an artist source.
 */
internal fun resolveArtistMetadata(
    title: String,
    displaySubtitle: String?,
    artist: String?,
    albumArtist: String?,
    author: String?,
): String? = listOf(displaySubtitle, artist, albumArtist, author)
    .firstNotNullOfOrNull { normalizeArtistMetadataCandidate(title, it) }

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
