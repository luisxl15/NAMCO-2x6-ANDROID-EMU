package com.armsx2.data.library

import android.content.Context
import android.net.Uri
import com.armsx2.runtime.MainActivityRuntime
import java.io.File
import java.io.InputStream
import java.nio.channels.SeekableByteChannel
import java.util.zip.ZipInputStream
import org.apache.commons.compress.archivers.sevenz.SevenZFile

/**
 * Installing a game from the archive it arrived in.
 *
 * Every one of these games is passed around as one archive holding a folder: the CHD, the
 * security dongle and `proverb.elf`. Getting it playable meant extracting it somewhere with a
 * file manager, checking the folder came out named after the gameid, moving it into the ROM
 * directory and only then opening the add-game sheet. Four steps outside the app, each of which
 * ends in "the emulator does not see my game" when one of them goes slightly wrong.
 *
 * ZIP and 7z. Android ships a zip decoder and nothing else, so 7z -- the format half of these
 * games are passed around in -- used to be refused by name; commons-compress reads it now. RAR is
 * still refused by name rather than failing halfway through with something unreadable.
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
        /** The archive's own filename, which is what decides zip or 7z at extraction time. */
        val archiveName: String,
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
    fun unsupported(name: String): Boolean = name.endsWith(".rar", true)

    /** Whether to read this as 7z rather than zip. By extension: the two containers share no
     *  reader, and guessing from the magic bytes would mean opening the file twice. */
    internal fun isSevenZip(name: String): Boolean = name.endsWith(".7z", true)

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
        val read = runCatching { listEntries(context, uri, isSevenZip(displayName), entries) }
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
                archiveName = displayName,
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

    // ---- a folder of archives ------------------------------------------------

    /** What became of one archive in a folder install. */
    data class Result(val archive: String, val ok: Boolean, val message: String)

    /**
     * Install every archive in a list, one after another.
     *
     * Never stops on a failure. Building a collection means pointing at a folder of ten files of
     * which two are a different console's, one is a .7z and one is half-downloaded — and an
     * installer that gives up on the first of those has installed nothing. Each archive gets its
     * own line in the result, and the seven good ones are in the library when it finishes.
     *
     * Blocking and slow: call it off the main thread. [onArchive] announces which one is starting
     * (index is zero-based), [onProgress] carries bytes within the current one.
     */
    fun installAll(
        context: Context,
        archives: List<Pair<Uri, String>>,
        onArchive: (Int, String) -> Unit,
        onProgress: (Long) -> Unit,
    ): List<Result> = archives.mapIndexed { index, (uri, name) ->
        onArchive(index, name)
        when {
            unsupported(name) -> Result(name, false, "não é .zip")
            else -> when (val look = runCatching { inspect(context, uri, name) }
                .getOrElse { Look.Refused(it.message ?: "falha ao ler") }) {
                is Look.Refused -> Result(name, false, look.why)
                is Look.Ready -> {
                    val preview = look.preview
                    if (preview.alreadyThere) {
                        Result(name, false, "${preview.gameId} já está na biblioteca")
                    } else {
                        val problem = install(context, uri, preview, onProgress)
                        if (problem == null) Result(name, true, preview.gameId)
                        else Result(name, false, problem)
                    }
                }
            }
        }
    }

    // ---- reading the two containers ------------------------------------------
    //
    // Zip streams; 7z does not. Its directory is at the END of the file and its entries can be
    // compressed as one solid block, so it needs random access -- which a content URI can still
    // give us, through the file descriptor's channel. That is the whole difference; above this
    // line neither the caller nor the preview knows which kind it is holding.

    private fun listEntries(
        context: Context,
        uri: Uri,
        sevenZip: Boolean,
        into: MutableList<Pair<String, Long>>,
    ): Boolean {
        if (sevenZip) {
            val channel = openChannel(context, uri) ?: return false
            channel.use { ch ->
                SevenZFile.builder().setSeekableByteChannel(ch).get().use { archive ->
                    while (true) {
                        val e = archive.nextEntry ?: break
                        if (!e.isDirectory) {
                            into += e.name.replace('\\', '/') to e.size.coerceAtLeast(0L)
                        }
                    }
                }
            }
            return true
        }
        val stream = openStream(context, uri) ?: return false
        stream.use { input ->
            ZipInputStream(input.buffered()).use { zip ->
                while (true) {
                    val e = zip.nextEntry ?: break
                    if (!e.isDirectory) into += e.name.replace('\\', '/') to e.size.coerceAtLeast(0L)
                    zip.closeEntry()
                }
            }
        }
        return true
    }

    /** Where one entry lands, or null when its name would escape the folder being built. */
    private fun target(staging: File, root: String, name: String): File? {
        val rel = name.replace('\\', '/').trimStart('/').removePrefix(root)
        val out = File(staging, rel)
        // Zip-slip: an entry named ../../something would otherwise be written outside.
        if (!out.canonicalPath.startsWith(staging.canonicalPath + File.separator)) return null
        return out
    }

    private fun extractAll(
        context: Context,
        uri: Uri,
        preview: Preview,
        staging: File,
        onProgress: (Long) -> Unit,
    ): String? {
        var written = 0L
        val buffer = ByteArray(1 shl 16)

        if (isSevenZip(preview.archiveName)) {
            val channel = openChannel(context, uri) ?: return "Não foi possível abrir o arquivo."
            channel.use { ch ->
                SevenZFile.builder().setSeekableByteChannel(ch).get().use { archive ->
                    while (true) {
                        val entry = archive.nextEntry ?: break
                        if (entry.isDirectory) continue
                        val out = target(staging, preview.root, entry.name) ?: continue
                        out.parentFile?.mkdirs()
                        out.outputStream().buffered().use { sink ->
                            while (true) {
                                val n = archive.read(buffer)
                                if (n <= 0) break
                                sink.write(buffer, 0, n)
                                written += n
                                onProgress(written)
                            }
                        }
                    }
                }
            }
            return null
        }

        val stream = openStream(context, uri) ?: return "Não foi possível abrir o arquivo."
        stream.use { input ->
            ZipInputStream(input.buffered()).use { zip ->
                while (true) {
                    val entry = zip.nextEntry ?: break
                    if (entry.isDirectory) { zip.closeEntry(); continue }
                    val out = target(staging, preview.root, entry.name)
                    if (out == null) { zip.closeEntry(); continue }
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
        }
        return null
    }

    /** A seekable view of the archive, which 7z needs and a plain stream cannot give. */
    private fun openChannel(context: Context, uri: Uri): SeekableByteChannel? = runCatching {
        if (uri.scheme == "file") {
            uri.path?.let { java.io.RandomAccessFile(File(it), "r").channel }
        } else {
            context.contentResolver.openFileDescriptor(uri, "r")?.let { pfd ->
                android.os.ParcelFileDescriptor.AutoCloseInputStream(pfd).channel
            }
        }
    }.getOrNull()

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

        val problem = runCatching {
            extractAll(context, uri, preview, staging, onProgress)
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
