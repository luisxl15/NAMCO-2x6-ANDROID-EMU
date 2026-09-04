package com.armsx2

/**
 * BIOS info as parsed by emucore's IsBIOSFromFd. Region values are the same
 * values [BiosTools.cpp:138] returns from the ROMVER fifth-byte switch:
 *
 * - 0  Japan
 * - 1  USA
 * - 2  Europe
 * - 4  Asia (H)
 * - 6  China
 * - 8  T10K / COH-H (debug board)
 * - 9  Test
 * - 10 Free
 *
 * `version` packs major in the high byte and minor in the low byte
 * (e.g. v2.30 → 0x0230).
 *
 * Constructed from native code via JNI; keep the constructor signature
 * (I,I,Ljava/lang/String;,Ljava/lang/String;) in sync with native-lib.cpp.
 */
class BiosInfo(
    @JvmField val version: Int,
    @JvmField val region: Int,
    @JvmField val description: String,
    @JvmField val zone: String,
) {
    /** "v2.30" form derived from the packed version int. */
    val versionString: String get() = "v%d.%02d".format((version shr 8) and 0xFF, version and 0xFF)

    /** Short region code for the BIOS list. Set in text rather than flag emoji: emoji render in
     *  a different weight (and on some devices not at all), so a row of them sits oddly beside
     *  the version and date it lines up with. */
    val regionFlag: String get() = when (region) {
        0 -> "JP"
        1 -> "US"
        2 -> "EU"
        4 -> "ASIA"
        6 -> "CN"
        8 -> "DEVKIT"
        9 -> "TEST"
        10 -> "FREE"
        else -> "?"
    }
}
