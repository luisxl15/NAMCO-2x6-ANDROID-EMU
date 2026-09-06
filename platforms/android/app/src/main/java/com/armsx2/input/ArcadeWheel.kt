package com.armsx2.input

import com.armsx2.runtime.MainActivityRuntime
import androidx.core.content.edit
import kr.co.iefriends.pcsx2.NativeApp

/**
 * How the steering feels, per game.
 *
 * Driving cabinets got their wheel axis and a layout shaped like a cabinet, and then nothing to
 * adjust. That is a real gap and not a preference: the games do not agree with each other. Ridge
 * Racer V's wheel is quick and wants a small stick movement to mean a lot of lock; Wangan Midnight
 * is a heavy car and the same setting makes it undriveable. And a thumbstick at rest is not at
 * zero — it reports drift, which a real wheel's spring does not, so a car left alone wanders.
 *
 * Two numbers, both per game, both pushed into the core rather than applied here: the pad path
 * that feeds the wheel never passes through Kotlin, so this is the only place they can be applied
 * without intercepting every stick event.
 */
object ArcadeWheel {

    private const val KEY_DEAD = "arcade.wheel.deadzone"
    private const val KEY_GAIN = "arcade.wheel.sensitivity"

    /** Percent of full stick travel treated as centre. */
    const val DEAD_DEFAULT = 8
    /** Percent applied to what is left; 100 is one-to-one. */
    const val GAIN_DEFAULT = 100

    private fun key(base: String, serial: String?) =
        if (serial.isNullOrBlank()) base else "game.$serial.$base"

    fun deadzone(serial: String?): Int =
        MainActivityRuntime.prefs.getInt(key(KEY_DEAD, serial), DEAD_DEFAULT).coerceIn(0, 40)

    fun sensitivity(serial: String?): Int =
        MainActivityRuntime.prefs.getInt(key(KEY_GAIN, serial), GAIN_DEFAULT).coerceIn(50, 250)

    fun setDeadzone(serial: String?, value: Int) {
        MainActivityRuntime.prefs.edit { putInt(key(KEY_DEAD, serial), value.coerceIn(0, 40)) }
        apply(serial)
    }

    fun setSensitivity(serial: String?, value: Int) {
        MainActivityRuntime.prefs.edit { putInt(key(KEY_GAIN, serial), value.coerceIn(50, 250)) }
        apply(serial)
    }

    /** Push this game's numbers into the core. Safe to call with no VM: the JNI ignores it. */
    fun apply(serial: String?) {
        runCatching {
            NativeApp.jvsSetWheelCalibration(deadzone(serial) / 100f, sensitivity(serial) / 100f)
        }
    }

    /** True while the running board is a driving cabinet. */
    fun isDriving(): Boolean =
        runCatching { NativeApp.jvsGetModeName() }.getOrDefault("none") == "drive"

    /** Whether tilt steering is on for this game — the app's motion mode 2. */
    fun tiltSteering(serial: String?): Boolean =
        ControllerMappings.gyroModeScope(serial) == 2

    /**
     * Turn tilt steering on or off for this game.
     *
     * Writes the same per-game motion mode the Pad tab does rather than inventing a second
     * setting, because there is only one set of sensors and two switches for it would disagree.
     * Off returns the game to no motion at all, which is what a driving game had before this.
     */
    fun setTiltSteering(serial: String?, on: Boolean) {
        ControllerMappings.setGyroMode(if (on) 2 else 0, serial)
        if (on) MainActivityRuntime.gyroActive.value = true
        ControllerMappings.invalidateRuntimeCaches()
    }
}
