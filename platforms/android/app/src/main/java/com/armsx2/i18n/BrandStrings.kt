package com.armsx2.i18n

import com.armsx2.ui.premium.Branding

/**
 * This fork's wording, applied at lookup time instead of by editing the string tables.
 *
 * It used to be done by editing them: a find-and-replace of the upstream product name across
 * I18n.kt and all nineteen translation JSONs, plus a few rewritten sentences. It worked, and it
 * made those twenty files 21,936 changed lines -- around three quarters of this fork's entire
 * diff against upstream -- almost all of it reformatting noise from rewriting the JSON rather
 * than real edits. Every upstream translation update would then have collided with it, which is
 * the opposite of what a fork wants: the files we never touch are the files that merge for free.
 *
 * So the tables stay byte-identical to upstream and the difference lives here, in one file that
 * cannot conflict with anything. Two rules:
 *
 *  - [OVERRIDES] replaces whole strings that say something different in this build, keyed by
 *    string id and language.
 *  - Everything else gets the product name swapped, because that is what the bulk of those
 *    21,936 lines actually were.
 */
internal object BrandStrings {

    /** What upstream calls itself in its own strings. */
    private const val UPSTREAM_NAME = "ARMSX2"

    /**
     * Strings whose MEANING differs here, not just the name in them.
     *
     * Keyed id -> (language -> text); "en" is the fallback for any language not listed, which is
     * also what the machine-translated tables do for a missing key.
     */
    private val OVERRIDES: Map<String, Map<String, String>> = mapOf(
        // Upstream describes a PlayStation 2 emulator. This one boots System 246/256 boards.
        "about.tagline" to mapOf(
            "en" to "NAMCO System 246/256 arcade emulation for Android.",
            "pt-BR" to "Emulação de arcade NAMCO System 246/256 para Android.",
        ),
        // The real lineage, which is two projects deep rather than one.
        "about.pcsx2.description" to mapOf(
            "en" to "Built on PCSX2x6 (a PCSX2 fork, which brings the System 246/256 arcade " +
                "support) and on ARMSX2 (the Android launcher and the ARM64 recompiler work) — " +
                "both open-source projects derived from PCSX2.",
            "pt-BR" to "Construído sobre o PCSX2x6 (um fork do PCSX2, de onde vem o suporte ao " +
                "arcade System 246/256) e sobre o ARMSX2 (o launcher Android e o trabalho de " +
                "recompilador ARM64) — ambos projetos de código aberto derivados do PCSX2.",
        ),
        // Upstream is asking for a console BIOS. This build boots arcade boards, whose BIOS is
        // the COH-H image off a System 246/256 -- a different file, from different hardware, and
        // a player who goes looking for "a PS2 BIOS" comes back with one that will not boot a
        // single game here.
        "setup.step.bios.description" to mapOf(
            "en" to "Pick a folder holding your NAMCO System 246/256 arcade BIOS (the COH-H " +
                "image) — every BIOS inside is added, along with any matching .mec and .nvm files.",
            "pt-BR" to "Escolha uma pasta com a BIOS do fliperama NAMCO System 246/256 (a imagem " +
                "COH-H) — toda BIOS que estiver dentro é adicionada, junto com os .mec e .nvm " +
                "correspondentes.",
        ),
        "setup.bios.error.noneFound" to mapOf(
            "en" to "No valid arcade BIOS found in that folder.",
            "pt-BR" to "Nenhuma BIOS de fliperama válida foi encontrada nessa pasta.",
        ),
        // Two loose literals in upstream view models, given ids here so they can be said in the
        // player's language as well as about the right hardware. Keys of this fork's own making:
        // I18n has none, so the lookup falls through to exactly this table.
        "arcadeBios.error.notValid" to mapOf(
            "en" to "That file is not a BIOS this emulator can use. It needs a System 246/256 " +
                "arcade BIOS (COH-H).",
            "pt-BR" to "Esse arquivo não é uma BIOS que este emulador consiga usar. É preciso uma " +
                "BIOS de fliperama System 246/256 (COH-H).",
        ),
        "arcadeBios.error.noneInFolder" to mapOf(
            "en" to "No arcade BIOS was found in that folder.",
            "pt-BR" to "Nenhuma BIOS de fliperama foi encontrada nessa pasta.",
        ),
        // The library takes .acgame manifests, not console disc images.
        "setup.step.rom.description" to mapOf(
            "en" to "Pick one or more folders holding your arcade games. Each game is a .acgame " +
                "manifest with its payload folder beside it.",
            "pt-BR" to "Escolha uma ou mais pastas com seus jogos de fliperama. Cada jogo é um " +
                "manifesto .acgame com a pasta de dados ao lado.",
        ),
    )

    /**
     * The text this build shows for [key], given whatever the tables returned in [upstream].
     *
     * Cheap enough to sit in the lookup path: a map miss and, for the common case, an indexOf
     * that fails. Only strings that actually contain the upstream name pay for a replace.
     */
    fun resolve(key: String, language: String, upstream: String): String {
        OVERRIDES[key]?.let { return it[language] ?: it["en"] ?: upstream }
        if (!upstream.contains(UPSTREAM_NAME)) return upstream
        return upstream.replace(UPSTREAM_NAME, Branding.name)
    }
}
