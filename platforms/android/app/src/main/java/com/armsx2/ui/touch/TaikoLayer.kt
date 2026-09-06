package com.armsx2.ui.touch

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.changedToDown
import androidx.compose.ui.input.pointer.pointerInput
import com.armsx2.input.Taiko
import kotlinx.coroutines.delay

/**
 * The drum, as four columns of glass.
 *
 * Full-screen and it consumes its pointers: on a Taiko cabinet there is nothing on the panel
 * except the drum, so a touch anywhere IS a hit. It still lets a touch that some widget above
 * already claimed through, which is what keeps the pause button reachable.
 *
 * Every finger counts separately. Two hands on the drum at once is not an edge case in this game,
 * it is the whole point of the big notes — a 大 is both heads struck together, and the core
 * produces one from two channels going high at the same moment.
 */
@Composable
fun TaikoLayer(widthPx: Float, heightPx: Float) {
    // Deliberately no remember in this composable at all -- not one above the early returns and
    // certainly not one below. A remember below a conditional return is reached on some
    // compositions and not others, which misaligns the slot table for the whole composition and
    // breaks something else entirely; this fork has already paid for that once.
    LaunchedEffect(Unit) {
        while (true) {
            Taiko.refresh()
            delay(1000)
        }
    }

    if (!Taiko.active.value) return
    if (widthPx <= 0f || heightPx <= 0f) return

    Box(
        Modifier
            .fillMaxSize()
            .pointerInput(widthPx) {
                awaitPointerEventScope {
                    while (true) {
                        val ev = awaitPointerEvent()
                        for (ch in ev.changes) {
                            // Only the strike matters. A drum has no "release" and no drag: the
                            // stick lands and comes back up, and a finger sliding across the head
                            // is one hit, not four.
                            if (!ch.changedToDown() || ch.isConsumed) continue
                            Taiko.hit(Taiko.padForX(ch.position.x, widthPx))
                            ch.consume()
                        }
                    }
                }
            },
    ) {
        Canvas(Modifier.fillMaxSize()) {
            Taiko.columns.forEachIndexed { column, range ->
                val pad = PAD_BY_COLUMN[column]
                val x = range.start * size.width
                val w = (range.endInclusive - range.start) * size.width
                // Read inside the draw lambda: the lit mask changes on every strike, and this
                // way a hit repaints without recomposing the layer that owns the pointers.
                val on = (Taiko.lit.value and (1 shl pad)) != 0
                drawRect(
                    color = if (Taiko.isDon(pad)) DON else KA,
                    topLeft = Offset(x, 0f),
                    size = Size(w, size.height),
                    alpha = if (on) 0.34f else 0.09f,
                )
                if (column > 0) {
                    drawRect(
                        color = Color.White,
                        topLeft = Offset(x - 1f, 0f),
                        size = Size(2f, size.height),
                        alpha = 0.10f,
                    )
                }
            }
        }
    }
}

/** Screen order: rim, head, head, rim. */
private val PAD_BY_COLUMN = intArrayOf(Taiko.KA_LEFT, Taiko.DON_LEFT, Taiko.DON_RIGHT, Taiko.KA_RIGHT)

// The drum's own colours: the heads are red, the rims blue. Faint until struck -- the picture
// behind this is the game, and a panel bright enough to read is a panel you cannot see past.
private val DON = Color(0xFFE03A3A)
private val KA = Color(0xFF3A7BE0)
