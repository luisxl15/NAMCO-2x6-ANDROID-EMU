package com.armsx2

import com.armsx2.ui.premium.boardName
import com.armsx2.ui.premium.normalise
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * Reading a board out of the four ways the project spells it.
 *
 * The tracker writes a bare "246", a manifest writes `platform=246`, and the notes say things
 * like "System 256" and "246C". A badge that reads any of them as the wrong board would be worse
 * than no badge: the board is what decides which BIOS boots the game.
 */
class BoardBadgeTest {

    @Test
    fun `the tracker's own spelling`() {
        assertEquals("246", normalise("246"))
        assertEquals("256", normalise("256"))
    }

    @Test
    fun `the other spellings mean the same two boards`() {
        assertEquals("256", normalise("System 256"))
        assertEquals("246", normalise("246C"))
        assertEquals("246", normalise("platform=246"))
    }

    @Test
    fun `a game that wants the newer board is not read as the older one`() {
        assertEquals("256", normalise("246/256"))
    }

    @Test
    fun `nothing to go on means no badge at all`() {
        assertEquals("", normalise(null))
        assertEquals("", normalise(""))
        assertEquals("", normalise("   "))
        assertEquals("", normalise("HDD"))
    }

    @Test
    fun `the meta line names the board, or admits it does not know`() {
        assertEquals("NAMCO System 246", boardName("246"))
        assertEquals("NAMCO System 256", boardName("256"))
        assertEquals("NAMCO System 246/256", boardName(null))
        assertEquals("NAMCO System 246/256", boardName("unknown"))
    }
}
