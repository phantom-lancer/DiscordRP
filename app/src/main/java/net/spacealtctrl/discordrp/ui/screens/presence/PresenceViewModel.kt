package net.spacealtctrl.discordrp.ui.screens.presence

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.content.pm.ApplicationInfo
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import net.spacealtctrl.discordrp.presence.ArtRegistry
import net.spacealtctrl.discordrp.presence.ExternalArtworkHost
import net.spacealtctrl.discordrp.presence.BridgeFeed
import net.spacealtctrl.discordrp.presence.NowPlaying
import net.spacealtctrl.discordrp.service.BridgeService
import net.spacealtctrl.discordrp.settings.CoverSource
import net.spacealtctrl.discordrp.settings.Mood
import net.spacealtctrl.discordrp.settings.Stash
import javax.inject.Inject

data class PlayerChoice(
    val label: String,
    val packageName: String,
    val chosen: Boolean,
)

enum class ShareFacet {
    ARTIST, ALBUM, TIMELINE, PLAYBACK_GLYPH, APP_ICON, APP_NAME, HIDE_PAUSED, ARTIST_AS_TITLE;

    val rival: ShareFacet?
        get() = when (this) {
            APP_NAME -> ARTIST_AS_TITLE
            ARTIST_AS_TITLE -> APP_NAME
            PLAYBACK_GLYPH -> APP_ICON
            APP_ICON -> PLAYBACK_GLYPH
            else -> null
        }
}

enum class TitleMode { APP, SONG, ARTIST }

enum class BadgeMode { APP_ICON, GLYPH, NONE }

data class PresenceUiState(
    val sharing: Boolean = true,
    val mood: Mood = Mood.ONLINE,
    val coverSource: CoverSource = CoverSource.PLAYER,
    val mobileBadge: Boolean = true,
    val facets: Map<ShareFacet, Boolean> = emptyMap(),
    val players: List<PlayerChoice> = emptyList(),
    val chosenCount: Int = 0,
    val chosenPackages: List<String> = emptyList(),
    val loadingPlayers: Boolean = true,
    val liveTrack: NowPlaying? = null,
    val titleMode: TitleMode = TitleMode.APP,
    val badgeMode: BadgeMode = BadgeMode.APP_ICON,
    val customArtworkEnabled: Boolean = false,
    val customArtworkUrl: String = "",
    val customArtworkUploading: Boolean = false,
    val customArtworkError: String? = null,
    val bitmapArtworkUploadEnabled: Boolean = false,
    val localArtworkFallbackEnabled: Boolean = true,
    val catboxUserHashConfigured: Boolean = false,
    val sampleAppLabel: String = "",
    val sampleAppPackage: String = "",
)

