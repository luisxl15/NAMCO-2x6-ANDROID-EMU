package com.armsx2.ui.premium

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
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
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil.compose.SubcomposeAsyncImage
import coil.request.ImageRequest
import com.armsx2.EnglishTitles
import com.armsx2.GameInfo
import com.armsx2.PlayTime
import com.armsx2.art.HeroArt

/**
 * The full library: a cover grid beside a detail pane for whatever is selected, over that game's
 * own key art.
 *
 * It used to be the grid alone. With a handful of games — which is the normal case for an arcade
 * library, where each title is a board rather than a disc you picked up cheap — that left most of
 * a landscape screen as empty backdrop, and the covers read as icons stranded in a corner. The
 * pane fills that space with something worth reading, and it keeps earning its place once the
 * grid is full: it is where the title, the board, the serial and how long you have played live.
 *
 * Selection, not launch, is what a tap does here, with the second tap on an already-selected
 * cover launching it. Tap-to-launch straight off the grid would make the pane unreachable by
 * touch, and one tap away from starting an emulator is a low bar for a mis-tap.
 */

/** How the grid is ordered. Kept small on purpose: three answers cover what anyone asks of a
 *  library -- "what did I just play", "where is the one called X", "what do I actually play". */
private enum class LibrarySort(val label: String) {
    Recent("Recentes"),
    Title("A–Z"),
    Played("Mais jogados"),
}

