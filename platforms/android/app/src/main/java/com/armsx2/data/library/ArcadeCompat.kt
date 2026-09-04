package com.armsx2.data.library

import android.content.Context
import org.json.JSONObject

/**
 * The project's compatibility list, bundled.
 *
 * A snapshot of the tracker the pcsx2x6 project keeps (issue #9), shipped in assets rather than
 * fetched: it is the answer to "will this even run", which is exactly the question you have
 * before you have a working setup, and often before you have a network.
 *
 * It carries three things the emulator cannot work out on its own:
 *
 *  - The BOARD. A .acgame declares platform=246 or 256, and it is not cosmetic -- Battle Gear 3
 *    refuses to boot on a System256 BIOS. Nothing in the game's files says which board it is, so
 *    a manifest written by hand or by us was guessing, and guessing wrong is a black screen.
 *  - The MEDIA. CD, DVD or HDD, which the manifest also declares.
 *  - The NOTES, which are the hard-won ones: Bloody Roar 3 crashes on the official Sony COH-H
 *    BIOS but runs on 246C and 256. That is not something a player can be expected to deduce
 *    from a crash.
 *
 * Being a snapshot, it goes stale. That is the trade for having it offline and for it costing
 * nothing at boot; the list moves slowly, and a game missing from it degrades to what the app did
 * before -- no badge, and the wizard's own defaults.
 */
object ArcadeCompat {

    enum class Status { Playable, Attract, Untested, Unknown }

    data class Entry(
        val gameId: String,
        val name: String,
        val status: Status,
        /** "246", "256", or empty when the list does not say. */
        val board: String,
        /** "CD", "DVD", "HDD", or empty. */
        val media: String,
        val note: String,
    )

    @Volatile
    private var loaded: Map<String, Entry>? = null

    fun entryFor(context: Context, gameId: String?): Entry? {
        val id = gameId?.uppercase()?.takeIf { it.isNotBlank() } ?: return null
        return all(context)[id]
    }

    fun all(context: Context): Map<String, Entry> {
        loaded?.let { return it }
        val parsed = synchronized(this) {
            loaded ?: parse(context).also { loaded = it }
        }
        return parsed
    }

    private fun parse(context: Context): Map<String, Entry> = runCatching {
        parseJson(context.assets.open("compat/arcade_compat.json").bufferedReader().use { it.readText() })
    }.getOrDefault(emptyMap())

    /**
     * The parse, separated from where the bytes come from so it can be tested on the JVM.
     *
     * Worth testing: a silent parse failure here degrades to "no badge, no note, and the add-game
     * wizard guessing the board again", which looks like a missing feature rather than a broken
     * asset. That is exactly the shape of bug an upstream merge introduces and nobody notices.
     */
    internal fun parseJson(text: String): Map<String, Entry> = runCatching {
        val games = JSONObject(text).optJSONObject("games") ?: return emptyMap()
        buildMap {
            games.keys().forEach { id ->
                val o = games.optJSONObject(id) ?: return@forEach
                put(
                    id,
                    Entry(
                        gameId = id,
                        name = o.optString("name"),
                        status = when (o.optString("status").uppercase()) {
                            "PLAYABLE" -> Status.Playable
                            "ATTRACT" -> Status.Attract
                            "UNTESTED" -> Status.Untested
                            else -> Status.Unknown
                        },
                        board = o.optString("board"),
                        media = o.optString("media"),
                        note = o.optString("note"),
                    ),
                )
            }
        }
    }.getOrDefault(emptyMap())
}
