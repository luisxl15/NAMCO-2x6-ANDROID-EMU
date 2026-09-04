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
    const val name = "System 246"
    const val tagline = "Emulação de arcade NAMCO System 246/256 para Android."

    /** The fork this is built from -- the arcade work, not the console launcher. */
    const val repository = "PS2Homebrew-arcade/pcsx2x6"
    const val repositoryUrl = "https://github.com/PS2Homebrew-arcade/pcsx2x6"
}
