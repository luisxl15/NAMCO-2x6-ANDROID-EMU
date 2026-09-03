package com.armsx2.ui.premium

import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.armsx2.runtime.MainActivityRuntime

/**
 * A premium, Apple-flavoured design system for the launcher: near-black ground, translucent
 * "material" surfaces layered over a soft out-of-focus backdrop, Apple's dark-mode label
 * colours, and the NAMCO System 246/256 red as the single accent.
 *
 * Note on materials: Apple's blur is a live backdrop filter. Compose's Modifier.blur is API 31+
 * only, and the Android 11 images this runs on can't use it — so instead of faking a blur per
 * panel, [AuroraBackground] renders a backdrop that is *already* soft (large feathered
 * gradients). Translucent panels over it read as glass on every API level, and on API 31+ the
 * panels additionally pick up a real blur (see [Modifier.material]).
 */
object PremiumUi {
    private const val KEY = "ui.premiumLauncher"
    val enabled = mutableStateOf(true)

    fun load() {
        enabled.value = MainActivityRuntime.prefs.getBoolean(KEY, true)
    }

    fun setEnabled(value: Boolean) {
        enabled.value = value
        MainActivityRuntime.prefs.edit().putBoolean(KEY, value).apply()
    }
}

object Palette {
    /** Ground. Not pure black — Apple's dark canvas sits just off it so blacks can still recede. */
    val ground = Color(0xFF0B0B0D)
    val groundDeep = Color(0xFF060607)

    /** NAMCO System 246/256 red, sampled from the logotype. */
    val accent = Color(0xFFA5121F)
    val accentBright = Color(0xFFC8202F)
    val accentGlow = Color(0x33C8202F)

    /** Apple dark-mode label colours. */
    val label = Color(0xFFF5F5F7)
    val labelSecondary = Color(0x99EBEBF5)
    val labelTertiary = Color(0x4DEBEBF5)
    val labelQuaternary = Color(0x2EEBEBF5)

    /** Vibrancy fills, mirroring ultraThin → thick material. */
    val materialUltraThin = Color(0x14FFFFFF)
    val materialThin = Color(0x1FFFFFFF)
    val materialRegular = Color(0x29FFFFFF)
    val materialThick = Color(0x38FFFFFF)

    /** Hairline separators and the specular top edge a real glass panel catches. */
    val hairline = Color(0x24FFFFFF)
    val hairlineStrong = Color(0x3DFFFFFF)
    val specular = Color(0x1FFFFFFF)

    val scrim = Color(0x99000000)
}

/**
 * Apple's type scale. The system face stands in for SF Pro — what carries the feel is the
 * scale, the weights and the tight tracking on the large sizes, not the exact glyphs.
 */
object Type {
    private val f = FontFamily.SansSerif

    val largeTitle = TextStyle(fontFamily = f, fontSize = 34.sp, lineHeight = 41.sp, fontWeight = FontWeight.Bold, letterSpacing = (-0.8).sp)
    val title1 = TextStyle(fontFamily = f, fontSize = 28.sp, lineHeight = 34.sp, fontWeight = FontWeight.Bold, letterSpacing = (-0.5).sp)
    val title2 = TextStyle(fontFamily = f, fontSize = 22.sp, lineHeight = 28.sp, fontWeight = FontWeight.SemiBold, letterSpacing = (-0.3).sp)
    val title3 = TextStyle(fontFamily = f, fontSize = 20.sp, lineHeight = 25.sp, fontWeight = FontWeight.SemiBold, letterSpacing = (-0.2).sp)
    val headline = TextStyle(fontFamily = f, fontSize = 17.sp, lineHeight = 22.sp, fontWeight = FontWeight.SemiBold, letterSpacing = (-0.1).sp)
    val body = TextStyle(fontFamily = f, fontSize = 17.sp, lineHeight = 22.sp, fontWeight = FontWeight.Normal)
    val callout = TextStyle(fontFamily = f, fontSize = 16.sp, lineHeight = 21.sp, fontWeight = FontWeight.Normal)
    val subheadline = TextStyle(fontFamily = f, fontSize = 15.sp, lineHeight = 20.sp, fontWeight = FontWeight.Medium)
    val footnote = TextStyle(fontFamily = f, fontSize = 13.sp, lineHeight = 18.sp, fontWeight = FontWeight.Normal)
    val caption = TextStyle(fontFamily = f, fontSize = 12.sp, lineHeight = 16.sp, fontWeight = FontWeight.Medium)
    /** All-caps section eyebrow, the way Apple sets small group headers. */
    val eyebrow = TextStyle(fontFamily = f, fontSize = 12.sp, lineHeight = 16.sp, fontWeight = FontWeight.SemiBold, letterSpacing = 1.2.sp)
}

object Radii {
    val card = 22.dp
    val tile = 18.dp
    val chip = 14.dp
    val control = 12.dp
    val pill = 100.dp
}
