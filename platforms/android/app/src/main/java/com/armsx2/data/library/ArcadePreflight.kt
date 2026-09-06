package com.armsx2.data.library

import android.content.Context
import android.os.ParcelFileDescriptor
import androidx.compose.runtime.mutableStateOf
import com.armsx2.BiosInfo
import com.armsx2.runtime.MainActivityRuntime
import kr.co.iefriends.pcsx2.NativeApp
import java.io.File

/**
 * What this game needs, checked before the board is asked to boot it.
 *
 * An arcade launch fails silently. A missing dongle, a half-copied CHD, a manifest naming a boot
 * ELF that is not there — every one of those ends at a black screen, and the sentence explaining
 * it goes to the native console, which on a phone nobody reads. The player is left with a game
 * that "does not work" and no way to tell which of six things is wrong.
 *
 * So the same conditions the loader will fail on are checked here first, from the same manifest,
 * in the same order, and said out loud. The rules are copied from `VMManager::ApplyBootParameters`
 * (the `isArcadeManifest` branch) rather than invented: the gameid shape, the subdir default, the
 * dongle search through the payload folder / the manifest folder / memcards, the elf default of
 * `boot.elf`, the mediasrc default of `<gameid>.chd`. Anything this reports as blocking is
 * something the loader itself would refuse.
 *
 * It never has the last word. A check that is wrong must cost one extra tap, not a game the
 * player can no longer start — so a blocked launch is offered anyway, and the answer is
 * remembered for the session.
 */
object ArcadePreflight {

    data class Problem(
        /** True when the loader will refuse the boot outright, not merely do something worse. */
        val blocking: Boolean,
        val what: String,
        val fix: String,
    )

    data class Report(val gameId: String?, val problems: List<Problem>) {
        val blocking: Boolean get() = problems.any { it.blocking }
        val ok: Boolean get() = problems.isEmpty()
    }

    /** A launch held back, and the way to let it through. Read by the dialog. */
    class Held(val title: String, val report: Report, val proceed: () -> Unit)

    val held = mutableStateOf<Held?>(null)

    /** Launches the player chose to start anyway. Session-only: a fixed folder should be re-checked
     *  next time, and an unfixed one should not nag on every boot of the same sitting. */
    private val confirmed = mutableSetOf<String>()

    // ---- the check -------------------------------------------------------

    private fun isManifest(path: String) = path.endsWith(".acgame", ignoreCase = true)

