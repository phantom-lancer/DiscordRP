package net.spacealtctrl.discordrp.presence

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ArtworkUrlsTest {
    @Test
    fun expandsYandexImageSizePlaceholder() {
        assertEquals(
            "https://avatars.mds.yandex.net/get-music-content/123/400x400",
            ArtworkUrls.normalize(" https://avatars.mds.yandex.net/get-music-content/123/%% "),
        )
    }

    @Test
    fun keepsValidHttpsImageUrlIntact() {
        val url = "https://cdn.example.org/cover.jpg?size=512"
        assertEquals(url, ArtworkUrls.normalize(url))
    }

    @Test
    fun rejectsNonHttpsAndLocalMediaLocations() {
        assertNull(ArtworkUrls.normalize("http://cdn.example.org/cover.jpg"))
        assertNull(ArtworkUrls.normalize("content://media/external/images/1"))
        assertNull(ArtworkUrls.normalize("file:///sdcard/cover.jpg"))
    }

    @Test
    fun rejectsCredentialsAndNonstandardPorts() {
        assertNull(ArtworkUrls.normalize("https://user:pass@cdn.example.org/cover.jpg"))
        assertNull(ArtworkUrls.normalize("https://cdn.example.org:8443/cover.jpg"))
    }

    @Test
    fun rejectsMalformedAndOversizedUrls() {
        assertNull(ArtworkUrls.normalize("not a URL"))
        assertNull(ArtworkUrls.normalize("https://cdn.example.org/" + "x".repeat(4_100)))
    }
}
