package com.armsx2.art

import android.content.Context
import java.io.File
import java.net.HttpURLConnection
import java.net.URL

/**
 * The project's media repository: game logos and the menu theme.
 *
 * Both are fetched once and kept on disk rather than shipped. The logo pack alone is 15 MB and
 * the menu video is 27 MB — together that is over half again the size of the APK, for artwork
 * most people will see a handful of files from.
 *
 * ★ The logos are keyed by MAME SHORT NAME, not by the System 246/256 game id the app knows a
 * game by. Nothing in a game's files says "tekken4", so the mapping below is the bridge, and it
 * is written by hand because that is the only place the two naming schemes meet. A game with no
 * entry simply has no logo — the launcher falls back to its title in text, which is what it did
 * before any of this existed.
 */
object ArcadeMedia {

    private const val BASE =
        "https://raw.githubusercontent.com/luisxl15/Namco-System-246-MEDIA-REPO/main/"

    /**
     * System 246/256 game id -> the logo pack's filename.
     *
     * Taken from the compatibility list on one side and the pack's own file listing on the other.
     * Around two dozen of the pack's files are for titles the list does not carry (Pac-Man
     * Arrangement, Tatsunoko vs Capcom, the Virtua Striker pair) and are simply unreachable from
     * here; the pack is not ours to prune.
     */
    private val LOGOS = mapOf(
        "NM00001" to "rrvac",       // Ridge Racer V - Arcade Battle
        "NM00002" to "bldyr3b",     // Bloody Roar 3
        "NM00003" to "vnight",      // Vampire Night
        "NM00004" to "tekken4",     // Tekken 4
        "NM00005" to "wanganmr",    // Wangan Midnight R
        "NM00006" to "scptour",     // Smash Court Pro Tournament
        "NM00007" to "soulclb2",    // Soul Calibur II
        "NM00008" to "wanganmd",    // Wangan Midnight
        "NM00009" to "netchu02",    // Netchuu! Pro Baseball 2002
        "NM00010" to "batlgr3",     // Battle Gear 3
        "NM00011" to "prdgp03",     // Pride GP 2003
        "NM00012" to "timecrs3",    // Time Crisis 3
        "NM00013" to "zgundm",      // Gundam Zeta - A.E.U.G. vs Titans
        "NM00015" to "batlgr3t",    // Battle Gear 3 Tuned
        "NM00016" to "zoidsinf",    // Zoids Infinity
        "NM00017" to "zgundmdx",    // Gundam Zeta DX
        "NM00018" to "fghtjam",     // Capcom Fighting Jam
        "NM00019" to "tekken51",    // Tekken 5 / 5.1
        "NM00021" to "cobrata",     // Cobra - The Arcade
        "NM00022" to "idolm",       // The IDOLM@STER
        "NM00024" to "gundzaft",    // Gundam SEED - Federation vs Z.A.F.T.
        "NM00025" to "zoidiexp",    // Zoids Infinity EX Plus
        "NM00026" to "tekken5d",    // Tekken 5 - Dark Resurrection
        "NM00027" to "superdbz",    // Super Dragon Ball Z
        "NM00029" to "kinniku",     // Kinnikuman Muscle Grand Prix
        "NM00031" to "soulclb3",    // Soul Calibur III - Arcade Edition
        "NM00032" to "timecrs4",    // Time Crisis 4
        "NM00034" to "gsd",         // Gundam SEED Destiny
        "NM00039" to "motogp",      // MotoGP
        "NM00040" to "kinniku2",    // Kinnikuman Muscle Grand Prix 2
        "NM00042" to "sbxc",        // Sengoku Basara X Cross
        "NM00043" to "gdvsgd",      // Gundam vs Gundam
        "NM00047" to "acedriv3",    // Ace Driver 3 - Final Turn
        "NM00048" to "fateulc",     // Fate - Unlimited Codes
        "NM00052" to "gdvsgdnx",    // Gundam vs Gundam NEXT
    )

    private fun dir(context: Context, name: String): File =
        File(context.filesDir, "media/$name").also { it.mkdirs() }

    /** True when this game has a logo in the pack at all. Cheap; no disk, no network. */
    fun hasLogo(gameId: String?): Boolean = LOGOS.containsKey(gameId?.uppercase())

    /**
     * The cached logo for a game, downloading it if this is the first time.
     *
     * Returns null for a game with no logo, and for a download that did not work — a missing
     * picture is not worth an error, and the caller has a title to fall back on.
     */
    fun logo(context: Context, gameId: String?): File? {
        val short = LOGOS[gameId?.uppercase()] ?: return null
        val out = File(dir(context, "logos"), "$short.png")
        if (out.length() > 0L) return out
        return if (download("${BASE}logos/$short.png", out)) out else null
    }

    /** The menu background video, downloading it once. Null until it is there. */
    fun menuVideo(context: Context): File? {
        val out = File(dir(context, "theme"), "menu.mp4")
        if (out.length() > 0L) return out
        return if (download("${BASE}theme/menu.mp4", out)) out else null
    }

    /**
     * Fetch to a `.part` file and rename on success.
     *
     * The rename is the point: a half-written file that already carries the final name looks
     * exactly like a cached one to the check above, so a download interrupted midway would be
     * served as the logo forever after. This is the same failure that once turned the library's
     * covers into grey smears.
     */
    private fun download(url: String, dest: File): Boolean = runCatching {
        val part = File(dest.parentFile, dest.name + ".part")
        val conn = (URL(url).openConnection() as HttpURLConnection).apply {
            connectTimeout = 10_000
            readTimeout = 30_000
            instanceFollowRedirects = true
        }
        try {
            if (conn.responseCode != HttpURLConnection.HTTP_OK) return false
            conn.inputStream.use { input -> part.outputStream().use { input.copyTo(it) } }
        } finally {
            conn.disconnect()
        }
        if (part.length() <= 0L) {
            part.delete()
            return false
        }
        dest.delete()
        part.renameTo(dest)
    }.getOrDefault(false)
}
