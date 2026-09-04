package com.armsx2

import com.armsx2.data.library.AcgameWizard
import com.armsx2.data.library.ArcadeBios
import com.armsx2.data.library.ArcadeCompat
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * The arcade layer's silent failures.
 *
 * FORK.md says a bad upstream merge breaks the boot chain first and breaks it quietly. These are
 * the parts of that chain which are ordinary Kotlin and can therefore be checked without a device
 * or a game: the manifest we write, the compatibility data we read, and the BIOS rule that stops
 * a known-bad boot. All three fail by producing nothing rather than by throwing, which is exactly
 * why they are worth a test — nothing is indistinguishable from "feature not implemented".
 */
class ArcadeTest {

    // ----------------------------------------------------------------- manifest

    private fun candidate(
        gameId: String = "NM00010",
        title: String? = null,
        media: String? = "NM00010.chd",
        dongle: String? = "NM00010.ps2",
        elf: String? = "proverb.elf",
        compat: ArcadeCompat.Entry? = null,
    ) = AcgameWizard.Candidate(
        parent = AcgameWizard.Location.Path(File("/tmp/roms")),
        folderName = gameId,
        gameId = gameId,
        title = title,
        media = media,
        dongle = dongle,
        elf = elf,
        compat = compat,
    )

    private fun entry(
        gameId: String,
        board: String,
        media: String = "",
        name: String = "Nome",
        status: ArcadeCompat.Status = ArcadeCompat.Status.Playable,
        note: String = "",
    ) = ArcadeCompat.Entry(gameId, name, status, board, media, note)

    @Test
    fun `manifest takes board and media from the compatibility list`() {
        // Battle Gear 3: the list says 246, and it is the game that refuses to boot on a 256
        // BIOS. Writing the default "256" here is the bug this test exists for.
        val text = AcgameWizard.manifestFor(
            candidate(compat = entry("NM00010", board = "246", media = "CD")),
        )
        assertTrue("platform=246 esperado, veio:\n$text", text.contains("platform=246"))
        assertTrue("media=CD esperado, veio:\n$text", text.contains("media=CD"))
        assertTrue(text.contains("gameid=NM00010"))
        assertTrue(text.contains("subdir=NM00010"))
        assertTrue(text.contains("dongle=NM00010.ps2"))
    }

    @Test
    fun `manifest falls back only when the list says nothing`() {
        val text = AcgameWizard.manifestFor(candidate(gameId = "NM09999", compat = null))
        assertTrue(text.contains("platform=256"))
        assertTrue(text.contains("media=DVD"))
    }

    @Test
    fun `manifest prefers the database title over the list name over the id`() {
        assertTrue(
            AcgameWizard.manifestFor(candidate(title = "Do banco")).contains("name=Do banco"),
        )
        assertTrue(
            AcgameWizard.manifestFor(candidate(compat = entry("NM00010", "246", name = "Da lista")))
                .contains("name=Da lista"),
        )
        assertTrue(AcgameWizard.manifestFor(candidate()).contains("name=NM00010"))
    }

    @Test
    fun `a candidate missing its dongle is not complete`() {
        assertTrue(candidate().complete)
        assertTrue(!candidate(dongle = null).complete)
        assertTrue(!candidate(elf = null).complete)
        assertTrue(!candidate(media = null).complete)
    }

    // ------------------------------------------------------- compatibility list

    @Test
    fun `compatibility entries parse, including the fields the wizard depends on`() {
        val parsed = ArcadeCompat.parseJson(
            """
            {"source":"t","games":{
              "NM00010":{"name":"Battle Gear 3","status":"PLAYABLE","board":"246","media":"",
                         "note":"Game Rejects system256 BIOS!"},
              "NM00014":{"name":"Dragon Chronicle","status":"ATTRACT","board":"246","media":"CD","note":""},
              "NM00028":{"name":"Druaga","status":"UNTESTED","board":"256","media":"","note":""},
              "NM09998":{"name":"Estranho","status":"WHAT","board":"","media":"","note":""}
            }}
            """.trimIndent(),
        )
        assertEquals(4, parsed.size)
        assertEquals(ArcadeCompat.Status.Playable, parsed["NM00010"]!!.status)
        assertEquals("246", parsed["NM00010"]!!.board)
        assertTrue(parsed["NM00010"]!!.note.contains("256"))
        assertEquals(ArcadeCompat.Status.Attract, parsed["NM00014"]!!.status)
        assertEquals(ArcadeCompat.Status.Untested, parsed["NM00028"]!!.status)
        // An unrecognised status must not drop the game or throw; it just has no badge.
        assertEquals(ArcadeCompat.Status.Unknown, parsed["NM09998"]!!.status)
    }

    @Test
    fun `malformed compatibility data yields nothing rather than throwing`() {
        assertTrue(ArcadeCompat.parseJson("not json at all").isEmpty())
        assertTrue(ArcadeCompat.parseJson("""{"games":null}""").isEmpty())
    }

    // ------------------------------------------------------------------- BIOS

