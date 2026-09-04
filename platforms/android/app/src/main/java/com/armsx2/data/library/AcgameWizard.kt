package com.armsx2.data.library

import android.content.Context
import android.net.Uri
import androidx.documentfile.provider.DocumentFile
import kr.co.iefriends.pcsx2.NativeApp
import com.armsx2.runtime.MainActivityRuntime
import java.io.File

/**
 * Finds arcade games that are on disk but have no `.acgame` manifest, and writes one.
 *
 * Adding a game meant hand-writing an INI. Not a hard one, but it has to be exactly right --
 * the gameid drives the dongle, the SRAM path and the JVS mode, and a typo produces a boot
 * failure with no obvious cause. Everything that file needs is already sitting in the folder:
 * the media is `<gameid>.chd`, the dongle `<gameid>.ps2`, the bootloader `proverb.elf`, and the
 * gameid is the folder's own name. So there is nothing to ask the user that cannot be read.
 *
 * Deliberately NOT a folder picker. A game arrives by copying its folder into the ROM directory,
 * which the app already has permission to read -- asking for that folder a second time through
 * the storage picker would be asking the user to find something we are already looking at.
 */
object AcgameWizard {

    /** A payload folder with no manifest beside it. */
    data class Candidate(
        /** Where the manifest will be written, and how. */
        val parent: Location,
        val folderName: String,
        val gameId: String,
        /** From the game database, when it knows this id. */
        val title: String?,
        val media: String?,
        val dongle: String?,
        val elf: String?,
        /** What the project's compatibility list says about this id, when it knows it. */
        val compat: ArcadeCompat.Entry? = null,
    ) {
        /** A board without its dongle cannot boot; say so before the manifest is written. */
        val complete: Boolean get() = media != null && dongle != null && elf != null
    }

    /** The ROM directory a manifest goes into, in whichever form we can write to it. */
    sealed interface Location {
        data class Tree(val uri: Uri) : Location
        data class Path(val dir: File) : Location
    }

    private val MEDIA = setOf("chd", "iso", "cso", "zso")

    /** Every folder under the ROM directories that looks like a game and has no manifest. */
    fun findCandidates(context: Context): List<Candidate> {
        val out = mutableListOf<Candidate>()
        val compat = ArcadeCompat.all(context)
        MainActivityRuntime.romsDirs.value.forEach { raw ->
            val posix = MainActivityRuntime.resolveDocumentUriToPosix(raw)
                ?: raw.takeIf { it.startsWith("/") }
            if (posix != null && File(posix).canRead()) {
                scanRaw(File(posix), out, compat)
            } else {
                runCatching { DocumentFile.fromTreeUri(context, Uri.parse(raw)) }
                    .getOrNull()?.let { scanTree(it, out, compat) }
            }
        }
        return out.sortedBy { it.gameId }
    }

    private fun scanRaw(dir: File, out: MutableList<Candidate>, compat: Map<String, ArcadeCompat.Entry>) {
        val children = dir.listFiles() ?: return
        val manifests = children.filter { it.isFile && it.extension.equals("acgame", true) }
            .map { it.nameWithoutExtension.lowercase() }.toSet()
        children.filter { it.isDirectory && it.name.lowercase() !in manifests }.forEach { folder ->
            val inside = folder.listFiles()?.filter { it.isFile }.orEmpty()
            val media = inside.firstOrNull { it.extension.lowercase() in MEDIA }?.name ?: return@forEach
            out += build(
                parent = Location.Path(dir),
                folderName = folder.name,
                media = media,
                dongle = inside.firstOrNull { it.extension.equals("ps2", true) }?.name,
                elf = inside.firstOrNull { it.extension.equals("elf", true) }?.name,
                compat = compat,
            )
        }
    }

    private fun scanTree(dir: DocumentFile, out: MutableList<Candidate>, compat: Map<String, ArcadeCompat.Entry>) {
        val children = runCatching { dir.listFiles() }.getOrNull() ?: return
        val manifests = children.filter { !it.isDirectory }
            .mapNotNull { it.name?.takeIf { n -> n.endsWith(".acgame", true) }?.substringBeforeLast('.') }
            .map { it.lowercase() }.toSet()
        children.filter { it.isDirectory && it.name?.lowercase() !in manifests }.forEach { folder ->
            val inside = runCatching { folder.listFiles() }.getOrNull()?.filter { !it.isDirectory }.orEmpty()
            val media = inside.mapNotNull { it.name }
                .firstOrNull { it.substringAfterLast('.', "").lowercase() in MEDIA } ?: return@forEach
            out += build(
                parent = Location.Tree(dir.uri),
                folderName = folder.name.orEmpty(),
                media = media,
                dongle = inside.mapNotNull { it.name }.firstOrNull { it.endsWith(".ps2", true) },
                elf = inside.mapNotNull { it.name }.firstOrNull { it.endsWith(".elf", true) },
                compat = compat,
            )
        }
    }

