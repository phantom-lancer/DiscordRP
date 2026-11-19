package net.spacealtctrl.discordrp.presence

import org.junit.Assert.assertEquals
import org.junit.Test

class ActivityLinesTest {
    private fun arrange(showAppName: Boolean = false, artistAsTitle: Boolean = false) =
        PresenceComposer.arrangeLines(
            title = "Karry On",
            artist = "Morning Glory",
            album = "War Psalms",
            appLabel = "Symfonium",
            showAppName = showAppName,
            artistAsTitle = artistAsTitle,
        )

    @Test
    fun `artist prefix is removed from formatted track title`() {
        assertEquals("Karry On", normalizeTrackTitle("Morning Glory - Karry On", "Morning Glory"))
    }

    @Test
    fun `artist suffix is removed from formatted track title`() {
        assertEquals("Karry On", normalizeTrackTitle("Karry On - Morning Glory", "Morning Glory"))
    }

    @Test
    fun `track title is unchanged when artist is not a complete title segment`() {
        assertEquals("Karry On (Morning Glory Remix)", normalizeTrackTitle("Karry On (Morning Glory Remix)", "Morning Glory"))
    }

    @Test
    fun `artist comparison ignores invisible characters`() {
        assertEquals("Karry On", normalizeTrackTitle("Morning Glory - Karry On", "Morning\u200B Glory"))
    }

    @Test
    fun `artist metadata never repeats the track title`() {
        assertEquals(
            "Correct Artist",
            distinctMetadataText("Track Name", " Track Name ", "Correct Artist"),
        )
    }

    @Test
    fun `duplicate artist candidates are ignored`() {
        assertEquals(
            null,
            distinctMetadataText("Track Name", "Track Name", " track name ", " "),
        )
    }

    @Test
    fun `metadata comparison ignores invisible characters and compatibility spacing`() {
        assertEquals(
            "Correct Artist",
            distinctMetadataText("Song Name", " S\u200Bo\u200Bng Name ", "Correct Artist"),
        )
    }

    @Test
    fun `album artist is used when track artist metadata is missing`() {
        assertEquals(
            "Album Artist",
            resolveArtistMetadata(
                title = "Track Name",
                displaySubtitle = "Track Name",
                artist = null,
                albumArtist = "Album Artist",
                author = null,
            ),
        )
    }

    @Test
    fun `generic display description is not mistaken for an artist`() {
        assertEquals(
            null,
            resolveArtistMetadata(
                title = "Track Name",
                displaySubtitle = "Track Name",
                artist = null,
                albumArtist = null,
                author = null,
            ),
        )
    }

    @Test
    fun `display title takes precedence over legacy title`() {
        assertEquals("Clean Display Title", preferredTrackTitle("Clean Display Title", "Legacy Title"))
        assertEquals("Legacy Title", preferredTrackTitle(null, "Legacy Title"))
    }

    @Test
    fun `display subtitle takes precedence over a composite artist field`() {
        assertEquals(
            "Correct Artist",
            resolveArtistMetadata(
                title = "Track Name",
                displaySubtitle = "Correct Artist",
                artist = "Track Name - Wrong Artist",
                albumArtist = "Album Artist",
                author = null,
            ),
        )
    }

    @Test
    fun `track title is removed from a composite subtitle before using it as artist`() {
        assertEquals(
            "Correct Artist",
            resolveArtistMetadata(
                title = "Track Name",
                displaySubtitle = "Track Name - Correct Artist",
                artist = null,
                albumArtist = null,
                author = null,
            ),
        )
    }

    @Test
    fun `artist candidate containing track title is reduced to its artist segment`() {
        assertEquals(
            "Correct Artist",
            normalizeArtistMetadataCandidate("Track Name", "Track Name • Correct Artist"),
        )
    }

    @Test
    fun `bullet separated artist prefix is removed from formatted title`() {
        assertEquals("Track Name", normalizeTrackTitle("Correct Artist • Track Name", "Correct Artist"))
    }

    @Test
    fun `both title layouts show artist and track exactly once with composite metadata`() {
        val title = normalizeTrackTitle("Correct Artist - Track Name", "Correct Artist")
        val artist = resolveArtistMetadata(
            title = title,
            displaySubtitle = "Correct Artist",
            artist = "Correct Artist - Track Name",
            albumArtist = null,
            author = null,
        )

        val artistHeading = PresenceComposer.arrangeLines(
            title = title,
            artist = PresenceComposer.artistForLayout(
                title = title,
                artist = artist,
                albumArtist = null,
                showArtist = false,
                artistAsTitle = true,
            ),
            album = null,
            appLabel = "Yandex Music",
            showAppName = false,
            artistAsTitle = true,
        )
        assertEquals("Correct Artist", artistHeading.name)
        assertEquals("Track Name", artistHeading.details)
        assertEquals(null, artistHeading.state)

        val trackHeading = PresenceComposer.arrangeLines(
            title = title,
            artist = PresenceComposer.artistForLayout(
                title = title,
                artist = artist,
                albumArtist = null,
                showArtist = true,
                artistAsTitle = false,
            ),
            album = null,
            appLabel = "Yandex Music",
            showAppName = false,
            artistAsTitle = false,
        )
        assertEquals("Track Name", trackHeading.name)
        assertEquals("Correct Artist", trackHeading.details)
        assertEquals(null, trackHeading.state)
    }

