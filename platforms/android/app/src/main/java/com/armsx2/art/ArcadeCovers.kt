package com.armsx2.art

import android.content.Context
import com.armsx2.CustomCovers
import com.armsx2.GameInfo
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.net.HttpURLConnection
import java.net.URL

/**
 * Box art for System 246/256 boards, keyed by gameid.
 *
 * The right source for this app, and it should be tried before SteamGridDB rather than after.
 * SteamGridDB is keyed by TITLE and is a database of console and PC games, which is why fetching
 * an arcade cover through it goes wrong in three ways at once: the arcade name is not always the
 * filed name, the top match can be a different game entirely, and a game with several uploads
 * leads with whichever console box someone submitted first — Capcom Fighting Jam came back as an
 * Xbox case. None of that can happen here. `NM00018.png` is Capcom Fighting Jam by definition.
 *
 * It also needs no API key, so covers work on a fresh install with nothing configured. That was
 * the previous state of things: no key, no covers, and a grid of branded placeholder plates.
 *
 * Not every id is there — 44 of the 55 the compatibility list knows — so SteamGridDB stays as the
 * fallback for the rest, and for wide hero art, which this repository does not carry.
 */
object ArcadeCovers {

    private const val BASE =
        "https://raw.githubusercontent.com/sashkinbro/EmuCoreX-Arcade-Covers/main/covers/"
    private const val TIMEOUT_MS = 15_000

    /** True when this looks like an arcade board id at all; nothing else can be here. */
    private fun gameIdOf(game: GameInfo): String? =
        game.serial?.uppercase()?.takeIf {
            it.length == 7 && it.startsWith("NM") && it.drop(2).all(Char::isDigit)
        }

    /**
     * Fetch and store this game's cover. Returns true when one was stored, false when the
     * repository has no art for the id — which is an ordinary outcome, not an error, and the
     * caller should move on to the next source.
     */
    suspend fun fetch(context: Context, game: GameInfo): Boolean = withContext(Dispatchers.IO) {
        val id = gameIdOf(game) ?: return@withContext false
        runCatching {
            val conn = (URL(BASE + id + ".png").openConnection() as HttpURLConnection).apply {
                requestMethod = "GET"
                connectTimeout = TIMEOUT_MS
                readTimeout = TIMEOUT_MS
                setRequestProperty("User-Agent", "pcsx2x6-android")
            }
            try {
                // A missing id is a plain 404. Reading the error stream as if it were the image
                // would store an HTML page as a png and leave a permanently broken cover.
                if (conn.responseCode != HttpURLConnection.HTTP_OK) return@runCatching false
                val bytes = conn.inputStream.use { it.readBytes() }
                bytes.isNotEmpty() && CustomCovers.setBytes(context, game, bytes)
            } finally {
                conn.disconnect()
            }
        }.getOrDefault(false)
    }
}
