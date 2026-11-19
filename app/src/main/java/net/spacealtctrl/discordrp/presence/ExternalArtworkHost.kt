package net.spacealtctrl.discordrp.presence

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.ConnectivityManager
import android.net.Uri
import android.os.Build
import dagger.hilt.android.qualifiers.ApplicationContext
import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.plugins.timeout
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.request.forms.formData
import io.ktor.client.request.forms.submitFormWithBinaryData
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.Headers
import io.ktor.http.HttpHeaders
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.content
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import java.io.ByteArrayOutputStream
import net.spacealtctrl.discordrp.BuildConfig
import net.spacealtctrl.discordrp.R
import net.spacealtctrl.discordrp.settings.Stash
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class ExternalArtworkHost @Inject constructor(
    @ApplicationContext private val context: Context,
    private val http: HttpClient,
    private val stash: Stash,
) {
    suspend fun uploadCustomArtwork(uri: Uri): String = withContext(Dispatchers.IO) {
        val bitmap = decodeUri(uri) ?: error("Не удалось прочитать выбранное изображение")
        try {
            uploadJpeg(compress(bitmap))
        } finally {
            bitmap.recycle()
        }
    }

    suspend fun uploadBitmap(bitmap: Bitmap): String? {
        if (!hasActiveNetwork()) return null
        val bytes = withContext(Dispatchers.Default) { compress(bitmap) }
        if (!hasActiveNetwork()) return null
        return uploadJpeg(bytes)
    }

    fun canUseUnmeteredNetwork(): Boolean = runCatching {
        val manager = context.getSystemService(ConnectivityManager::class.java)
            ?: return@runCatching false
        manager.activeNetwork != null && !manager.isActiveNetworkMetered
    }.getOrDefault(false)

    private fun hasActiveNetwork(): Boolean = runCatching {
        context.getSystemService(ConnectivityManager::class.java)?.activeNetwork != null
    }.getOrDefault(false)

    private fun decodeUri(uri: Uri): Bitmap? {
        val resolver = context.contentResolver
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        val boundsStream = resolver.openInputStream(uri) ?: return null
        boundsStream.use { BitmapFactory.decodeStream(it, null, bounds) }
        if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return null

        var sample = 1
        while (bounds.outWidth / sample > MAX_SIDE * 2 || bounds.outHeight / sample > MAX_SIDE * 2) {
            sample *= 2
        }
        val options = BitmapFactory.Options().apply { inSampleSize = sample }
        return resolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, options) }
    }

    private fun compress(source: Bitmap): ByteArray {
        val sourceMax = maxOf(source.width, source.height).coerceAtLeast(1)
        for (side in intArrayOf(MAX_SIDE, 512, 384)) {
            val scale = minOf(1f, side.toFloat() / sourceMax)
            val bitmap = if (scale < 1f) {
                Bitmap.createScaledBitmap(
                    source,
                    (source.width * scale).toInt().coerceAtLeast(1),
                    (source.height * scale).toInt().coerceAtLeast(1),
                    true,
                )
            } else {
                source
            }
            try {
                for (quality in intArrayOf(JPEG_QUALITY, 72, 62)) {
                    val output = ByteArrayOutputStream()
                    bitmap.compress(Bitmap.CompressFormat.JPEG, quality, output)
                    val bytes = output.toByteArray()
                    if (bytes.isNotEmpty() && bytes.size <= MAX_UPLOAD_BYTES) return bytes
                }
            } finally {
                if (bitmap !== source) bitmap.recycle()
            }
        }
        error("Изображение не удалось сжать до допустимого размера")
    }

    /**
     * Catbox is the preferred host. If it rejects anonymous uploads on this network,
     * transparently try Pomf instead of failing the entire artwork flow.
     */
    private suspend fun uploadJpeg(bytes: ByteArray): String {
        require(bytes.isNotEmpty() && bytes.size <= MAX_UPLOAD_BYTES) {
            "Размер изображения превышает лимит"
        }

        val catboxFailure = try {
            return uploadToCatbox(bytes)
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (error: Exception) {
            error
        }

        return try {
            uploadToPomf(bytes)
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (fallbackError: Exception) {
            val primary = catboxFailure.message.orEmpty().take(180)
            val fallback = fallbackError.message.orEmpty().take(180)
            error("Catbox: $primary. Резервный хост Pomf тоже не принял изображение: $fallback")
        }
    }

    private suspend fun uploadToCatbox(bytes: ByteArray): String {
        val userHash = stash.catboxUserHash
        val response = http.submitFormWithBinaryData(
            url = CATBOX_API,
            formData = formData {
                append("reqtype", "fileupload")
                if (userHash.isNotBlank()) append("userhash", userHash)
                append(
                    "fileToUpload",
                    bytes,
                    Headers.build {
                        append(
                            HttpHeaders.ContentDisposition,
                            "form-data; name=\"fileToUpload\"; filename=\"rp-music-cover.jpg\"",
                        )
                        append(HttpHeaders.ContentType, ContentType.Image.JPEG.toString())
                    },
                )
            },
        ) {
            header(
                HttpHeaders.UserAgent,
                "DiscordRP/${BuildConfig.VERSION_NAME} (Android ${Build.VERSION.SDK_INT})",
            )
        }
        val body = response.bodyAsText().trim()
        if (response.status.value == 412) {
            val details = body.replace(Regex("\\s+"), " ").take(220)
                .ifBlank { context.getString(R.string.catbox_empty_error_body) }
            val trace = sequenceOf(
                response.headers["cf-ray"],
                response.headers["x-request-id"],
                response.headers["request-id"],
            ).firstOrNull { !it.isNullOrBlank() }
                ?: context.getString(R.string.catbox_trace_unavailable)
            error(context.getString(R.string.catbox_http_412_detail, details, trace))
        }
        check(response.status.value in 200..299) {
            "Catbox ответил HTTP ${response.status.value}: " +
                body.replace(Regex("\\s+"), " ").take(200)
        }
        return validateHostedUrl(
            body,
            allowedHosts = setOf(CATBOX_HOST),
            pathPattern = Regex("/[A-Za-z0-9]+\\.jpg"),
        )
    }

    private suspend fun uploadToPomf(bytes: ByteArray): String {
        val response = http.submitFormWithBinaryData(
            url = POMF_API,
            formData = formData {
                append(
                    "files[]",
                    bytes,
                    Headers.build {
                        append(
                            HttpHeaders.ContentDisposition,
                            "form-data; name=\"files[]\"; filename=\"rp-music-cover.jpg\"",
                        )
                        append(HttpHeaders.ContentType, ContentType.Image.JPEG.toString())
                    },
                )
            },
        )
        val body = response.bodyAsText().trim()
        check(response.status.value in 200..299) {
            "Pomf ответил HTTP ${response.status.value}: " +
                body.replace(Regex("\\s+"), " ").take(200)
        }

        val payload = runCatching { Json.parseToJsonElement(body).jsonObject }
            .getOrElse { error("Pomf вернул некорректный ответ") }
        check(payload["success"]?.jsonPrimitive?.booleanOrNull == true) {
            "Pomf отклонил загрузку: ${body.replace(Regex("\\s+"), " ").take(180)}"
        }
        val imageUrl = payload["files"]?.jsonArray
            ?.firstOrNull()
            ?.jsonObject
            ?.get("url")
            ?.jsonPrimitive
            ?.content
            ?.trim()
            .orEmpty()

        return validateHostedUrl(
            imageUrl,
            allowedHosts = POMF_HOSTS,
            pathPattern = Regex("/f/[A-Za-z0-9]+\\.(?:jpg|jpeg|png|webp)"),
        )
    }

    /**
     * Re-host a player's direct artwork URL when Discord cannot fetch that URL itself.
     * This is the missing path for services that expose a URL but block Discord's fetcher.
     */
    suspend fun uploadArtworkUrl(rawUrl: String): String {
        val url = ArtworkUrls.normalize(rawUrl)
            ?: error("Плеер передал некорректную ссылку на обложку")
        val response = http.get(url) {
            timeout {
                requestTimeoutMillis = ARTWORK_FETCH_TIMEOUT_MS
                socketTimeoutMillis = ARTWORK_FETCH_TIMEOUT_MS
            }
            header(
                HttpHeaders.UserAgent,
                "DiscordRP/${BuildConfig.VERSION_NAME} (Android ${Build.VERSION.SDK_INT})",
            )
        }
        check(response.status.value in 200..299) {
            "Не удалось скачать обложку плеера (HTTP ${response.status.value})"
        }

        val declaredLength = response.headers[HttpHeaders.ContentLength]?.toLongOrNull()
        require(declaredLength == null || declaredLength in 1..MAX_SOURCE_ART_BYTES.toLong()) {
            "Обложка плеера слишком большая"
        }
        val sourceBytes: ByteArray = response.body()
        require(sourceBytes.isNotEmpty() && sourceBytes.size <= MAX_SOURCE_ART_BYTES) {
            "Обложка плеера пустая или слишком большая"
        }

        val bitmap = decodeArtworkBytes(sourceBytes)
            ?: error("Не удалось распознать изображение обложки плеера")
        return try {
            uploadJpeg(compress(bitmap))
        } finally {
            bitmap.recycle()
        }
    }

    private fun decodeArtworkBytes(bytes: ByteArray): Bitmap? {
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeByteArray(bytes, 0, bytes.size, bounds)
        if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return null

        var sample = 1
        while (
            bounds.outWidth / sample > MAX_SIDE * 2 ||
            bounds.outHeight / sample > MAX_SIDE * 2
        ) {
            sample *= 2
        }
        return BitmapFactory.decodeByteArray(
            bytes,
            0,
            bytes.size,
            BitmapFactory.Options().apply { inSampleSize = sample },
        )
    }

    private fun validateHostedUrl(
        raw: String,
        allowedHosts: Set<String>,
        pathPattern: Regex,
    ): String {
        val uri = runCatching { java.net.URI(raw.trim()) }.getOrNull()
            ?: error("Хостинг не вернул корректную ссылку на изображение")
        check(
            uri.scheme == "https" &&
                uri.host in allowedHosts &&
                uri.path.orEmpty().matches(pathPattern),
        ) {
            "Хостинг не вернул корректную ссылку на изображение"
        }
        return uri.toASCIIString()
    }

    private companion object {
        const val CATBOX_API = "https://catbox.moe/user/api.php"
        const val CATBOX_HOST = "files.catbox.moe"
        const val POMF_API = "https://pomf.lain.la/upload.php"
        val POMF_HOSTS = setOf("pomf.lain.la", "pomf2.lain.la")
        const val MAX_SIDE = 640
        const val JPEG_QUALITY = 82
        const val MAX_UPLOAD_BYTES = 256 * 1024
        const val MAX_SOURCE_ART_BYTES = 12 * 1024 * 1024
        const val ARTWORK_FETCH_TIMEOUT_MS = 15_000L
    }
}