    /**
     * Inspect one `.acgame`.
     *
     * [uri] is whatever the launcher would hand the core: a POSIX path, or a document URI that
     * gets resolved the same way the launch does. Cheap enough to run on the main thread — it
     * reads one small INI and asks the filesystem about half a dozen names.
     */
    fun inspect(context: Context, uri: String): Report {
        val posix = MainActivityRuntime.resolveDocumentUriToPosix(uri)
            ?: uri.takeIf { it.startsWith("/") }
        // The launch itself cannot recover from this: the core takes the manifest's DIRECTORY and
        // looks inside it for the dongle and the image, and a content:// path is not a directory
        // anything can open.
        if (posix == null) {
            return Report(
                null,
                listOf(
                    Problem(
                        blocking = true,
                        what = "Esta pasta de jogos não tem um caminho real que o emulador consiga abrir.",
                        fix = "Copie os jogos para a memória interna (por exemplo Download/roms) e " +
                            "escolha essa pasta em Configurações.",
                    ),
                ),
            )
        }

        val manifest = File(posix)
        val text = runCatching { manifest.readText() }.getOrNull()
        if (text.isNullOrBlank()) {
            return Report(
                null,
                listOf(
                    Problem(
                        blocking = true,
                        what = "O manifesto ${manifest.name} não pôde ser lido.",
                        fix = "Confira se o arquivo existe e não está vazio.",
                    ),
                ),
            )
        }

        val ini = parseIni(text)
        val problems = mutableListOf<Problem>()
        val gameId = ini["game.gameid"].orEmpty().trim()

        // "Invalid GameID!" -- the loader's own test, letter for letter: NM plus five digits.
        if (!looksLikeGameId(gameId)) {
            problems += Problem(
                blocking = true,
                what = "O gameid \"$gameId\" não tem o formato NMxxxxx.",
                fix = "Corrija a linha gameid= na seção [game] do arquivo .acgame.",
            )
            // Everything below is named after the gameid, so there is nothing further to say.
            return Report(gameId.takeIf { it.isNotBlank() }, problems)
        }

        val acgameDir = manifest.parentFile ?: File("/")
        // Missing key = the gameid; present but empty = the manifest's own folder. Both are the
        // loader's behaviour, and they are not the same thing.
        val subdir = ini["data.subdir"] ?: gameId
        val baseDir = if (subdir.isBlank()) acgameDir else File(acgameDir, subdir)

        if (!baseDir.isDirectory) {
            problems += Problem(
                blocking = true,
                what = "A pasta de dados \"${baseDir.name}\" não está ao lado do manifesto.",
                fix = "O .acgame e a pasta com o CHD, o dongle e o ELF ficam juntos.",
            )
            return Report(gameId, problems)
        }

        val memcards = File(MainActivityRuntime.assetCopyRoot(context), "memcards")

        // Slot 1 is the dongle, and without it the board stops before it draws anything. The
        // loader looks in the payload folder, then beside the manifest, then in memcards/.
        val dongle = ini["data.dongle"] ?: "$gameId.ps2"
        if (dongle.isNotBlank() && findCard(dongle, baseDir, acgameDir, memcards) == null) {
            problems += Problem(
                blocking = true,
                what = "O dongle \"$dongle\" não foi encontrado.",
                fix = "Sem o dongle a placa não dá boot. Coloque o arquivo .ps2 dentro da pasta do jogo.",
            )
        }

        // Slot 2 is the save card, and only some games declare one.
        val card = ini["data.card"].orEmpty()
        if (card.isNotBlank() && findCard(card, baseDir, acgameDir, memcards) == null) {
            problems += Problem(
                blocking = true,
                what = "O cartão de memória \"$card\" pedido pelo manifesto não foi encontrado.",
                fix = "Coloque o arquivo na pasta do jogo, ou apague a linha card= do .acgame.",
            )
        }

        // The default really is boot.elf, and the manifests this app writes say proverb.elf --
        // so a manifest with no elf= line beside a proverb.elf is a real failure, not a nitpick.
        val elf = (ini["data.elf"] ?: "boot.elf").trim()
        if (elf.isNotBlank() && !File(baseDir, elf).isFile) {
            problems += Problem(
                blocking = true,
                what = "O arquivo de boot \"$elf\" não está na pasta do jogo.",
                fix = "Estas placas dão boot pelo proverb.elf; ajuste a linha elf= ou copie o arquivo.",
            )
        }

        val mediaSrc = (ini["data.mediasrc"] ?: "$gameId.chd").trim()
        val image = File(baseDir, mediaSrc)
        when {
            !image.isFile -> problems += Problem(
                blocking = true,
                what = "A imagem \"$mediaSrc\" não está na pasta do jogo.",
                fix = "É o CHD (ou ISO) do jogo, e o nome tem que bater com a linha mediasrc=.",
            )
            image.length() == 0L -> problems += Problem(
                blocking = true,
                what = "A imagem \"$mediaSrc\" está com 0 byte.",
                fix = "A cópia não terminou. Copie o arquivo de novo.",
            )
        }

        // Only three values exist, and a fourth stops the boot with a message about signatures
        // that means nothing to anyone who has not read the loader.
        val region = ini["data.256region"].orEmpty().trim()
        if (region.isNotBlank() && region !in setOf("ASIA4", "ASIA5", "JAPAN")) {
            problems += Problem(
                blocking = true,
                what = "256Region=\"$region\" não é um valor válido.",
                fix = "Só valem ASIA4, ASIA5 e JAPAN — ou apague a linha.",
            )
        }

        // Media type: blank here means the core asks its game database. It usually knows, so this
        // is a warning rather than a refusal -- the compatibility list is a second opinion, not
        // the same source.
        if (ini["data.media"].isNullOrBlank() &&
            ArcadeCompat.entryFor(context, gameId)?.media.isNullOrBlank()
        ) {
            problems += Problem(
                blocking = false,
                what = "O manifesto não diz o tipo de mídia (CD, DVD ou HDD).",
                fix = "Se o jogo não iniciar, acrescente media=DVD na seção [data].",
            )
        }

        problems += biosProblems(context, gameId)

        // The board's own settings live in sram/<gameid>/ beside the manifest, created at boot. On
        // a read-only card that fails, and the game starts every time on the backup-error screen
        // with the operator settings gone -- which reads as the game being broken.
        if (!canWrite(acgameDir)) {
            problems += Problem(
                blocking = false,
                what = "A pasta do jogo não aceita gravação.",
                fix = "As configurações da placa (SRAM) não vão ser salvas; use uma pasta na memória interna.",
            )
        }

        ArcadeCompat.entryFor(context, gameId)?.let { entry ->
            when (entry.status) {
                ArcadeCompat.Status.Attract -> problems += Problem(
                    blocking = false,
                    what = "Na lista de compatibilidade este jogo só chega à tela de atração.",
                    fix = "Ele deve iniciar, mas não deve ser jogável.",
                )
                ArcadeCompat.Status.Untested -> problems += Problem(
                    blocking = false,
                    what = "Este jogo nunca foi testado na lista de compatibilidade.",
                    fix = "Pode funcionar; ninguém confirmou.",
                )
                else -> Unit
            }
        }

        return Report(gameId, problems)
    }

    /** "NM" plus five digits -- the shape the loader validates before it will boot. */
    internal fun looksLikeGameId(s: String): Boolean =
        s.length == 7 && s.startsWith("NM", ignoreCase = true) && s.drop(2).all(Char::isDigit)

