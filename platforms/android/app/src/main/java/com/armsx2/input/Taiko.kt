package com.armsx2.input

import android.os.Handler
import android.os.Looper
import androidx.compose.runtime.mutableStateOf
import kr.co.iefriends.pcsx2.NativeApp

/**
 * The Taiko drum, played on the glass.
 *
 * These ten games had no input at all on Android. Not awkward input — none: every other cabinet's
 * controls are JVS switches, and the pad was mirrored into the switch table, but a drum's four
 * sensors per side hang off JVS ANALOG channels. Nothing in that path could reach them, so the
 * games booted, played their attract loop and ignored every touch and every button forever.
 *
 * A real head is a piezo: struck, it spikes and falls back. So a hit here is a PULSE, not a hold —
 * the channel goes high and comes back down a few frames later, whether the finger is still on the
 * glass or not. Holding a finger down is not a drum roll on a real cabinet either, and a channel
 * left high across a hundred polls is not something the game was written to see.
 */
object Taiko {

    /** Pads, in the order the native side expects them. */
    const val DON_LEFT = 0
    const val DON_RIGHT = 1
    const val KA_LEFT = 2
    const val KA_RIGHT = 3

    /**
     * How long a strike is held high.
     *
     * Long enough that a JVS poll cannot fall between the rise and the fall — the board is read
     * about once a frame — and short enough that two fast alternating hits stay two hits. Three
     * frames.
     */
    private const val HIT_MS = 50L

    private val main by lazy { Handler(Looper.getMainLooper()) }

    /** True while a drum cabinet is running. Polled by the layer; ACJV is not live until boot. */
    val active = mutableStateOf(false)

    /** Which pads are lit right now, as a bit per pad — for the on-screen feedback only. */
    val lit = mutableStateOf(0)

    fun refresh() {
        val on = runCatching { NativeApp.jvsDrumActive() }.getOrDefault(false)
        if (on != active.value) {
            active.value = on
            if (!on) lit.value = 0
        }
    }

    /** Strike one pad. [player] 0 or 1 — the cabinet has two sides. */
    fun hit(pad: Int, player: Int = 0) {
        if (!active.value) return
        runCatching { NativeApp.jvsDrumHit(player, pad, true) }
        // Head and rim have to feel different, or the panel is four identical buzzes and the
        // only way to know which one you hit is to look at the chart you should be reading.
        if (isDon(pad)) CabinetHaptics.don() else CabinetHaptics.ka()
        if (player == 0) lit.value = lit.value or (1 shl pad)
        main.postDelayed({
            runCatching { NativeApp.jvsDrumHit(player, pad, false) }
            if (player == 0) lit.value = lit.value and (1 shl pad).inv()
        }, HIT_MS)
    }

    /**
     * Which pad a touch lands on, given where it is across the screen.
     *
     * Four columns, outer to inner to inner to outer, because that is the drum seen from the
     * front: the two red heads are in the middle, the two blue rims outside them. A player who
     * has stood at one of these machines already knows where to hit; nobody has to be taught a
     * layout that matches the thing.
     *
     * The middle two are wider. The heads are what a chart is mostly made of, and on a drum they
     * are also physically the bigger target — a rim is the edge.
     */
    fun padForX(x: Float, width: Float): Int {
        if (width <= 0f) return DON_LEFT
        val f = (x / width).coerceIn(0f, 1f)
        return when {
            f < 0.22f -> KA_LEFT
            f < 0.50f -> DON_LEFT
            f < 0.78f -> DON_RIGHT
            else -> KA_RIGHT
        }
    }

    /** The four column edges, as fractions of the width — so the drawing and the hit test agree. */
    val columns: List<ClosedFloatingPointRange<Float>> = listOf(
        0f..0.22f,      // KA_LEFT
        0.22f..0.50f,   // DON_LEFT
        0.50f..0.78f,   // DON_RIGHT
        0.78f..1f,      // KA_RIGHT
    )

    /** Column index for a pad, so a caller can draw it in screen order rather than pad order. */
    fun columnOf(pad: Int): Int = when (pad) {
        KA_LEFT -> 0
        DON_LEFT -> 1
        DON_RIGHT -> 2
        else -> 3
    }

    fun isDon(pad: Int): Boolean = pad == DON_LEFT || pad == DON_RIGHT
}
