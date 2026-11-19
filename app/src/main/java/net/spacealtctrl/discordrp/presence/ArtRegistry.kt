package net.spacealtctrl.discordrp.presence

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.util.DisplayMetrics
import dagger.hilt.android.qualifiers.ApplicationContext
import net.spacealtctrl.discordrp.discord.DiscordApi
import net.spacealtctrl.discordrp.log.AppLog
import net.spacealtctrl.discordrp.settings.Stash
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicInteger
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import javax.inject.Inject
import javax.inject.Singleton
import java.security.MessageDigest

sealed interface ArtSource {
    data class Remote(val url: String, val cacheKey: String) : ArtSource

    data class Cover(
        val cacheKey: String,
        val artist: String? = null,
        val albumArtist: String? = null,
        val album: String? = null,
        val title: String? = null,
        val durationMillis: Long? = null,
        val artworkUrl: String? = null,
        val artwork: Bitmap? = null,
    ) : ArtSource

    data class AppIcon(val packageName: String) : ArtSource

    data object DefaultIcon : ArtSource
}

@Singleton
class ArtRegistry @Inject constructor(
    @ApplicationContext private val context: Context,
    private val coverLookup: CoverLookup,
    private val externalArtworkHost: ExternalArtworkHost,
    private val discordApi: DiscordApi,
    private val stash: Stash,
    private val log: AppLog,
) {
    private val unavailableAt = ConcurrentHashMap<String, Long>()
    private val unavailableStreak = ConcurrentHashMap<String, Int>()
    private val uploadCheckedAt = ConcurrentHashMap<String, Long>()
    private val coverGeneration = AtomicInteger()
    private val assetMutex = Mutex()
    private val uploadScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val pendingBitmapUploads = ConcurrentHashMap.newKeySet<String>()
    private val _artworkAvailable = MutableSharedFlow<Unit>(extraBufferCapacity = 1)
    val artworkAvailable = _artworkAvailable.asSharedFlow()

    suspend fun resolve(source: ArtSource?): String? = when (source) {
        null -> null
        is ArtSource.Remote -> register(source.url, source.cacheKey, coverGeneration.get())
        is ArtSource.Cover -> resolveCover(source)
        is ArtSource.AppIcon -> resolveAppIcon(source)
        ArtSource.DefaultIcon -> register(defaultIconUrl(), "default:icon", coverGeneration.get())
    }

    private suspend fun resolveCover(cover: ArtSource.Cover): String? {
        val generation = coverGeneration.get()
        val directArtwork = ArtworkUrls.normalize(cover.artworkUrl)
        if (directArtwork != null) {
            val direct = store(directArtwork, cover.cacheKey, generation)
            if (direct != null) {
                forgetMiss(cover.cacheKey)
                unavailableStreak.remove(cover.cacheKey)
                return direct
            }
        }

        val cached = stash.coverAssets[cover.cacheKey]
        if (cached?.startsWith("mp:") == true) return cached
        if (ArtworkUrls.normalize(cached) != null) {
            store(cached!!, cover.cacheKey, generation)?.let { return it }
        }

        val bitmap = cover.artwork
        if (bitmap != null &&
            stash.bitmapArtworkUploadEnabled &&
            !unavailableIsFresh(cover.cacheKey)
        ) {
            queueBitmapUpload(cover, generation)
        }

        if (!stash.coverSource.usesMusicBrainzFallback ||
            !stash.powerMode.fetchesRemoteArt ||
            !externalArtworkHost.canUseUnmeteredNetwork()
        ) return null
        if (unavailableIsFresh(cover.cacheKey)) return null

        uploadedGroup(cover.cacheKey)?.let { groupId ->
            checkUpload(cover.cacheKey, groupId, generation)?.let { return it }
        }
        if (missIsFresh(cover.cacheKey)) return null

        val request = CoverRequest(
            artist = cover.artist,
            albumArtist = cover.albumArtist,
            album = cover.album,
            title = cover.title,
            durationMillis = cover.durationMillis,
        )
        return when (val result = coverLookup.cover(request)) {
            is CoverResult.Found -> {
                if (isStale(generation)) return null
                store(result.url, cover.cacheKey, generation)?.also { forgetMiss(cover.cacheKey) }
                    ?: markUnavailable(cover.cacheKey, generation)
            }
            is CoverResult.Missing -> {
                if (!isStale(generation)) rememberMiss(cover.cacheKey, result)
                null
            }
            CoverResult.Unavailable -> markUnavailable(cover.cacheKey, generation)
        }
    }

    private fun queueBitmapUpload(cover: ArtSource.Cover, generation: Int) {
        if (!pendingBitmapUploads.add(cover.cacheKey)) return
        uploadScope.launch {
            try {
                val bitmap = cover.artwork ?: return@launch
                val uploaded = externalArtworkHost.uploadBitmap(bitmap) ?: run {
                    deferBitmapUploadRetry(cover.cacheKey, generation)
                    return@launch
                }
                if (isStale(generation)) return@launch
                val registered = store(uploaded, cover.cacheKey, generation) ?: run {
                    deferBitmapUploadRetry(cover.cacheKey, generation)
                    return@launch
                }

                stash.coverAssets = stash.coverAssets + (cover.cacheKey to registered)
                forgetMiss(cover.cacheKey)
                unavailableStreak.remove(cover.cacheKey)
                _artworkAvailable.tryEmit(Unit)
            } catch (error: Exception) {
                deferBitmapUploadRetry(cover.cacheKey, generation)
                log.warn(
                    TAG,
                    "Could not upload bitmap artwork: " +
                        (error.message ?: error.javaClass.simpleName).take(240),
                )
            } finally {
                pendingBitmapUploads.remove(cover.cacheKey)
            }
        }
    }

    private fun deferBitmapUploadRetry(cacheKey: String, generation: Int) {
        markUnavailable(cacheKey, generation)
        if (!isStale(generation)) _artworkAvailable.tryEmit(Unit)
    }

    private fun markUnavailable(cacheKey: String, generation: Int): String? {
        if (!isStale(generation)) {
            unavailableAt[cacheKey] = System.currentTimeMillis()
            unavailableStreak.merge(cacheKey, 1) { streak, failure -> streak + failure }
        }
        return null
    }

    private fun isStale(generation: Int): Boolean = coverGeneration.get() != generation

    fun coverSourceChanged() {
        coverGeneration.incrementAndGet()
        unavailableAt.clear()
        unavailableStreak.clear()
        stash.coverDeezerMisses = emptyMap()
        if (!stash.coverSource.usesMusicBrainzFallback) stash.dropDeezerCovers()
    }

    fun artworkHostingChanged() {
        unavailableAt.clear()
        unavailableStreak.clear()
        uploadCheckedAt.clear()
    }

    private fun unavailableIsFresh(cacheKey: String, now: Long = System.currentTimeMillis()): Boolean {
        val failedAt = unavailableAt[cacheKey] ?: return false
        return now - failedAt in 0 until UNAVAILABLE_RETRY_MS
    }

    private fun missIsFresh(cacheKey: String, now: Long = System.currentTimeMillis()): Boolean {
        val missedAt = stash.coverMisses[cacheKey] ?: return false
        return now - missedAt in 0 until MISS_RETRY_MS
    }

    fun missingGroupFor(cacheKey: String): String? =
        stash.coverMissGroups[cacheKey]?.takeIf { uploadedGroup(cacheKey) == null }

    fun deezerAlbumFor(cacheKey: String): Long? =
        stash.coverDeezerAlbums[cacheKey]?.toLongOrNull()?.takeIf { albumMissingFor(cacheKey) }

    fun albumMissingFor(cacheKey: String): Boolean =
        cacheKey in stash.coverAlbumMisses && stash.coverMissGroups[cacheKey] == null

    fun noteNewRelease(cacheKey: String, groupId: String) {
        stash.coverMissGroups = stash.coverMissGroups + (cacheKey to groupId)
        stash.coverDeezerAlbums = stash.coverDeezerAlbums - cacheKey
        stash.coverAlbumMisses = stash.coverAlbumMisses - cacheKey
    }

    fun coverPending(cacheKey: String): Boolean =
        (unavailableIsFresh(cacheKey) && unavailableStreak.getOrDefault(cacheKey, 0) < UNAVAILABLE_RETRIES) ||
            uploadedGroup(cacheKey) != null

    fun noteUpload(cacheKey: String) {
        if (stash.coverMissGroups[cacheKey] == null) return
        stash.coverUploads = stash.coverUploads + (cacheKey to System.currentTimeMillis())
        uploadCheckedAt.remove(cacheKey)
    }

    private suspend fun checkUpload(cacheKey: String, groupId: String, generation: Int): String? {
        val now = System.currentTimeMillis()
        val lastCheck = uploadCheckedAt[cacheKey]
        if (lastCheck != null && now - lastCheck in 0 until UPLOAD_RECHECK_MS) return null
        uploadCheckedAt[cacheKey] = now
        val found = coverLookup.frontFor(groupId) as? CoverResult.Found ?: return null
        val reference = store(found.url, cacheKey, generation) ?: return null
        forgetMiss(cacheKey)
        return reference
    }

    private fun uploadedGroup(cacheKey: String, now: Long = System.currentTimeMillis()): String? {
        val uploadedAt = stash.coverUploads[cacheKey] ?: return null
        if (now - uploadedAt !in 0 until UPLOAD_WATCH_MS) return null
        return stash.coverMissGroups[cacheKey]
    }

    private fun rememberMiss(cacheKey: String, miss: CoverResult.Missing) {
        stash.coverMisses = stash.coverMisses + (cacheKey to System.currentTimeMillis())
        stash.coverMissGroups = if (miss.groupId != null) {
            stash.coverMissGroups + (cacheKey to miss.groupId)
        } else {
            stash.coverMissGroups - cacheKey
        }
        stash.coverAlbumMisses = if (miss.albumMissing) {
            stash.coverAlbumMisses + cacheKey
        } else {
            stash.coverAlbumMisses - cacheKey
        }
    }

    private fun forgetMiss(cacheKey: String) {
        val tracked = cacheKey in stash.coverMisses ||
            cacheKey in stash.coverMissGroups ||
            cacheKey in stash.coverUploads ||
            cacheKey in stash.coverDeezerAlbums ||
            cacheKey in stash.coverAlbumMisses ||
            cacheKey in stash.coverDeezerAssets ||
            cacheKey in stash.coverDeezerMisses
        if (!tracked) return
        stash.coverMisses = stash.coverMisses - cacheKey
        stash.coverMissGroups = stash.coverMissGroups - cacheKey
        stash.coverUploads = stash.coverUploads - cacheKey
        stash.coverDeezerAlbums = stash.coverDeezerAlbums - cacheKey
        stash.coverAlbumMisses = stash.coverAlbumMisses - cacheKey
        stash.coverDeezerAssets = stash.coverDeezerAssets - cacheKey
        stash.coverDeezerMisses = stash.coverDeezerMisses - cacheKey
        uploadCheckedAt.remove(cacheKey)
    }

    private suspend fun resolveAppIcon(icon: ArtSource.AppIcon): String? {
        val name = HostedIcons.nameFor(icon.packageName) ?: return register(
            defaultIconUrl(),
            "default:icon",
            coverGeneration.get(),
        )
        return register(
            ICON_BASE + "/" + name + ".png?v=" + ICON_REVISION,
            "app-icon:" + icon.packageName,
            coverGeneration.get(),
        )
    }

    private fun defaultIconUrl(): String = ICON_BASE + "/discordrp.png?v=" + ICON_REVISION

    private suspend fun register(url: String, cacheKey: String, generation: Int): String? =
        store(url, cacheKey, generation)

    private suspend fun store(url: String, cacheKey: String, generation: Int): String? {
        val direct = ArtworkUrls.normalize(url) ?: return null
        if (isStale(generation)) return null

        val urlKey = "asset:url:" + sha256(direct)
        stash.coverAssets[urlKey]?.takeIf { it.startsWith("mp:") }?.let { cached ->
            stash.coverAssets = stash.coverAssets + (cacheKey to cached)
            return cached
        }

        return assetMutex.withLock {
            if (isStale(generation)) return@withLock null
            stash.coverAssets[urlKey]?.takeIf { it.startsWith("mp:") }?.let { cached ->
                stash.coverAssets = stash.coverAssets + (cacheKey to cached)
                return@withLock cached
            }

            val registered = discordApi.hostedAssetFor(direct).getOrElse { error ->
                log.warn(TAG, "Discord external artwork registration failed: " + error.javaClass.simpleName)
                return@withLock null
            }
            if (!registered.startsWith("mp:") || registered.removePrefix("mp:").isBlank()) {
                log.warn(TAG, "Discord returned an invalid external artwork reference")
                return@withLock null
            }
            if (isStale(generation)) return@withLock null

            stash.coverAssets = stash.coverAssets +
                (urlKey to registered) +
                (cacheKey to registered)
            unavailableStreak.remove(cacheKey)
            registered
        }
    }

    private fun sha256(value: String): String =
        MessageDigest.getInstance("SHA-256")
            .digest(value.toByteArray(Charsets.UTF_8))
            .joinToString("") { byte -> "%02x".format(byte) }

    fun iconBitmap(packageName: String): Bitmap? = runCatching {
        val pm = context.packageManager
        val info = pm.getApplicationInfo(packageName, 0)
        val drawable = runCatching {
            pm.getResourcesForApplication(info)
                .getDrawableForDensity(info.icon, DisplayMetrics.DENSITY_XXXHIGH, null)
        }.getOrNull() ?: pm.getApplicationIcon(info)

        val width = drawable.intrinsicWidth.coerceAtLeast(1)
        val height = drawable.intrinsicHeight.coerceAtLeast(1)
        Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888).also { bitmap ->
            val canvas = Canvas(bitmap)
            drawable.setBounds(0, 0, canvas.width, canvas.height)
            drawable.draw(canvas)
        }
    }.getOrNull()

    private companion object {
        const val TAG = "ArtRegistry"

        const val ICON_BASE =
            "https://raw.githubusercontent.com/phantom-lancer/DiscordRP/main/assets/icons"
        const val ICON_REVISION = 5

        const val MISS_RETRY_MS = 5 * 60 * 1000L
        const val UNAVAILABLE_RETRY_MS = 2 * 60 * 1000L
        const val UNAVAILABLE_RETRIES = 3
        const val UPLOAD_RECHECK_MS = 150 * 1000L
        const val UPLOAD_WATCH_MS = 60 * 60 * 1000L
    }
}