    private val s256 = BiosInfo(0x0100, 8, "COH-H   System 256 20040519-145634", "COH-H")
    private val s246 = BiosInfo(0x0100, 8, "COH-H   System 246 20030227-102834", "COH-H")
    private val sonyCohH = BiosInfo(0x0100, 8, "COH-H Board (A-000-010) 20020207", "COH-H")
    private val console = BiosInfo(0x0160, 2, "Europe   20011004-175839", "Europe")

    @Test
    fun `boards are read out of what the core reports`() {
        assertEquals(ArcadeBios.Board.S256, ArcadeBios.boardOf(s256))
        assertEquals(ArcadeBios.Board.S246, ArcadeBios.boardOf(s246))
        assertEquals(ArcadeBios.Board.Console, ArcadeBios.boardOf(console))
    }

    @Test
    fun `a game with no rule is never touched`() {
        // Tekken 4 runs on the 256 BIOS. Swapping it would be the regression.
        assertEquals(
            ArcadeBios.Decision.Keep,
            ArcadeBios.decide("NM00004", mapOf("a.bin" to s256, "b.bin" to s246), "a.bin"),
        )
    }

    @Test
    fun `Battle Gear 3 moves off a System 256 BIOS when a 246 is installed`() {
        val d = ArcadeBios.decide("NM00010", mapOf("s256.bin" to s256, "s246.bin" to s246), "s256.bin")
        assertTrue("esperava Switch, veio $d", d is ArcadeBios.Decision.Switch)
        assertEquals("s246.bin", (d as ArcadeBios.Decision.Switch).fileName)
    }

    @Test
    fun `Battle Gear 3 on a 246 BIOS is already fine`() {
        assertEquals(
            ArcadeBios.Decision.Keep,
            ArcadeBios.decide("NM00010", mapOf("s246.bin" to s246), "s246.bin"),
        )
    }

    @Test
    fun `a console BIOS is never offered as the replacement`() {
        // It boots these boards even less well than the wrong arcade one, so "no usable BIOS" is
        // the honest answer rather than a swap that looks like a fix.
        val d = ArcadeBios.decide("NM00010", mapOf("s256.bin" to s256, "eu.bin" to console), "s256.bin")
        assertTrue("esperava NoneUsable, veio $d", d is ArcadeBios.Decision.NoneUsable)
    }

    @Test
    fun `Bloody Roar 3 rejects the official Sony COH-H image specifically`() {
        // Its note excludes one image, not a board: 246C and 256 are both fine.
        val d = ArcadeBios.decide("NM00002", mapOf("sony.bin" to sonyCohH, "s256.bin" to s256), "sony.bin")
        assertEquals("s256.bin", (d as ArcadeBios.Decision.Switch).fileName)
        assertEquals(
            ArcadeBios.Decision.Keep,
            ArcadeBios.decide("NM00002", mapOf("s256.bin" to s256), "s256.bin"),
        )
    }

    @Test
    fun `an unknown or absent current BIOS decides nothing`() {
        assertEquals(ArcadeBios.Decision.Keep, ArcadeBios.decide("NM00010", emptyMap(), null))
        assertEquals(
            ArcadeBios.Decision.Keep,
            ArcadeBios.decide(null, mapOf("s256.bin" to s256), "s256.bin"),
        )
    }

    // -------------------------------------------------- the data we actually ship

    /**
     * The bundled list, read off disk rather than through a fixture.
     *
     * Gradle runs unit tests with the module directory as the working directory, so the asset is
     * reachable without a device. Worth doing: every test above proves the code is right about a
     * fixture, and none of them would notice the shipped file being truncated, re-keyed, or
     * dropped by a merge.
     */
    private val shipped: Map<String, ArcadeCompat.Entry> by lazy {
        val f = File("src/main/assets/compat/arcade_compat.json")
        assertTrue("asset nao encontrado em ${f.absolutePath}", f.isFile)
        ArcadeCompat.parseJson(f.readText())
    }

    @Test
    fun `the bundled compatibility list parses and is not empty`() {
        assertTrue("lista veio com ${shipped.size} jogos", shipped.size >= 50)
    }

    @Test
    fun `every bundled entry carries fields the manifest can be written from`() {
        val ids = Regex("^NM[0-9]{5}$")
        shipped.forEach { (id, e) ->
            assertTrue("id fora do formato: $id", ids.matches(id))
            assertTrue("$id sem nome", e.name.isNotBlank())
            assertTrue("$id com board invalido: '${e.board}'", e.board in setOf("", "246", "256"))
            assertTrue(
                "$id com media invalida: '${e.media}'",
                e.media.uppercase() in setOf("", "CD", "DVD", "HDD"),
            )
        }
    }

    @Test
    fun `every BIOS rule still matches a documented game in the bundled list`() {
        // A rule outliving its note is the failure here: it would keep refusing a BIOS for a
        // reason the project no longer records.
        ArcadeBios.ruledGames.forEach { id ->
            val e = shipped[id]
            assertTrue("regra de BIOS para $id, que nao esta na lista", e != null)
            assertTrue("$id sem nota que justifique a regra", e!!.note.isNotBlank())
        }
    }
}