@Composable
fun PremiumLibrary(
    games: List<GameInfo>,
    scanning: Boolean,
    onLaunch: (GameInfo) -> Unit,
    onRefresh: () -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var adding by remember { mutableStateOf(false) }
    var sort by rememberSaveable { mutableStateOf(LibrarySort.Recent) }
    var query by rememberSaveable { mutableStateOf("") }

    // Reading the revision subscribes the sort to play-time writes, so finishing a session
    // reorders the grid rather than leaving it stale until the screen is reopened.
    PlayTime.revision.value
    val shown = remember(games, sort, query, PlayTime.revision.value) {
        val filtered = if (query.isBlank()) {
            games
        } else {
            // Serial as well as title: an arcade board is as often known by its gameid as by
            // its name, and the id is the thing printed on the dongle.
            val q = query.trim().lowercase()
            games.filter {
                it.displayTitle(EnglishTitles.enabled.value).lowercase().contains(q) ||
                    it.serial.orEmpty().lowercase().contains(q)
            }
        }
        when (sort) {
            LibrarySort.Recent -> filtered.sortedByDescending { PlayTime.lastPlayedMillis(it.serial) }
            LibrarySort.Title -> filtered.sortedBy { it.displayTitle(EnglishTitles.enabled.value).lowercase() }
            LibrarySort.Played -> filtered.sortedByDescending { PlayTime.playedSeconds(it.serial) }
        }
    }

    var selectedKey by remember(games.size) { mutableStateOf(games.firstOrNull()?.uri?.toString()) }
    // Follow the visible list: a selection filtered out of view would leave the detail pane
    // describing a game that is no longer on screen.
    val selected = shown.firstOrNull { it.uri.toString() == selectedKey } ?: shown.firstOrNull()

    Box(modifier.fillMaxSize().background(Palette.ground)) {
        AuroraBackground(Modifier.fillMaxSize())
        selected?.let { LibraryBackdrop(it) }

        Column(Modifier.fillMaxSize().padding(horizontal = 34.dp, vertical = 22.dp)) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Row(
                    Modifier
                        .material(MaterialLevel.Thin, RoundedCornerShape(Radii.pill))
                        .silverTrace(Radii.pill, phase = 0.2f, intensity = 0.75f)
                        .clickable { onBack() }
                        .padding(horizontal = 16.dp, vertical = 9.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    ArcIcon(Arc.chevronLeft, tint = Palette.labelSecondary, size = 15.dp)
                    Spacer(Modifier.width(7.dp))
                    Text("Início", style = Type.footnote, color = Palette.labelSecondary)
                }
                Spacer(Modifier.width(20.dp))
                Text("Biblioteca", style = Type.title1, color = Palette.label)
                Spacer(Modifier.weight(1f))
                Text(
                    if (shown.size == games.size) {
                        "${games.size} ${if (games.size == 1) "jogo" else "jogos"}"
                    } else {
                        "${shown.size} de ${games.size}"
                    },
                    style = Type.footnote, color = Palette.labelTertiary,
                )
                Spacer(Modifier.width(14.dp))
                // Add a game: turns a folder already sitting in the ROM directory into an entry.
                Box(
                    Modifier
                        .size(36.dp)
                        .clip(CircleShape)
                        .material(MaterialLevel.Thin, CircleShape)
                        .clickable { adding = true },
                    contentAlignment = Alignment.Center,
                ) { ArcIcon(Arc.plus, tint = Palette.labelSecondary, size = 17.dp) }
                Spacer(Modifier.width(8.dp))
                // Rescan. There was no way to do this at all: the affordance used to live on the
                // stock library toolbar, which the premium launcher replaced, so a game added to
                // the ROM folder never appeared until the folder itself was changed -- the scan
                // cache is keyed by the folder list, not by what is inside it.
                RescanButton(scanning = scanning, onClick = onRefresh)
            }

            // Only worth the row it takes once there is a list to work on.
            if (games.size > 3) {
                Spacer(Modifier.height(16.dp))
                LibraryControls(
                    sort = sort,
                    onSort = { sort = it },
                    query = query,
                    onQuery = { query = it },
                )
            }

            Spacer(Modifier.height(20.dp))

            if (games.isEmpty()) {
                EmptyLibrary()
            } else {
                if (shown.isEmpty()) {
                    NoMatches(query)
                } else BoxWithConstraints(Modifier.fillMaxSize()) {
                    // The pane needs real width to be worth the space it takes; below that the
                    // grid gets the whole screen and the pane would only crowd it.
                    val roomForPane = maxWidth >= 720.dp
                    Row(Modifier.fillMaxSize(), horizontalArrangement = Arrangement.spacedBy(26.dp)) {
                        LazyVerticalGrid(
                            columns = GridCells.Adaptive(minSize = 116.dp),
                            modifier = Modifier.weight(if (roomForPane) 1.35f else 1f).fillMaxHeight(),
                            // Room at the top as well as the bottom. The grid clips to its own
                            // bounds, so with items flush against the top edge the press and
                            // selection transforms pushed the first row past it and shaved the
                            // rounded corners square.
                            contentPadding = PaddingValues(top = 8.dp, bottom = 24.dp),
                            horizontalArrangement = Arrangement.spacedBy(16.dp),
                            verticalArrangement = Arrangement.spacedBy(20.dp),
                        ) {
                            items(shown, key = { it.uri.toString() }) { game ->
                                val key = game.uri.toString()
                                LibraryCard(
                                    game = game,
                                    selected = key == selectedKey,
                                    onClick = {
                                        if (key == selectedKey) onLaunch(game) else selectedKey = key
                                    },
                                )
                            }
                        }
                        if (roomForPane && selected != null) {
                            AnimatedContent(
                                targetState = selected,
                                modifier = Modifier.weight(1f).fillMaxHeight(),
                                transitionSpec = {
                                    (fadeIn(tween(220)) togetherWith fadeOut(tween(160)))
                                },
                                label = "library-detail",
                            ) { game ->
                                DetailPane(game, onLaunch)
                            }
                        }
                    }
                }
            }
        }

        // Last inside the Box, so it draws over the screen it is covering. Placed before the
        // content it would be painted under -- a dialog you can see the list through, and cannot
        // press, because the list is on top of it.
        if (adding) {
            AddGameSheet(onDismiss = { adding = false }, onCreated = onRefresh)
        }
    }
}

/**
 * The selected game's key art behind everything, dimmed hard.
 *
 * This is the same picture the featured card uses, at screen size and turned right down: the
 * point is a ground with depth and the game's own colour in it, not a wallpaper competing with
 * the covers in front of it.
 */
@Composable
private fun LibraryBackdrop(game: GameInfo) {
    val context = LocalContext.current
    val heroVersion = HeroArt.version.intValue
    val hero = remember(game.uri, heroVersion) { HeroArt.fileFor(context, game) } ?: return

    Box(Modifier.fillMaxSize()) {
        SubcomposeAsyncImage(
            model = ImageRequest.Builder(context)
                .data(hero)
                .memoryCacheKey(fileCacheKey(hero))
                .diskCacheKey(fileCacheKey(hero))
                .crossfade(true)
                .build(),
            contentDescription = null,
            contentScale = ContentScale.Crop,
            alignment = Alignment.TopEnd,
            modifier = Modifier.fillMaxSize(),
        )
        // Two scrims: one flat, one falling off towards the top-right where the art is
        // brightest, so the header and the grid keep their contrast.
        Box(Modifier.fillMaxSize().background(Palette.ground.copy(alpha = 0.58f)))
        Box(
            Modifier.fillMaxSize().background(
                Brush.linearGradient(
                    0.0f to Palette.ground.copy(alpha = 0.86f),
                    0.58f to Palette.ground.copy(alpha = 0.30f),
                    1.0f to Color.Transparent,
                ),
            ),
        )
    }
}

