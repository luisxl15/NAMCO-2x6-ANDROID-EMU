package com.armsx2.ui.premium

import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import coil.compose.SubcomposeAsyncImage
import coil.request.ImageRequest
import com.armsx2.EnglishTitles
import com.armsx2.GameInfo
import com.armsx2.R
import com.armsx2.navigation.AppRoute
import com.armsx2.runtime.MainActivityRuntime
import com.armsx2.ui.home.HomeViewModel
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale
import kotlinx.coroutines.delay

/**
 * The launcher home. Apple-style: a soft out-of-focus ground, translucent material panels,
 * one accent (the NAMCO red), and a clear hierarchy — hero, then recents, then destinations.
 * Games launch through the same [HomeViewModel] the stock library uses.
 */
@Composable
fun PremiumHome(
    onOpenMenu: () -> Unit,
    onNavigate: (AppRoute) -> Unit,
    modifier: Modifier = Modifier,
    viewModel: HomeViewModel = viewModel(),
) {
    val state = viewModel.state.value
    var showLibrary by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        viewModel.load(MainActivityRuntime.romsDirs.value, MainActivityRuntime.nativeReady.value)
    }

    val games = remember(state.recentGames, state.allGames) {
        state.recentGames.ifEmpty { state.allGames }
    }
    var selected by remember { mutableIntStateOf(0) }
    if (selected >= games.size) selected = (games.size - 1).coerceAtLeast(0)
    val hero = games.getOrNull(selected)

    if (showLibrary) {
        PremiumLibrary(
            games = state.allGames,
            onLaunch = { viewModel.launch(it) },
            onBack = { showLibrary = false },
            modifier = modifier,
        )
        return
    }

    Box(modifier.fillMaxSize()) {
        AuroraBackground(Modifier.fillMaxSize())

        Column(
            Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .navigationBarsPadding()
                .padding(horizontal = 34.dp)
                .padding(top = 14.dp, bottom = 18.dp),
        ) {
            TopBar(onOpenMenu = onOpenMenu, onSettings = { onNavigate(AppRoute.Settings()) })

            Spacer(Modifier.height(18.dp))

            Text(greeting(), style = Type.largeTitle, color = Palette.label)
            Text(
                if (state.allGames.isEmpty()) "Nenhum jogo na biblioteca"
                else "${state.allGames.size} ${if (state.allGames.size == 1) "jogo" else "jogos"} na biblioteca",
                style = Type.subheadline,
                color = Palette.labelSecondary,
            )

            Spacer(Modifier.height(16.dp))

            if (hero != null) {
                HeroCard(
                    game = hero,
                    onPlay = { viewModel.launch(hero) },
                    modifier = Modifier.fillMaxWidth().widthIn(max = 780.dp),
                )
                Spacer(Modifier.height(22.dp))
            }

            if (games.size > 1) {
                Text("RECENTES", style = Type.eyebrow, color = Palette.labelTertiary)
                Spacer(Modifier.height(10.dp))
                RecentRow(
                    games = games,
                    selectedIndex = selected,
                    onSelect = { selected = it },
                )
                Spacer(Modifier.height(18.dp))
            }

            Spacer(Modifier.weight(1f))

            DestinationRow(
                onLibrary = { showLibrary = true },
                onNavigate = onNavigate,
            )
        }
    }
}

@Composable
private fun TopBar(onOpenMenu: () -> Unit, onSettings: () -> Unit) {
    var clock by remember { mutableStateOf(nowHhMm()) }
    LaunchedEffect(Unit) { while (true) { clock = nowHhMm(); delay(10_000) } }

    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Image(
            painter = painterResource(R.drawable.namco_2x6),
            contentDescription = "NAMCO System 246/256",
            contentScale = ContentScale.Fit,
            modifier = Modifier.height(30.dp),
        )
        Spacer(Modifier.weight(1f))
        Text(clock, style = Type.headline, color = Palette.label)
        Spacer(Modifier.width(14.dp))
        GlyphButton("⚙", onSettings)
        Spacer(Modifier.width(8.dp))
        GlyphButton("☰", onOpenMenu)
    }
}

