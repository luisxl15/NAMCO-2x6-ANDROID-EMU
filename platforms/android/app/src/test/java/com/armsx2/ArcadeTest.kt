package com.armsx2

import com.armsx2.data.library.AcgameWizard
import com.armsx2.data.library.ArcadeBios
import com.armsx2.art.ArcadePatches
import com.armsx2.data.library.ArcadeCompat
import com.armsx2.data.library.ArcadePreflight
import com.armsx2.data.library.ArcadeRepair
import com.armsx2.data.library.ArcadeZipInstall
import com.armsx2.data.library.LanUpload
import com.armsx2.data.library.OpenBiosRepo
import com.armsx2.input.AndroidGyroscopeInput
import com.armsx2.input.ArcadeSwitches
import com.armsx2.input.LightgunAim
import com.armsx2.input.Taiko
import com.armsx2.i18n.AppMessages
import com.armsx2.i18n.HotkeyNames
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

    // -------------------------------------------------------------- repair

    private fun facts(
        gameId: String = "NM00004",
        fileGameId: String? = "NM00004",
        baseNames: List<String> = listOf("NM00004.chd", "NM00004.ps2", "proverb.elf"),
        mediaDirs: List<String> = listOf("NM00004"),
        baseDirExists: Boolean = true,
        compatMedia: String? = "DVD",
    ) = ArcadeRepair.Facts(gameId, fileGameId, baseNames, mediaDirs, baseDirExists, compatMedia)

    @Test
    fun `a manifest that names boot dot elf beside a proverb dot elf is corrected`() {
        val ini = ArcadePreflight.parseIni(
            "[game]\ngameid=NM00004\n[data]\nsubdir=NM00004\nmediasrc=NM00004.chd\nmedia=DVD\n",
        )
        val plan = ArcadeRepair.plan(ini, facts())
        assertEquals(1, plan.size)
        assertEquals("elf", plan.first().key)
        assertEquals("proverb.elf", plan.first().to)
    }

    @Test
    fun `two candidates of the same kind are left alone`() {
        // Picking one would rewrite the line that decides what boots. Better to say nothing and
        // let the check keep reporting the problem.
        val ini = ArcadePreflight.parseIni("[game]\ngameid=NM00004\n[data]\nelf=boot.elf\nmedia=DVD\n")
        val two = facts(baseNames = listOf("NM00004.chd", "a.elf", "b.elf", "NM00004.ps2"))
        assertTrue(ArcadeRepair.plan(ini, two).none { it.key == "elf" })
    }

    @Test
    fun `proverb wins even when the folder carries another elf`() {
        val ini = ArcadePreflight.parseIni("[game]\ngameid=NM00004\n[data]\nelf=boot.elf\nmedia=DVD\n")
        val f = facts(baseNames = listOf("NM00004.chd", "extra.elf", "proverb.elf", "NM00004.ps2"))
        assertEquals("proverb.elf", ArcadeRepair.plan(ini, f).first { it.key == "elf" }.to)
    }

    @Test
    fun `a payload folder that got renamed is found when there is only one`() {
        val ini = ArcadePreflight.parseIni("[game]\ngameid=NM00004\n[data]\nsubdir=NM00004\n")
        val plan = ArcadeRepair.plan(ini, facts(baseDirExists = false, mediaDirs = listOf("tekken4")))
        assertEquals(listOf("subdir"), plan.map { it.key })
        assertEquals("tekken4", plan.first().to)
        // And nothing else: the filenames were read out of a folder that does not exist, so
        // judging them would be judging an empty list.
        assertEquals(1, plan.size)
    }

    @Test
    fun `nothing is proposed for a folder that is already right`() {
        val ini = ArcadePreflight.parseIni(
            "[game]\ngameid=NM00004\n[data]\nsubdir=NM00004\nelf=proverb.elf\n" +
                "dongle=NM00004.ps2\nmediasrc=NM00004.chd\nmedia=DVD\n",
        )
        assertTrue(ArcadeRepair.plan(ini, facts()).isEmpty())
    }

    @Test
    fun `the rewrite keeps every line it was not asked about`() {
        val before = "[game]\nname=Tekken 4\ngameid=NM00004\n\n[data]\n" +
            "subdir=NM00004\nelf=boot.elf\njvsmode=fighting\n"
        val after = ArcadeRepair.applyTo(
            before,
            listOf(ArcadeRepair.Change("data", "elf", "boot.elf", "proverb.elf", "")),
        )
        assertTrue(after.contains("elf=proverb.elf"))
        assertTrue("jvsmode= foi perdido:\n$after", after.contains("jvsmode=fighting"))
        assertTrue(after.contains("name=Tekken 4"))
        assertTrue(!after.contains("elf=boot.elf"))
    }

    @Test
    fun `a key that was not there is added under its own section`() {
        val after = ArcadeRepair.applyTo(
            "[game]\ngameid=NM00004\n\n[data]\nsubdir=NM00004\n",
            listOf(ArcadeRepair.Change("data", "media", null, "DVD", "")),
        )
        val parsed = ArcadePreflight.parseIni(after)
        assertEquals("DVD", parsed["data.media"])
        assertEquals("NM00004", parsed["data.subdir"])
        assertEquals("NM00004", parsed["game.gameid"])
    }

    // ----------------------------------------------------------------- zip

    @Test
    fun `the folder an archive wraps everything in is stripped once`() {
        assertEquals(
            "NM00004/",
            ArcadeZipInstall.commonRoot(listOf("NM00004/NM00004.chd", "NM00004/proverb.elf")),
        )
        // A flat archive has no root.
        assertEquals("", ArcadeZipInstall.commonRoot(listOf("NM00004.chd", "proverb.elf")))
        // Neither does one whose entries do not all share a first folder -- stripping "NM00004/"
        // there would drop the other files out of the install entirely.
        assertEquals(
            "",
            ArcadeZipInstall.commonRoot(listOf("NM00004/NM00004.chd", "leiame.txt")),
        )
    }

    @Test
    fun `the game id comes from the archive when anything in it says so`() {
        assertEquals(
            "NM00004",
            ArcadeZipInstall.gameIdFrom(listOf("NM00004/x.chd"), "x.chd", "tekken.zip"),
        )
        assertEquals(
            "NM00021",
            ArcadeZipInstall.gameIdFrom(listOf("game/NM00021.chd"), "NM00021.chd", "qualquer.zip"),
        )
        assertEquals(
            "NM00015",
            ArcadeZipInstall.gameIdFrom(listOf("disc.chd"), "disc.chd", "NM00015.zip"),
        )
        // Nothing states it: better to refuse than to install under a name that boots nothing.
        assertEquals(null, ArcadeZipInstall.gameIdFrom(listOf("disc.chd"), "disc.chd", "jogo.zip"))
    }

    // ---------------------------------------------------------------- taiko

    @Test
    fun `the drum reads left to right as rim head head rim`() {
        val w = 1000f
        assertEquals(Taiko.KA_LEFT, Taiko.padForX(10f, w))
        assertEquals(Taiko.DON_LEFT, Taiko.padForX(300f, w))
        assertEquals(Taiko.DON_RIGHT, Taiko.padForX(700f, w))
        assertEquals(Taiko.KA_RIGHT, Taiko.padForX(990f, w))
        // A touch outside the surface still has to answer with a pad rather than throw.
        assertEquals(Taiko.KA_LEFT, Taiko.padForX(-40f, w))
        assertEquals(Taiko.KA_RIGHT, Taiko.padForX(4000f, w))
    }

    @Test
    fun `the drawn columns and the hit test are the same four columns`() {
        // Two descriptions of one layout: if they drift, the player hits a red column and the
        // game hears a blue rim, which is the kind of bug that reads as bad timing.
        val w = 1000f
        Taiko.columns.forEachIndexed { column, range ->
            val middle = (range.start + range.endInclusive) / 2f * w
            assertEquals(column, Taiko.columnOf(Taiko.padForX(middle, w)))
        }
        assertTrue(Taiko.isDon(Taiko.DON_LEFT) && Taiko.isDon(Taiko.DON_RIGHT))
        assertTrue(!Taiko.isDon(Taiko.KA_LEFT) && !Taiko.isDon(Taiko.KA_RIGHT))
    }

    // ------------------------------------------------------------ gun aiming

    @Test
    fun `a gyroscope moves the crosshair and stopping leaves it where it is`() {
        // A rate: held still the sensor reports zero, and zero must mean "stay", not "recentre".
        var (x, y) = LightgunAim.next(AndroidGyroscopeInput.KIND_GYRO, 0.5f, 0.5f, 1f, 0f, 100L)
        assertTrue("esperava andar para a direita, veio $x", x > 0.5f)
        val moved = x
        val held = LightgunAim.next(AndroidGyroscopeInput.KIND_GYRO, moved, y, 0f, 0f, 100L)
        assertEquals(moved, held.first, 0.0001f)
    }

    @Test
    fun `tilt is a position, not a speed`() {
        // The bug this guards: treating gravity's ANGLE as a rate accelerates the crosshair off
        // the screen while the phone sits perfectly still at a tilt. The same input twice has to
        // give the same place twice.
        val first = LightgunAim.next(AndroidGyroscopeInput.KIND_TILT, 0.5f, 0.5f, 0.4f, 0f, 100L)
        val second = LightgunAim.next(AndroidGyroscopeInput.KIND_TILT, first.first, first.second, 0.4f, 0f, 100L)
        assertEquals(first.first, second.first, 0.0001f)
        // And level means centre.
        assertEquals(0.5f, LightgunAim.next(AndroidGyroscopeInput.KIND_TILT, 0.9f, 0.1f, 0f, 0f, 100L).first, 0.0001f)
    }

    @Test
    fun `the crosshair never leaves the screen`() {
        var x = 0.5f
        repeat(200) { x = LightgunAim.next(AndroidGyroscopeInput.KIND_GYRO, x, 0.5f, 1f, 0f, 100L).first }
        assertEquals(1f, x, 0.0001f)
        repeat(400) { x = LightgunAim.next(AndroidGyroscopeInput.KIND_GYRO, x, 0.5f, -1f, 0f, 100L).first }
        assertEquals(0f, x, 0.0001f)
    }

    @Test
    fun `a long gap between samples cannot fling the crosshair`() {
        // The app was paused for a minute; the next sample must not carry a minute of travel.
        val jump = LightgunAim.next(AndroidGyroscopeInput.KIND_GYRO, 0.5f, 0.5f, 1f, 0f, 60_000L)
        val capped = LightgunAim.next(AndroidGyroscopeInput.KIND_GYRO, 0.5f, 0.5f, 1f, 0f, 100L)
        assertEquals(capped.first, jump.first, 0.0001f)
    }

    // ---------------------------------------------------------- hotkey names

    @Test
    fun `every hotkey has a Portuguese name`() {
        // The point of this one is the NEXT hotkey somebody adds. The list is translated by a
        // map keyed on the enum's own names, so a new entry silently shows its English label in
        // an otherwise Portuguese screen -- which is exactly the kind of thing nobody notices
        // until a user does.
        val english = ControllerMappings.SysHotkey.values().filter {
            HotkeyNames.actionIn("pt-BR", it) == it.label && !it.name.startsWith("ARCADE_")
        }
        assertTrue("sem tradução: ${english.map { it.name }}", english.isEmpty())
    }

    @Test
    fun `the per-slot hotkeys name their own slot`() {
        assertEquals(
            "Salvar estado no slot 3",
            HotkeyNames.actionIn("pt-BR", ControllerMappings.SysHotkey.SAVE_SLOT_3),
        )
        assertEquals(
            "Carregar estado do slot 7",
            HotkeyNames.actionIn("pt-BR", ControllerMappings.SysHotkey.LOAD_SLOT_7),
        )
    }

    @Test
    fun `another language is left alone`() {
        val hk = ControllerMappings.SysHotkey.SAVE_STATE
        assertEquals(hk.label, HotkeyNames.actionIn("en", hk))
        assertEquals("D-Pad Up", HotkeyNames.bindingIn("en", "D-Pad Up"))
        assertEquals("Fast Forward ON", HotkeyNames.feedbackIn("de", "Fast Forward ON"))
    }

    @Test
    fun `what is printed on the controller is not translated`() {
        // L1, R2 and Start are moulded into the plastic in the player's hand. Translating those
        // would make the screen disagree with the thing it is describing.
        assertEquals("Select + R1", HotkeyNames.bindingIn("pt-BR", "Select + R1"))
        assertEquals("L2", HotkeyNames.bindingIn("pt-BR", "L2"))
        // What IS language gets translated, whole rather than half.
        assertEquals("Direcional cima", HotkeyNames.bindingIn("pt-BR", "D-Pad Up"))
        assertEquals("Analógico E esquerda", HotkeyNames.bindingIn("pt-BR", "L-Stick Left"))
        assertEquals("Botão A", HotkeyNames.bindingIn("pt-BR", "Button A"))
    }

    @Test
    fun `a message the hotkeys no longer send falls back to English`() {
        // Keyed by the English text, so an upstream rewording misses the map. Coming through in
        // English is the right failure; coming through as a raw key would not be.
        assertEquals("Something New", HotkeyNames.feedbackIn("pt-BR", "Something New"))
        assertEquals("Avanço rápido ligado", HotkeyNames.feedbackIn("pt-BR", "Fast Forward ON"))
    }

    @Test
    fun `a manifest at the top of the archive is not a wrapping folder`() {
        // The shape these games are actually distributed in: NM00018.acgame sitting beside the
        // NM00018/ folder its subdir= names. Nothing wraps them, so nothing may be stripped --
        // treating the first entry as a root would put the payload one level below where the
        // manifest says it is, and the game would install cleanly and then find nothing.
        val entries = listOf(
            "NM00018.acgame",
            "NM00018/NM00018.chd",
            "NM00018/NM00018.ps2",
            "NM00018/proverb.elf",
        )
        assertEquals("", ArcadeZipInstall.commonRoot(entries))
    }

    @Test
    fun `a wrapping folder is still stripped when the manifest is inside it`() {
        // Same drop, zipped one level down. Here NM00018/ IS the wrapper and comes off, leaving
        // the same layout as the test above.
        val entries = listOf(
            "Capcom Fighting Jam/NM00018.acgame",
            "Capcom Fighting Jam/NM00018/NM00018.chd",
            "Capcom Fighting Jam/NM00018/proverb.elf",
        )
        assertEquals("Capcom Fighting Jam/", ArcadeZipInstall.commonRoot(entries))
    }

    // ------------------------------------------------------------ open BIOS

    @Test
    fun `the open BIOS catalogue reads its own index`() {
        val entries = OpenBiosRepo.parseIndex(
            """
            {"bios":[
              {"name":"Open246","file":"open246.7d","note":"System 246","bytes":2097152},
              {"name":"","file":"open256.8g"},
              {"note":"sem arquivo, não conta"}
            ]}
            """.trimIndent(),
        )
        assertEquals(2, entries.size)
        assertEquals("Open246", entries[0].name)
        assertEquals(2097152L, entries[0].bytes)
        // A nameless entry falls back to its filename rather than showing an empty row.
        assertEquals("open256.8g", entries[1].name)
    }

    @Test
    fun `without an index the repository listing does`() {
        // Every such repository starts with no index at all, so the fallback is the normal case
        // for a while, not an error path.
        val entries = OpenBiosRepo.parseListing(
            """
            [
              {"name":"open246.7d","type":"file","size":2097152},
              {"name":"open256.8g","type":"file","size":2097152},
              {"name":"README.md","type":"file","size":37},
              {"name":"index.json","type":"file","size":120},
              {"name":"docs","type":"dir","size":0}
            ]
            """.trimIndent(),
        )
        assertEquals(listOf("open246.7d", "open256.8g"), entries.map { it.file })
    }

    @Test
    fun `a catalogue that will not parse is empty, not a crash`() {
        assertTrue(OpenBiosRepo.parseIndex("nao e json").isEmpty())
        assertTrue(OpenBiosRepo.parseIndex("""{"bios":null}""").isEmpty())
        assertTrue(OpenBiosRepo.parseListing("{}").isEmpty())
    }

    // ------------------------------------------------------- manager messages

    @Test
    fun `a message that carries a filename keeps it`() {
        // These are matched by shape, not by an exact key, because most of them have a filename
        // or a slot number in the middle. Dropping the captured group is the way that goes wrong.
        assertEquals(
            "Não foi possível apagar r27v1602f.8g.",
            AppMessages.translate("pt-BR", "Unable to delete r27v1602f.8g."),
        )
        assertEquals(
            "NM00004.ps2 atribuído ao slot 1.",
            AppMessages.translate("pt-BR", "NM00004.ps2 assigned to slot 1."),
        )
        assertEquals(
            "O slot 2 volta a seguir o cartão global. Reinicie o jogo para aplicar.",
            AppMessages.translate("pt-BR", "Slot 2 follows the global card again. Restart the game to apply."),
        )
    }

    @Test
    fun `a sentence with no rule comes through in English`() {
        // The right failure: an upstream rewording loses its translation and keeps its meaning.
        val unknown = "Something upstream started saying today."
        assertEquals(unknown, AppMessages.translate("pt-BR", unknown))
        // And another language is never touched.
        assertEquals(
            "Unable to delete x.bin.",
            AppMessages.translate("en", "Unable to delete x.bin."),
        )
    }

    @Test
    fun `the longest messages match whole, not by prefix`() {
        val english = "Stop the game before restoring a memory card. The console keeps its own " +
            "picture of the card while it runs, and would write over the restored copy."
        val out = AppMessages.translate("pt-BR", english)
        assertTrue("não traduziu: $out", out.startsWith("Feche o jogo"))
        // The whole sentence, not just its opening: the second half is where a prefix match
        // would leave English behind. ("memory card" stays -- it is what the app calls them.)
        assertTrue("parou no meio: $out", out.endsWith("cópia restaurada."))
        assertTrue("sobrou inglês: $out", !out.contains("Stop the game"))
    }

    // ------------------------------------------------------------------ 7z

    @Test
    fun `the 7z reader is on the classpath and works`() {
        // A smoke test of the dependency itself, not of our code. commons-compress reads 7z from
        // a random-access file and delegates LZMA2 to xz; without the second one an archive opens
        // and then throws on its first entry, which is exactly the kind of half-working that a
        // "compiles fine" build hides.
        val file = File.createTempFile("armsx2", ".7z")
        file.deleteOnExit()
        org.apache.commons.compress.archivers.sevenz.SevenZOutputFile(file).use { out ->
            val entry = out.createArchiveEntry(File("NM00004.chd"), "NM00004/NM00004.chd")
            out.putArchiveEntry(entry)
            out.write(ByteArray(4096) { 7 })
            out.closeArchiveEntry()
        }
        org.apache.commons.compress.archivers.sevenz.SevenZFile.builder()
            .setFile(file).get().use { archive ->
                val e = archive.nextEntry
                assertEquals("NM00004/NM00004.chd", e.name)
                val buf = ByteArray(4096)
                assertEquals(4096, archive.read(buf))
                assertEquals(7.toByte(), buf[0])
                assertEquals(null, archive.nextEntry)
            }
    }

    @Test
    fun `only rar is refused by name now`() {
        assertTrue(ArcadeZipInstall.isSevenZip("jogo.7z"))
        assertTrue(!ArcadeZipInstall.isSevenZip("jogo.zip"))
        assertTrue(!ArcadeZipInstall.unsupported("jogo.7z"))
        assertTrue(ArcadeZipInstall.unsupported("jogo.rar"))
    }

    // ----------------------------------------------------------- upload names

    @Test
    fun `an uploaded name is stripped to a bare filename`() {
        // A browser sends whatever it was given. A name carrying a path would write outside the
        // ROM folder, which is the one thing a server that accepts files must not allow.
        assertEquals("jogo.zip", LanUpload.fileNameFrom("/upload?name=jogo.zip"))
        assertEquals("jogo.zip", LanUpload.fileNameFrom("/upload?name=%2Fetc%2Fjogo.zip"))
        assertEquals("jogo.zip", LanUpload.fileNameFrom("/upload?name=..%2F..%2Fjogo.zip"))
        assertEquals("jogo.zip", LanUpload.fileNameFrom("/upload?name=C%3A%5Ctmp%5Cjogo.zip"))
        // Percent-encoding is undone, so a real name with spaces survives.
        assertEquals(
            "Capcom Fighting Jam.7z",
            LanUpload.fileNameFrom("/upload?name=Capcom%20Fighting%20Jam.7z"),
        )
    }

    @Test
    fun `a name that is only a path is refused`() {
        assertEquals(null, LanUpload.fileNameFrom("/upload?name=..%2F.."))
        assertEquals(null, LanUpload.fileNameFrom("/upload?name="))
        assertEquals(null, LanUpload.fileNameFrom("/upload"))
    }
}
