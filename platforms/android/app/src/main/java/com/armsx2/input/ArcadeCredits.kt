package com.armsx2.input

import androidx.core.content.edit
import com.armsx2.runtime.MainActivityRuntime
import kotlinx.coroutines.delay
import kr.co.iefriends.pcsx2.NativeApp

/**
 * Fichas colocadas por você, ou pelo app.
 *
 * A cabinet counts coins, and this build makes you insert them by hand on every launch — which is
 * exactly right, and exactly tiresome after the tenth time. So: an opt-in that drops the credits
 * in for you once the board is up, and nothing else changes.
 *
 * **Off by default, and it stays off.** The coin is part of what the machine is, and turning it
 * into a setting nobody asked for would take that away from everyone to save one tap. Whoever
 * wants it goes and asks for it, per game.
 *
 * It is also not the board's own free play. Real free play is a TEST-menu setting, per game, and
 * this does not touch it: the board still counts coins, still shows the credit, still behaves like
 * a cabinet with money in it. What changes is who put the money in — which is why the switch says
 * so rather than promising something it does not do.
 */
object ArcadeCredits {

    private const val KEY_ON = "arcade.credits.auto"
    private const val KEY_COUNT = "arcade.credits.count"

    /** How many credits, when it is on. Two is a fighting game's default for a full match. */
    const val COUNT_DEFAULT = 2

    /**
     * How long to wait after the JVS board answers before dropping the first coin.
     *
     * The board being live is not the game being ready — `proverb.elf` has only just handed over,
     * and a coin inserted into a game still writing its boot screen is a coin the counter never
     * sees. Long enough to be past that, short enough that you have not reached for the button.
     */
    private const val SETTLE_MS = 3500L

    /** Spacing between coins: a real slot cannot swallow two in the same millisecond either. */
    private const val GAP_MS = 260L

    private fun key(base: String, serial: String?) =
        if (serial.isNullOrBlank()) base else "game.$serial.$base"

    fun enabled(serial: String?): Boolean =
        MainActivityRuntime.prefs.getBoolean(key(KEY_ON, serial), false)

    fun count(serial: String?): Int =
        MainActivityRuntime.prefs.getInt(key(KEY_COUNT, serial), COUNT_DEFAULT).coerceIn(1, 9)

    fun setEnabled(serial: String?, on: Boolean) =
        MainActivityRuntime.prefs.edit { putBoolean(key(KEY_ON, serial), on) }

    fun setCount(serial: String?, value: Int) =
        MainActivityRuntime.prefs.edit { putInt(key(KEY_COUNT, serial), value.coerceIn(1, 9)) }

    /** The boot this has already paid for, so a poll that keeps running does not keep paying. */
    @Volatile
    private var armedFor: String? = null

    /** Called when a game stops, so the next boot of the same game is paid for again. */
    fun forget() {
        armedFor = null
    }

    /**
     * Drop this game's credits in, once per boot. Does nothing when the option is off.
     *
     * Suspends: the wait is the point, and the caller is already a polling coroutine.
     */
    suspend fun onBoardReady(serial: String?) {
        val id = serial?.takeIf { it.isNotBlank() } ?: return
        if (armedFor == id) return
        armedFor = id
        if (!enabled(id)) return

        delay(SETTLE_MS)
        repeat(count(id)) {
            runCatching { NativeApp.jvsInsertCoin(0) }
            delay(GAP_MS)
        }
    }
}
