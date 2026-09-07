package com.armsx2.data.library

import android.content.Context
import android.net.Uri
import com.armsx2.runtime.MainActivityRuntime
import java.io.File
import java.io.InputStream
import java.util.zip.ZipInputStream

/**
 * Installing a game from the archive it arrived in.
 *
 * Every one of these games is passed around as one archive holding a folder: the CHD, the
 * security dongle and `proverb.elf`. Getting it playable meant extracting it somewhere with a
 * file manager, checking the folder came out named after the gameid, moving it into the ROM
 * directory and only then opening the add-game sheet. Four steps outside the app, each of which
 * ends in "the emulator does not see my game" when one of them goes slightly wrong.
 *
 * Only ZIP. A `.7z` is a different container and Android ships no decoder for it, so it is
 * refused by name rather than failing halfway through with something unreadable.
 *
 * Extraction stages into a `.part` folder and renames at the end, so an install interrupted
 * halfway — the app killed, the storage full — leaves nothing the library will scan and nothing
 * the player has to know to clean up.
 */
object ArcadeZipInstall {

    private val MEDIA_EXT = setOf("chd", "iso", "cso", "zso")

    /** What is in the archive, and what installing it would produce. */
    data class Preview(
        val gameId: String,
        val title: String?,
        /** Entry names, already stripped of the archive's own top-level folder. */
        val files: List<String>,
        /** That folder, with its slash, or "" -- so extraction strips exactly what this did. */
        val root: String,
        val media: String?,
        val dongle: String?,
        val elf: String?,
        /**
         * The archive's own `.acgame`, sitting at its top level beside the payload folder.
         *
         * This is the difference between the two shapes an archive comes in, and it decides
         * where the whole thing is extracted -- see [install]. Null means the archive is a bare
         * payload and a manifest has to be written for it.
         */
        val manifest: String?,
        /** Sum of the uncompressed sizes, or 0 when the archive does not declare them. */
        val totalBytes: Long,
        val destination: File,
        val compat: ArcadeCompat.Entry?,
    ) {
        val complete: Boolean get() = media != null && dongle != null && elf != null
        val hasManifest: Boolean get() = manifest != null
        /** Where it lands: the ROM folder itself for a drop, a folder of its own otherwise. */
        val landsIn: File get() = if (manifest != null) destination.parentFile ?: destination else destination
        val alreadyThere: Boolean
            get() = destination.exists() ||
                (manifest != null && File(destination.parentFile, manifest).exists())
    }

    /**
     * Where a game can be written. The first ROM folder that is a real, writable path.
     *
     * The TREE resolver first, and that is the whole of it. A ROM folder is stored as the tree
     * URI the folder picker handed back -- `.../tree/primary%3Anamco_246%2Froms` -- which has no
     * `/document/` segment, so DocumentsContract.getDocumentId throws on it and the document
     * resolver answers null for every ROM folder the app has. This returned null on a perfectly
     * good folder, and the zip installer refused to open anything. The rest of the app never saw
     * it because the library scan falls back to DocumentFile; this has no such fallback, because
     * extracting gigabytes has to go through the file API.
     */
    fun romsDir(): File? = MainActivityRuntime.romsDirs.value.asSequence()
        .mapNotNull { raw ->
            (MainActivityRuntime.resolveTreeUriToPosix(raw)
                ?: MainActivityRuntime.resolveDocumentUriToPosix(raw)
                ?: raw.takeIf { it.startsWith("/") })
                ?.let(::File)
        }
        .firstOrNull { it.isDirectory && canWrite(it) }

    private fun canWrite(dir: File): Boolean = runCatching {
        val probe = File(dir, ".armsx2_probe")
        probe.writeText("")
        probe.delete()
        true
    }.getOrDefault(false)

    /** True for archives this cannot open — said before anything is extracted. */
    fun unsupported(name: String): Boolean =
        name.endsWith(".7z", true) || name.endsWith(".rar", true)

    /**
     * What came of looking inside an archive.
     *
     * A refusal carries its own sentence, because there are five different ways this can decline
     * and they want five different answers from the player: a folder to fix, a different file to
     * pick, a rename. One "nothing to install" for all of them says only that something is wrong
     * with something.
     */
    sealed interface Look {
        data class Ready(val preview: Preview) : Look
        data class Refused(val why: String) : Look
    }

