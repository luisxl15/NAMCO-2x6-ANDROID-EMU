package com.armsx2.art

import android.content.Context
import android.util.Log
import com.armsx2.runtime.MainActivityRuntime
import java.io.File
import java.net.HttpURLConnection
import java.net.URL

/**
 * The project's media repository: game logos, cabinet bezels, and the menu film.
 *
 * Fetched once and kept, rather than shipped. The three packs are 92 MB together — more than the
 * APK — for artwork most people will see a handful of files from.
 *
 * They land in the emulator's own data folder, beside bios/ and memcards/, NOT in app-private
 * storage. That folder is the one the player already knows and can open: a bezel they do not like
 * can be replaced with their own file of the same name, and a download that went wrong can be
 * deleted by hand. Artwork the user cannot reach is artwork they cannot fix.
 *
 * ★ The packs are keyed three different ways and none of them is the System 246/256 game id.
 * Logos use MAME short names, bezels use display titles, and nothing in a game's files says which.
 * The two tables below are that bridge, written by hand because this is the only place the naming
 * schemes meet. A game absent from a table simply has no artwork of that kind.
 */
object ArcadeMedia {

    private const val TAG = "ArcadeMedia"

    private const val BASE =
        "https://raw.githubusercontent.com/luisxl15/Namco-System-246-MEDIA-REPO/main/"

    /**
     * Folder names under the data root. The player sees these.
     *
     * NOT the same as the paths in the repository, and the video is why: it is asked for as
     * `video/` here because that is the folder name this app wants, while the repository keeps it
     * under `theme/` beside the System 246/256 mark. Sharing one constant between the two made
     * the app request a path that does not exist, and the only symptom was a background that
     * never appeared -- a 404 is not an error this code raises, it is just a file that is never
     * there.
     */
    const val DIR_VIDEO = "video"
    const val DIR_LOGOS = "logos"
    const val DIR_BEZELS = "bezels"

    /** Where the same things live in the repository. */
    private const val REMOTE_VIDEO = "theme"

    /** System 246/256 game id -> the logo pack's MAME short name. */
    private val LOGOS = mapOf(
        "NM00001" to "rrvac", "NM00002" to "bldyr3b", "NM00003" to "vnight",
        "NM00004" to "tekken4", "NM00005" to "wanganmr", "NM00006" to "scptour",
        "NM00007" to "soulclb2", "NM00008" to "wanganmd", "NM00009" to "netchu02",
        "NM00010" to "batlgr3", "NM00011" to "prdgp03", "NM00012" to "timecrs3",
        "NM00013" to "zgundm", "NM00015" to "batlgr3t", "NM00016" to "zoidsinf",
        "NM00017" to "zgundmdx", "NM00018" to "fghtjam", "NM00019" to "tekken51",
        "NM00021" to "cobrata", "NM00022" to "idolm", "NM00024" to "gundzaft",
        "NM00025" to "zoidiexp", "NM00026" to "tekken5d", "NM00027" to "superdbz",
        "NM00029" to "kinniku", "NM00031" to "soulclb3", "NM00032" to "timecrs4",
        "NM00034" to "gsd", "NM00039" to "motogp", "NM00040" to "kinniku2",
        "NM00042" to "sbxc", "NM00043" to "gdvsgd", "NM00047" to "acedriv3",
        "NM00048" to "fateulc", "NM00052" to "gdvsgdnx",
    )