@Composable
private fun GlyphButton(glyph: String, onClick: () -> Unit) {
    Box(
        Modifier
            .size(38.dp)
            .material(MaterialLevel.Thin, RoundedCornerShape(Radii.pill))
            .clickable { onClick() },
        contentAlignment = Alignment.Center,
    ) {
        Text(glyph, color = Palette.labelSecondary, fontSize = 17.sp)
    }
}

@Composable
private fun HeroCard(game: GameInfo, onPlay: () -> Unit, modifier: Modifier = Modifier) {
    val title = game.displayTitle(EnglishTitles.enabled.value)
    Row(
        modifier
            .material(MaterialLevel.UltraThin, RoundedCornerShape(Radii.card), elevation = 18.dp)
            .padding(20.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            Modifier
                .height(188.dp)
                .aspectRatio(0.72f)
                .clip(RoundedCornerShape(Radii.tile)),
        ) {
            CoverArt(game, Modifier.fillMaxSize())
        }

        Spacer(Modifier.width(22.dp))

        Column(Modifier.weight(1f)) {
            Text("CONTINUAR", style = Type.eyebrow, color = Palette.labelTertiary)
            Spacer(Modifier.height(6.dp))
            Text(
                title, style = Type.title1, color = Palette.label,
                maxLines = 2, overflow = TextOverflow.Ellipsis,
            )
            Spacer(Modifier.height(4.dp))
            Text(metaLine(game), style = Type.footnote, color = Palette.labelSecondary)
            Spacer(Modifier.height(16.dp))
            PlayButton(onPlay)
        }
    }
}