@Composable
private fun LibraryControls(
    sort: LibrarySort,
    onSort: (LibrarySort) -> Unit,
    query: String,
    onQuery: (String) -> Unit,
) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            LibrarySort.entries.forEach { option ->
                val on = option == sort
                Box(
                    Modifier
                        .clip(RoundedCornerShape(Radii.pill))
                        .background(if (on) Palette.accent else Palette.materialUltraThin)
                        .clickable { onSort(option) }
                        .padding(horizontal = 15.dp, vertical = 8.dp),
                ) {
                    Text(
                        option.label,
                        style = Type.caption,
                        color = if (on) Color.White else Palette.labelSecondary,
                    )
                }
            }
        }

        Spacer(Modifier.weight(1f))

        Row(
            Modifier
                .widthIn(max = 320.dp)
                .clip(RoundedCornerShape(Radii.pill))
                .material(MaterialLevel.Thin, RoundedCornerShape(Radii.pill))
                .padding(start = 14.dp, end = 8.dp, top = 4.dp, bottom = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            ArcIcon(Arc.search, tint = Palette.labelTertiary, size = 15.dp)
            Spacer(Modifier.width(10.dp))
            BasicTextField(
                value = query,
                onValueChange = onQuery,
                singleLine = true,
                textStyle = Type.subheadline.copy(color = Palette.label),
                cursorBrush = SolidColor(Palette.accentBright),
                modifier = Modifier.weight(1f).padding(vertical = 10.dp),
                decorationBox = { inner ->
                    if (query.isEmpty()) {
                        Text("Buscar", style = Type.subheadline, color = Palette.labelTertiary)
                    }
                    inner()
                },
            )
            if (query.isNotEmpty()) {
                Box(
                    Modifier
                        .size(28.dp)
                        .clip(RoundedCornerShape(Radii.pill))
                        .clickable { onQuery("") },
                    contentAlignment = Alignment.Center,
                ) { ArcIcon(Arc.close, tint = Palette.labelTertiary, size = 12.dp) }
            }
        }
    }
}

@Composable
private fun NoMatches(query: String) {
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            ArcIcon(Arc.search, tint = Palette.labelTertiary, size = 30.dp)
            Spacer(Modifier.height(14.dp))
            Text("Nada para \"$query\"", style = Type.title3, color = Palette.label)
            Spacer(Modifier.height(6.dp))
            Text(
                "A busca olha o nome e o gameid.",
                style = Type.footnote, color = Palette.labelSecondary,
            )
        }
    }
}

@Composable
private fun RescanButton(scanning: Boolean, onClick: () -> Unit) {
    val spin = rememberInfiniteTransition(label = "rescan")
    val angle by spin.animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(tween(1100, easing = LinearEasing)),
        label = "rescanSpin",
    )
    Box(
        Modifier
            .size(36.dp)
            .clip(CircleShape)
            .material(MaterialLevel.Thin, CircleShape)
            .clickable(enabled = !scanning, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        ArcIcon(
            Arc.refresh,
            tint = if (scanning) Palette.accentBright else Palette.labelSecondary,
            size = 17.dp,
            modifier = if (scanning) Modifier.rotate(angle) else Modifier,
        )
    }
}

@Composable
private fun EmptyLibrary() {
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            ArcIcon(Arc.library, tint = Palette.labelTertiary, size = 34.dp)
            Spacer(Modifier.height(14.dp))
            Text("Nenhum jogo encontrado", style = Type.title3, color = Palette.label)
            Spacer(Modifier.height(6.dp))
            Text(
                "Adicione uma pasta de jogos nas Configurações.",
                style = Type.footnote, color = Palette.labelSecondary,
                textAlign = TextAlign.Center,
            )
        }
    }
}

