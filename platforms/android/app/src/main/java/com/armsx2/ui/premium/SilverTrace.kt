package com.armsx2.ui.premium

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.RoundRect
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathMeasure
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import kotlin.math.min
import kotlin.math.pow

/**
 * A point of silver light that runs continuously around the outline of a shape.
 *
 * Drawn as a comet walking the shape's own path rather than as an animated gradient. A sweep
 * gradient is the usual trick, but its colour stops are pinned to angles about a centre, so
 * making the highlight MOVE means either rotating the canvas -- which turns the rounded corners
 * with it -- or reordering stops every frame, which breaks the moment the bright band wraps past
 * the seam. Walking the path with [PathMeasure] means the light follows the real outline,
 * corners included, and the wrap is just two segments.
 *
 * The glow is faked in layers, because Compose's blur modifier is API 31 and up and this ships to
 * Android 11: the tail is stroked three times per slice at falling width and rising opacity, and
 * the head carries two soft discs so the light has an obvious source rather than just being the
 * bright end of a line.
 *
 * [phase] offsets where an element's light starts, so a row of them does not sweep in lockstep
 * like a progress bar.
 */
@Composable
fun Modifier.silverTrace(
    cornerRadius: Dp,
    phase: Float = 0f,
    durationMillis: Int = 5600,
    intensity: Float = 1f,
): Modifier {
    val transition = rememberInfiniteTransition(label = "silverTrace")
    val head by transition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(durationMillis, easing = LinearEasing)),
        label = "silverTraceHead",
    )

    // Allocated once and rewound per frame: a Path and a PathMeasure per element per frame would
    // be real garbage on a screen running seven of these at 60fps.
    val outline = remember { Path() }
    val piece = remember { Path() }
    val measure = remember { PathMeasure() }

    return this.drawWithContent {
        drawContent()

        val radius = min(cornerRadius.toPx(), min(size.width, size.height) / 2f)
        outline.rewind()
        outline.addRoundRect(
            RoundRect(Rect(0f, 0f, size.width, size.height), CornerRadius(radius, radius)),
        )
        measure.setPath(outline, true)
        val total = measure.length
        if (total <= 0f) return@drawWithContent

        val at = ((head + phase) % 1f) * total
        val tail = total * TAIL_FRACTION

        // Painted widest-and-faintest first, so the hot filament lands on top of its own halo.
        // Each layer is sliced only as finely as it needs: a 7dp band gets its softness from its
        // width, and cutting it into eighteen pieces just multiplies the stroke count. The thin
        // bright layers are the ones where a coarse slice would show as banding.
        for (layer in LAYERS) {
            for (step in layer.steps - 1 downTo 0) {
                val near = at - tail * step / layer.steps
                val far = at - tail * (step + 1) / layer.steps
                // Distance behind the head, 0 at the head and 1 at the tip. The exponent keeps
                // the trail present well behind the head before it falls away, rather than
                // collapsing right after the leading edge.
                val d = (step + 0.5f) / layer.steps
                val ramp = (1f - d).pow(1.45f) * intensity
                drawTailSlice(measure, piece, far, near, total, layer.width.toPx(), ramp * layer.alpha)
            }
        }

        // The source. Two soft discs and a hot core, so the leading edge looks lit rather than
        // simply drawn brighter.
        val point = measure.getPosition(at)
        drawHead(point, intensity)
    }
}

/** One pass of the tail: a width, its share of the brightness, and how finely it is sliced. */
private class TraceLayer(val width: Dp, val alpha: Float, val steps: Int)

private val LAYERS = listOf(
    TraceLayer(7.0.dp, 0.055f, steps = 5),
    TraceLayer(3.6.dp, 0.130f, steps = 8),
    TraceLayer(1.5.dp, 0.520f, steps = 16),
    TraceLayer(0.9.dp, 0.900f, steps = 16),
)

private fun DrawScope.drawHead(point: Offset, intensity: Float) {
    val glow = 7.0.dp.toPx()
    drawCircle(
        brush = Brush.radialGradient(
            colors = listOf(SILVER.copy(alpha = 0.30f * intensity), Color.Transparent),
            center = point,
            radius = glow,
        ),
        radius = glow,
        center = point,
    )
    drawCircle(SILVER.copy(alpha = 0.85f * intensity), radius = 1.1.dp.toPx(), center = point)
}

/**
 * Stroke the piece of [measure]'s path between [from] and [to], wrapping at the seam when the
 * range runs off the start. getSegment does not wrap, so a range crossing zero is asked for
 * as two.
 */
private fun DrawScope.drawTailSlice(
    measure: PathMeasure,
    scratch: Path,
    from: Float,
    to: Float,
    total: Float,
    width: Float,
    alpha: Float,
) {
    if (alpha <= 0.004f || to <= from) return
    val stroke = Stroke(width = width, cap = StrokeCap.Round)
    val paint = SILVER.copy(alpha = alpha)

    fun segment(a: Float, b: Float) {
        if (b <= a) return
        scratch.rewind()
        measure.getSegment(a, b, scratch, true)
        drawPath(scratch, color = paint, style = stroke)
    }

    if (from >= 0f) {
        segment(from, to)
    } else {
        // Crossed the seam: the older part of this slice is at the far end of the path.
        segment(total + from, total)
        segment(0f, to.coerceAtLeast(0f))
    }
}

/** Cool white, so it reads as polished metal rather than as a coloured accent. */
private val SILVER = Color(0xFFE6EAF4)

/** How much of the perimeter the comet occupies. Long enough to read as a sweep rather than a
 *  nick in the edge, short enough that the outline is clearly not simply lit all round. */
private const val TAIL_FRACTION = 0.40f