    /** The loader's card search: payload folder, then the manifest's folder, then memcards/. */
    private fun findCard(name: String, base: File, acgame: File, memcards: File): File? =
        listOf(File(base, name), File(acgame, name), File(memcards, name)).firstOrNull { it.isFile }

    private fun canWrite(dir: File): Boolean = runCatching {
        val probe = File(dir, ".armsx2_probe")
        probe.writeText("")
        probe.delete()
        true
    }.getOrDefault(false)

    // ---- BIOS ------------------------------------------------------------

    /** Parsed BIOS images, keyed by the folder's contents so a newly-installed one is seen. */
    private var biosCache: Pair<String, Map<String, BiosInfo>>? = null

    private fun installedBios(context: Context): Map<String, BiosInfo> {
        val files = runCatching {
            MainActivityRuntime.internalBiosDir(context).listFiles().orEmpty().filter(File::isFile)
        }.getOrDefault(emptyList())
        val signature = files.sortedBy { it.name }.joinToString("|") { "${it.name}:${it.length()}" }
        biosCache?.let { (sig, map) -> if (sig == signature) return map }
        val parsed = files.mapNotNull { f ->
            runCatching {
                val fd = ParcelFileDescriptor.open(f, ParcelFileDescriptor.MODE_READ_ONLY)
                NativeApp.getBiosInfoFromFd(fd.detachFd())?.let { f.name to it }
            }.getOrNull()
        }.toMap()
        biosCache = signature to parsed
        return parsed
    }

    private fun biosProblems(context: Context, gameId: String): List<Problem> {
        val installed = installedBios(context)
        val settings = runCatching { com.armsx2.config.ConfigStore.resolveForGame(gameId) }.getOrNull()
        val chosen = settings?.biosFilename?.takeIf { it.isNotBlank() }
            ?: MainActivityRuntime.bios.value?.takeIf { it.isNotEmpty() }?.let { File(it).name }

        if (chosen.isNullOrBlank() || installed[chosen] == null) {
            return listOf(
                Problem(
                    blocking = true,
                    what = "Nenhuma BIOS está escolhida.",
                    fix = "Estas placas precisam de uma BIOS COH-H de System 246 ou 256. Escolha uma em Configurações.",
                ),
            )
        }

        val out = mutableListOf<Problem>()
        if (ArcadeBios.boardOf(installed.getValue(chosen)) == ArcadeBios.Board.Console) {
            out += Problem(
                blocking = false,
                what = "A BIOS escolhida ($chosen) é de PlayStation 2 doméstico, não de placa arcade.",
                fix = "Use uma imagem COH-H de System 246/256.",
            )
        }
        // The switch this would otherwise make happens automatically at boot, so only the case
        // with nothing to switch to is worth saying.
        (ArcadeBios.decide(gameId, installed, chosen) as? ArcadeBios.Decision.NoneUsable)?.let {
            out += Problem(
                blocking = true,
                what = "Com a BIOS $chosen, ${it.reason}.",
                fix = "Não há outra BIOS instalada que sirva. Instale uma segunda imagem.",
            )
        }
        return out
    }

    // ---- INI -------------------------------------------------------------

    /**
     * The manifest as `section.key` -> value.
     *
     * Section-aware on purpose: `gameid` belongs to [game] and everything else to [data], and a
     * flat parse would read a stray key from the wrong block. Both halves are lowercased, so a
     * manifest someone typed by hand with `MediaSrc=` reads the same as one this app wrote.
     */
    internal fun parseIni(text: String): Map<String, String> {
        val out = mutableMapOf<String, String>()
        var section = ""
        for (raw in text.lineSequence()) {
            val line = raw.trim()
            if (line.isEmpty() || line.startsWith(';') || line.startsWith('#')) continue
            if (line.startsWith('[') && line.endsWith(']')) {
                section = line.substring(1, line.length - 1).trim().lowercase()
                continue
            }
            val eq = line.indexOf('=')
            if (eq <= 0) continue
            val key = line.substring(0, eq).trim()
            val value = line.substring(eq + 1).trim()
            out.putIfAbsent("$section.${key.lowercase()}", value)
        }
        return out
    }

    // ---- launch interception --------------------------------------------

    /**
     * Called on the way into a launch. True means the launch was held and a dialog is up.
     *
     * Only blocking problems hold anything: a warning is worth reading on the game's page, not
     * worth a modal between the player and the game.
     */
    fun holdLaunch(context: Context, uri: String, title: String, proceed: () -> Unit): Boolean {
        if (!isManifest(uri) || uri in confirmed) return false
        val report = runCatching { inspect(context, uri) }.getOrNull() ?: return false
        if (!report.blocking) return false
        held.value = Held(title, report) {
            confirmed += uri
            held.value = null
            proceed()
        }
        return true
    }

    fun dismiss() {
        held.value = null
    }
}
