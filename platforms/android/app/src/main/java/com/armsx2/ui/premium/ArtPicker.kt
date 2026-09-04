package com.armsx2.ui.premium

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil.compose.SubcomposeAsyncImage
import coil.request.ImageRequest
import com.armsx2.EnglishTitles
import com.armsx2.GameInfo
import com.armsx2.art.SteamGridDb
import com.armsx2.ui.settings.controllerFocusable
import kotlinx.coroutines.launch

/** Which kind of art the picker is choosing. */
enum class ArtKind { Cover, Hero }

/**
 * Pick the artwork instead of accepting whatever came back first.
 *
 * The automatic fetch takes the top search match and its first image, which fails in three ways
 * that all look identical to the user — "no cover". The title may not match exactly (an arcade
 * board's "Battle Gear 3 Tuned" is filed as "Battle Gear 3"); the top match may be a different
 * game that happens to have no art at all, hiding a correct entry further down the list; and a
 * game with several uploads may lead with a console box that has nothing to do with the arcade
 * release. All three are choices a person makes in a second and a heuristic gets wrong.
 *
 * So: the matches across the top, their art below, and the search term editable, because for a
 * title the database files under a different name nothing else will do.
 */
@Composable
fun ArtPicker(
    game: GameInfo,
    kind: ArtKind,
    onDismiss: () -> Unit,
    onApplied: () -> Unit,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    var term by remember { mutableStateOf(game.displayTitle(EnglishTitles.enabled.value)) }
    var matches by remember { mutableStateOf<List<SteamGridDb.Match>>(emptyList()) }
    var chosen by remember { mutableStateOf<SteamGridDb.Match?>(null) }
    var art by remember { mutableStateOf<List<String>>(emptyList()) }
    var busy by remember { mutableStateOf(false) }
    var note by remember { mutableStateOf<String?>(null) }
    var applying by remember { mutableStateOf<String?>(null) }

    fun search(q: String) {
        scope.launch {
            busy = true
            note = null
            art = emptyList()
            chosen = null
            SteamGridDb.searchGames(q)
                .onSuccess {
                    matches = it
                    chosen = it.firstOrNull()
                    if (it.isEmpty()) note = "Nenhum jogo com esse nome."
                }
                .onFailure { matches = emptyList(); note = SteamGridDb.describe(it) }
            busy = false
        }
    }

    // Claim the D-pad while this is up. The registry keeps an exclusive layer stack precisely so
    // a modal's selection cannot walk out through its own scrim onto the screen behind it -- and
    // without claiming one, every press here would be moving the library underneath a dialog the
    // user is looking at. LocalNavLayer is what tells each control below which layer it is in;
    // position in the tree is the only spelling of that which cannot be got wrong.
    val navLayer = "art-picker"
    androidx.compose.runtime.DisposableEffect(Unit) {
        com.armsx2.ui.settings.SettingsControllerNav.pushLayer(navLayer)
        onDispose { com.armsx2.ui.settings.SettingsControllerNav.popLayer(navLayer) }
    }

    // Back closes this, not the screen behind it. Without it the key falls through to the
    // app's own handler and opens the drawer over a dialog that is still up.
    androidx.activity.compose.BackHandler(onBack = onDismiss)

    LaunchedEffect(Unit) { search(term) }

    // Load the chosen match's art. Keyed on the match so tapping a different one re-fetches.
    LaunchedEffect(chosen, kind) {
        val match = chosen ?: return@LaunchedEffect
        busy = true
        note = null
        val result = if (kind == ArtKind.Cover) {
            SteamGridDb.coverCandidates(match.id)
        } else {
            SteamGridDb.heroCandidates(match.id)
        }
        result
            .onSuccess {
                art = it
                if (it.isEmpty()) note = "\"${match.name}\" não tem arte deste tipo. Tente outra correspondência."
            }
            .onFailure { art = emptyList(); note = SteamGridDb.describe(it) }
        busy = false
    }

    androidx.compose.runtime.CompositionLocalProvider(
        com.armsx2.ui.settings.LocalNavLayer provides navLayer,
    ) {
    Box(
        Modifier
            .fillMaxSize()
            .background(Palette.scrim)
            .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) {
                onDismiss()
            },
        contentAlignment = Alignment.Center,
    ) {
        Column(
            Modifier
                .fillMaxWidth(0.86f)
                .fillMaxHeight(0.88f)
                .clip(RoundedCornerShape(Radii.card))
                // An opaque ground under the material. Everything else in this app is glass over
                // an out-of-focus backdrop, but here the backdrop is a LIST OF THE SAME ROWS the
                // dialog is about -- reading covers through covers is just noise.
                .background(Palette.groundDeep.copy(alpha = 0.97f))
                .material(MaterialLevel.Thin, RoundedCornerShape(Radii.card), elevation = 26.dp)
                // Swallow taps so they do not reach the dismiss scrim behind.
                .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) {}
                .padding(24.dp),
        ) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(
                        if (kind == ArtKind.Cover) "Escolher capa" else "Escolher arte de fundo",
                        style = Type.title2, color = Palette.label,
                    )
                    Text(
                        game.displayTitle(EnglishTitles.enabled.value),
                        style = Type.footnote, color = Palette.labelSecondary,
                        maxLines = 1, overflow = TextOverflow.Ellipsis,
                    )
                }
                Box(
                    Modifier
                        .size(36.dp)
                        .clip(RoundedCornerShape(Radii.pill))
                        .material(MaterialLevel.Thin, RoundedCornerShape(Radii.pill))
                        .controllerFocusable("art.close", RoundedCornerShape(Radii.pill), onConfirm = onDismiss)
                        .clickable { onDismiss() },
                    contentAlignment = Alignment.Center,
                ) { ArcIcon(Arc.close, tint = Palette.labelSecondary, size = 14.dp) }
            }

            Spacer(Modifier.height(16.dp))

            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                OutlinedTextField(
                    value = term,
                    onValueChange = { term = it },
                    singleLine = true,
                    label = { Text("Buscar por", style = Type.footnote) },
                    modifier = Modifier.weight(1f),
                )
                Spacer(Modifier.width(12.dp))
                Box(
                    Modifier
                        .clip(RoundedCornerShape(Radii.pill))
                        .background(Palette.accent)
                        .clickable { search(term) }
                        .padding(horizontal = 20.dp, vertical = 13.dp),
                ) { Text("Buscar", style = Type.subheadline, color = Color.White) }
            }

            if (matches.isNotEmpty()) {
                Spacer(Modifier.height(14.dp))
                Row(
                    Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    matches.forEach { match ->
                        val on = match.id == chosen?.id
                        Box(
                            Modifier
                                .clip(RoundedCornerShape(Radii.pill))
                                .background(if (on) Palette.accent else Palette.materialUltraThin)
                                .controllerFocusable("art.match.${match.id}", RoundedCornerShape(Radii.pill), onConfirm = { chosen = match })
                        .clickable { chosen = match }
                                .padding(horizontal = 14.dp, vertical = 8.dp),
                        ) {
                            Text(
                                match.name,
                                style = Type.caption,
                                color = if (on) Color.White else Palette.labelSecondary,
                                maxLines = 1, overflow = TextOverflow.Ellipsis,
                            )
                        }
                    }
                }
            }

            Spacer(Modifier.height(16.dp))

            Box(Modifier.fillMaxSize()) {
                when {
                    busy && art.isEmpty() -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        CircularProgressIndicator(Modifier.size(26.dp), strokeWidth = 2.dp, color = Palette.accentBright)
                    }
                    art.isEmpty() -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Text(
                            note ?: "Nada encontrado.",
                            style = Type.footnote, color = Palette.labelSecondary,
                        )
                    }
                    else -> LazyVerticalGrid(
                        columns = GridCells.Adaptive(minSize = if (kind == ArtKind.Cover) 112.dp else 210.dp),
                        modifier = Modifier.fillMaxSize(),
                        contentPadding = PaddingValues(top = 4.dp, bottom = 12.dp),
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        items(art, key = { it }) { url ->
                            ArtOption(
                                url = url,
                                aspect = if (kind == ArtKind.Cover) 0.72f else 1.9f,
                                applying = applying == url,
                            ) {
                                scope.launch {
                                    applying = url
                                    val r = if (kind == ArtKind.Cover) {
                                        SteamGridDb.applyCover(context, game, url)
                                    } else {
                                        SteamGridDb.applyHero(context, game, url)
                                    }
                                    applying = null
                                    r.onSuccess { onApplied(); onDismiss() }
                                        .onFailure { note = SteamGridDb.describe(it) }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
    }
}

@Composable
private fun ArtOption(url: String, aspect: Float, applying: Boolean, onPick: () -> Unit) {
    Box(
        Modifier
            .fillMaxWidth()
            .aspectRatio(aspect)
            .clip(RoundedCornerShape(Radii.tile))
            .border(1.dp, Palette.hairline, RoundedCornerShape(Radii.tile))
            .controllerFocusable("art.opt.$url", RoundedCornerShape(Radii.tile), onConfirm = onPick)
            .clickable(enabled = !applying, onClick = onPick),
    ) {
        SubcomposeAsyncImage(
            model = ImageRequest.Builder(LocalContext.current).data(url).crossfade(true).build(),
            contentDescription = null,
            contentScale = ContentScale.Crop,
            modifier = Modifier.fillMaxSize(),
        )
        if (applying) {
            Box(
                Modifier.fillMaxSize().background(Palette.ground.copy(alpha = 0.66f)),
                contentAlignment = Alignment.Center,
            ) {
                CircularProgressIndicator(Modifier.size(22.dp), strokeWidth = 2.dp, color = Color.White)
            }
        }
    }
}
