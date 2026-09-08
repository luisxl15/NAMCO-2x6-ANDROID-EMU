package com.armsx2.ui.premium

import android.content.Context
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.armsx2.data.library.ArcadeCompat

/**
 * Which of the two boards a title is from.
 *
 * System 246 and System 256 look like the same machine from the outside and are not
 * interchangeable underneath: a 256 game wants a 256 BIOS, and Battle Gear 3 refuses to boot on
 * one. The launcher already knew this -- it is what the wizard writes into `platform=` and what
 * decides which BIOS gets picked -- but nowhere on screen did it say so, and the meta line said
 * "NAMCO System 246/256" for every game, which is the one thing that is never true of any of
 * them.
 *
 * So the number goes on the artwork, small, where a shelf of covers can be read at a glance: the
 * board is the first thing that matters when a game will not start, and the last thing anybody
 * memorises for fifty-five titles.
 *
 * The colour is a second channel, never the only one -- the number is always written out, so the
 * tag still works for a player who cannot tell the two hues apart, and neither hue is the red
 * this app spends everywhere else or the green/amber the compatibility verdict owns.
 */
@Composable
fun BoardBadge(board: String, modifier: Modifier = Modifier) {
    val kind = normalise(board).ifEmpty { return }
    Box(
        modifier
            .clip(RoundedCornerShape(Radii.pill))
            .background(tint(kind).copy(alpha = 0.16f))
            .padding(horizontal = 11.dp, vertical = 5.dp),
    ) {
        Text("System $kind", style = Type.caption, color = tint(kind))
    }
}

/**
 * The same fact, sized to sit on top of cover art.
 *
 * Artwork is not a background we control -- a cover can be white, busy, or bright exactly where
 * the tag lands -- so this one carries its own dark plate and hairline instead of tinting a
 * transparent fill the way [BoardBadge] does over a known surface.
 */
@Composable
fun BoardTag(board: String, modifier: Modifier = Modifier) {
    val kind = normalise(board).ifEmpty { return }
    Box(
        modifier
            .clip(RoundedCornerShape(7.dp))
            .background(Palette.ground.copy(alpha = 0.72f))
            .border(0.5.dp, tint(kind).copy(alpha = 0.55f), RoundedCornerShape(7.dp))
            .padding(horizontal = 5.dp, vertical = 2.dp),
    ) {
        Text(kind, style = TagType, color = tint(kind))
    }
}

/** The board for a game id, from the bundled tracker, or "" when the list does not carry it. */
fun boardFor(context: Context, gameId: String?): String =
    normalise(ArcadeCompat.entryFor(context, gameId)?.board)

/**
 * "246" or "256" out of whatever names the board, and "" when nothing does.
 *
 * The tracker writes a bare number, a manifest writes `platform=246`, and a note might say
 * "System 256" or "246C" -- the same two facts in four spellings. 256 is tested first so
 * "246/256", which means a game that wants the newer board, does not read as 246.
 */
internal fun normalise(raw: String?): String = when {
    raw.isNullOrBlank() -> ""
    raw.contains("256") -> "256"
    raw.contains("246") -> "246"
    else -> ""
}

/** The board named in full, for a line of prose rather than a chip. */
internal fun boardName(raw: String?): String = when (normalise(raw)) {
    "246" -> "NAMCO System 246"
    "256" -> "NAMCO System 256"
    else -> "NAMCO System 246/256"
}

private fun tint(kind: String): Color = if (kind == "256") BOARD_256 else BOARD_246

// Neither is the brand red, and neither is the green/amber/grey the compatibility badge owns:
// a cover carrying both tags has to read as two separate facts.
private val BOARD_246 = Color(0xFF38BDF8)
private val BOARD_256 = Color(0xFFA78BFA)

private val TagType = TextStyle(
    fontFamily = FontFamily.SansSerif,
    fontSize = 10.sp,
    lineHeight = 12.sp,
    fontWeight = FontWeight.Bold,
    letterSpacing = 0.4.sp,
)
