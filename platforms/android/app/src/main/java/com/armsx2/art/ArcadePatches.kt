package com.armsx2.art

import android.content.Context
import android.util.Log
import androidx.compose.runtime.mutableStateOf
import org.json.JSONObject
import java.io.File

/**
 * Widescreen and no-interlacing patches, fetched for the game that is running.
 *
 * The repository ships an index, and that is the difference between this and the logo and bezel
 * packs: those carry no manifest, so the mapping from game id to filename had to be written into
 * the app by hand and a new file means a new build. Here the list is data. A patch added to the
 * repository shows up in a game's menu without anything being rebuilt.
 *
 * Only patches for the running game are ever offered. A pnach for another title is not merely
 * useless — PCSX2 finds patches by filename, so one dropped into the folder under the wrong id
 * would be applied to the wrong game, writing another game's addresses into memory.
 */
object ArcadePatches {

    private const val TAG = "ArcadePatches"
    private const val BASE =
        "https://raw.githubusercontent.com/luisxl15/Namco-System-246-Widescreen__Patches/main/"

    data class Entry(
        val gameId: String,
        val game: String,
        val title: String,
        val file: String,
        val author: String,
        val groups: List<String>,
    ) {
        /**
         * What the file is called once installed.
         *
         * `<gameid> - <title>.pnach`, because that is what the emulator looks for. pcsx2x6 keys
         * arcade patches by the game id and reports a CRC of 0 for them, so the search is
         * "NM00004*.pnach" — a patch named after the CRC, which is how they arrive from
         * everywhere else, is never found.
         */
        val installName: String get() = "$gameId - $title.pnach"
    }

    /** The index, once fetched. Empty until then, and after a failure. */
    val index = mutableStateOf<List<Entry>>(emptyList())

    @Volatile private var loading = false

    /** Patches for one game. The only list any screen is allowed to show. */
    fun forGame(gameId: String?): List<Entry> {
        val id = gameId?.uppercase()?.takeIf { it.isNotBlank() } ?: return emptyList()
        return index.value.filter { it.gameId == id }
    }

    /** True once this patch's file is in the emulator's patches folder. */
    fun isInstalled(context: Context, entry: Entry): Boolean =
        File(patchesDir(context), entry.installName).length() > 0L

    fun patchesDir(context: Context): File = ArcadeMedia.dir(context, "patches")

    /**
     * Fetch the index, unless it is already here.
     *
     * Blocking, so call it off the main thread. Failure leaves the list empty, which reads as "no
     * patches for this game" — the honest outcome when we could not find out.
     */
    fun ensureIndex() {
        if (index.value.isNotEmpty() || loading) return
        loading = true
        try {
            val text = ArcadeMedia.fetchText(BASE + "index.json") ?: run {
                Log.w(TAG, "index could not be fetched")
                return
            }
            index.value = parseIndex(text)
        } finally {
            loading = false
        }
    }

    internal fun parseIndex(text: String): List<Entry> = runCatching {
        val arr = JSONObject(text).optJSONArray("patches") ?: return emptyList()
        buildList {
            for (i in 0 until arr.length()) {
                val o = arr.optJSONObject(i) ?: continue
                val id = o.optString("gameid").uppercase()
                val file = o.optString("file")
                if (id.isBlank() || file.isBlank()) continue
                val groupsArr = o.optJSONArray("groups")
                add(
                    Entry(
                        gameId = id,
                        game = o.optString("game"),
                        title = o.optString("title").ifBlank { "Patch" },
                        file = file,
                        author = o.optString("author"),
                        groups = buildList {
                            for (g in 0 until (groupsArr?.length() ?: 0)) {
                                groupsArr?.optString(g)?.takeIf { it.isNotBlank() }?.let { add(it) }
                            }
                        },
                    ),
                )
            }
        }
    }.getOrElse {
        Log.w(TAG, "index did not parse: ${it.message}")
        emptyList()
    }

    /** Download a patch into the emulator's patches folder. Returns null on success, else why not. */
    fun install(context: Context, entry: Entry): String? {
        val dest = File(patchesDir(context), entry.installName)
        return if (ArcadeMedia.fetchTo(BASE + entry.file, dest)) {
            null
        } else {
            "Não foi possível baixar o patch."
        }
    }

    /** Remove an installed patch. */
    fun remove(context: Context, entry: Entry): Boolean =
        runCatching { File(patchesDir(context), entry.installName).delete() }.getOrDefault(false)
}
