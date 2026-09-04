package com.armsx2.ui.premium

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.armsx2.data.library.ArcadeCompat

/**
 * How far a title is known to get, from the project's compatibility tracker.
 *
 * Worth stating plainly on the game itself. "Attract" is the distinction that matters and the one
 * no other part of the app can express: the board boots, the attract loop plays, and it never
 * reaches a game — which without a label reads as the emulator being broken rather than as a
 * known limit someone already recorded.
 */
@Composable
fun CompatBadge(status: ArcadeCompat.Status, modifier: Modifier = Modifier) {
    val (label, tint) = when (status) {
        ArcadeCompat.Status.Playable -> "Jogável" to PLAYABLE
        ArcadeCompat.Status.Attract -> "Só atrai" to ATTRACT
        ArcadeCompat.Status.Untested -> "Não testado" to UNTESTED
        ArcadeCompat.Status.Unknown -> return
    }
    Box(
        modifier
            .clip(RoundedCornerShape(Radii.pill))
            .background(tint.copy(alpha = 0.16f))
            .padding(horizontal = 11.dp, vertical = 5.dp),
    ) {
        Text(label, style = Type.caption, color = tint)
    }
}

// Not the accent red: this is a verdict, and colouring "playable" with the brand colour would
// make the good case indistinguishable from every other chip on the screen.
private val PLAYABLE = Color(0xFF4ADE80)
private val ATTRACT = Color(0xFFFACC15)
private val UNTESTED = Color(0xFF94A3B8)
