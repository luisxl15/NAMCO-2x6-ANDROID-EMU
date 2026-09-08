package com.armsx2

import com.armsx2.data.library.ArcadeSram
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import java.nio.file.Files

/**
 * Copying the board's memory, and the one rule that must not be broken.
 *
 * A restore while the game runs is worse than no restore at all: the core keeps its own copy of
 * the SRAM in memory and flushes it on the next pause, so the file would go back to what it was
 * and the player would be told it worked.
 */
class ArcadeSramTest {

    private fun sram(bytes: Int = 32768, fill: Byte = 1): File {
        val dir = Files.createTempDirectory("sram").toFile()
        val file = File(File(dir, "NM00004"), "sram.bin")
        file.parentFile?.mkdirs()
        file.writeBytes(ByteArray(bytes) { fill })
        return file
    }

    @Test
    fun `a backup is a copy of what is on disk`() {
        val file = sram()
        val snap = ArcadeSram.backup(file).getOrThrow()

        assertTrue(snap.file.exists())
        assertEquals(32768L, snap.bytes)
        assertTrue(file.readBytes().contentEquals(snap.file.readBytes()))
        assertEquals(1, ArcadeSram.backups(file).size)
    }

    @Test
    fun `nothing written yet is not an error worth a file`() {
        val file = sram(bytes = 0)
        assertFalse(ArcadeSram.exists(file))
        assertTrue(ArcadeSram.backup(file).isFailure)
        assertTrue(ArcadeSram.backups(file).isEmpty())
    }

    @Test
    fun `a restore while the game runs is refused`() {
        val file = sram()
        val snap = ArcadeSram.backup(file).getOrThrow()
        file.writeBytes(ByteArray(32768) { 9 })

        val result = ArcadeSram.restore(file, snap, running = true)

        assertTrue(result.isFailure)
        // And the live file is untouched, which is the whole point of refusing.
        assertEquals(9.toByte(), file.readBytes()[0])
    }

    @Test
    fun `a restore with the game closed puts the bytes back`() {
        val file = sram()
        val snap = ArcadeSram.backup(file).getOrThrow()
        file.writeBytes(ByteArray(32768) { 9 })

        assertTrue(ArcadeSram.restore(file, snap, running = false).isSuccess)
        assertEquals(1.toByte(), file.readBytes()[0])
    }

    @Test
    fun `the exported name says which game and which day`() {
        val name = ArcadeSram.exportName("NM00004")
        assertTrue(name.startsWith("NM00004-"))
        assertTrue(name.endsWith(".bin"))
        assertTrue(ArcadeSram.exportName(null).startsWith("sram-"))
    }

    @Test
    fun `a game with no resolvable folder has nothing to back up`() {
        assertFalse(ArcadeSram.exists(null))
        assertTrue(ArcadeSram.backups(null).isEmpty())
        assertTrue(ArcadeSram.backup(null).isFailure)
    }
}
