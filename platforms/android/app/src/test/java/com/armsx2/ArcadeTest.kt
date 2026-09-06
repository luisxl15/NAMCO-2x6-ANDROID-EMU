package com.armsx2

import com.armsx2.data.library.AcgameWizard
import com.armsx2.data.library.ArcadeBios
import com.armsx2.art.ArcadePatches
import com.armsx2.data.library.ArcadeCompat
import com.armsx2.data.library.ArcadePreflight
import com.armsx2.input.ArcadeSwitches
import com.armsx2.input.ControllerMappings
import com.armsx2.ui.premium.humanNote
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

    @Test
    fun `every note the bundled list carries is rewritten for the player`() {
        // The tracker's notes are shorthand written by and for its maintainers ("Game Rejects
        // system256 BIOS!"). Unrecognised ones pass through on purpose, so this is the check
        // that none of the notes we SHIP are falling through -- which is what a reworded entry
        // upstream would cause, silently, next to a green "playable" badge.
        val raw = shipped.values.map { it.note }.filter { it.isNotBlank() }.distinct()
        assertTrue("a lista deveria ter notas", raw.isNotEmpty())
        raw.forEach { note ->
            assertTrue("nota nao reescrita: $note", humanNote(note) != note)
        }
    }

    // ------------------------------------------------------------- patches

    private val patchIndex = """
        {"version":1,"patches":[
          {"gameid":"NM00004","game":"Tekken 4","title":"Widescreen",
           "file":"patches/NM00004-tekken4.pnach","author":"Lorizzuoso","groups":["Widescreen"]},
          {"gameid":"NM00001","game":"Ridge Racer V","title":"Widescreen (RRV1 A)",
           "file":"patches/NM00001-rrv1a.pnach","author":"Franco23444",
           "groups":["Widescreen 16:9","No-Interlacing"]},
          {"gameid":"NM00001","game":"Ridge Racer V","title":"Widescreen (RRV2 B)",
           "file":"patches/NM00001-rrv2b.pnach","author":"Franco23444","groups":["Widescreen 16:9"]},
          {"gameid":"","game":"quebrado","title":"x","file":"patches/x.pnach"},
          {"gameid":"NM00099","game":"sem arquivo","title":"y","file":""}
        ]}
    """.trimIndent()

    @Test
    fun `the patch index parses, and entries with no id or no file are dropped`() {
        val all = ArcadePatches.parseIndex(patchIndex)
        assertEquals(3, all.size)
        val t4 = all.first { it.gameId == "NM00004" }
        assertEquals("Widescreen", t4.title)
        assertEquals("Lorizzuoso", t4.author)
        assertEquals(listOf("Widescreen"), t4.groups)
    }

    @Test
    fun `a game is only ever offered its own patches`() {
        // The reason this is a rule and not tidiness: PCSX2 finds patches by filename, so a pnach
        // installed under another game's id is not ignored -- it is applied, writing one game's
        // addresses into another's memory.
        ArcadePatches.index.value = ArcadePatches.parseIndex(patchIndex)
        assertEquals(1, ArcadePatches.forGame("NM00004").size)
        assertEquals(2, ArcadePatches.forGame("NM00001").size)
        assertTrue(ArcadePatches.forGame("NM00010").isEmpty())
        assertTrue(ArcadePatches.forGame(null).isEmpty())
        assertTrue(ArcadePatches.forGame("").isEmpty())
        // Case is not the player's problem.
        assertEquals(1, ArcadePatches.forGame("nm00004").size)
    }

    @Test
    fun `the installed filename is the one the emulator actually looks for`() {
        // pcsx2x6 keys arcade patches by the game id and reports CRC 0 for them, so the search is
        // "NM00004*.pnach". A CRC-named file -- which is how these arrive from everywhere else --
        // is never found, and that is the whole reason this name is built rather than kept.
        val t4 = ArcadePatches.parseIndex(patchIndex).first { it.gameId == "NM00004" }
        assertEquals("NM00004 - Widescreen.pnach", t4.installName)
        assertTrue(t4.installName.startsWith("NM00004"))
        assertTrue(t4.installName.endsWith(".pnach"))
    }

    @Test
    fun `a broken index is an empty list, not a crash`() {
        assertTrue(ArcadePatches.parseIndex("nao e json").isEmpty())
        assertTrue(ArcadePatches.parseIndex("""{"patches":null}""").isEmpty())
    }

    @Test
    fun `installing one patch supersedes the others for that game only`() {
        // Ridge Racer V has four, and they are alternatives for different revisions of the game.
        // The emulator searches "NM00001*.pnach", so two left in the folder are both found and
        // both applied -- which is why installing one has to remove the rest.
        ArcadePatches.index.value = ArcadePatches.parseIndex(patchIndex)
        val rrv = ArcadePatches.forGame("NM00001")
        val superseded = ArcadePatches.supersededBy(rrv.first())
        assertEquals(1, superseded.size)
        assertEquals(rrv[1].installName, superseded.first().installName)

        // And never another game's: deleting across games would take away a patch nobody touched.
        val t4 = ArcadePatches.forGame("NM00004").first()
        assertTrue(ArcadePatches.supersededBy(t4).isEmpty())
        assertTrue(superseded.none { it.gameId != "NM00001" })
    }

    // ---------------------------------------------------------------- pre-flight

    private val manifest = """
        [game]
        name=Tekken 4
        gameid=NM00004
        platform=256

        ; a comment, and a blank line above
        [data]
        subdir=NM00004
        Elf=proverb.elf
        dongle=NM00004.ps2
        mediasrc=NM00004.chd
        media=DVD
    """.trimIndent()

    @Test
    fun `the manifest is read section by section`() {
        val ini = ArcadePreflight.parseIni(manifest)
        assertEquals("NM00004", ini["game.gameid"])
        assertEquals("Tekken 4", ini["game.name"])
        assertEquals("NM00004.chd", ini["data.mediasrc"])
        // Keys are matched case-insensitively: a manifest typed by hand says Elf=, and the
        // check that reads it must not decide the boot ELF is missing because of a capital.
        assertEquals("proverb.elf", ini["data.elf"])
        // The name lives in [game] only. A flat parse would answer for "data.name" too, which is
        // how a stray key from the wrong block gets read as configuration.
        assertEquals(null, ini["data.name"])
    }

    @Test
    fun `a missing key and an empty one are different answers`() {
        // The loader's defaults only apply when the key is ABSENT: `subdir=` with nothing after
        // it means the payload sits beside the manifest, not in a folder named after the game.
        val ini = ArcadePreflight.parseIni("[data]\nsubdir=\n")
        assertEquals("", ini["data.subdir"])
        assertEquals(null, ini["data.dongle"])
    }

    @Test
    fun `only NM plus five digits is a game id`() {
        assertTrue(ArcadePreflight.looksLikeGameId("NM00004"))
        assertTrue(!ArcadePreflight.looksLikeGameId("NM0004"))
        assertTrue(!ArcadePreflight.looksLikeGameId("NM0000A"))
        assertTrue(!ArcadePreflight.looksLikeGameId("SLUS_200.81"))
        assertTrue(!ArcadePreflight.looksLikeGameId(""))
    }

    // ------------------------------------------------------------ cabinet keys

    @Test
    fun `the cabinet hotkeys are the last entries in the enum`() {
        // Hotkeys are persisted by ORDINAL (stickCodeForHotkey is base + ordinal), so an entry
        // inserted before these re-points bindings people already have. The enum says so in a
        // comment; this is the part that notices when someone does it anyway.
        val all = ControllerMappings.SysHotkey.values()
        val tail = all.takeLast(4).map { it.name }
        assertEquals(listOf("ARCADE_COIN", "ARCADE_START", "ARCADE_SERVICE", "ARCADE_TEST"), tail)
    }

    @Test
    fun `every cabinet hotkey is dispatched`() {
        // The dispatch in MainActivityRuntime is one `in ArcadeSwitches.hotkeys` branch, so a
        // fifth switch added to the enum and not to this set binds fine and then does nothing.
        val declared = ControllerMappings.SysHotkey.values().filter { it.name.startsWith("ARCADE_") }
        assertEquals(declared.toSet(), ArcadeSwitches.hotkeys)
    }
}
