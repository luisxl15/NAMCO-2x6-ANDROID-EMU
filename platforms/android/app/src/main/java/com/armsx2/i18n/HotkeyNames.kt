package com.armsx2.i18n

import com.armsx2.input.ControllerMappings

/**
 * The hotkey list, in the app's language.
 *
 * It was the last island of English in the interface, and for a structural reason rather than an
 * oversight: every other string reaches the screen through [I18n], but a hotkey's name is a
 * literal in the `SysHotkey` enum, which is upstream's and which the translation tables have
 * never had a key for. The same goes for the button a hotkey is bound to — "D-Pad Up" is built in
 * `ControllerMappings.labelForKey`, not looked up.
 *
 * Translated here, at the point of display, for the reason [BrandStrings] exists: I18n.kt and the
 * nineteen translation JSONs stay byte-identical to upstream and go on merging for free. Adding
 * forty-eight keys to them would have been forty-eight future conflicts.
 *
 * Any language other than Portuguese gets the English label back untouched. That is honest — this
 * fork has one translator and it is not going to pretend otherwise.
 */
internal object HotkeyNames {

    private val PT: Map<String, String> = mapOf(
        "MENU" to "Menu / Pausa",
        "SAVE_STATE" to "Salvar estado (rápido)",
        "LOAD_STATE" to "Carregar estado (rápido)",
        "CYCLE_SLOT" to "Avançar o slot de estado",
        "PREV_SLOT" to "Voltar o slot de estado",
        "TEXTURE_DUMP" to "Extração de texturas (alternar)",
        "SCREENSHOT" to "Captura de tela",
        "TOGGLE_OSD" to "Estatísticas na tela (alternar)",
        "FAST_FORWARD" to "Avanço rápido (segurar)",
        "FAST_FORWARD_TOGGLE" to "Avanço rápido (alternar)",
        "SLOW_DOWN" to "Câmera lenta (alternar)",
        "RES_UP" to "Aumentar a resolução",
        "RES_DOWN" to "Diminuir a resolução",
        "ACHIEVEMENTS" to "Abrir as conquistas",
        "CLOSE_GAME" to "Fechar o jogo",
        "QUIT_APP" to "Fechar o jogo e sair do app",
        "SAVE_AND_EXIT" to "Salvar estado e sair",
        "RESET_GAME" to "Reiniciar o jogo",
        "PRESSURE_MOD" to "Toque leve (segurar)",
        "GYRO_TOGGLE" to "Giroscópio (alternar)",
        "GYRO_HOLD" to "Giroscópio (segurar para mirar)",
        "GYRO_RECENTER" to "Centralizar o movimento",
        "TOGGLE_KEYBOARD" to "Teclado na tela (alternar)",
        "DISPLAY_REFRESH" to "Alternar a taxa de atualização da tela",
        // The four cabinet switches were written in Portuguese to begin with; they are here so
        // the map is the whole list rather than most of it.
        "ARCADE_COIN" to "Cabine: Ficha",
        "ARCADE_START" to "Cabine: START",
        "ARCADE_SERVICE" to "Cabine: SERVICE",
        "ARCADE_TEST" to "Cabine: TEST",
    )

    /** The words inside a binding label, longest first so "D-Pad Up" is not half-translated. */
    private val PT_BINDING: List<Pair<String, String>> = listOf(
        "D-Pad " to "Direcional ",
        "L-Stick " to "Analógico E ",
        "R-Stick " to "Analógico D ",
        "Button " to "Botão ",
        "Up" to "cima",
        "Down" to "baixo",
        "Left" to "esquerda",
        "Right" to "direita",
    )

    /**
     * What the hotkeys say back, keyed by the English they say it in.
     *
     * Keyed by the text rather than by an id so the call site keeps reading as English — an
     * upstream file that still says `"Fast Forward ON"` merges cleanly, and if upstream ever
     * reworded it the lookup misses and the English comes through, which is the failure worth
     * having.
     */
    private val PT_FEEDBACK: Map<String, String> = mapOf(
        "Gyro ON" to "Giroscópio ligado",
        "Gyro OFF" to "Giroscópio desligado",
        "Motion not active" to "O movimento não está ativo",
        "Motion recentered" to "Movimento centralizado",
        "Fast Forward ON" to "Avanço rápido ligado",
        "Fast Forward OFF" to "Avanço rápido desligado",
        "Slow Down ON (50%)" to "Câmera lenta ligada (50%)",
        "Slow Down OFF" to "Câmera lenta desligada",
        "Slow Down is disabled in RetroAchievements Hardcore mode"
            to "A câmera lenta é bloqueada no modo Hardcore do RetroAchievements",
        "Texture dumping ON" to "Extração de texturas ligada",
        "Texture dumping OFF" to "Extração de texturas desligada",
        // Two with a number in them. Kept as templates rather than pieced together at the call
        // site, because word order is exactly the thing that does not survive concatenation.
        "Display %s Hz" to "Tela em %s Hz",
        "Only %s Hz available" to "Só há %s Hz disponível",
    )

    /** One of the hotkeys' own messages. Unknown text comes back unchanged. */
    fun feedback(english: String): String = feedbackIn(I18n.current, english)

    internal fun feedbackIn(language: String, english: String): String =
        if (portuguese(language)) PT_FEEDBACK[english] ?: english else english

    private fun portuguese(language: String): Boolean = language.startsWith("pt")

    /** What a hotkey is called. */
    fun action(hotkey: ControllerMappings.SysHotkey): String = actionIn(I18n.current, hotkey)

    // The language is a parameter on these three rather than read inside them, so the rules can
    // be checked on the JVM: I18n.current has a private setter and is restored from preferences,
    // neither of which a unit test has.
    internal fun actionIn(language: String, hotkey: ControllerMappings.SysHotkey): String {
        if (!portuguese(language)) return hotkey.label
        // The per-slot pairs are twenty entries that differ by one digit. Spelling all twenty out
        // is twenty chances to typo a number that the UI would then show against the wrong slot.
        val slot = ControllerMappings.slotForHotkey(hotkey)
        if (slot >= 0) {
            return if (ControllerMappings.isSaveSlotHotkey(hotkey)) "Salvar estado no slot $slot"
            else "Carregar estado do slot $slot"
        }
        return PT[hotkey.name] ?: hotkey.label
    }

    /**
     * The button a hotkey is bound to, e.g. "Select + R1" or "D-Pad Up".
     *
     * Substituted rather than rebuilt, because most of what comes back is not language at all:
     * L1, R2, L3 and Start are printed on the controller in the player's hand, and translating
     * what is written on the hardware would make the screen disagree with the thing it describes.
     */
    fun binding(text: String): String = bindingIn(I18n.current, text)

    internal fun bindingIn(language: String, text: String): String {
        if (!portuguese(language) || text.isEmpty()) return text
        var out = text
        PT_BINDING.forEach { (from, to) -> out = out.replace(from, to) }
        return out
    }
}