    /** Read the archive's table of contents. Blocking. */
    fun inspect(context: Context, uri: Uri, displayName: String): Look {
        val roms = romsDir() ?: return Look.Refused(
            "Nenhuma pasta de ROMs com caminho de arquivo real e gravável. " +
                "Use uma pasta na memória interna do aparelho.",
        )
        val entries = mutableListOf<Pair<String, Long>>()
        val read = runCatching {
            openStream(context, uri)?.use { input ->
                ZipInputStream(input.buffered()).use { zip ->
                    while (true) {
                        val e = zip.nextEntry ?: break
                        if (!e.isDirectory) entries += e.name.replace('\\', '/') to e.size.coerceAtLeast(0L)
                        zip.closeEntry()
                    }
                }
                true
            } ?: false
        }
        if (read.isFailure) {
            return Look.Refused("Não consegui ler o arquivo: ${read.exceptionOrNull()?.message}")
        }
        if (read.getOrDefault(false).not()) return Look.Refused("Não consegui abrir o arquivo.")
        if (entries.isEmpty()) return Look.Refused("O arquivo está vazio ou não é um .zip.")

        val root = commonRoot(entries.map { it.first })
        val stripped = entries.map {
            it.first.replace('\\', '/').trimStart('/').removePrefix(root)
        }
        val names = stripped.map { it.substringAfterLast('/') }
        val media = names.firstOrNull { it.substringAfterLast('.', "").lowercase() in MEDIA_EXT }
            ?: return Look.Refused(
                "Não há imagem de jogo (.chd, .iso, .cso ou .zso) dentro do arquivo.",
            )

        val gameId = gameIdFrom(entries.map { it.first }, media, displayName)
            ?: return Look.Refused(
                "Não consegui descobrir o gameid. Nada dentro do arquivo, nem o nome dele, " +
                    "está no formato NMxxxxx.",
            )
        val compat = ArcadeCompat.entryFor(context, gameId)
        return Look.Ready(
            Preview(
            gameId = gameId,
            title = compat?.name?.takeIf { it.isNotBlank() },
            files = stripped,
            root = root,
            media = media,
            dongle = names.firstOrNull { it.endsWith(".ps2", true) },
            elf = names.firstOrNull { it.endsWith(".elf", true) },
            // Only one at the TOP level counts. A .acgame buried inside the payload folder is
            // not the archive's own layout, it is a stray file, and treating it as one would
            // scatter the payload across the ROM folder.
            manifest = stripped.firstOrNull { !it.contains('/') && it.endsWith(".acgame", true) },
            totalBytes = entries.sumOf { it.second },
                destination = File(roms, gameId),
                compat = compat,
            ),
        )
    }

    /**
     * The gameid, from whichever part of the archive states it.
     *
     * In order of how much the source is worth trusting: a folder inside the archive named like a
     * gameid (whoever packed it named it deliberately), then the image's own filename, then the
     * archive's. The last two are how these files are actually distributed.
     */
    internal fun gameIdFrom(rawEntries: List<String>, media: String, archiveName: String): String? {
        rawEntries.forEach { path ->
            path.split('/').forEach { part ->
                if (ArcadePreflight.looksLikeGameId(part.uppercase())) return part.uppercase()
            }
        }
        media.substringBeforeLast('.').uppercase()
            .takeIf { ArcadePreflight.looksLikeGameId(it) }?.let { return it }
        return archiveName.substringBeforeLast('.').uppercase()
            .takeIf { ArcadePreflight.looksLikeGameId(it) }
    }

    /**
     * The single folder an archive wraps everything in, as the prefix to strip ("" when there
     * is none).
     *
     * `NM00004/NM00004.chd` and `NM00004.chd` have to install to the same place, or the payload
     * ends up one level deeper than the manifest says and nothing is found. Worked out once over
     * the whole listing, because a folder is the archive's root only if EVERY entry is inside
     * it -- which no single entry can answer for.
     */
    internal fun commonRoot(paths: List<String>): String {
        val cleaned = paths.map { it.replace('\\', '/').trimStart('/') }
        val first = cleaned.firstOrNull()?.substringBefore('/') ?: return ""
        if (cleaned.none { it.contains('/') }) return ""
        if (!cleaned.all { it.startsWith("$first/") }) return ""
        return "$first/"
    }

