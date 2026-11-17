package net.spacealtctrl.discordrp.presence

internal object HostedIcons {
    private const val YOUTUBE_MUSIC = "com.google.android.apps.youtube.music"
    private const val YOUTUBE = "com.google.android.youtube"

    private val availableIcons = setOf(
        "app.symfonik.music.player",
        "code.name.monkey.retromusic",
        "com.amazon.mp3",
        "com.apple.android.music",
        "com.aspiro.tidal",
        "com.google.android.apps.mediashell",
        "com.google.android.apps.youtube.kids",
        YOUTUBE_MUSIC,
        "com.google.android.apps.youtube.unplugged",
        "com.google.android.music",
        "com.google.android.videos",
        "com.google.android.youtube.googletv",
        YOUTUBE,
        "com.google.android.youtube.tv",
        "com.jrtstudio.AnotherMusicPlayer",
        "com.maxmpz.audioplayer",
        "com.mxtech.videoplayer.ad",
        "com.mxtech.videoplayer.pro",
        "com.netease.cloudmusic",
        "com.netflix.mediaclient",
        "com.pandora.android",
        "com.rhapsody",
        "com.sec.android.app.music",
        "com.sonyericsson.music",
        "com.soundcloud.android",
        "com.spotify.music",
        "com.tbig.playerpro",
        "deezer.android.app",
        "org.oxycblt.auxio",
        "org.videolan.vlc",
        "tv.twitch.android.app",
    )

    fun nameFor(packageName: String): String? {
        val canonical = when {
            packageName.endsWith(".android.apps.youtube.music") -> YOUTUBE_MUSIC
            packageName.endsWith(".android.youtube") -> YOUTUBE
            else -> packageName
        }
        return canonical.takeIf { it in availableIcons }
    }
}