    /**
     * System 246/256 game id -> the bezel pack's filename, without the extension.
     *
     * Spelled exactly as the pack spells them, typos included ("Wangnam", "Resurection"): these
     * are filenames on a server, and correcting them here would only mean asking for a file that
     * is not there. The Taiko bezels are shared across a game's regional variants, which is right
     * — the cabinet art does not change between the Taiwan and Asia releases.
     */
    private val BEZELS = mapOf(
        "NM00001" to "Ridge Racer V",
        "NM00002" to "Bloody Roar 3",
        "NM00003" to "Vampire Night",
        "NM00004" to "Tekken 4",
        "NM00005" to "Wangnam Midnight R",
        "NM00006" to "Smash Court Pro Tournament",
        "NM00007" to "Soul Calibur 2",
        "NM00008" to "Wangnam Midnight",
        "NM00009" to "Netchuu Pro Baseball 2002",
        "NM00010" to "Battle Gear 3",
        "NM00011" to "Pride GP 2003",
        "NM00012" to "Time Crisis 3",
        "NM00013" to "Mobile Suit Gundam Zeta - A.E.U.G. vs. Titans",
        "NM00015" to "Battle Gear 3 Tuned",
        "NM00016" to "Zoids Infinity",
        "NM00017" to "Mobile Suit Gundam Zeta - A.E.U.G. vs. Titans DX",
        "NM00018" to "Capcom Fighting Jam",
        "NM00019" to "Tekken 5",
        "NM00021" to "Cobra The Arcade",
        "NM00023" to "taiko 7",
        "NM00024" to "Mobile Suit Gundam SEED - Federation vs. Z.A.F.T.",
        "NM00025" to "Zoids Infinity EX",
        "NM00026" to "Tekken 5 Dark Resurection",
        "NM00027" to "Super Dragon Ball Z",
        "NM00029" to "Kinnikuman Grand Prix",
        "NM00030" to "Mobile Suit Gundam Quiz Warrior",
        "NM00031" to "Soul Calibur 3",
        "NM00032" to "Time Crisis 4",
        "NM00033" to "taiko 8",
        "NM00034" to "Mobile Suit Gundam SEED - Federation vs. Z.A.F.T.II",
        "NM00035" to "The Battle Of Yu Yu Hakusho",
        "NM00037" to "Quiz & Variety Suku Suku Inufuku 2",
        "NM00038" to "taiko 9",
        "NM00039" to "Moto gp",
        "NM00040" to "Kinnikuman Grand Prix 2",
        "NM00041" to "taiko 10",
        "NM00042" to "Sengoku Basara X",
        "NM00043" to "Mobile Suit Gundam - Gundam vs. Gundam",
        "NM00044" to "taiko 11",
        "NM00045" to "taiko 11",
        "NM00046" to "taiko 11",
        "NM00047" to "Ace Driver 3 Final turn",
        "NM00048" to "Fate Unlimited Code",
        "NM00051" to "taiko 12",
        "NM00052" to "Mobile Suit Gundam - Gundam vs. Gundam NEXT",
        "NM00053" to "taiko 12",
        "NM00054" to "taiko 12",
        "NM00056" to "taiko 13",
        "NM00057" to "taiko 14",
        "NM10003" to "Technic Beat",
    )

    /**
     * `<data root>/<name>/`, created on demand, with a fallback.
     *
     * The data root is whichever folder the player pointed the emulator at, and that is not
     * guaranteed to be somewhere this process can create directories in — a folder reached
     * through the storage picker rather than as a real path, an SD card that went away, a
     * permission that was not granted for writing. When it is not, everything downloaded here
     * fails at the first write and the only symptom is artwork that never appears.
     *
     * So: try the data root, and if a directory cannot be made and written there, use the app's
     * own external files folder, which always exists and is always writable. Still a folder the
     * player can open; just not the one they chose.
     */
    fun dir(context: Context, name: String): File {
        val preferred = File(MainActivityRuntime.assetCopyRoot(context), name)
        if (usable(preferred)) return preferred
        val fallback = File(context.getExternalFilesDir(null), name)
        Log.w(TAG, "media dir ${preferred.absolutePath} not writable, using ${fallback.absolutePath}")
        return fallback.apply { mkdirs() }
    }

    private fun usable(dir: File): Boolean = runCatching {
        dir.mkdirs()
        if (!dir.isDirectory) return false
        // isDirectory is not enough: a path can exist and still refuse a write. Ask by writing.
        val probe = File(dir, ".probe")
        probe.writeText("")
        probe.delete()
        true
    }.getOrDefault(false)