    @Test
    fun `default layout headlines the track and hides the player`() {
        val lines = arrange()
        assertEquals("Karry On", lines.name)
        assertEquals("Morning Glory", lines.details)
        assertEquals("War Psalms", lines.state)
        assertEquals(null, lines.largeText)
    }

    @Test
    fun `optional player name uses app as title`() {
        val lines = arrange(showAppName = true)
        assertEquals("Symfonium", lines.name)
        assertEquals("Karry On", lines.details)
        assertEquals("Morning Glory", lines.state)
        assertEquals("War Psalms", lines.largeText)
    }

    @Test
    fun `default track layout stays independent of player name`() {
        val lines = arrange(showAppName = false)
        assertEquals("Karry On", lines.name)
        assertEquals("Morning Glory", lines.details)
        assertEquals("War Psalms", lines.state)
        assertEquals(null, lines.largeText)
    }

    @Test
    fun `artist title mode keeps artist visible when artist facet is off`() {
        assertEquals(
            "Morning Glory",
            PresenceComposer.artistForLayout(
                title = "Karry On",
                artist = "Morning Glory",
                albumArtist = null,
                showArtist = false,
                artistAsTitle = true,
            ),
        )
    }

    @Test
    fun `artist title mode falls back to album artist`() {
        assertEquals(
            "Morning Glory",
            PresenceComposer.artistForLayout(
                title = "Karry On",
                artist = null,
                albumArtist = "Morning Glory",
                showArtist = false,
                artistAsTitle = true,
            ),
        )
    }

    @Test
    fun `artist title mode rejects track title as artist`() {
        val artist = PresenceComposer.artistForLayout(
            title = "Karry On",
            artist = "Karry On",
            albumArtist = "Karry On",
            showArtist = false,
            artistAsTitle = true,
        )
        val lines = PresenceComposer.arrangeLines(
            title = "Karry On",
            artist = artist,
            album = null,
            appLabel = "Symfonium",
            showAppName = false,
            artistAsTitle = true,
        )
        assertEquals("Karry On", lines.name)
        assertEquals(null, lines.details)
    }

    @Test
    fun `artist layout headlines the artist`() {
        val lines = arrange(artistAsTitle = true)
        assertEquals("Morning Glory", lines.name)
        assertEquals("Karry On", lines.details)
        assertEquals("War Psalms", lines.state)
        assertEquals(null, lines.largeText)
    }

    @Test
    fun `artist title layout does not repeat the track title as album`() {
        val lines = PresenceComposer.arrangeLines(
            title = "SOS",
            artist = "yukan archive",
            album = "SOS",
            appLabel = "Player",
            showAppName = false,
            artistAsTitle = true,
        )
        assertEquals("yukan archive", lines.name)
        assertEquals("SOS", lines.details)
        assertEquals(null, lines.state)
    }

    @Test
    fun `normal layout does not repeat artist as album`() {
        val lines = PresenceComposer.arrangeLines(
            title = "SOS",
            artist = "yukan archive",
            album = "Yukan Archive",
            appLabel = "Player",
            showAppName = false,
            artistAsTitle = false,
        )
        assertEquals("SOS", lines.name)
        assertEquals("yukan archive", lines.details)
        assertEquals(null, lines.state)
    }

    @Test
    fun `artist title mode removes punctuation variants of the same track from album line`() {
        val lines = PresenceComposer.arrangeLines(
            title = "SOS",
            artist = "yukan archive",
            album = "S.O.S.",
            appLabel = "Player",
            showAppName = false,
            artistAsTitle = true,
        )
        assertEquals("yukan archive", lines.name)
        assertEquals("SOS", lines.details)
        assertEquals(null, lines.state)
    }

    @Test
    fun `duplicate lines are removed after Discord 128 character clipping`() {
        val title = "A".repeat(128) + " track suffix"
        val album = "A".repeat(128) + " album suffix"
        val lines = PresenceComposer.arrangeLines(
            title = title,
            artist = "yukan archive",
            album = album,
            appLabel = "Player",
            showAppName = false,
            artistAsTitle = false,
        )
        assertEquals("A".repeat(128), lines.name)
        assertEquals("yukan archive", lines.details)
        assertEquals(null, lines.state)
    }

    @Test
    fun `artist layout wins when both are set`() {
        val lines = arrange(showAppName = true, artistAsTitle = true)
        assertEquals("Morning Glory", lines.name)
    }

    @Test
    fun `artist layout falls back to the track when artist is missing`() {
        val lines = PresenceComposer.arrangeLines(
            title = "Karry On",
            artist = null,
            album = null,
            appLabel = "Symfonium",
            showAppName = false,
            artistAsTitle = true,
        )
        assertEquals("Karry On", lines.name)
        assertEquals(null, lines.details)
        assertEquals(null, lines.state)
        assertEquals(null, lines.largeText)
    }
}
