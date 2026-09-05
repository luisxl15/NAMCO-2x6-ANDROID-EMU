package com.armsx2.art

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.os.Handler
import android.os.Looper
import androidx.compose.runtime.mutableStateOf
import com.armsx2.runtime.MainActivityRuntime
import java.io.File

/**
 * The cabinet bezel for the running arcade game.
 *
 * A System 246/256 game is 4:3 and a phone is not, so the picture fills the middle of the screen
 * and leaves two black columns. The bezel pack is the art that filled exactly that space on the
 * real machine.
 *
 * Distinct from [com.armsx2.OverlayRepo], which is for RetroArch overlay packs the player installed
 * themselves: those are one global choice for everything. This is per game, picked from the game's
 * id without being asked, and it defers to a chosen overlay rather than overriding it — someone
 * who went and installed their own artwork meant it.
 *
 * Cached in the emulator's own `bezels/` folder, so swapping one for a different picture is a
 * matter of dropping a file in under the same name.
 */
object ArcadeBezel {

    private const val KEY = "bezel.arcade"

    /** Whether to use a game's own bezel. On by default: it is the point of having them. */
    val enabled = mutableStateOf(true)

    /**
     * The decoded bezel, as Compose state.
     *
     * State, not a plain field, and that is the whole lesson here: the first version cached the
     * bitmap in a `@Volatile var`, so when the background download finished there was nothing for
     * Compose to notice and the bezel simply never appeared until something else happened to
     * redraw the screen. A value the UI reads has to be a value the UI can subscribe to.
     */
    val bitmap = mutableStateOf<Bitmap?>(null)

    private val loadedFor = mutableStateOf<String?>(null)

    @Volatile private var fetching: String? = null

    fun load() {
        enabled.value =
            runCatching { MainActivityRuntime.prefs.getBoolean(KEY, true) }.getOrDefault(true)
    }

    /**
     * Aspect ratios the bezel is drawn for: 4:3, and the Auto that resolves to 4:3 on these
     * boards. (Settings.aspectRatio: 0 Stretch, 1 Auto 4:3/3:2, 2 4:3, 3 16:9, ...)
     *
     * The art is a photograph of a cabinet with a hole cut where the screen was, and the hole is
     * 4:3 because the screen was. Stretch the picture to fill a phone and the game runs out under
     * the cabinet -- the bezel stops being a frame and becomes something covering the game.
     */
    private val FOUR_THREE = setOf(1, 2)

    /** Whether the running game's picture is the shape the bezel was cut for. */
    fun aspectAllowsBezel(): Boolean = runCatching {
        val serial = MainActivityRuntime.currentGame.value?.settingsKey
        com.armsx2.config.ConfigStore.resolveForGame(serial).aspectRatio in FOUR_THREE
    }.getOrDefault(true)

    /**
     * Turn it on, if the picture is the right shape.
     *
     * Refuses rather than turning on and drawing something wrong: a bezel over a stretched
     * picture looks like a bug in the bezel, and the player would have no way to know the aspect
     * setting three screens away is what did it. So it says so.
     */
    fun setEnabled(on: Boolean, onRefused: (String) -> Unit = {}) {
        if (on && !aspectAllowsBezel()) {
            onRefused(
                "O bezel só funciona com a imagem em 4:3. Mude a proporção para 4:3 ou Automático " +
                    "em Configurações → Renderizador.",
            )
            return
        }
        enabled.value = on
        runCatching { MainActivityRuntime.prefs.edit().putBoolean(KEY, on).apply() }
        if (!on) forget()
    }

    private fun forget() {
        loadedFor.value = null
        bitmap.value = null
    }

    /**
     * The bezel for [serial], loading or fetching it as needed.
     *
     * Called from a composable that runs every frame, so it does the least it can: once the right
     * bezel is loaded this is two state reads and a string compare. Never downloads or decodes on
     * the calling thread — a first run would stall the frame — so the first answer for a new game
     * is null and the picture arrives when the file does.
     */
    fun bitmapFor(context: Context, serial: String?): Bitmap? {
        if (!enabled.value) return null
        // Checked here too, not only where it is switched on: the aspect can be changed later,
        // from a different screen, and the bezel has to stop rather than sit over the picture.
        if (!aspectAllowsBezel()) return null
        val id = serial?.uppercase()?.takeIf { it.isNotBlank() }
        if (id == null) {
            if (loadedFor.value != null) forget()
            return null
        }
        if (loadedFor.value == id) return bitmap.value
        if (!ArcadeMedia.hasBezel(id)) {
            loadedFor.value = id
            bitmap.value = null
            return null
        }
        loadInBackground(context, id)
        return null
    }

    /** Fetch if missing, decode, and publish — all off the UI thread. */
    private fun loadInBackground(context: Context, id: String) {
        if (fetching == id) return
        fetching = id
        Thread {
            val file = runCatching { ArcadeMedia.bezel(context, id) }.getOrNull()
            val bmp = file?.let {
                runCatching { BitmapFactory.decodeFile(it.absolutePath) }.getOrNull()
            }
            // Back to the main thread to publish: Compose state must be written there.
            Handler(Looper.getMainLooper()).post {
                // Only if the game has not changed under us while this was running.
                if (fetching == id) {
                    bitmap.value = bmp
                    loadedFor.value = id
                }
                fetching = null
            }
        }.apply { isDaemon = true }.start()
    }
}
