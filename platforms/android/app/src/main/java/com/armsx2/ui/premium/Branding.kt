package com.armsx2.ui.premium

/**
 * What this build calls itself, in one place.
 *
 * The upstream launcher branded every screen "ARMSX2" and pointed its links at that project's
 * repository. This is an arcade fork: it boots System 246/256 titles through a COH-H BIOS, a JVS
 * I/O board and an ACATA drive, and the upstream releases those links offer would replace all of
 * that with a plain PS2 build. So the name is ours, and it lives here rather than in forty string
 * literals -- change [name] and the whole app follows.
 *
 * The APPLICATION ID stays com.armsx2. It is not shown anywhere in the UI, and renaming it would
 * move the app's data folder (/sdcard/Android/data/<id>), orphaning every BIOS, save and SRAM
 * already on the device and forcing a reinstall.
 */
object Branding {
    const val name = "Namco System 246 EMU"
    const val tagline = "Emulação de arcade NAMCO System 246/256 para Android."

    /**
     * What this is built from, shown as text rather than as links.
     *
     * The chain is real and worth stating in full: the arcade support comes from PCSX2x6, a fork
     * of PCSX2; the Android launcher and the ARM64 recompiler work come from ARMSX2, also built
     * on PCSX2. All three are open source, and the credit stays whatever the About page looks
     * like -- the emulator core is not ours to take credit for.
     */
    const val basedOn = "PCSX2x6 (fork do PCSX2) e ARMSX2, ambos baseados no PCSX2"
}
