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
 * The middle state is the one that matters and the one no other part of the app can express: the
 * board boots, the demo loop plays, and it never reaches a game. The tracker calls that
 * "attract", after the attract mode an arcade cabinet runs while nobody is playing — which is
 * exactly right and completely opaque to anyone who has not run a cabinet. Translated literally
 * it came out as "só atrai", which is not even jargon, just words. So the badge says what
 * happens instead of what the state is called, and [explain] spells it out underneath.
 */
@Composable
fun CompatBadge(status: ArcadeCompat.Status, modifier: Modifier = Modifier) {
    val (label, tint) = when (status) {
        ArcadeCompat.Status.Playable -> "Jogável" to PLAYABLE
        ArcadeCompat.Status.Attract -> "Só demonstração" to ATTRACT
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

/**
 * One line saying what the badge means, for the states where the word alone is not enough.
 * Null for a playable game: there, the badge says everything and a sentence explaining that a
 * game works is just noise.
 */
fun explain(status: ArcadeCompat.Status): String? = when (status) {
    ArcadeCompat.Status.Attract -> "Liga e roda a demonstração, mas não chega a uma partida."
    ArcadeCompat.Status.Untested -> "Ninguém reportou este jogo ainda. Pode funcionar."
    ArcadeCompat.Status.Playable, ArcadeCompat.Status.Unknown -> null
}

/**
 * The tracker's note, said in the app's own voice.
 *
 * The notes are written by and for the people maintaining the list, which is why they read like
 * "Game Rejects system256 BIOS!" -- shorthand, in English, and phrased as a fault rather than as
 * something the player can act on. Shown raw, next to a green "Jogável" badge, it says a game
 * both works and is broken.
 *
 * So the ones we know are rewritten to say what it means for the person holding the phone, and
 * anything unrecognised is passed through unchanged: a note the tracker adds tomorrow is still
 * worth more on screen than nothing, even in its original words.
 */
fun humanNote(note: String): String {
    val n = note.lowercase()
    return when {
        // "Game Rejects system256 BIOS!"
        n.contains("rejects system256") ->
            "Não inicia com uma BIOS de System 256. O emulador troca sozinho se você tiver uma de 246."
        // "crashes when using official sony COH-H ps2 board bios ... 246C and 256 bios work ok"
        n.contains("a-000-010") ->
            "Trava com a BIOS oficial da Sony (COH-H A-000-010). Funciona com as de 246C e 256, e o " +
                "emulador troca sozinho se tiver uma delas."
        // "game studio recycled security dongles bought for vampire night here..."
        n.contains("recycled security dongles") ->
            "O estúdio reaproveitou o dongle de outro jogo, então este usa um código de jogo não oficial."
        else -> note
    }
}

// Not the accent red: this is a verdict, and colouring "playable" with the brand colour would
// make the good case indistinguishable from every other chip on the screen.
private val PLAYABLE = Color(0xFF4ADE80)
private val ATTRACT = Color(0xFFFACC15)
private val UNTESTED = Color(0xFF94A3B8)
