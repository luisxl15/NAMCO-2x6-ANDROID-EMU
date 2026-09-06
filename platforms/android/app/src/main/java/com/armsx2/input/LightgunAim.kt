package com.armsx2.input

import androidx.compose.runtime.mutableStateOf
import com.armsx2.runtime.MainActivityRuntime

/**
 * Aiming the cabinet's gun by pointing the phone.
 *
 * Touch aiming works, and it costs the finger that should be on the trigger: to shoot at
 * something you have to be touching it, so you are always shooting where your hand already is,
 * and the hand is on top of what you are trying to see. A real cabinet separates the two — the
 * gun points, the finger pulls — and a phone has the sensors to do the same.
 *
 * The maths is here rather than in the layer so it can be checked without a device, which matters
 * because the two sensor kinds mean opposite things:
 *
 *  - A gyroscope reports a RATE. Integrating it gives a relative pan, the way a mouse works: turn
 *    the phone and the crosshair travels, stop and it stays.
 *  - Gravity (every phone without a gyroscope) reports an ANGLE, already relative to wherever the
 *    device was when the mode started. That is an absolute position, and integrating it would
 *    accelerate the crosshair off the screen while the phone sits still at a tilt.
 *
 * Treating the second like the first is the bug this file exists to make impossible.
 */
object LightgunAim {

    private const val KEY_ENABLED = "lightgun.gyroAim"

    /** How far a full-rate turn sweeps, in screens per second. */
    private const val RATE_SPEED = 1.35f

    /** How much of the screen a full tilt covers, from the centre. */
    private const val TILT_REACH = 0.5f

    val enabled = mutableStateOf(false)

    /** Where the gun points, in 0..1 of the screen. Compose state: the reticle follows it. */
    val x = mutableStateOf(0.5f)
    val y = mutableStateOf(0.5f)

    /** On only when the player asked for it AND a gun game is up. */
    val active: Boolean get() = enabled.value && Lightgun.active

    fun load() {
        enabled.value = MainActivityRuntime.prefs.getBoolean(KEY_ENABLED, false)
    }

    fun setEnabled(on: Boolean) {
        enabled.value = on
        MainActivityRuntime.prefs.edit().putBoolean(KEY_ENABLED, on).apply()
        if (on) recenter()
    }

    /** Put the crosshair back in the middle. The sensor's own zero is recentred alongside it. */
    fun recenter() {
        x.value = 0.5f
        y.value = 0.5f
    }

    /**
     * One sensor sample.
     *
     * [kind] is [AndroidGyroscopeInput.KIND_GYRO] / KIND_TILT / KIND_ROTATION, [vx]/[vy] its
     * normalized output, [dtMs] the time since the previous sample. Returns the new position so
     * the caller can push it without reading the state back.
     */
    fun step(kind: Int, vx: Float, vy: Float, dtMs: Long): Pair<Float, Float> {
        val (nx, ny) = next(kind, x.value, y.value, vx, vy, dtMs)
        x.value = nx
        y.value = ny
        return nx to ny
    }

    /** [step]'s decision, with no state of its own. */
    internal fun next(
        kind: Int,
        px: Float,
        py: Float,
        vx: Float,
        vy: Float,
        dtMs: Long,
    ): Pair<Float, Float> {
        if (kind == AndroidGyroscopeInput.KIND_GYRO) {
            // A stale timestamp after a pause would otherwise fling the crosshair across the
            // screen in one step, so a gap longer than a few frames counts as a few frames.
            val dt = (dtMs.coerceIn(0L, 100L)) / 1000f
            return ((px + vx * RATE_SPEED * dt).coerceIn(0f, 1f) to
                (py + vy * RATE_SPEED * dt).coerceIn(0f, 1f))
        }
        // Absolute: the sensor already says where, relative to the centre it was zeroed at.
        return ((0.5f + vx * TILT_REACH).coerceIn(0f, 1f) to
            (0.5f + vy * TILT_REACH).coerceIn(0f, 1f))
    }
}
