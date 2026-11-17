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