    private fun openStream(context: Context, uri: Uri): InputStream? = runCatching {
        if (uri.scheme == "file") uri.path?.let { File(it).inputStream() }
        else context.contentResolver.openInputStream(uri)
    }.getOrNull()

    /**
     * Extract and write the manifest. Returns null on success, else why not.
     *
     * [onProgress] is called with bytes written so far; the caller knows the total from the
     * preview. Blocking, and slow — a CHD is gigabytes.
     */
    fun install(
        context: Context,
        uri: Uri,
        preview: Preview,
        onProgress: (Long) -> Unit,
    ): String? {
        val dest = preview.destination
        val roms = dest.parentFile ?: return "Sem pasta de ROMs."
        if (dest.exists()) return "Já existe uma pasta chamada ${preview.gameId}."
        preview.manifest?.let {
            if (File(roms, it).exists()) return "Já existe um $it na biblioteca."
        }
        val staging = File(roms, ".${preview.gameId}.part")
        runCatching { staging.deleteRecursively() }
        if (!staging.mkdirs()) return "Não foi possível criar a pasta do jogo."

        var written = 0L
        val problem = runCatching {
            openStream(context, uri)?.use { input ->
                ZipInputStream(input.buffered()).use { zip ->
                    val buffer = ByteArray(1 shl 16)
                    while (true) {
                        val entry = zip.nextEntry ?: break
                        if (entry.isDirectory) { zip.closeEntry(); continue }
                        val rel = entry.name.replace('\\', '/').trimStart('/')
                            .removePrefix(preview.root)
                        val out = File(staging, rel)
                        // Zip-slip: an entry named ../../something would otherwise be written
                        // outside the folder we are building.
                        if (!out.canonicalPath.startsWith(staging.canonicalPath + File.separator)) {
                            zip.closeEntry()
                            continue
                        }
                        out.parentFile?.mkdirs()
                        out.outputStream().buffered().use { sink ->
                            while (true) {
                                val n = zip.read(buffer)
                                if (n <= 0) break
                                sink.write(buffer, 0, n)
                                written += n
                                onProgress(written)
                            }
                        }
                        zip.closeEntry()
                    }
                }
                null
            } ?: "Não foi possível abrir o arquivo."
        }.getOrElse { it.message ?: "Falha ao extrair." }

        if (problem != null) {
            runCatching { staging.deleteRecursively() }
            return problem
        }

        // An archive that brought its own manifest is ALREADY in the on-disk layout: the .acgame
        // at the top beside the folder its subdir= names. That is the commonest way these games
        // are passed around, and it has to be extracted into the ROM folder AS IT IS. Dropping
        // the whole thing into a folder of its own instead would put the payload one level below
        // where the manifest says it is -- a game that installs cleanly and then finds nothing.
        //
        // Its own manifest is also kept rather than rewritten: whoever packed it may have set
        // jvsmode or 256Region, and nothing here would reproduce those.
        if (preview.manifest != null) {
            val children = staging.listFiles().orEmpty()
            children.firstOrNull { File(roms, it.name).exists() }?.let { clash ->
                runCatching { staging.deleteRecursively() }
                return "Já existe ${clash.name} na biblioteca."
            }
            val moved = mutableListOf<Pair<File, File>>()
            for (child in children) {
                val target = File(roms, child.name)
                if (child.renameTo(target)) {
                    moved += child to target
                } else {
                    // Put back whatever went, so a half-installed game is never left behind.
                    moved.forEach { (from, to) -> runCatching { to.renameTo(from) } }
                    runCatching { staging.deleteRecursively() }
                    return "Não foi possível mover os arquivos para a biblioteca."
                }
            }
            runCatching { staging.delete() }
            return null
        }

        // No manifest in the archive: what is in staging is the payload itself, so it becomes the
        // folder the written manifest will name.
        if (!staging.renameTo(dest)) {
            runCatching { staging.deleteRecursively() }
            return "Não foi possível mover a pasta para a biblioteca."
        }

        return AcgameWizard.create(
            context,
            AcgameWizard.Candidate(
                parent = AcgameWizard.Location.Path(dest.parentFile!!),
                folderName = dest.name,
                gameId = preview.gameId,
                title = preview.title,
                media = preview.media,
                dongle = preview.dongle,
                elf = preview.elf,
                compat = preview.compat,
            ),
        )
    }
}