    fun hasLogo(gameId: String?): Boolean = LOGOS.containsKey(gameId?.uppercase())

    fun hasBezel(gameId: String?): Boolean = BEZELS.containsKey(gameId?.uppercase())

    /** The bezel pack's base filename for a game, without extension. Null when it has none. */
    fun bezelName(gameId: String?): String? = BEZELS[gameId?.uppercase()]

    /**
     * The cached logo for a game, downloading it the first time.
     *
     * Null for a game with no logo and for a download that did not work — a missing picture is not
     * worth an error when the caller has a title to fall back on.
     */
    fun logo(context: Context, gameId: String?): File? {
        val short = LOGOS[gameId?.uppercase()] ?: return null
        return cached(File(dir(context, DIR_LOGOS), "$short.png"), "${DIR_LOGOS}/$short.png")
    }

    /** The cabinet bezel for a game, downloading it the first time. */
    fun bezel(context: Context, gameId: String?): File? {
        val name = BEZELS[gameId?.uppercase()] ?: return null
        return cached(File(dir(context, DIR_BEZELS), "$name.png"), "${DIR_BEZELS}/$name.png")
    }

    /** The menu background film, downloading it once. Null until it is there. */
    fun menuVideo(context: Context): File? =
        cached(File(dir(context, DIR_VIDEO), "menu.mp4"), "$REMOTE_VIDEO/menu.mp4")

    /** On disk already, or fetched now. A file the player replaced is theirs and is left alone. */
    private fun cached(dest: File, remotePath: String): File? {
        if (dest.length() > 0L) return dest
        return if (download(BASE + encodePath(remotePath), dest)) dest else null
    }

    /**
     * Percent-encode a path, leaving the separators alone.
     *
     * The bezels are named as titles -- spaces, ampersands, "Z.A.F.T.II" -- so the URL needs
     * encoding. Not URLEncoder: that is built for form bodies and turns a space into "+", which a
     * server reads as a literal plus in a path, so every bezel with a space in its name would
     * 404. Only the characters that actually need it, and only those.
     */
    private fun encodePath(path: String): String = path.split('/').joinToString("/") { seg ->
        buildString {
            seg.forEach { c ->
                when {
                    c.isLetterOrDigit() || c in "-_.~" -> append(c)
                    else -> c.toString().toByteArray(Charsets.UTF_8)
                        .forEach { b -> append("%%%02X".format(b.toInt() and 0xFF)) }
                }
            }
        }
    }

    /**
     * Fetch to a `.part` file and rename on success.
     *
     * The rename is the point: a half-written file already carrying the final name looks exactly
     * like a cached one to the check above, so a download cut off midway would be served as the
     * artwork forever after. That is the failure that once turned the library's covers into grey
     * smears.
     */
    private fun download(url: String, dest: File): Boolean = runCatching {
        val part = File(dest.parentFile, dest.name + ".part")
        val conn = (URL(url).openConnection() as HttpURLConnection).apply {
            connectTimeout = 10_000
            readTimeout = 30_000
            instanceFollowRedirects = true
        }
        try {
            if (conn.responseCode != HttpURLConnection.HTTP_OK) {
                // Say so. A download that 404s produces exactly the same nothing as one that was
                // never attempted, and the only report anyone can give is "the background never
                // appeared" -- which is how a wrong path went unnoticed once already.
                Log.w(TAG, "media ${conn.responseCode} for $url")
                return false
            }
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
    }.onFailure { Log.w(TAG, "media fetch failed for $url: ${it.message}") }
        .getOrDefault(false)

    /**
     * Remove the app-private cache an earlier build downloaded into.
     *
     * That location was wrong -- the player could not see it, so they could not replace or delete
     * anything in it -- and left behind it would be up to 92 MB of storage nothing ever reads
     * again, in a place nothing would ever show them.
     */
    fun clearLegacyCache(context: Context) {
        runCatching { File(context.filesDir, "media").deleteRecursively() }
    }
}
