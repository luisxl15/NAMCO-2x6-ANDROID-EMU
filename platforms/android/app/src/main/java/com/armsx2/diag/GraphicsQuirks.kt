package com.armsx2.diag

/**
 * What the graphics stack under this app can actually do.
 *
 * One thing so far, and it is not a preference: HARDWARE bitmaps. Coil decodes cover art and game
 * logos straight into graphics memory, which is the right thing on a phone -- no Java heap copy,
 * no upload on every frame. It needs the gralloc mapper to be there. Inside an Android emulator
 * running on a PC it often is not: this app's own log on MSI App Player says
 *
 *     I/Gralloc4: mapper 4.x is not supported
 *     W/Gralloc3: mapper 3.x is not supported
 *
 * and the artwork then draws as a white smear -- a 600x900 cover as a grey gradient, a wordmark as
 * an unreadable sliver -- while everything drawn from resources is fine. Nothing errors: the
 * bitmap decodes, it just cannot be sampled.
 *
 * So the check is on the stack, not on a list of device names. `ro.hardware.egl` says what is
 * providing GL, and a translation layer says so plainly ("emulation" for the goldfish/ranchu
 * stack every PC emulator descends from, "swiftshader" for the CPU rasteriser). A phone never
 * answers either. Everywhere else nothing changes and hardware bitmaps stay on.
 */
object GraphicsQuirks {

    private val EMULATED_GL = setOf("emulation", "swiftshader", "swiftshader_indirect")

    /** True when Coil may decode into graphics memory -- i.e. everywhere but an emulated GL stack. */
    val hardwareBitmaps: Boolean by lazy { !emulatedGl() }

    private fun emulatedGl(): Boolean {
        if (SysProp.read("ro.kernel.qemu") == "1") return true
        val egl = SysProp.read("ro.hardware.egl")?.trim()?.lowercase().orEmpty()
        return egl in EMULATED_GL
    }
}
