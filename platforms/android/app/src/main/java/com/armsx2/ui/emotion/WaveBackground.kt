package com.armsx2.ui.emotion

import androidx.compose.foundation.Canvas
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import kotlinx.coroutines.android.awaitFrame
import kotlin.math.sin
import kotlin.random.Random

/**
 * PS2-style animated background — data-stream sine waves + rising particles + a radial
 * vignette. A direct port of ps2-launcher/renderer/js/effects.js. One frame loop advances
 * both the wave phase clock and the particle simulation; the Canvas only reads state.
 */

private data class WaveSpec(
    val amp: Float, val freq: Float, val speed: Float,
    val phase: Float, val lineWidth: Float, val alpha: Float,
)

private val WAVES = listOf(
    WaveSpec(90f, 0.0018f, 0.0006f, 0f, 1.5f, 0.25f),
    WaveSpec(60f, 0.0024f, 0.0009f, 2.1f, 1.2f, 0.20f),
    WaveSpec(120f, 0.0013f, 0.0004f, 4.7f, 2.5f, 0.15f),
    WaveSpec(45f, 0.0032f, 0.0014f, 1.3f, 0.8f, 0.30f),
    WaveSpec(75f, 0.0020f, 0.0007f, 3.5f, 1.8f, 0.18f),
    WaveSpec(30f, 0.0042f, 0.0018f, 5.1f, 0.6f, 0.22f),
)

private class Particle(
    var x: Float, var y: Float,
    val vx: Float, val vy: Float,
    val r: Float, val maxA: Float,
    var life: Float, val maxLife: Float,
    val color: Color,
)

@Composable
fun WaveBackground(
    palette: EmotionPalette,
    modifier: Modifier = Modifier,
) {
    // Phase clock in JS-equivalent milliseconds, and a repaint tick the Canvas observes.
    var clock by remember { mutableFloatStateOf(0f) }
    var tick by remember { mutableIntStateOf(0) }
    val particles = remember { ArrayList<Particle>(96) }
    val rng = remember { Random(0xE20205) }
    var w by remember { mutableFloatStateOf(0f) }
    var h by remember { mutableFloatStateOf(0f) }

    LaunchedEffect(Unit) {
        var last = 0L
        while (true) {
            val now = awaitFrame()
            val dtMs = if (last == 0L) 16f else ((now - last) / 1_000_000f).coerceIn(0f, 64f)
            last = now
            clock += dtMs

            if (w > 0f && h > 0f) {
                // Spawn up to the cap.
                if (particles.size < 80) {
                    repeat(2) {
                        particles.add(
                            Particle(
                                x = rng.nextFloat() * w, y = h + 10f,
                                vx = (rng.nextFloat() - 0.5f) * 0.6f,
                                vy = -(rng.nextFloat() * 1.2f + 0.4f),
                                r = rng.nextFloat() * 2.5f + 0.5f,
                                maxA = rng.nextFloat() * 0.7f + 0.2f,
                                life = 0f, maxLife = rng.nextFloat() * 300f + 200f,
                                color = palette.particles[rng.nextInt(palette.particles.size)],
                            ),
                        )
                    }
                }
                val frames = (dtMs / 16f).coerceIn(0.5f, 3f)
                val it = particles.iterator()
                while (it.hasNext()) {
                    val p = it.next()
                    p.x += p.vx * frames
                    p.y += p.vy * frames
                    p.life += frames
                    if (p.life >= p.maxLife || p.y < -10f) it.remove()
                }
            }
            tick++
        }
    }

    Canvas(modifier = modifier) {
        w = size.width; h = size.height
        @Suppress("UNUSED_EXPRESSION") tick // observe the repaint tick

        drawRect(
            brush = Brush.verticalGradient(0f to palette.bg0, 0.5f to palette.bg1, 1f to palette.bg2),
        )
        drawWaves(clock, palette)

        for (p in particles) {
            val progress = p.life / p.maxLife
            val a = when {
                progress < 0.15f -> p.maxA * (progress / 0.15f)
                progress > 0.7f -> p.maxA * (1f - (progress - 0.7f) / 0.3f)
                else -> p.maxA
            }
            drawCircle(color = p.color.copy(alpha = a.coerceIn(0f, 1f)), radius = p.r, center = Offset(p.x, p.y))
        }

        drawRect(
            brush = Brush.radialGradient(
                colors = listOf(Color.Transparent, palette.vignette),
                center = Offset(size.width / 2f, size.height / 2f),
                radius = maxOf(size.width, size.height) * 0.75f,
            ),
        )
    }
}

private fun DrawScope.drawWaves(t: Float, palette: EmotionPalette) {
    val w = size.width
    val y0 = size.height * 0.5f
    WAVES.forEachIndexed { i, wave ->
        val color = palette.waves[i % palette.waves.size]

        fun buildPath(stepPx: Int): Path {
            val path = Path()
            var x = 0f
            var first = true
            while (x <= w + 10f) {
                val y = y0 +
                    sin(x * wave.freq + wave.phase + t * wave.speed) * wave.amp +
                    sin(x * wave.freq * 1.7f + t * wave.speed * 0.6f) * wave.amp * 0.4f
                if (first) { path.moveTo(x, y); first = false } else path.lineTo(x, y)
                x += stepPx
            }
            return path
        }

        drawPath(buildPath(4), color.copy(alpha = wave.alpha), style = Stroke(width = wave.lineWidth))
        drawPath(buildPath(8), color.copy(alpha = wave.alpha * 0.15f), style = Stroke(width = wave.lineWidth * 4f))
    }
}
