package com.armsx2.data.library

import android.content.Context
import android.os.ParcelFileDescriptor
import android.util.Log
import androidx.compose.runtime.mutableStateOf
import com.armsx2.BiosInfo
import com.armsx2.art.ArcadeMedia
import com.armsx2.runtime.MainActivityRuntime
import kr.co.iefriends.pcsx2.NativeApp
import org.json.JSONObject
import java.io.File

/**
 * Open BIOS images, fetched from the project's own repository.
 *
 * These boards need a BIOS and this app has never had one to offer: every path into the BIOS
 * folder starts with a file the player already has. An open image is the only kind this can
 * legitimately hand out, so this is the door for it.
 *
 * Deliberately index-first and list-second. An `index.json` at the repository root is the stable
 * contract — a name, a file, a line about what it is — and it lets an image be added or renamed
 * without an app release. Where there is no index yet the repository's own file listing is used
 * instead, so a repo with two files in it and nothing else works today.
 *
 * A download lands in TWO places, and the split is the point:
 *
 *  - `<data root>/BIOS/` — a plain folder next to the player's own files, which is where an image
 *    under development belongs. They can open it, replace it, diff it.
 *  - the emulator's own BIOS folder — but only once the core recognises the image. That folder is
 *    pinned to the app's externalFilesDir/bios (native-lib pins `Folders/Bios` to it), it is the
 *    only folder the core ever reads, and putting an unusable file there would fill the BIOS list
 *    with entries that cannot boot anything.
 *
 * Nothing here decides whether an image is any good. A download is handed straight to the core's
 * own `IsBIOSFromFd`, the same check the BIOS list uses, and whatever it answers is what the
 * player is told.
 */
object OpenBiosRepo {

    private const val TAG = "OpenBiosRepo"

    private const val OWNER = "luisxl15"
    private const val REPO = "BasicInput_Output_Sys_Namco_246_256"
    private const val BRANCH = "main"

    private const val RAW = "https://raw.githubusercontent.com/$OWNER/$REPO/$BRANCH/"
    private const val LISTING = "https://api.github.com/repos/$OWNER/$REPO/contents/?ref=$BRANCH"

    /** The folder downloads land in, beside the player's own data. Capitalised as asked for. */
    private const val VISIBLE_DIR = "BIOS"

    /** Files in the repository that are never BIOS images. */
    private val IGNORED = setOf("readme.md", "license", "license.md", "index.json", ".gitignore")

    data class Entry(
        val name: String,
        val file: String,
        val note: String,
        val bytes: Long,
    )

    /** What the repository offers. Empty until fetched, and after a failure. */
    val index = mutableStateOf<List<Entry>>(emptyList())

    /** Where one of these images stands on this device. */
    sealed interface Status {
        data object Missing : Status

        /** Downloaded and sitting in the visible folder; the core does not know what it is. */
        data class Unusable(val file: File) : Status

        /** The core recognised it, so it is in the emulator's BIOS folder and can be selected. */
        data class Ready(val file: File, val description: String) : Status

        data class Failed(val why: String) : Status
    }

    @Volatile private var loading = false

    /** Fetch the catalogue. Blocking; call it off the main thread. */
    fun ensureIndex() {
        if (index.value.isNotEmpty() || loading) return
        loading = true
        try {
            val fromIndex = ArcadeMedia.fetchText(RAW + "index.json")?.let(::parseIndex)
            if (!fromIndex.isNullOrEmpty()) {
                index.value = fromIndex
                return
            }
            val listing = ArcadeMedia.fetchText(LISTING)
            if (listing == null) {
                Log.w(TAG, "neither index.json nor the file listing could be fetched")
                return
            }
            index.value = parseListing(listing)
        } finally {
            loading = false
        }
    }

    /**
     * The documented form: `{"bios":[{"name":…,"file":…,"note":…,"bytes":…}]}`.
     *
     * Same shape as the patch repository's index, for the same reason: a client should not have
     * to learn a second grammar to read a second catalogue.
     */
    internal fun parseIndex(text: String): List<Entry> = runCatching {
        val arr = JSONObject(text).optJSONArray("bios") ?: return emptyList()
        buildList {
            for (i in 0 until arr.length()) {
                val o = arr.optJSONObject(i) ?: continue
                val file = o.optString("file")
                if (file.isBlank()) continue
                add(
                    Entry(
                        name = o.optString("name").ifBlank { file },
                        file = file,
                        note = o.optString("note"),
                        bytes = o.optLong("bytes"),
                    ),
                )
            }
        }
    }.getOrElse {
        Log.w(TAG, "index did not parse: ${it.message}")
        emptyList()
    }

