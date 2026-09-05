package com.armsx2.ui.touch

/**
 * A touch layout shaped like the cabinet, for the games that had a steering wheel.
 *
 * The generic layout is a console pad: d-pad on the left, four face buttons on the right, two
 * small sticks at the bottom. On a driving cabinet that is wrong in every position — steering is
 * an axis and lands on a stick barely wider than a thumb, the accelerator is a shoulder button the
 * size of a fingertip, and the four face buttons are three cabinet switches and one nothing.
 *
 * So driving games get this instead: a wide stick under the left thumb for the wheel, two tall
 * pedals under the right, the shift paddles above them, and the cabinet's three switches out of
 * the way at the top. It is applied only when the player has no layout of their own for that game
 * — the moment they move a single control, the layout becomes theirs and this stops being
 * consulted.
 *
 * ★ Keyed by game id rather than asked of the core. The core knows the cabinet type (ACJV's
 * JVS_MODE::DRIVE) but not until the game has booted, and the layout has to be right on the first
 * frame — by the time ACJV could answer, the player is already looking at the wrong controls.
 * The ids come from pcsx2x6's own racing table (ACJV_Inputs.h, s_racing_layouts).
 */
object ArcadeTouchLayouts {

    /** The System 246/256 cabinets with a wheel. */
    private val DRIVING = setOf(
        "NM00001", // Ridge Racer V - Arcade Battle
        "NM00005", // Wangan Midnight R
        "NM00008", // Wangan Midnight
        "NM00010", // Battle Gear 3
        "NM00015", // Battle Gear 3 Tuned
        "NM00039", // MotoGP
        "NM00047", // Ace Driver 3 - Final Turn
    )

    fun isDriving(gameId: String?): Boolean = DRIVING.contains(gameId?.uppercase())

    /**
     * The wheel layout.
     *
     * Positions are fractions of the screen, so this is the same layout on any device. The left
     * stick is deliberately large: it is the steering wheel, it carries the axis the JVS board
     * reads, and a small one makes a car that will not hold a line.
     */
    fun driving(): TouchLayout = TouchLayout(
        listOf(
            // Wheel. Low and left, and the biggest thing on screen.
            TouchButtonCfg(TouchButtonId.L_STICK, xFrac = 0.17f, yFrac = 0.72f, sizeDp = 190f),

            // Pedals. Tall targets on the right, accelerator outermost where the thumb rests.
            TouchButtonCfg(TouchButtonId.R2, xFrac = 0.93f, yFrac = 0.74f, sizeDp = 104f),
            TouchButtonCfg(TouchButtonId.L2, xFrac = 0.79f, yFrac = 0.80f, sizeDp = 88f),

            // Shift paddles, above the pedals, where a hand on the wheel would find them.
            TouchButtonCfg(TouchButtonId.R1, xFrac = 0.93f, yFrac = 0.47f, sizeDp = 74f),
            TouchButtonCfg(TouchButtonId.L1, xFrac = 0.79f, yFrac = 0.52f, sizeDp = 74f),

            // The cabinet's own switches: view, sidebrake, hazard. Small, and up out of the way --
            // they are pressed between corners, not during them.
            TouchButtonCfg(TouchButtonId.TRIANGLE, xFrac = 0.62f, yFrac = 0.16f, sizeDp = 58f),
            TouchButtonCfg(TouchButtonId.SQUARE, xFrac = 0.72f, yFrac = 0.16f, sizeDp = 58f),
            TouchButtonCfg(TouchButtonId.CIRCLE, xFrac = 0.82f, yFrac = 0.16f, sizeDp = 58f),

            // Present but off: a driving cabinet has no d-pad, no second stick, and no cross.
            // Left in the layout rather than dropped so the editor still lists them and anyone
            // who wants one back can switch it on.
            TouchButtonCfg(TouchButtonId.DPAD, xFrac = 0.14f, yFrac = 0.35f, sizeDp = 130f, enabled = false),
            TouchButtonCfg(TouchButtonId.R_STICK, xFrac = 0.72f, yFrac = 0.72f, sizeDp = 120f, enabled = false),
            TouchButtonCfg(TouchButtonId.CROSS, xFrac = 0.88f, yFrac = 0.30f, sizeDp = 62f, enabled = false),

            TouchButtonCfg(TouchButtonId.START, xFrac = 0.50f, yFrac = 0.92f, sizeDp = 52f),
            TouchButtonCfg(TouchButtonId.PAUSE, xFrac = 0.965f, yFrac = 0.06f, sizeDp = 48f),
        ),
    )
}
