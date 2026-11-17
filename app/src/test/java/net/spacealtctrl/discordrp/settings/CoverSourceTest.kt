package net.spacealtctrl.discordrp.settings

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class CoverSourceTest {
    @Test
    fun `direct player artwork is the default`() {
        assertEquals(CoverSource.PLAYER, CoverSource.fromStored(null))
        assertEquals(CoverSource.PLAYER, CoverSource.fromStored("player"))
        assertFalse(CoverSource.PLAYER.usesMusicBrainzFallback)
    }

    @Test
    fun `legacy MusicBrainz preferences are preserved without Deezer`() {
        assertEquals(CoverSource.MUSICBRAINZ, CoverSource.fromStored("musicbrainz"))
        assertEquals(CoverSource.MUSICBRAINZ, CoverSource.fromStored("musicbrainz_deezer"))
        assertEquals(CoverSource.MUSICBRAINZ, CoverSource.fromStored("deezer"))
        assertTrue(CoverSource.MUSICBRAINZ.usesMusicBrainzFallback)
    }

    @Test
    fun `stored choices come back`() {
        for (source in CoverSource.entries) assertEquals(source, CoverSource.fromStored(source.stored))
    }
}
