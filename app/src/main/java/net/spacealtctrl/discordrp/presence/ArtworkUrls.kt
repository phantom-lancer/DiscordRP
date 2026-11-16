package net.spacealtctrl.discordrp.presence

import java.net.URI

internal object ArtworkUrls {
    private const val MAX_URL_LENGTH = 4_096

    fun normalize(raw: String?): String? {
        val source = raw?.trim()?.takeIf { it.isNotEmpty() && it.length <= MAX_URL_LENGTH }
            ?: return null
        val expanded = source.replace("%%", "400x400")
        val uri = runCatching { URI(expanded) }.getOrNull() ?: return null
        if (!"https".equals(uri.scheme, ignoreCase = true)) return null
        if (uri.host.isNullOrBlank() || uri.rawUserInfo != null) return null
        if (uri.port != -1 && uri.port != 443) return null
        return uri.toASCIIString()
    }
}