@Composable
private fun LibraryCard(game: GameInfo, selected: Boolean, onClick: () -> Unit) {
    var pressed by remember { mutableStateOf(false) }
    // No scale-up on selection: the trace and the lifted brightness already say which one is
    // selected, and growing a tile that sits at the edge of a clipping grid only costs it its
    // corners.
    val scale by animateFloatAsState(
        if (pressed) 0.95f else 1f,
        spring(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = Spring.StiffnessMediumLow),
        label = "libScale",
    )
    val title = game.displayTitle(EnglishTitles.enabled.value)

    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Box(
            Modifier
                .scale(scale)
                .fillMaxWidth()
                .aspectRatio(0.72f)
                .clip(RoundedCornerShape(Radii.tile))
                .then(
                    if (selected) {
                        Modifier.silverTrace(Radii.tile, intensity = 0.9f)
                    } else {
                        Modifier
                    },
                )
                .clickable { pressed = true; onClick() },
        ) {
            CoverArt(game, Modifier.fillMaxSize())
            if (!selected) {
                // The unselected covers step back rather than the selected one shouting, so a
                // full grid does not turn into a wall of competing artwork.
                Box(Modifier.fillMaxSize().background(Palette.ground.copy(alpha = 0.42f)))
            }
        }
        Spacer(Modifier.height(9.dp))
        Text(
            title,
            style = Type.caption,
            color = if (selected) Palette.label else Palette.labelTertiary,
            maxLines = 2, overflow = TextOverflow.Ellipsis, textAlign = TextAlign.Center,
        )
    }
}

/** Everything known about the selected game, and the button that starts it. */
@Composable
private fun DetailPane(game: GameInfo, onLaunch: (GameInfo) -> Unit) {
    PlayTime.revision.value
    val serial = game.serial
    val seconds = PlayTime.playedSeconds(serial)
    val last = PlayTime.lastPlayedMillis(serial)

    Column(
        Modifier
            .fillMaxHeight()
            .material(MaterialLevel.UltraThin, RoundedCornerShape(Radii.card))
            .padding(24.dp),
    ) {
        // 0.46 rather than something that fills the pane: at 2:3 a cover grows in height twice
        // as fast as in width, and a bigger one here pushed the stats off the bottom edge
        // entirely -- present in the layout, never on screen.
        Box(
            Modifier
                .fillMaxWidth(0.46f)
                .aspectRatio(0.72f)
                .clip(RoundedCornerShape(Radii.tile)),
        ) {
            CoverArt(game, Modifier.fillMaxSize())
        }

        Spacer(Modifier.height(18.dp))
        Text(
            game.displayTitle(EnglishTitles.enabled.value),
            style = Type.title2, color = Palette.label,
            maxLines = 3, overflow = TextOverflow.Ellipsis,
        )
        Spacer(Modifier.height(6.dp))
        Text(metaLine(game), style = Type.footnote, color = Palette.labelSecondary)

        Spacer(Modifier.height(22.dp))
        LaunchButton { onLaunch(game) }

        if (seconds > 0L || last > 0L) {
            Spacer(Modifier.height(20.dp))
            Column(verticalArrangement = Arrangement.spacedBy(9.dp)) {
                if (seconds > 0L) {
                    DetailStat(Arc.performance, "Tempo de jogo", PlayTime.formatPlayed(seconds))
                }
                if (last > 0L) {
                    DetailStat(Arc.refresh, "Última sessão", PlayTime.formatLastPlayed(last))
                }
            }
        }
    }
}

@Composable
private fun DetailStat(@androidx.annotation.DrawableRes icon: Int, label: String, value: String) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        ArcIcon(icon, tint = Palette.accentBright, size = 15.dp)
        Spacer(Modifier.width(11.dp))
        Text(label, style = Type.footnote, color = Palette.labelTertiary, modifier = Modifier.weight(1f))
        Text(value, style = Type.subheadline, color = Palette.label, maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}

@Composable
private fun LaunchButton(onClick: () -> Unit) {
    var pressed by remember { mutableStateOf(false) }
    val scale by animateFloatAsState(
        if (pressed) 0.96f else 1f,
        spring(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = Spring.StiffnessMediumLow),
        label = "launchScale",
    )
    Row(
        Modifier
            .scale(scale)
            .clip(RoundedCornerShape(Radii.pill))
            .background(Brush.horizontalGradient(listOf(Palette.accentBright, Palette.accent)))
            .clickable { pressed = true; onClick() }
            .padding(start = 10.dp, end = 24.dp, top = 10.dp, bottom = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            Modifier.size(26.dp).clip(CircleShape).background(Color.White),
            contentAlignment = Alignment.Center,
        ) { ArcIcon(Arc.play, tint = Palette.accent, size = 12.dp) }
        Spacer(Modifier.width(11.dp))
        Text("Jogar", style = Type.headline, color = Color.White)
    }
}
