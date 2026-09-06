package com.armsx2.data.library

import android.content.Context
import com.armsx2.runtime.MainActivityRuntime
import java.io.File

/**
 * The manifest, corrected against what is actually in the folder.
 *
 * [ArcadePreflight] says what is missing; this is the half that does something about it. Most of
 * what it finds is not a missing file at all — the file is there under another name, and the
 * `.acgame` says the old one. `elf=boot.elf` beside a `proverb.elf`, `mediasrc=NM00021.chd` beside
 * `disc.chd`, a `subdir` naming a folder that got renamed on the way in. Every one of those ends
 * the same way: a black screen, and a text file the player is expected to open and edit by hand.
 *
 * A change is only proposed when the answer is unambiguous — one candidate of that kind in the
 * folder, and no doubt about which it is. Two ELFs and it says nothing, because a wrong guess
 * here rewrites the file that boots the board.
 *
 * The decision is separated from the disk ([plan] takes [Facts], not a directory) so the rules can
 * be checked without a device, which matters: this writes to a file the player did not ask us to
 * touch.
 */
object ArcadeRepair {

    private val MEDIA_EXT = setOf("chd", "iso", "cso", "zso")

    /** One line of the manifest, and why it is wrong. */
    data class Change(
        val section: String,
        val key: String,
        val from: String?,
        val to: String,
        val why: String,
    )

    /** What the folder actually contains. */
    data class Facts(
        val gameId: String,
        /** The gameid the manifest's own filename implies, when it implies one. */
        val fileGameId: String?,
        /** Filenames in the payload folder. */
        val baseNames: List<String>,
        /** Folders beside the manifest that hold a media image — subdir candidates. */
        val mediaDirs: List<String>,
        val baseDirExists: Boolean,
        /** "CD" / "DVD" / "HDD" from the compatibility list, when it knows this game. */
        val compatMedia: String?,
    )

    /**
     * What would be changed, in the order it would be written. Empty means nothing here is
     * safely fixable — which is not the same as nothing being wrong.
     */
    internal fun plan(ini: Map<String, String>, f: Facts): List<Change> {
        val out = mutableListOf<Change>()

        // The gameid names the dongle, the SRAM folder and the patches, so it is fixed first and
        // everything below is resolved against the corrected one.
        var gameId = f.gameId
        if (!ArcadePreflight.looksLikeGameId(gameId) && f.fileGameId != null) {
            out += Change(
                "game", "gameid", gameId.ifBlank { null }, f.fileGameId,
                "o arquivo se chama ${f.fileGameId}.acgame",
            )
            gameId = f.fileGameId
        }
        if (!ArcadePreflight.looksLikeGameId(gameId)) return out

        // A payload folder that is not where the manifest says. Only when exactly one folder
        // beside the manifest holds a game image: with two, picking one is a guess.
        if (!f.baseDirExists && f.mediaDirs.size == 1) {
            out += Change(
                "data", "subdir", ini["data.subdir"], f.mediaDirs.first(),
                "a pasta com a imagem se chama ${f.mediaDirs.first()}",
            )
            // Nothing below can be judged: baseNames were read from a folder that does not exist.
            return out
        }
        if (!f.baseDirExists) return out

        fun only(pick: (String) -> Boolean): String? =
            f.baseNames.filter(pick).takeIf { it.size == 1 }?.first()

        val dongle = ini["data.dongle"] ?: "$gameId.ps2"
        if (dongle.isNotBlank() && dongle !in f.baseNames) {
            only { it.endsWith(".ps2", true) }?.let {
                out += Change("data", "dongle", ini["data.dongle"], it, "é o único .ps2 na pasta")
            }
        }

        val elf = ini["data.elf"] ?: "boot.elf"
        if (elf !in f.baseNames) {
            // proverb.elf is the boot loader every one of these boards uses, so when it is there
            // it is the answer even if the folder also carries some other ELF.
            val pick = f.baseNames.firstOrNull { it.equals("proverb.elf", true) }
                ?: only { it.endsWith(".elf", true) }
            pick?.let {
                out += Change(
                    "data", "elf", ini["data.elf"], it,
                    if (it.equals("proverb.elf", true)) "é o carregador destas placas" else "é o único .elf na pasta",
                )
            }
        }

        val mediaSrc = ini["data.mediasrc"] ?: "$gameId.chd"
        if (mediaSrc !in f.baseNames) {
            only { it.substringAfterLast('.', "").lowercase() in MEDIA_EXT }?.let {
                out += Change("data", "mediasrc", ini["data.mediasrc"], it, "é a única imagem na pasta")
            }
        }

        if (ini["data.media"].isNullOrBlank() && !f.compatMedia.isNullOrBlank()) {
            out += Change(
                "data", "media", ini["data.media"], f.compatMedia,
                "é o que a lista de compatibilidade diz",
            )
        }

        return out
    }