    /**
     * The fallback: GitHub's own listing of the repository root.
     *
     * So a repository that is just two files and a README works before anyone has written an
     * index for it — which is the state every such repository starts in.
     */
    internal fun parseListing(text: String): List<Entry> = runCatching {
        val arr = org.json.JSONArray(text)
        buildList {
            for (i in 0 until arr.length()) {
                val o = arr.optJSONObject(i) ?: continue
                if (o.optString("type") != "file") continue
                val name = o.optString("name")
                if (name.isBlank() || name.lowercase() in IGNORED) continue
                add(Entry(name = name, file = name, note = "", bytes = o.optLong("size")))
            }
        }
    }.getOrElse {
        Log.w(TAG, "listing did not parse: ${it.message}")
        emptyList()
    }

    /**
     * `<data root>/BIOS`, created on demand.
     *
     * Logged, because the data root is not a constant: it is whatever folder the player pointed
     * the wizard at, and only falls back to the app's own external files directory. When a
     * download later turns up somewhere unexpected, this line is the answer.
     */
    fun visibleDir(context: Context): File {
        val dir = File(MainActivityRuntime.assetCopyRoot(context), VISIBLE_DIR)
        if (!dir.isDirectory) {
            val made = dir.mkdirs()
            Log.i(TAG, "pasta BIOS ${dir.absolutePath} criada=$made existe=${dir.isDirectory}")
        }
        return dir
    }

    private fun visibleFile(context: Context, entry: Entry) = File(visibleDir(context), entry.name)

    private fun coreFile(context: Context, entry: Entry) =
        File(MainActivityRuntime.internalBiosDir(context), entry.name)

    /** A real file with bytes in it -- length() alone answers 0 for a directory-less path AND
     *  for a directory, and the two must not read the same here. */
    private fun present(file: File) = file.isFile && file.length() > 0L

    /** Where this image stands, without fetching anything. */
    fun status(context: Context, entry: Entry): Status {
        val visible = visibleFile(context, entry)
        val core = coreFile(context, entry)
        val on = when {
            present(visible) -> visible
            // The emulator's own folder counts as downloaded too: that is where an earlier
            // version of this put them, and it is where the core reads from.
            present(core) -> core
            else -> return Status.Missing
        }
        val described = describe(on)
        return if (described == null) Status.Unusable(visible) else Status.Ready(core, described)
    }

    /**
     * Download one image, put it where the core can read it, and work out what it is.
     *
     * It lands in BOTH places, recognised or not. The visible folder is where the author works
     * on it; the emulator's own folder is the only one the core ever reads, and an image that
     * cannot be selected is an image that cannot be tested — which for a BIOS under development
     * is the whole loop. What changes with recognition is what the player is TOLD, not where the
     * file goes.
     */
    fun install(context: Context, entry: Entry): Status {
        val visible = visibleFile(context, entry)
        val core = coreFile(context, entry)
        if (!ArcadeMedia.fetchTo(RAW + entry.file, visible)) {
            Log.w(TAG, "download falhou para ${entry.file} -> ${visible.absolutePath}")
            return Status.Failed("Não foi possível baixar ${entry.file}.")
        }
        val copied = runCatching {
            core.parentFile?.mkdirs()
            visible.copyTo(core, overwrite = true)
            true
        }.getOrElse {
            Log.w(TAG, "não deu para copiar para ${core.absolutePath}: ${it.message}")
            false
        }
        if (!copied) {
            return Status.Failed("Baixada, mas não deu para copiar para a pasta do emulador.")
        }
        val described = describe(core)
        return if (described == null) Status.Unusable(visible) else Status.Ready(core, described)
    }

    /** The file the emulator would boot from, once this image has been downloaded. */
    fun bootableFile(context: Context, entry: Entry): File = coreFile(context, entry)

    /** What the core makes of a file on disk, or null when it is not a BIOS to it. */
    private fun describe(file: File): String? = runCatching {
        val fd = ParcelFileDescriptor.open(file, ParcelFileDescriptor.MODE_READ_ONLY)
        NativeApp.getBiosInfoFromFd(fd.detachFd())?.let { info: BiosInfo ->
            info.description.trim().ifBlank { info.zone }
        }
    }.getOrNull()

    /** Remove both copies. */
    fun remove(context: Context, entry: Entry) {
        runCatching { visibleFile(context, entry).delete() }
        runCatching { coreFile(context, entry).delete() }
    }
}
