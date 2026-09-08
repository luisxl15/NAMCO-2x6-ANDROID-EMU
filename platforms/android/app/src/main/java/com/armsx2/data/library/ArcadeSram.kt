package com.armsx2.data.library

import android.content.Context
import android.net.Uri
import com.armsx2.runtime.MainActivityRuntime
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * The board's battery-backed memory: the high scores, and everything else in it.
 *
 * A System 246/256 PCB keeps 32 KB of SRAM alive on a battery, and that is where the rankings,
 * the coin bookkeeping and every setting entered through the TEST menu live. The emulator writes
 * it to `sram/<gameid>/sram.bin` beside the .acgame, on a clean shutdown and on every pause. One
 * file, and nothing in the app could copy it anywhere.
 *
 * That is a worse gap than it looks. The file is the only copy: a phone that gets wiped, an app
 * uninstalled with its data, a game folder moved to another card — and the table of records is
 * gone, with no way to have kept it. Memory cards have had backup, restore and export for a long
 * time; this is the arcade equivalent, and the file is 32 KB, so a backup costs nothing.
 *
 * Backups sit in `sram/<gameid>/backup/` beside the live file rather than in app storage, for the
 * reason the artwork does: a folder the player can open, copy and carry is a folder they can fix.
 */
object ArcadeSram {

    /** How many timestamped backups to keep per game before the oldest is dropped. */
    private const val KEEP = 12

    /** The size a real board's chip is; anything else is not one of these files. */
    const val SIZE = 32768L

    data class Snapshot(val file: File, val whenMillis: Long, val bytes: Long) {
        val label: String
            get() = SimpleDateFormat("dd/MM/yyyy HH:mm", Locale.getDefault()).format(Date(whenMillis))
    }

    /**
     * Where this game's SRAM lives, or null when the manifest cannot be resolved to a real path.
     *
     * Mirrors what VMManager does at boot — `sram/<gameid>/<sram name>` under the folder holding
     * the .acgame — rather than guessing, because a path that disagreed with the core's would
     * back up a file nothing writes.
     */
    fun fileFor(uri: String, gameId: String?): File? {
        // Three shapes reach here and all three are ordinary: a document URI from the picker, a
        // file:// URI from a raw folder scan (whose path is percent-encoded -- "teste%20emulador"
        // -- so it has to be parsed rather than trimmed), and a bare path.
        val posix = MainActivityRuntime.resolveDocumentUriToPosix(uri)
            ?: runCatching { Uri.parse(uri) }.getOrNull()?.takeIf { it.scheme == "file" }?.path
            ?: uri.takeIf { it.startsWith("/") }
            ?: return null
        val manifest = File(posix)
        val dir = manifest.parentFile ?: return null
        val id = gameId?.takeIf { it.isNotBlank() } ?: return null
        val name = runCatching {
            ArcadePreflight.parseIni(manifest.readText())["data.sram"]
        }.getOrNull()?.takeIf { it.isNotBlank() } ?: "sram.bin"
        return File(File(File(dir, "sram"), id), name)
    }

    /** True when there is something worth copying: the file exists and has bytes in it. */
    fun exists(sram: File?): Boolean = sram != null && sram.isFile && sram.length() > 0L

    private fun backupDir(sram: File): File = File(sram.parentFile, "backup")

    /** The backups for this game, newest first. */
    fun backups(sram: File?): List<Snapshot> {
        val dir = sram?.let(::backupDir) ?: return emptyList()
        val files = dir.listFiles()?.filter { it.isFile && it.length() > 0L } ?: return emptyList()
        return files.map { Snapshot(it, it.lastModified(), it.length()) }
            .sortedByDescending { it.whenMillis }
    }

    /**
     * Copy the live SRAM into a timestamped backup.
     *
     * Deliberately allowed while the game is running: the core holds the SRAM in memory and
     * flushes it on every pause, so a backup taken from the pause menu is the state on screen.
     */
    fun backup(sram: File?): Result<Snapshot> {
        if (!exists(sram)) {
            return Result.failure(IllegalStateException("Este jogo ainda não gravou nada na SRAM."))
        }
        val file = sram!!
        val dir = backupDir(file).apply { mkdirs() }
        val stamp = SimpleDateFormat("yyyyMMdd-HHmmss", Locale.US).format(Date())
        val out = File(dir, "$stamp.bin")
        return runCatching {
            file.copyTo(out, overwrite = true)
            prune(dir)
            Snapshot(out, out.lastModified(), out.length())
        }
    }

    /** Keep the newest [KEEP]: a 32 KB file is cheap but not free, and a folder of 400 is noise. */
    private fun prune(dir: File) {
        val files = dir.listFiles()?.filter { it.isFile }?.sortedByDescending { it.lastModified() }
            ?: return
        files.drop(KEEP).forEach { runCatching { it.delete() } }
    }

    /**
     * Put a backup back.
     *
     * Refused while a game is running, for the same reason restoring a memory card is: the core
     * keeps its own copy of the SRAM in memory and writes it out on the next pause, straight over
     * whatever was just restored.
     */
    fun restore(sram: File?, snapshot: Snapshot, running: Boolean): Result<Unit> {
        if (running) {
            return Result.failure(
                IllegalStateException(
                    "Feche o jogo antes de restaurar. Enquanto ele roda, o emulador mantém a " +
                        "própria cópia da SRAM e gravaria por cima da restaurada.",
                ),
            )
        }
        val file = sram ?: return Result.failure(IllegalStateException("Sem pasta de SRAM para este jogo."))
        return runCatching {
            file.parentFile?.mkdirs()
            snapshot.file.copyTo(file, overwrite = true)
            Unit
        }
    }

    /** Write the live SRAM out to a place the player chose. */
    fun export(context: Context, sram: File?, target: Uri): Result<Unit> {
        if (!exists(sram)) {
            return Result.failure(IllegalStateException("Este jogo ainda não gravou nada na SRAM."))
        }
        return runCatching {
            context.contentResolver.openOutputStream(target)?.use { out ->
                sram!!.inputStream().use { it.copyTo(out) }
            } ?: throw IllegalStateException("Não foi possível escrever no arquivo escolhido.")
            Unit
        }
    }

    /**
     * Read a file the player chose back into this game's SRAM.
     *
     * The size is checked because it is the only thing that can be: these images have no header
     * and no signature — the per-game layout is the whole content — so a wrong file would be
     * accepted in silence and the game would boot to its backup-error screen. 32 KB does not
     * prove the file belongs to THIS game, but it does rule out a photo.
     */
    fun import(context: Context, sram: File?, source: Uri, running: Boolean): Result<Unit> {
        if (running) {
            return Result.failure(IllegalStateException("Feche o jogo antes de importar uma SRAM."))
        }
        val file = sram ?: return Result.failure(IllegalStateException("Sem pasta de SRAM para este jogo."))
        return runCatching {
            val bytes = context.contentResolver.openInputStream(source)?.use { it.readBytes() }
                ?: throw IllegalStateException("Não foi possível ler o arquivo escolhido.")
            if (bytes.size.toLong() != SIZE) {
                throw IllegalStateException(
                    "Isso não é uma SRAM de placa: tem ${bytes.size} bytes e uma SRAM tem $SIZE.",
                )
            }
            file.parentFile?.mkdirs()
            file.writeBytes(bytes)
        }
    }

    /** A name worth offering when exporting: the game id and the day. */
    fun exportName(gameId: String?): String {
        val stamp = SimpleDateFormat("yyyyMMdd", Locale.US).format(Date())
        val id = gameId.orEmpty().ifBlank { "sram" }
        return "$id-$stamp.bin"
    }
}
