package com.armsx2.i18n

/**
 * The messages the manager screens hand to an overlay, in the app's language.
 *
 * I said once that the hotkey list was the last English in the interface and that was wrong.
 * These are the other island: about fifty sentences written straight into the view models —
 * "Unable to delete r27v1602f.8g.", "Select another BIOS before deleting the active one." — none
 * of which ever passed through the string tables, and all of which appear in front of a player
 * at exactly the moment something went wrong.
 *
 * Translated at the point of display, like [HotkeyNames] and for the same reason: the tables and
 * the nineteen translation JSONs stay byte-identical to upstream and go on merging for free.
 * Matching is by regular expression rather than by exact text, because most of these carry a
 * filename or a slot number in the middle and there is no key to look up — the shape is the key.
 *
 * A sentence that matches nothing comes through in English. That is the right failure: an
 * upstream rewording loses its translation and keeps its meaning, rather than turning into a
 * blank or a raw identifier.
 */
internal object AppMessages {

    private val PT: List<Pair<Regex, String>> = listOf(
        // ---- BIOS
        Regex("^Unable to set a per-game BIOS\\.$") to "Não foi possível definir a BIOS deste jogo.",
        Regex("^Select another BIOS before deleting the active one\\.$")
            to "Escolha outra BIOS antes de apagar a que está em uso.",
        Regex("^Unable to delete (.+)\\.$") to "Não foi possível apagar $1.",
        Regex("^Deleted (.+)\\.$") to "$1 apagado.",

        // ---- memory cards
        Regex("^The emulator core is still starting\\.$") to "O núcleo do emulador ainda está iniciando.",
        Regex("^Created (.+)\\.$") to "$1 criado.",
        Regex("^Unable to create (.+)\\.$") to "Não foi possível criar $1.",
        Regex("^Imported folder card (.+)\\.$") to "Cartão em pasta $1 importado.",
        Regex("^Imported (.+)\\.$") to "$1 importado.",
        Regex("^Memory card import failed\\.$") to "A importação do memory card falhou.",
        Regex("^That zip isn't a folder memory card \\(no _pcsx2_superblock inside\\)\\.$")
            to "Esse zip não é um memory card em pasta (não há _pcsx2_superblock dentro).",
        Regex("^That folder isn't a memory card \\(no _pcsx2_superblock inside\\)\\.$")
            to "Essa pasta não é um memory card (não há _pcsx2_superblock dentro).",
        Regex("^Unable to open the selected folder\\.$") to "Não foi possível abrir a pasta escolhida.",
        Regex("^Unable to change slot (.+)\\.$") to "Não foi possível trocar o slot $1.",
        Regex("^Unable to set a per-game card\\.$") to "Não foi possível definir o cartão deste jogo.",
        Regex("^Unable to clear the per-game card\\.$") to "Não foi possível limpar o cartão deste jogo.",
        Regex("^Assign another card before deleting an active card\\.$")
            to "Atribua outro cartão antes de apagar um que está em uso.",
        Regex("^Exported (.+)\\.$") to "$1 exportado.",
        Regex("^Export failed for (.+)\\.$") to "A exportação de $1 falhou.",
        Regex("^Stop the game before backing up a memory card\\.$")
            to "Feche o jogo antes de fazer backup de um memory card.",
        Regex("^Backed up (.+)\\.$") to "Backup de $1 feito.",
        Regex("^Could not back up (.+)\\.$") to "Não foi possível fazer backup de $1.",
        Regex("^(.+) is not there to back up\\.$") to "$1 não está lá para ser copiado.",
        Regex("^(.+) cannot be read, so there is nothing worth backing up\\. Restore an earlier backup instead\\.$")
            to "$1 não pode ser lido, então não há nada que valha a pena copiar. " +
            "Restaure um backup anterior no lugar.",
        Regex(
            "^Stop the game before restoring a memory card\\. The console keeps its own picture " +
                "of the card while it runs, and would write over the restored copy\\.$",
        ) to "Feche o jogo antes de restaurar um memory card. Enquanto ele roda, o console mantém " +
            "a própria imagem do cartão e escreveria por cima da cópia restaurada.",
        Regex("^(.+) assigned to slot (.+)\\.$") to "$1 atribuído ao slot $2.",
        Regex("^(.+) set for this game \\(slot (.+)\\)\\. Restart the game to apply\\.$")
            to "$1 definido para este jogo (slot $2). Reinicie o jogo para aplicar.",
        Regex("^Slot (.+) follows the global card again\\. Restart the game to apply\\.$")
            to "O slot $1 volta a seguir o cartão global. Reinicie o jogo para aplicar.",

        // ---- patches
        Regex("^Patch import failed\\.$") to "A importação do patch falhou.",
        Regex("^Could not save the patch file\\.$") to "Não foi possível salvar o arquivo de patch.",
        Regex("^Couldn't install the selected patches\\.$")
            to "Não foi possível instalar os patches escolhidos.",
        Regex("^Select at least one patch or cheat first\\.$")
            to "Escolha pelo menos um patch ou trapaça primeiro.",
        Regex("^No game serial to look up patches for\\. Open this from a game\\.$")
            to "Não há um jogo para procurar patches. Abra esta tela a partir de um jogo.",
        Regex("^Couldn't update (.+) \\(unusual formatting\\)\\. Edit it as text instead\\.$")
            to "Não foi possível atualizar $1 (formatação incomum). Edite como texto.",
        Regex("^Installed (.+) item\\(s\\) for (.+)\\. Restart the game if it's running\\.$")
            to "$1 item(ns) instalado(s) para $2. Reinicie o jogo se ele estiver rodando.",
        Regex(
            "^Can't install for (.+): no disc CRC known\\. Launch the game once, then install — " +
                "the core only loads <serial>_<CRC>\\.pnach\\.$",
        ) to "Não dá para instalar em $1: o CRC do disco não é conhecido. Abra o jogo uma vez e " +
            "instale depois — o núcleo só carrega <serial>_<CRC>.pnach.",

        // ---- textures
        Regex("^Deleted texture pack (.+)\\.$") to "Pacote de texturas $1 apagado.",
        Regex("^Imported (.+) texture files for (.+)\\.$")
            to "$1 arquivos de textura importados para $2.",
        Regex("^No texture files were imported\\.$") to "Nenhum arquivo de textura foi importado.",
        Regex("^Name the selected folder with the game's serial, for example SLES-52187\\.$")
            to "Dê à pasta escolhida o nome do identificador do jogo, por exemplo NM00004.",

        // ---- setup and achievements
        Regex("^No writable SD card was found\\.$") to "Nenhum cartão SD gravável foi encontrado.",
        Regex("^Couldn't resolve that folder\\.$") to "Não foi possível resolver essa pasta.",
        Regex("^Enter your RetroAchievements username and password\\.$")
            to "Digite seu usuário e senha do RetroAchievements.",
        Regex("^Couldn't import that sound file\\.$") to "Não foi possível importar esse arquivo de som.",
    )

    /** The message as this build should show it. */
    fun text(message: String): String = translate(I18n.current, message)

    // The language is a parameter so the rules can be checked on the JVM: I18n.current has a
    // private setter and is restored from preferences, neither of which a unit test has.
    internal fun translate(language: String, message: String): String {
        if (!language.startsWith("pt") || message.isBlank()) return message
        PT.forEach { (pattern, replacement) ->
            if (pattern.matches(message)) return pattern.replace(message, replacement)
        }
        return message
    }
}
