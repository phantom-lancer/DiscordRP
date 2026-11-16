package net.spacealtctrl.discordrp.presence

import android.content.ComponentName
import android.content.Context
import android.media.MediaMetadata
import android.media.session.MediaController
import android.media.session.MediaSessionManager
import android.media.session.PlaybackState
import dagger.hilt.android.qualifiers.ApplicationContext
import net.spacealtctrl.discordrp.log.AppLog
import net.spacealtctrl.discordrp.service.MediaNoticeListener
import net.spacealtctrl.discordrp.settings.Stash
import java.util.concurrent.ConcurrentHashMap
import javax.inject.Inject

class PlaybackScanner @Inject constructor(
    @ApplicationContext private val context: Context,
    private val stash: Stash,
    private val log: AppLog,
) {
    private val listenerComponent = ComponentName(context, MediaNoticeListener::class.java)
    private val appLabels = ConcurrentHashMap<String, String>()

    fun currentTrack(): NowPlaying? {
        val manager =
            context.getSystemService(Context.MEDIA_SESSION_SERVICE) as MediaSessionManager
        val sessions = try {
            manager.getActiveSessions(listenerComponent)
        } catch (e: SecurityException) {
            log.error(TAG, "Media session access revoked")
            return null
        }

        return sessions
            .asSequence()
            .filter { stash.isPlayerChosen(it.packageName) }
            .sortedBy { playbackPriority(it.playbackState?.state) }
            .mapNotNull(::readSession)
            .firstOrNull()
    }

    private fun readSession(controller: MediaController): NowPlaying? {
        if (!stash.isPlayerChosen(controller.packageName)) return null

        val state = controller.playbackState?.state
        if (state == PlaybackState.STATE_STOPPED || state == PlaybackState.STATE_NONE) return null

        val playing = state == PlaybackState.STATE_PLAYING ||
            state == PlaybackState.STATE_FAST_FORWARDING ||
            state == PlaybackState.STATE_REWINDING
        val paused = state == PlaybackState.STATE_PAUSED
        if (stash.hideWhenPaused && paused) return null

        val metadata = controller.metadata ?: return null
        val title = metadata.firstText(
            MediaMetadata.METADATA_KEY_TITLE,
            MediaMetadata.METADATA_KEY_DISPLAY_TITLE,
        ) ?: return null
        val timeline = timeline(controller, playing, metadata)

        return NowPlaying(
            title = title,
            artist = metadata.firstText(
                MediaMetadata.METADATA_KEY_ARTIST,
                MediaMetadata.METADATA_KEY_AUTHOR,
                MediaMetadata.METADATA_KEY_DISPLAY_SUBTITLE,
            ),
            album = metadata.firstText(MediaMetadata.METADATA_KEY_ALBUM),
            albumArtist = metadata.firstText(
                MediaMetadata.METADATA_KEY_ALBUM_ARTIST,
                MediaMetadata.METADATA_KEY_ARTIST,
                MediaMetadata.METADATA_KEY_AUTHOR,
            ),
            appLabel = labelFor(controller.packageName),
            appPackage = controller.packageName,
            artwork = metadata.bitmap(MediaMetadata.METADATA_KEY_ALBUM_ART)
                ?: metadata.bitmap(MediaMetadata.METADATA_KEY_ART),
            artworkUrl = ArtworkUrls.normalize(
                metadata.firstText(
                    MediaMetadata.METADATA_KEY_ALBUM_ART_URI,
                    MediaMetadata.METADATA_KEY_ART_URI,
                ),
            ),
            playing = playing,
            stopped = false,
            startedAtMillis = timeline?.first,
            endsAtMillis = timeline?.second,
            durationMillis = metadata.getLong(MediaMetadata.METADATA_KEY_DURATION).takeIf { it > 0 },
        )
    }

    private fun playbackPriority(state: Int?): Int = when (state) {
        PlaybackState.STATE_PLAYING -> 0
        PlaybackState.STATE_BUFFERING,
        PlaybackState.STATE_CONNECTING,
        PlaybackState.STATE_FAST_FORWARDING,
        PlaybackState.STATE_REWINDING -> 1
        PlaybackState.STATE_PAUSED -> 2
        PlaybackState.STATE_STOPPED,
        PlaybackState.STATE_NONE -> 4
        else -> 3
    }

    private fun timeline(
        controller: MediaController,
        playing: Boolean,
        metadata: MediaMetadata,
    ): Pair<Long, Long>? {
        if (!playing) return null
        val duration = metadata.getLong(MediaMetadata.METADATA_KEY_DURATION).takeIf { it > 0 } ?: return null
        val position = controller.playbackState?.position?.coerceAtLeast(0L) ?: 0L
        val start = System.currentTimeMillis() - position
        return start to (start + duration)
    }

    private fun MediaMetadata.firstText(vararg keys: String): String? =
        keys.firstNotNullOfOrNull { key -> getString(key)?.trim()?.takeIf { it.isNotEmpty() } }

    private fun MediaMetadata.bitmap(key: String) =
        runCatching { getBitmap(key) }.getOrNull()

    private fun labelFor(packageName: String): String =
        appLabels[packageName] ?: runCatching {
            val pm = context.packageManager
            pm.getApplicationLabel(pm.getApplicationInfo(packageName, 0)).toString()
        }.getOrDefault(packageName).also { appLabels.putIfAbsent(packageName, it) }

    private companion object {
        const val TAG = "PlaybackScanner"
    }
}
