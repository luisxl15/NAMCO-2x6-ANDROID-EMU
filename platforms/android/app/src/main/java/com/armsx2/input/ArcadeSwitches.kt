package com.armsx2.input

import android.os.Handler
import android.os.Looper
import kr.co.iefriends.pcsx2.NativeApp

/**
 * The cabinet's own switches, driven from a physical controller.
 *
 * A System 246/256 board reads four things a DualShock2 has no equivalent for: the coin
 * mechanism, START, SERVICE and the TEST switch. They exist on screen already (ArcadePanel),
 * and that is fine for a phone held in two hands — but with a gamepad or a wheel in your hands
 * the coin slot is on the glass, so every credit means letting go of the controller. On a
 * cabinet these are just four more buttons, and here they should be too.
 *
 * Bound through the ordinary hotkey machinery rather than a mapper of its own: the Hotkeys tab
 * already captures a button, stores it, supports two-button combos and analog-stick bindings,
 * and its dispatch already runs only while a game is running. Four entries appended to
 * [ControllerMappings.SysHotkey] get all of it.
 *
 * Every call is a no-op unless an arcade board is actually live: the hotkeys are visible in the
 * list at all times, and pressing one at the menu should do nothing rather than something.
 *
 * Each switch belongs to the controller that pressed it. A cabinet has two coin slots and two
 * sets of switches, and the pad path already routes a second controller to JVS player 2 -- so
 * player 2's own controller puts in player 2's credit. Without that the second player has the
 * whole game wired up and no way to pay for it. TEST is the exception: it is one switch inside
 * the cabinet, not one per side.
 */
object ArcadeSwitches {

    // Matches JvsUiButton in native-lib.cpp.
    private const val JVS_START = 0
    private const val JVS_SERVICE = 1

    // DIP index 0 is the Test switch (ACJV::GetTestModeDIPSwitch).
    private const val DIP_TEST = 0

    /** How long a tapped switch stays down when the source gives us no release edge. */
    private const val TAP_MS = 120L

    // Lazy so that merely naming this object costs nothing: the JVM test that checks every
    // cabinet hotkey is dispatched touches [hotkeys], and there is no main Looper there.
    private val main by lazy { Handler(Looper.getMainLooper()) }

    /** The hotkeys this object answers for. Read by the dispatch in MainActivityRuntime. */
    val hotkeys: Set<ControllerMappings.SysHotkey> = setOf(
        ControllerMappings.SysHotkey.ARCADE_COIN,
        ControllerMappings.SysHotkey.ARCADE_START,
        ControllerMappings.SysHotkey.ARCADE_SERVICE,
        ControllerMappings.SysHotkey.ARCADE_TEST,
    )

    private fun live(): Boolean = runCatching { NativeApp.jvsIsArcade() }.getOrDefault(false)

    /**
     * A key edge for one of the four.
     *
     * START and SERVICE are momentary switches the board reads as held, so they follow the
     * button: press for press, release for release. A credit is an event, not a state, and the
     * TEST switch is a latch on the real cabinet — both act on the press and ignore the release.
     */
    fun onKey(h: ControllerMappings.SysHotkey, down: Boolean, port: Int = 0) {
        if (!live()) return
        // The board has two sides and no more; a third pad folded onto player 1 by the router
        // arrives here as port 0 already.
        val player = if (port == 1) 1 else 0
        // On the press only: these are switches, and a switch is felt going down.
        if (down) {
            if (h == ControllerMappings.SysHotkey.ARCADE_COIN) CabinetHaptics.coin()
            else CabinetHaptics.switchClick()
        }
        when (h) {
            ControllerMappings.SysHotkey.ARCADE_COIN ->
                if (down) runCatching { NativeApp.jvsInsertCoin(player) }
            ControllerMappings.SysHotkey.ARCADE_START ->
                runCatching { NativeApp.jvsSetButton(player, JVS_START, down) }
            ControllerMappings.SysHotkey.ARCADE_SERVICE ->
                runCatching { NativeApp.jvsSetButton(player, JVS_SERVICE, down) }
            // One switch in the cabinet, reachable from either seat.
            ControllerMappings.SysHotkey.ARCADE_TEST ->
                if (down) runCatching { NativeApp.jvsToggleDipSwitch(DIP_TEST) }
            else -> {}
        }
    }

    /**
     * The same four from a source with no release edge — a stick pushed past its threshold.
     *
     * START and SERVICE would otherwise latch on with nothing to let them go, which on a board
     * reading a held START is worse than not binding it at all. So they are tapped: down now, up
     * a moment later.
     */
    fun tap(h: ControllerMappings.SysHotkey, port: Int = 0) {
        if (!live()) return
        when (h) {
            ControllerMappings.SysHotkey.ARCADE_START,
            ControllerMappings.SysHotkey.ARCADE_SERVICE -> {
                onKey(h, true, port)
                main.postDelayed({ onKey(h, false, port) }, TAP_MS)
            }
            else -> onKey(h, true, port)
        }
    }

    /** Whether the TEST switch is currently up. Only meaningful while a board is live. */
    fun testEngaged(): Boolean =
        runCatching { NativeApp.jvsGetDipSwitchState(DIP_TEST) }.getOrDefault(false)
}
