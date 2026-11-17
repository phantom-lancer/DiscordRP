package net.spacealtctrl.discordrp.presence

import net.spacealtctrl.discordrp.BuildConfig
import net.spacealtctrl.discordrp.gateway.ActivityCard
import net.spacealtctrl.discordrp.gateway.ArtRefs
import net.spacealtctrl.discordrp.gateway.PresenceUpdate
import net.spacealtctrl.discordrp.gateway.TimeSpan
import net.spacealtctrl.discordrp.settings.Mood
import net.spacealtctrl.discordrp.settings.Stash
import javax.inject.Inject

data class ComposedPresence(
    val update: PresenceUpdate,
    val art: PublishedArt,
)

data class ActivityLines(
    val name: String,
    val details: String?,
    val state: String?,
    val largeText: String?,
)

class PresenceComposer @Inject constructor(
    private val stash: Stash,
    private val artRegistry: ArtRegistry,
) {
    fun quietUpdate(mood: Mood = stash.mood): PresenceUpdate =
        PresenceUpdate(status = mood.wire, afk = false, since = 0L)

    suspend fun compose(track: NowPlaying): ComposedPresence {
        val lines = arrangeLines(
            title = track.title,
            artist = artistForLayout(
                title = track.title,
                artist = track.artist,
                showArtist = stash.showArtist,
                artistAsTitle = stash.artistAsTitle,
            ),
            album = track.album.takeIf { stash.showAlbum },
            appLabel = track.appLabel,
            showAppName = stash.showAppName,
            artistAsTitle = stash.artistAsTitle,
        )

        val art = resolveArt(track)
        val assets = art.large?.let { large ->
            ArtRefs(
                largeImage = large,
                smallImage = art.small,
                largeText = ActivityCard.clip(lines.largeText),
                smallText = ActivityCard.clip(track.appLabel.takeIf { stash.showAppName }),
            )
        }

        val update = PresenceUpdate(
            status = stash.mood.wire,
            afk = true,
            since = 0L,
            activities = listOf(
                ActivityCard(
                    name = lines.name,
                    type = LISTENING,
                    details = ActivityCard.clip(lines.details),
                    state = ActivityCard.clip(lines.state),
                    timestamps = track.takeIf { stash.showTimeline }?.let {
                        TimeSpan(start = it.startedAtMillis, end = it.endsAtMillis)
                            .takeIf { span -> span.start != null || span.end != null }
                    },
                    assets = assets,
                    applicationId = BuildConfig.DISCORD_APP_ID,
                ),
            ),
        )
        return ComposedPresence(update = update, art = art)
    }

    private suspend fun resolveArt(track: NowPlaying): PublishedArt {
        val cover = ArtSource.Cover(
            cacheKey = coverKey(track),
            artist = track.artist,
            albumArtist = track.albumArtist,
            album = track.album,
            title = track.title,
            durationMillis = track.durationMillis,
            artworkUrl = track.artworkUrl,
            artwork = track.artwork,
        ).takeIf { !track.album.isNullOrBlank() || track.title.isNotBlank() }
        val icon = ArtSource.AppIcon(track.appPackage).takeIf { stash.showAppIcon }
        val customArtwork = stash.customArtworkUrl.takeIf {
            stash.customArtworkEnabled && ArtworkUrls.normalize(it) != null
        }?.let { ArtSource.Remote(it, "custom-cover:" + it.hashCode()) }

        val coverRef = customArtwork?.let { artRegistry.resolve(it) } ?: artRegistry.resolve(cover)
        val glyphRef = artRegistry.resolve(playbackGlyph(track))
        val iconRef = artRegistry.resolve(icon)
        val fallbackRef = if (coverRef == null && iconRef == null) {
            artRegistry.resolve(ArtSource.DefaultIcon)
        } else {
            null
        }
        val large = coverRef ?: iconRef ?: fallbackRef
        val small = if (coverRef != null) glyphRef ?: iconRef else glyphRef
        return PublishedArt(
            large = large,
            small = small,
            coverKey = cover?.cacheKey,
            missingGroupId = cover?.let { artRegistry.missingGroupFor(it.cacheKey) },
            deezerAlbumId = cover?.let { artRegistry.deezerAlbumFor(it.cacheKey) },
            albumMissing = cover?.let { artRegistry.albumMissingFor(it.cacheKey) } == true,
            retrySoon = cover?.let { artRegistry.coverPending(it.cacheKey) } == true,
        )
    }

    private fun playbackGlyph(track: NowPlaying): ArtSource.Remote? {
        if (!stash.showPlaybackGlyph) return null
        val name = when {
            track.playing -> "play"
            track.stopped -> "stop"
            else -> "pause"
        }
        return ArtSource.Remote(url = "$GLYPH_BASE/$name.png", cacheKey = "glyph:$name")
    }

    private fun coverKey(track: NowPlaying): String {
        val artist = track.albumArtist ?: track.artist
        val album = track.album?.takeIf { it.isNotBlank() }
        return if (album != null) "$artist|$album".lowercase() else "$artist|song:${track.title}".lowercase()
    }

    companion object {
        const val LISTENING = 2

        fun cardName(track: NowPlaying, stash: Stash): String = arrangeLines(
            title = track.title,
            artist = artistForLayout(
                title = track.title,
                artist = track.artist,
                showArtist = stash.showArtist,
                artistAsTitle = stash.artistAsTitle,
            ),
            album = track.album.takeIf { stash.showAlbum },
            appLabel = track.appLabel,
            showAppName = stash.showAppName,
            artistAsTitle = stash.artistAsTitle,
        ).name

        const val GLYPH_BASE =
            "https://raw.githubusercontent.com/phantom-lancer/DiscordRP/main/assets/glyphs"

        internal fun artistForLayout(
            title: String,
            artist: String?,
            showArtist: Boolean,
            artistAsTitle: Boolean,
        ): String? = if (showArtist || artistAsTitle) {
            distinctMetadataText(title, artist)
        } else {
            null
        }

        fun arrangeLines(
            title: String,
            artist: String?,
            album: String?,
            appLabel: String,
            showAppName: Boolean,
            artistAsTitle: Boolean,
        ): ActivityLines = when {
            artistAsTitle -> {
                val visibleArtist = artist?.trim()?.takeIf {
                    it.isNotEmpty() && !it.equals(title.trim(), ignoreCase = true)
                }
                ActivityLines(
                    name = visibleArtist ?: title,
                    details = title.takeIf { visibleArtist != null },
                    state = album,
                    largeText = appLabel.takeIf { showAppName },
                )
            }
            showAppName -> ActivityLines(
                name = appLabel,
                details = title,
                state = artist,
                largeText = album,
            )
            else -> ActivityLines(
                name = title,
                details = artist,
                state = album,
                largeText = null,
            )
        }
    }
}
