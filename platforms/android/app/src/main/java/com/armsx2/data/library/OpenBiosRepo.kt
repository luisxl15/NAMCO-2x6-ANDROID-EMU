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
 * Nothing here decides whether an image is any good. A download is handed straight to the core's
 * own `IsBIOSFromFd`, which is the same check the BIOS list uses, and whatever it answers is what
 * the player is told: a board name when the image is real, and a plain "not recognised" when it
 * is not. That distinction matters more here than anywhere else in the app -- an open BIOS under
 * development will spend a long time being the second thing, and saying so is the difference
 * between a project you can measure and a black screen.
 */
object OpenBiosRepo {

    private const val TAG = "OpenBiosRepo"

    private const val OWNER = "luisxl15"
    private const val REPO = "BasicInput_Output_Sys_Namco_246_256"
    private const val BRANCH = "main"

    private const val RAW = "https://raw.githubusercontent.com/$OWNER/$REPO/$BRANCH/"
    private const val LISTING = "https://api.github.com/repos/$OWNER/$REPO/contents/?ref=$BRANCH"

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

    /** What happened to an image once the core looked at it. */
    sealed interface Outcome {
        /** The core recognised it; [description] is what the BIOS list will show. */
        data class Installed(val file: File, val description: String) : Outcome

        /** Downloaded, kept, and not recognised as a BIOS. Says so rather than pretending. */
        data class NotRecognised(val file: File) : Outcome

        data class Failed(val why: String) : Outcome
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

    /** True once this image is in the BIOS folder. */
    fun isInstalled(context: Context, entry: Entry): Boolean =
        File(MainActivityRuntime.internalBiosDir(context), entry.name).length() > 0L

    /**
     * Download one image into the BIOS folder and ask the core what it is.
     *
     * The file is kept either way. An image the core does not recognise is not rubbish — for an
     * open BIOS it is the normal state of the work in progress — and deleting it would take away
     * the thing the author is trying to iterate on.
     */
    fun install(context: Context, entry: Entry): Outcome {
        val dir = MainActivityRuntime.internalBiosDir(context).apply { mkdirs() }
        val dest = File(dir, entry.name)
        if (!ArcadeMedia.fetchTo(RAW + entry.file, dest)) {
            return Outcome.Failed("Não foi possível baixar ${entry.file}.")
        }
        val info = describe(dest)
        return if (info == null) Outcome.NotRecognised(dest) else Outcome.Installed(dest, info)
    }

    /** What the core makes of a file on disk, or null when it is not a BIOS to it. */
    private fun describe(file: File): String? = runCatching {
        val fd = ParcelFileDescriptor.open(file, ParcelFileDescriptor.MODE_READ_ONLY)
        NativeApp.getBiosInfoFromFd(fd.detachFd())?.let { info: BiosInfo ->
            info.description.trim().ifBlank { info.zone }
        }
    }.getOrNull()

    fun remove(context: Context, entry: Entry): Boolean = runCatching {
        File(MainActivityRuntime.internalBiosDir(context), entry.name).delete()
    }.getOrDefault(false)
}
