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

    /** The author's GitHub, the one link the About page carries. */
    const val repository = "jessicanataliagta"
    const val repositoryUrl = "https://github.com/jessicanataliagta"

    /** Shown as text, not as a link. The emulator core is PCSX2 (LGPL/GPL), and dropping the
     *  attribution along with the link would be wrong regardless of what the About page looks
     *  like -- so the credit stays even though the clickable card is gone. */
    const val basedOn = "PCSX2"
}