    /**
     * The manifest text with [changes] written in.
     *
     * Line-edited rather than regenerated. A `.acgame` can carry things this app knows nothing
     * about — `args=`, `jvsmode=`, `256Region=`, a comment somebody left themselves — and
     * rewriting the file from a template would quietly drop every one of them.
     */
    internal fun applyTo(text: String, changes: List<Change>): String {
        if (changes.isEmpty()) return text
        val lines = text.lines().toMutableList()
        val remaining = changes.toMutableList()

        var section = ""
        var i = 0
        // Pass one: rewrite the lines that already exist.
        while (i < lines.size) {
            val line = lines[i]
            val trimmed = line.trim()
            if (trimmed.startsWith('[') && trimmed.endsWith(']')) {
                section = trimmed.substring(1, trimmed.length - 1).trim().lowercase()
            } else {
                val eq = trimmed.indexOf('=')
                if (eq > 0 && !trimmed.startsWith(';') && !trimmed.startsWith('#')) {
                    val key = trimmed.substring(0, eq).trim().lowercase()
                    remaining.firstOrNull { it.section == section && it.key.lowercase() == key }
                        ?.let { c ->
                            lines[i] = "${trimmed.substring(0, eq).trim()}=${c.to}"
                            remaining.remove(c)
                        }
                }
            }
            i++
        }

        // Pass two: the keys that were absent go under their section, at its end, so a file with
        // no [data] block at all still comes out valid.
        for (c in remaining.toList()) {
            val header = lines.indexOfFirst { it.trim().equals("[${c.section}]", true) }
            if (header < 0) {
                if (lines.lastOrNull()?.isNotBlank() == true) lines += ""
                lines += "[${c.section}]"
                lines += "${c.key}=${c.to}"
            } else {
                var end = header + 1
                while (end < lines.size && !lines[end].trim().startsWith('[')) end++
                // Back up over the blank lines that separate this block from the next.
                while (end - 1 > header && lines[end - 1].isBlank()) end--
                lines.add(end, "${c.key}=${c.to}")
            }
            remaining.remove(c)
        }

        return lines.joinToString("\n")
    }

    // ---- disk ------------------------------------------------------------

    private fun posixOf(uri: String): String? =
        MainActivityRuntime.resolveDocumentUriToPosix(uri) ?: uri.takeIf { it.startsWith("/") }

    /** Read the folder and work out what would change. Blocking; call it off the main thread. */
    fun plan(context: Context, uri: String): List<Change> {
        val manifest = posixOf(uri)?.let(::File) ?: return emptyList()
        val text = runCatching { manifest.readText() }.getOrNull() ?: return emptyList()
        val ini = ArcadePreflight.parseIni(text)
        val acgameDir = manifest.parentFile ?: return emptyList()
        val gameId = ini["game.gameid"].orEmpty().trim()
        val fileGameId = manifest.name.substringBeforeLast('.').uppercase()
            .takeIf { ArcadePreflight.looksLikeGameId(it) }

        val effectiveId = gameId.takeIf { ArcadePreflight.looksLikeGameId(it) } ?: fileGameId.orEmpty()
        val subdir = ini["data.subdir"] ?: effectiveId
        val baseDir = if (subdir.isBlank()) acgameDir else File(acgameDir, subdir)

        val siblings = acgameDir.listFiles().orEmpty()
        return plan(
            ini,
            Facts(
                gameId = gameId,
                fileGameId = fileGameId,
                baseNames = baseDir.listFiles().orEmpty().filter(File::isFile).map { it.name },
                mediaDirs = siblings.filter { it.isDirectory }
                    .filter { dir ->
                        dir.listFiles().orEmpty().any {
                            it.isFile && it.extension.lowercase() in MEDIA_EXT
                        }
                    }
                    .map { it.name },
                baseDirExists = baseDir.isDirectory,
                compatMedia = ArcadeCompat.entryFor(context, effectiveId)?.media,
            ),
        )
    }

    /**
     * Write the corrected manifest. Returns null on success, else why not.
     *
     * The original is kept as `<name>.acgame.bak`, once: this is someone else's file, and the
     * first thing anyone will want if the repair guessed wrong is the version they had.
     */
    fun apply(context: Context, uri: String, changes: List<Change>): String? {
        if (changes.isEmpty()) return null
        val manifest = posixOf(uri)?.let(::File) ?: return "Sem acesso ao arquivo."
        val text = runCatching { manifest.readText() }.getOrNull() ?: return "Não foi possível ler o manifesto."
        val backup = File(manifest.parentFile, "${manifest.name}.bak")
        return runCatching {
            if (!backup.exists()) manifest.copyTo(backup, overwrite = false)
            manifest.writeText(applyTo(text, changes))
            null
        }.getOrElse { it.message ?: "Não foi possível gravar o manifesto." }
    }
}