    private fun build(
        parent: Location,
        folderName: String,
        media: String,
        dongle: String?,
        elf: String?,
        compat: Map<String, ArcadeCompat.Entry>,
    ): Candidate {
        // The folder name is the gameid by convention; fall back to the media's name when the
        // folder was renamed, since that is what the dongle and the CHD are named after.
        val id = folderName.takeIf { looksLikeGameId(it) }
            ?: media.substringBeforeLast('.').takeIf { looksLikeGameId(it) }
            ?: folderName
        return Candidate(
            parent = parent,
            folderName = folderName,
            gameId = id.uppercase(),
            // The same lookup the library names a game with; it answers with the three titles
            // newline-separated, and the first is the display name.
            title = runCatching { NativeApp.getTitlesForSerial(id.uppercase()) }
                .getOrNull()?.substringBefore('\n')?.takeIf { it.isNotBlank() },
            media = media,
            dongle = dongle,
            elf = elf,
            compat = compat[id.uppercase()],
        )
    }

    /** "NM" followed by five digits — the shape VMManager validates before it will boot. */
    private fun looksLikeGameId(s: String): Boolean =
        s.length == 7 && s.startsWith("NM", ignoreCase = true) && s.drop(2).all(Char::isDigit)

    /**
     * The manifest text.
     *
     * Board and media come from the compatibility list rather than a default. Both matter: a
     * System246 title can refuse to boot on a 256 BIOS (Battle Gear 3 does), and the media type
     * decides how the image is mounted. Guessing "256 / DVD" for everything was right often
     * enough to be misleading and wrong exactly where it hurts.
     */
    fun manifestFor(c: Candidate): String {
        val board = c.compat?.board?.takeIf { it.isNotBlank() } ?: DEFAULT_BOARD
        val media = c.compat?.media?.takeIf { it.isNotBlank() } ?: DEFAULT_MEDIA
        val name = c.title ?: c.compat?.name ?: c.gameId
        return buildString {
            appendLine("[game]")
            appendLine("name=$name")
            appendLine("gameid=${c.gameId}")
            appendLine("platform=$board")
            appendLine()
            appendLine("[data]")
            appendLine("subdir=${c.folderName}")
            appendLine("elf=${c.elf ?: "proverb.elf"}")
            appendLine("dongle=${c.dongle ?: "${c.gameId}.ps2"}")
            appendLine("mediasrc=${c.media}")
            appendLine("media=$media")
        }
    }

    /** Only reached for an id the list has never heard of. 256 runs 246 titles; DVD is the
     *  commonest image. Both are the least-bad answer, not a good one. */
    private const val DEFAULT_BOARD = "256"
    private const val DEFAULT_MEDIA = "DVD"


    /** Write the manifest beside the payload folder. Returns null on success, else why not. */
    fun create(context: Context, c: Candidate): String? {
        val text = manifestFor(c)
        val fileName = "${c.gameId}.acgame"
        return runCatching {
            when (val at = c.parent) {
                is Location.Path -> {
                    File(at.dir, fileName).writeText(text)
                    null
                }
                is Location.Tree -> {
                    val dir = DocumentFile.fromTreeUri(context, at.uri)
                        ?: return "Sem acesso à pasta de ROMs."
                    // An existing one is replaced rather than duplicated: SAF would otherwise
                    // create "NM00004 (1).acgame", which the scanner reads as a second game.
                    dir.findFile(fileName)?.delete()
                    val doc = dir.createFile("application/octet-stream", fileName)
                        ?: return "Não foi possível criar o arquivo."
                    context.contentResolver.openOutputStream(doc.uri)?.use {
                        it.write(text.toByteArray())
                    } ?: return "Não foi possível gravar o arquivo."
                    null
                }
            }
        }.getOrElse { it.message ?: "Falha ao gravar o manifesto." }
    }
}
