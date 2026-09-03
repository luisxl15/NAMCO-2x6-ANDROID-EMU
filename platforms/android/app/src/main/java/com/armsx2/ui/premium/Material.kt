package com.armsx2.ui.premium

import android.os.Build
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/** How opaque a material reads, mirroring Apple's ultraThin → thick ladder. */
enum class MaterialLevel(val fill: Color) {
    UltraThin(Palette.materialUltraThin),
    Thin(Palette.materialThin),
    Regular(Palette.materialRegular),
    Thick(Palette.materialThick),
}

/**
 * A translucent glass panel: vibrancy fill, a hairline edge, and the specular highlight a real
 * glass surface catches along its top. Sits over [AuroraBackground], which supplies the soft
 * backdrop that makes the translucency read as depth without a per-panel runtime blur.
 */
fun Modifier.material(
    level: MaterialLevel = MaterialLevel.Regular,
    shape: RoundedCornerShape = RoundedCornerShape(Radii.card),
    borderColor: Color = Palette.hairline,
    elevation: Dp = 0.dp,
): Modifier {
    var m = this
    if (elevation > 0.dp) {
        m = m.shadow(elevation, shape, clip = false, ambientColor = Color.Black, spotColor = Color.Black)
    }
    return m
        .clip(shape)
        .background(level.fill)
        // Specular: a soft white sheen fading out by a third of the height.
        .background(
            Brush.verticalGradient(
                0f to Palette.specular,
                0.34f to Color.Transparent,
                1f to Color.Transparent,
            ),
        )
        .border(0.5.dp, borderColor, shape)
}

/** True where the platform can do a real backdrop blur (Android 12+). */
val supportsRuntimeBlur: Boolean
    get() = Build.VERSION.SDK_INT >= Build.VERSION_CODES.S
