package net.spacealtctrl.discordrp.presence

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.ConnectivityManager
import android.net.Uri
import dagger.hilt.android.qualifiers.ApplicationContext
import io.ktor.client.HttpClient
import io.ktor.client.request.forms.formData
import io.ktor.client.request.forms.submitFormWithBinaryData
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.Headers
import io.ktor.http.HttpHeaders
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class ExternalArtworkHost @Inject constructor(
    @ApplicationContext private val context: Context,
    private val http: HttpClient,
) {
    suspend fun uploadCustomArtwork(uri: Uri): String = withContext(Dispatchers.IO) {
        val bitmap = decodeUri(uri) ?: error("Не удалось прочитать выбранное изображение")
        try {
            uploadJpeg(compress(bitmap))
        } finally {
            bitmap.recycle()
        }
    }

    suspend fun uploadBitmapOnUnmeteredNetwork(bitmap: Bitmap): String? {
        if (!isUnmeteredNetwork()) return null
        val bytes = withContext(Dispatchers.Default) { compress(bitmap) }
        if (!isUnmeteredNetwork()) return null
        return uploadJpeg(bytes)
    }

    fun canUseUnmeteredNetwork(): Boolean = isUnmeteredNetwork()

    private fun isUnmeteredNetwork(): Boolean = runCatching {
        val manager = context.getSystemService(ConnectivityManager::class.java)
            ?: return@runCatching false
        manager.activeNetwork != null && !manager.isActiveNetworkMetered
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

    private suspend fun uploadJpeg(bytes: ByteArray): String {
        require(bytes.isNotEmpty() && bytes.size <= MAX_UPLOAD_BYTES) {
            "Размер изображения превышает лимит"
        }
        val response = http.submitFormWithBinaryData(
            url = CATBOX_API,
            formData = formData {
                append("reqtype", "fileupload")
                append(
                    "fileToUpload",
                    bytes,
                    Headers.build {
                        append(HttpHeaders.ContentDisposition, "filename=\"discordrp-cover.jpg\"")
                        append(HttpHeaders.ContentType, ContentType.Image.JPEG.toString())
                    },
                )
            },
        )
        check(response.status.value in 200..299) {
            "Хостинг изображения ответил HTTP ${response.status.value}"
        }
        val result = response.bodyAsText().trim()
        val uri = runCatching { java.net.URI(result) }.getOrNull()
        check(
            uri?.scheme == "https" &&
                uri.host == CATBOX_HOST &&
                uri.path.orEmpty().matches(Regex("/[A-Za-z0-9]+\\.jpg")),
        ) {
            "Хостинг не вернул корректную ссылку на изображение"
        }
        return result
    }

    private companion object {
        const val CATBOX_API = "https://catbox.moe/user/api.php"
        const val CATBOX_HOST = "files.catbox.moe"
        const val MAX_SIDE = 640
        const val JPEG_QUALITY = 82
        const val MAX_UPLOAD_BYTES = 256 * 1024
    }
}
