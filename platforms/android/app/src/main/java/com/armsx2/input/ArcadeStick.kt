package com.armsx2.input

import androidx.core.content.edit
import com.armsx2.runtime.MainActivityRuntime
import kr.co.iefriends.pcsx2.NativeApp

/**
 * The analog stick standing in for the cabinet's lever.
 *
 * A JVS panel has no stick: its four directions are switches, and every layout binds them to the
 * pad's d-pad. Which meant that on a phone, in a fighting or a standard cabinet, the analog stick
 * did nothing whatsoever -- the games were playable only on the d-pad, which is not how anyone
 * holds a controller and not how the cabinet felt either, since an arcade lever is a stick.
 *
 * On by default, because "the stick does nothing" is a fault rather than a preference, and off
 * per game for anyone who wants the d-pad alone. A driving cabinet ignores this entirely: there
 * the left stick is already the wheel, and a digital LEFT fired mid-corner would be a second,
 * wrong input.
 *
 * Both numbers go into the core rather than being applied here, for the reason [ArcadeWheel]
 * gives: the pad path that feeds JVS never passes through Kotlin.
 */
object ArcadeStick {

    private const val KEY_ON = "arcade.stick.lever"
    private const val KEY_DEAD = "arcade.stick.deadzone"

    /** Percent of full travel the stick must pass before a direction counts. */
    const val DEAD_DEFAULT = 35

    private fun key(base: String, serial: String?) =
        if (serial.isNullOrBlank()) base else "game.$serial.$base"

    fun enabled(serial: String?): Boolean =
        MainActivityRuntime.prefs.getBoolean(key(KEY_ON, serial), true)

    fun deadzone(serial: String?): Int =
        MainActivityRuntime.prefs.getInt(key(KEY_DEAD, serial), DEAD_DEFAULT).coerceIn(10, 80)

    fun setEnabled(serial: String?, on: Boolean) {
        MainActivityRuntime.prefs.edit { putBoolean(key(KEY_ON, serial), on) }
        apply(serial)
    }

    fun setDeadzone(serial: String?, value: Int) {
        MainActivityRuntime.prefs.edit { putInt(key(KEY_DEAD, serial), value.coerceIn(10, 80)) }
        apply(serial)
    }

    /** Push this game's setting into the core. Safe with no VM: the JNI just stores it. */
    fun apply(serial: String?) {
        runCatching { NativeApp.jvsSetStickLever(enabled(serial), deadzone(serial) / 100f) }
    }
}
