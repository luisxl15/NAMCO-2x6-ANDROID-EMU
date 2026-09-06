package com.armsx2.input

import androidx.compose.runtime.mutableStateOf
import androidx.core.content.edit
import com.armsx2.runtime.MainActivityRuntime
import kr.co.iefriends.pcsx2.NativeApp

/**
 * What the cabinet's controls feel like.
 *
 * Everything this fork put on the glass — the drum, the gun, the coin slot, the cabinet switches
 * — gives the finger nothing back. On a real machine every one of them is a physical event: a
 * drum head is taut and loud, a rim is a click, a coin drops through a mechanism, a trigger has a
 * sear. On a phone they are all the same flat pane, and in Taiko that is not just a missing
 * flourish: without the thump the only way to know a hit registered is to look away from the
 * chart at the note you just played.
 *
 * So each one gets its own weight and length. They are not decoration — they are how you tell
 * these controls apart without looking, which is the whole point of a control you use while
 * watching something else.
 *
 * Deliberately NOT a second vibration system. It goes through the same native path as the touch
 * tick and the pad's rumble, so the existing Vibration Strength slider still governs it and a
 * player who turned vibration off gets none of this.
 */
object CabinetHaptics {

    private const val KEY = "arcade.haptics"

    val enabled = mutableStateOf(true)

    fun load() {
        enabled.value = MainActivityRuntime.prefs.getBoolean(KEY, true)
    }

    fun setEnabled(on: Boolean) {
        enabled.value = on
        MainActivityRuntime.prefs.edit { putBoolean(KEY, on) }
    }

    /** The master vibration switch wins: off there means off everywhere, as it says it does. */
    private fun allowed(): Boolean = enabled.value && ControllerMappings.rumbleEnabled()

    private fun pulse(intensity: Float, ms: Int) {
        if (!allowed()) return
        runCatching { NativeApp.cabinetHaptic(intensity, ms) }
    }

    /** The drum's inner head. Loud, and it is the note the chart is mostly made of. */
    fun don() = pulse(1.0f, 20)

    /** The rim. Lighter and shorter, so the two are told apart by feel alone. */
    fun ka() = pulse(0.45f, 10)

    /** The gun going off. */
    fun shot() = pulse(0.85f, 24)

    /** Pointing away from the screen to reload: a longer, softer shove, not a bang. */
    fun reload() = pulse(0.4f, 40)

    /** A coin through the mechanism. */
    fun coin() = pulse(0.7f, 18)

    /** START, SERVICE, TEST — panel switches, so a click. */
    fun switchClick() = pulse(0.5f, 12)
}