@Composable
private fun PlayButton(onPlay: () -> Unit) {
    var pressed by remember { mutableStateOf(false) }
    val scale by animateFloatAsState(
        if (pressed) 0.96f else 1f,
        spring(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = Spring.StiffnessMediumLow),
        label = "playScale",
    )
    Row(
        Modifier
            .scale(scale)
            .clip(RoundedCornerShape(Radii.pill))
            .background(Brush.horizontalGradient(listOf(Palette.accentBright, Palette.accent)))
            .clickable { pressed = true; onPlay() }
            .padding(horizontal = 26.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text("▶", color = Color.White, fontSize = 14.sp)
        Spacer(Modifier.width(9.dp))
        Text("Jogar", style = Type.headline, color = Color.White)
    }
}

@Composable
private fun RecentRow(games: List<GameInfo>, selectedIndex: Int, onSelect: (Int) -> Unit) {
    val listState = rememberLazyListState()
    LaunchedEffect(selectedIndex) { listState.animateScrollToItem(selectedIndex) }
    LazyRow(
        state = listState,
        modifier = Modifier.fillMaxWidth(),
        contentPadding = PaddingValues(end = 34.dp),
        horizontalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        itemsIndexed(games, key = { _, g -> g.uri.toString() }) { index, game ->
            val isSelected = index == selectedIndex
            val scale by animateFloatAsState(
                if (isSelected) 1f else 0.93f,
                spring(stiffness = Spring.StiffnessMediumLow), label = "recentScale",
            )
            Column(
                Modifier.width(86.dp).scale(scale).clickable { onSelect(index) },
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Box(
                    Modifier
                        .fillMaxWidth()
                        .aspectRatio(0.72f)
                        .clip(RoundedCornerShape(Radii.chip))
                        .alpha(if (isSelected) 1f else 0.55f),
                ) {
                    CoverArt(game, Modifier.fillMaxSize())
                }
                Spacer(Modifier.height(7.dp))
                Text(
                    game.displayTitle(EnglishTitles.enabled.value),
                    style = Type.caption,
                    color = if (isSelected) Palette.label else Palette.labelTertiary,
                    maxLines = 1, overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}

@Composable
private fun DestinationRow(onLibrary: () -> Unit, onNavigate: (AppRoute) -> Unit) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        Destination("Biblioteca", "▦", Modifier.weight(1f)) { onLibrary() }
        Destination("Memory Cards", "▤", Modifier.weight(1f)) { onNavigate(AppRoute.MemoryCardManager()) }
        Destination("BIOS", "◈", Modifier.weight(1f)) { onNavigate(AppRoute.BiosManager()) }
        Destination("Controles", "◎", Modifier.weight(1f)) { onNavigate(AppRoute.ControllerManager) }
        Destination("Saves", "☁", Modifier.weight(1f)) { onNavigate(AppRoute.SaveManager) }
    }
}

@Composable
private fun Destination(label: String, glyph: String, modifier: Modifier = Modifier, onClick: () -> Unit) {
    var pressed by remember { mutableStateOf(false) }
    val scale by animateFloatAsState(
        if (pressed) 0.97f else 1f,
        spring(stiffness = Spring.StiffnessMediumLow), label = "destScale",
    )
    Row(
        modifier
            .scale(scale)
            .material(MaterialLevel.Thin, RoundedCornerShape(Radii.chip))
            .clickable { pressed = true; onClick() }
            .padding(horizontal = 14.dp, vertical = 13.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(glyph, color = Palette.accentBright, fontSize = 16.sp)
        Spacer(Modifier.width(9.dp))
        Text(label, style = Type.footnote, color = Palette.label, maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}

/**
 * Cover art, with a branded fallback: arcade titles have no cover in the online database, so
 * rather than an empty rectangle they get the NAMCO mark over a tinted field.
 */
@Composable
fun CoverArt(game: GameInfo, modifier: Modifier = Modifier) {
    val context = LocalContext.current
    // The cover repo is keyed by console disc serials. An arcade gameid (NMxxxxx) has no entry
    // there, so don't even ask — go straight to the branded fallback.
    val cover = game.coverUrl?.takeUnless { game.serial?.startsWith("NM") == true }

    Box(
        modifier.background(Brush.linearGradient(listOf(Color(0xFF17171B), Color(0xFF0E0E11)))),
        contentAlignment = Alignment.Center,
    ) {
        if (cover == null) {
            NamcoPlate()
        } else {
            SubcomposeAsyncImage(
                model = ImageRequest.Builder(context).data(cover).crossfade(true).build(),
                contentDescription = game.displayTitle(EnglishTitles.enabled.value),
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize(),
                // A 404 (common for anything not in the repo) lands here rather than leaving a
                // blank rectangle.
                error = { NamcoPlate() },
                loading = { NamcoPlate() },
            )
        }
    }
}

/** The branded stand-in used wherever there is no box art. */
@Composable
private fun NamcoPlate() {
    Box(
        Modifier
            .fillMaxSize()
            .background(Brush.linearGradient(listOf(Color(0x40A5121F), Color(0x0DFFFFFF)))),
        contentAlignment = Alignment.Center,
    ) {
        Image(
            painter = painterResource(R.drawable.namco_2x6),
            contentDescription = null,
            contentScale = ContentScale.Fit,
            modifier = Modifier.fillMaxWidth(0.78f),
        )
    }
}

private fun metaLine(game: GameInfo): String = buildString {
    val serial = game.serial
    // NMxxxxx is a System 246/256 gameid; anything else is a console disc.
    if (serial != null && serial.startsWith("NM")) append("NAMCO System 246/256") else append("PlayStation 2")
    if (serial != null) { append("  ·  "); append(serial) }
}

private fun nowHhMm(): String = SimpleDateFormat("HH:mm", Locale.getDefault()).format(Date())

private fun greeting(): String = when (Calendar.getInstance().get(Calendar.HOUR_OF_DAY)) {
    in 5..11 -> "Bom dia"
    in 12..17 -> "Boa tarde"
    else -> "Boa noite"
}