@HiltViewModel
class PresenceViewModel @Inject constructor(
    @ApplicationContext private val appContext: Context,
    private val stash: Stash,
    private val artRegistry: ArtRegistry,
    private val externalArtworkHost: ExternalArtworkHost,
) : ViewModel() {
    private data class InstalledApp(
        val label: String,
        val packageName: String,
        val looksLikePlayer: Boolean,
    )

    private val installedApps = MutableStateFlow<List<InstalledApp>?>(null)

    val query = MutableStateFlow("")

    private var badgeRestartJob: Job? = null

    private val customArtworkUploading = MutableStateFlow(false)
    private val customArtworkError = MutableStateFlow<String?>(null)
    private var customArtworkJob: Job? = null

    private val baseState = combine(
        stash.revisions,
        installedApps.asStateFlow(),
        query.asStateFlow(),
        BridgeFeed.snapshot,
    ) { _, installed, search, snapshot ->
        val chosen = stash.chosenPlayers
        val players = installed.orEmpty()
            .filter { app ->
                if (search.isBlank()) {
                    app.looksLikePlayer || app.packageName in chosen
                } else {
                    app.label.contains(search, ignoreCase = true) ||
                        app.packageName.contains(search, ignoreCase = true)
                }
            }
            .map { app -> PlayerChoice(app.label, app.packageName, app.packageName in chosen) }
            .sortedWith(compareByDescending<PlayerChoice> { it.chosen }.thenBy { it.label.lowercase() })

        val chosenSorted = chosen.sorted()
        val samplePackage = chosenSorted.firstOrNull().orEmpty()

        PresenceUiState(
            sharing = stash.presenceEnabled,
            mood = stash.mood,
            coverSource = stash.coverSource,
            mobileBadge = stash.mobileBadge,
            facets = ShareFacet.entries.associateWith { facetValue(it) },
            players = players,
            chosenCount = chosen.size,
            chosenPackages = chosenSorted,
            loadingPlayers = installed == null,
            liveTrack = snapshot.track,
            titleMode = when {
                stash.artistAsTitle -> TitleMode.ARTIST
                stash.showAppName -> TitleMode.APP
                else -> TitleMode.SONG
            },
            badgeMode = when {
                stash.showPlaybackGlyph -> BadgeMode.GLYPH
                stash.showAppIcon -> BadgeMode.APP_ICON
                else -> BadgeMode.NONE
            },
            sampleAppLabel = installed.orEmpty()
                .firstOrNull { it.packageName == samplePackage }?.label.orEmpty(),
            sampleAppPackage = samplePackage,
            customArtworkEnabled = stash.customArtworkEnabled,
            customArtworkUrl = stash.customArtworkUrl,
            bitmapArtworkUploadEnabled = stash.bitmapArtworkUploadEnabled,
            localArtworkFallbackEnabled = stash.localArtworkFallbackEnabled,
            catboxUserHashConfigured = stash.hasCatboxUserHash,
        )
    }

    val state: StateFlow<PresenceUiState> = combine(
        baseState,
        customArtworkUploading.asStateFlow(),
        customArtworkError.asStateFlow(),
    ) { base, uploading, error ->
        base.copy(
            customArtworkUploading = uploading,
            customArtworkError = error,
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), PresenceUiState())

    init {
        if (stash.showPlaybackGlyph && stash.showAppIcon) stash.showAppIcon = false

        viewModelScope.launch(Dispatchers.Default) {
            val pm = appContext.packageManager

            @Suppress("DEPRECATION")
            val mediaBrowserOwners = pm.queryIntentServices(
                Intent("android.media.browse.MediaBrowserService"), 0,
            ).mapNotNull { it.serviceInfo?.packageName }.toSet()

            installedApps.value = pm.getInstalledApplications(0)
                .asSequence()
                .filter { pm.getLaunchIntentForPackage(it.packageName) != null }
                .map { info ->
                    InstalledApp(
                        label = info.loadLabel(pm).toString(),
                        packageName = info.packageName,
                        looksLikePlayer = info.packageName in mediaBrowserOwners ||
                            info.category == ApplicationInfo.CATEGORY_AUDIO ||
                            info.category == ApplicationInfo.CATEGORY_VIDEO ||
                            info.packageName in Stash.DEFAULT_PLAYERS,
                    )
                }
                .toList()
        }
    }

    fun setSharing(on: Boolean) {
        stash.presenceEnabled = on
        nudgeBridge()
    }

    fun pickMood(mood: Mood) {
        stash.mood = mood
        nudgeBridge()
    }

    fun pickCoverSource(source: CoverSource) {
        if (stash.coverSource == source) return
        stash.coverSource = source
        artRegistry.coverSourceChanged()
        BridgeFeed.artForgotten()
        nudgeBridge()
    }

    fun setCustomArtworkEnabled(enabled: Boolean) {
        if (enabled && stash.customArtworkUrl.isBlank()) return
        if (stash.customArtworkEnabled == enabled) return
        stash.customArtworkEnabled = enabled
        nudgeBridge()
    }

    fun setBitmapArtworkUploadEnabled(enabled: Boolean) {
        if (stash.bitmapArtworkUploadEnabled == enabled) return
        stash.bitmapArtworkUploadEnabled = enabled
        if (enabled) {
            artRegistry.artworkHostingChanged()
            nudgeBridge()
        }
    }

    fun setLocalArtworkFallbackEnabled(enabled: Boolean) {
        if (stash.localArtworkFallbackEnabled == enabled) return
        stash.localArtworkFallbackEnabled = enabled
        artRegistry.artworkHostingChanged()
        nudgeBridge()
    }

    fun saveCatboxUserHash(value: String) {
        if (value.isBlank()) return
        stash.catboxUserHash = value
        artRegistry.artworkHostingChanged()
        nudgeBridge()
    }

    fun clearCatboxUserHash() {
        if (!stash.hasCatboxUserHash) return
        stash.clearCatboxUserHash()
        nudgeBridge()
    }

    fun uploadCustomArtwork(uri: Uri) {
        if (customArtworkJob?.isActive == true) return
        customArtworkJob = viewModelScope.launch {
            customArtworkUploading.value = true
            customArtworkError.value = null
            try {
                val url = externalArtworkHost.uploadCustomArtwork(uri)
                stash.customArtworkUrl = url
                stash.customArtworkEnabled = true
                nudgeBridge()
            } catch (cancelled: kotlinx.coroutines.CancellationException) {
                throw cancelled
            } catch (error: Exception) {
                customArtworkError.value = error.message ?: "Не удалось загрузить изображение"
            } finally {
                customArtworkUploading.value = false
            }
        }
    }

    fun setMobileBadge(on: Boolean) {
        stash.mobileBadge = on
        badgeRestartJob?.cancel()
        badgeRestartJob = viewModelScope.launch {
            delay(BADGE_RESTART_SETTLE_MS)
            if (BridgeService.alive.value) {
                val intent = Intent(appContext, BridgeService::class.java)
                appContext.stopService(intent)
                appContext.startForegroundService(intent)
            }
        }
    }

    fun setTitleMode(mode: TitleMode) {
        stash.showAppName = mode == TitleMode.APP
        stash.artistAsTitle = mode == TitleMode.ARTIST
        nudgeBridge()
    }

    fun setBadgeMode(mode: BadgeMode) {
        if (mode != BadgeMode.GLYPH) stash.showPlaybackGlyph = false
        if (mode != BadgeMode.APP_ICON) stash.showAppIcon = false
        if (mode == BadgeMode.GLYPH) stash.showPlaybackGlyph = true
        if (mode == BadgeMode.APP_ICON) stash.showAppIcon = true
        nudgeBridge()
    }

    fun flipFacet(facet: ShareFacet) {
        val turningOn = !facetValue(facet)
        writeFacet(facet, turningOn)
        if (turningOn) facet.rival?.let { writeFacet(it, false) }
        nudgeBridge()
    }

    fun flipPlayer(packageName: String) {
        stash.togglePlayer(packageName)
        nudgeBridge()
    }

    private fun facetValue(facet: ShareFacet): Boolean = when (facet) {
        ShareFacet.ARTIST -> stash.showArtist
        ShareFacet.ALBUM -> stash.showAlbum
        ShareFacet.TIMELINE -> stash.showTimeline
        ShareFacet.PLAYBACK_GLYPH -> stash.showPlaybackGlyph
        ShareFacet.APP_ICON -> stash.showAppIcon
        ShareFacet.HIDE_PAUSED -> stash.hideWhenPaused
        ShareFacet.APP_NAME -> stash.showAppName
        ShareFacet.ARTIST_AS_TITLE -> stash.artistAsTitle
    }

    private fun writeFacet(facet: ShareFacet, value: Boolean) = when (facet) {
        ShareFacet.ARTIST -> stash.showArtist = value
        ShareFacet.ALBUM -> stash.showAlbum = value
        ShareFacet.TIMELINE -> stash.showTimeline = value
        ShareFacet.PLAYBACK_GLYPH -> stash.showPlaybackGlyph = value
        ShareFacet.APP_ICON -> stash.showAppIcon = value
        ShareFacet.HIDE_PAUSED -> stash.hideWhenPaused = value
        ShareFacet.APP_NAME -> stash.showAppName = value
        ShareFacet.ARTIST_AS_TITLE -> stash.artistAsTitle = value
    }

    private fun nudgeBridge() {
        if (BridgeService.alive.value) {
            appContext.startService(BridgeService.refreshIntent(appContext))
        }
    }

    private companion object {
        const val BADGE_RESTART_SETTLE_MS = 500L
    }
}
