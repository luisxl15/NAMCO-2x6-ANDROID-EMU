package com.armsx2.ui.premium

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

/**
 * The out-of-focus backdrop the glass sits on: a near-black ground with a few very large,
 * heavily feathered colour fields drifting slowly across it. Because the gradients are wider
 * than the screen and never sharp, the result already looks like a blurred photograph — which
 * is what lets [Modifier.material] read as real glass on Android 11, where Compose has no
 * runtime backdrop blur.
 */
@Composable
fun AuroraBackground(modifier: Modifier = Modifier) {
    val transition = rememberInfiniteTransition(label = "aurora")
    val t by transition.animateFloat(
        initialValue = 0f,
        targetValue = (2 * PI).toFloat(),
        animationSpec = infiniteRepeatable(tween(48_000, easing = LinearEasing)),
        label = "drift",
    )

    Canvas(modifier) {
        val w = size.width
        val h = size.height

        // Ground, very slightly lifted at the top so the canvas has a direction of light.
        drawRect(
            brush = Brush.verticalGradient(
                0f to Palette.ground,
                1f to Palette.groundDeep,
            ),
        )

        // Each field: centre drifts on its own slow ellipse, radius is a large fraction of the
        // screen, and alpha stays low so they only ever suggest colour.
        fun field(color: Color, phase: Float, cx: Float, cy: Float, rx: Float, ry: Float, radius: Float) {
            val x = w * cx + w * rx * cos(t + phase)
            val y = h * cy + h * ry * sin(t * 0.8f + phase)
            drawCircle(
                brush = Brush.radialGradient(
                    colors = listOf(color, Color.Transparent),
                    center = Offset(x, y),
                    radius = maxOf(w, h) * radius,
                ),
                radius = maxOf(w, h) * radius,
                center = Offset(x, y),
            )
        }

        // NAMCO red, kept to the upper left so the logo sits in its own light.
        field(Color(0x30A5121F), 0.0f, 0.22f, 0.18f, 0.10f, 0.08f, 0.62f)
        // A deeper crimson pooling bottom-right for balance.
        field(Color(0x247A0E18), 2.4f, 0.82f, 0.78f, 0.12f, 0.09f, 0.58f)
        // Cool neutral to keep the whole thing from going monochrome-warm.
        field(Color(0x1F2A3550), 4.1f, 0.62f, 0.30f, 0.14f, 0.10f, 0.70f)
        // A dim warm centre lift, so the middle of the screen isn't a dead zone.
        field(Color(0x0AFFFFFF), 5.6f, 0.45f, 0.58f, 0.08f, 0.06f, 0.40f)

        // Vignette to seat the edges.
        drawRect(
            brush = Brush.radialGradient(
                colors = listOf(Color.Transparent, Color(0xB3000000)),
                center = Offset(w / 2f, h / 2f),
                radius = maxOf(w, h) * 0.78f,
            ),
        )
    }
}
