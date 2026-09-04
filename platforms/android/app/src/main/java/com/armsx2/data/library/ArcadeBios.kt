package com.armsx2.data.library

import com.armsx2.BiosInfo

/**
 * Which BIOS a given arcade board will actually boot with.
 *
 * The compatibility list carries a `board` field (246 or 256) but that is *informational*, and
 * matching it blindly would be wrong: a System 256 BIOS runs 246 titles perfectly well, which is
 * most of the library. The real constraints are the handful the tracker records as notes, and
 * they are exclusions rather than requirements — "this one will not boot on that BIOS".
 *
 * So this encodes those notes, and only those. Three games, quoted at their rules below. A game
 * with no rule is left alone; guessing on its behalf would swap a working BIOS for a different
 * one and call it a fix.
 *
 * This matters because the failure it prevents is silent. Battle Gear 3 on a System 256 BIOS does
 * not report anything — it black-screens, and the note explaining why is a line of text in a
 * screen the player has no reason to open.
 */
object ArcadeBios {

    /** What kind of board a BIOS image is from. */
    enum class Board { S246, S256, Console }

    /**
     * Read the board out of what the core tells us about the image.
     *
     * Arcade BIOSes report zone "COH-H" and carry the board in the description, e.g.
     * "COH-H   System 256 20040519-145634". Console images report a region zone (Europe, Japan)
     * and never mention a System number.
     */
    fun boardOf(info: BiosInfo): Board {
        val text = (info.description + " " + info.zone).uppercase()
        return when {
            text.contains("SYSTEM 256") -> Board.S256
            text.contains("SYSTEM 246") -> Board.S246
            else -> Board.Console
        }
    }

    /** True for the official Sony COH-H board image the Bloody Roar 3 note calls out. */
    private fun isOfficialSonyCohH(info: BiosInfo): Boolean =
        (info.description + " " + info.zone).uppercase().contains("A-000-010")

    /** Why a BIOS is unusable for a game, or null when it is fine. */
    private class Rule(val reason: String, val rejects: (BiosInfo) -> Boolean)

    /**
     * The tracker's BIOS notes, as rules. Each quotes the note it comes from so a future edit can
     * be checked against the source rather than against someone's memory of it.
     */
    private val RULES: Map<String, Rule> = mapOf(
        // "Game Rejects system256 BIOS!"
        "NM00010" to Rule("este jogo não inicia com uma BIOS de System 256") { boardOf(it) == Board.S256 },
        "NM00015" to Rule("este jogo não inicia com uma BIOS de System 256") { boardOf(it) == Board.S256 },
        // "this game crashes when using official sony COH-H ps2 board bios, the ones marked as
        //  `COH-H Board (A-000-010)`. 246C and 256 bios work ok"
        "NM00002" to Rule("este jogo trava com a BIOS oficial COH-H (A-000-010)") { isOfficialSonyCohH(it) },
    )

    /** The games a rule speaks for. Exposed so a test can check each one against the shipped
     *  compatibility list -- a rule whose note has disappeared from the tracker is acting on
     *  something nobody documents any more. */
    internal val ruledGames: Set<String> get() = RULES.keys

    /** What to do about the BIOS for this boot. */
    sealed interface Decision {
        /** Nothing known against the current choice. */
        data object Keep : Decision

        /** The current BIOS is known not to work; this installed one does. */
        data class Switch(val fileName: String, val reason: String) : Decision

        /** The current BIOS is known not to work and nothing installed is any better. */
        data class NoneUsable(val reason: String) : Decision
    }

    /**
     * Decide the BIOS for [gameId], given what is installed and what would otherwise be used.
     *
     * [installed] is (filename -> info) for every BIOS the app has. [current] is the filename the
     * normal resolution picked — the per-game pin if there is one, else the global choice.
     */
    fun decide(
        gameId: String?,
        installed: Map<String, BiosInfo>,
        current: String?,
    ): Decision {
        val rule = RULES[gameId?.uppercase()] ?: return Decision.Keep
        val currentInfo = current?.let { installed[it] } ?: return Decision.Keep
        if (!rule.rejects(currentInfo)) return Decision.Keep

        // Prefer another arcade BIOS: a console image boots these boards even less well than the
        // wrong arcade one, so it is not an improvement worth making silently.
        val replacement = installed.entries
            .filter { it.key != current && boardOf(it.value) != Board.Console && !rule.rejects(it.value) }
            .minByOrNull { it.key.lowercase() }
            ?: return Decision.NoneUsable(rule.reason)

        return Decision.Switch(replacement.key, rule.reason)
    }
}
