package net.spacealtctrl.discordrp.settings

enum class CoverSource(val stored: String) {
    PLAYER("player"),
    MUSICBRAINZ("musicbrainz");

    val usesMusicBrainzFallback: Boolean get() = this == MUSICBRAINZ

    companion object {
        fun fromStored(value: String?): CoverSource = when (value) {
            "musicbrainz", "musicbrainz_deezer" -> MUSICBRAINZ
            else -> PLAYER
        }
    }
}
