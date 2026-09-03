package com.armsx2.ui.emotion

import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import com.armsx2.R
import com.armsx2.runtime.MainActivityRuntime

/**
 * Emotion Launcher look, ported natively from the desktop Electron front-end
 * (ps2-launcher). This is a faithful reimplementation of its "Aero Glass / PS2 Blue"
 * design tokens as Compose values — see renderer/css/styles.css for the source palette.
 *
 * The three original themes (blue / silver / white) are kept as [EmotionThemeId]; the
 * palette below is the default blue. [EmotionTheme.of] returns the active palette so the
 * whole UI (and the animated wave background) follows one source of truth, exactly like
 * the CSS variables did.
 */
enum class EmotionThemeId { Blue, Silver, White }

/** Whether the Emotion Launcher home replaces the stock ARMSX2 library UI. */
object EmotionUi {
    private const val KEY = "ui.emotionLauncher"
    val enabled = mutableStateOf(true)
    val theme = mutableStateOf(EmotionThemeId.Blue)

    private const val THEME_KEY = "ui.emotionTheme"

    fun load() {
        enabled.value = MainActivityRuntime.prefs.getBoolean(KEY, true)
        theme.value = runCatching {
            EmotionThemeId.valueOf(
                MainActivityRuntime.prefs.getString(THEME_KEY, EmotionThemeId.Blue.name)
                    ?: EmotionThemeId.Blue.name,
            )
        }.getOrDefault(EmotionThemeId.Blue)
    }

    fun setEnabled(value: Boolean) {
        enabled.value = value
        MainActivityRuntime.prefs.edit().putBoolean(KEY, value).apply()
    }

    fun setTheme(id: EmotionThemeId) {
        theme.value = id
        MainActivityRuntime.prefs.edit().putString(THEME_KEY, id.name).apply()
    }
}

/** A resolved palette. Colors mirror the `:root` (and `[data-theme]`) variables in styles.css. */
data class EmotionPalette(
    val bg0: Color,
    val bg1: Color,
    val bg2: Color,
    val blueDeep: Color,
    val blueMid: Color,
    val blueHi: Color,
    val blueGlow: Color,
    val cyan: Color,
    val cyanDim: Color,
    val white: Color,
    val text1: Color,
    val text2: Color,
    val text3: Color,
    val glass: Color,
    val glassLight: Color,
    val glassBorder: Color,
    val selBg: Color,
    val selBorder: Color,
    /** Wave-background stroke colours, matching effects.js THEMES[*].waves (alpha applied at draw). */
    val waves: List<Color>,
    val particles: List<Color>,
    val vignette: Color,
) {
    val glow get() = blueGlow
}

object EmotionTheme {
    private val Blue = EmotionPalette(
        bg0 = Color(0xFF000510), bg1 = Color(0xFF000C28), bg2 = Color(0xFF001240),
        blueDeep = Color(0xFF001848), blueMid = Color(0xFF0050C8), blueHi = Color(0xFF0080FF),
        blueGlow = Color(0xFF00A8FF), cyan = Color(0xFF00D8FF), cyanDim = Color(0xFF0090B0),
        white = Color(0xFFFFFFFF), text1 = Color(0xFFE8F4FF), text2 = Color(0xFF7AB8E8),
        text3 = Color(0xFF3A6080),
        glass = Color(0x8C041032), glassLight = Color(0x730A1E50),
        glassBorder = Color(0x2E5096FF),
        selBg = Color(0x800A5AD2), selBorder = Color(0xD900C8FF),
        waves = listOf(
            Color(0xFF0050C8), Color(0xFF008CFF), Color(0xFF003CA0),
            Color(0xFF00C8FF), Color(0xFF0064DC), Color(0xFF50C8FF),
        ),
        particles = listOf(Color(0xFF00D8FF), Color(0xFF80C8FF), Color(0xFFFFFFFF)),
        vignette = Color(0x8C000010),
    )

    private val Silver = EmotionPalette(
        bg0 = Color(0xFF0A0C10), bg1 = Color(0xFF161A20), bg2 = Color(0xFF222831),
        blueDeep = Color(0xFF3A434F), blueMid = Color(0xFF7B8B9E), blueHi = Color(0xFFAEBCCB),
        blueGlow = Color(0xFFCFDBE8), cyan = Color(0xFFEAF2FB), cyanDim = Color(0xFF92A4B6),
        white = Color(0xFFFFFFFF), text1 = Color(0xFFF1F5F9), text2 = Color(0xFFB6C3D0),
        text3 = Color(0xFF7C8995),
        glass = Color(0x8C28303A), glassLight = Color(0x7338424E),
        glassBorder = Color(0x33AABED2),
        selBg = Color(0x6B96AAC3), selBorder = Color(0xE6DEEAF7),
        waves = listOf(
            Color(0xFF96AAC3), Color(0xFFB9CDE1), Color(0xFF788CA5),
            Color(0xFFD7E6F5), Color(0xFF8CA2B9), Color(0xFFCDDCEE),
        ),
        particles = listOf(Color(0xFFD6E4F2), Color(0xFFA6BCCF), Color(0xFFFFFFFF)),
        vignette = Color(0x99020408),
    )

    private val White = EmotionPalette(
        bg0 = Color(0xFFF2F6FB), bg1 = Color(0xFFE2EAF3), bg2 = Color(0xFFD2DDE9),
        blueDeep = Color(0xFF1E5AA0), blueMid = Color(0xFF235AAA), blueHi = Color(0xFF3A7FD0),
        blueGlow = Color(0xFF3C73BE), cyan = Color(0xFF25508A), cyanDim = Color(0xFF5F96D2),
        white = Color(0xFF0A1828), text1 = Color(0xFF0E2038), text2 = Color(0xFF2C4E76),
        text3 = Color(0xFF6A88AA),
        glass = Color(0xCCFFFFFF), glassLight = Color(0xB3EAF1FA),
        glassBorder = Color(0x33235AAA),
        selBg = Color(0x333A7FD0), selBorder = Color(0xE6235AAA),
        waves = listOf(
            Color(0xFF3C73BE), Color(0xFF235AAA), Color(0xFF5F96D2),
            Color(0xFF1950A0), Color(0xFF4B82C8), Color(0xFF78AADE),
        ),
        particles = listOf(Color(0xFF3A7FD0), Color(0xFF6AA0E0), Color(0xFF9CC2EE)),
        vignette = Color(0x4778AABE),
    )

    fun of(id: EmotionThemeId): EmotionPalette = when (id) {
        EmotionThemeId.Blue -> Blue
        EmotionThemeId.Silver -> Silver
        EmotionThemeId.White -> White
    }

    @Composable
    fun current(): EmotionPalette = of(EmotionUi.theme.value)
}

/** The EmotionEngine typeface the desktop launcher uses for every heading. */
val EmotionFont = FontFamily(
    Font(R.font.emotionengine_regular, FontWeight.Normal),
    Font(R.font.emotionengine_bold, FontWeight.Bold),
    Font(R.font.emotionengine_italic, FontWeight.Normal, FontStyle.Italic),
    Font(R.font.emotionengine_bolditalic, FontWeight.Bold, FontStyle.Italic),
)
