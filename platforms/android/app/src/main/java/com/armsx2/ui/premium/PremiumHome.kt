package com.armsx2.ui.premium

import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.foundation.border
import androidx.compose.foundation.shape.CircleShape
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
import coil.compose.AsyncImage
import coil.compose.SubcomposeAsyncImage
import coil.request.ImageRequest
import com.armsx2.EnglishTitles
import com.armsx2.GameInfo
import com.armsx2.PlayTime
import com.armsx2.art.HeroArt
import com.armsx2.R
import com.armsx2.navigation.AppRoute
import com.armsx2.runtime.MainActivityRuntime
import com.armsx2.ui.settings.controllerFocusable
import com.armsx2.ui.home.HomeViewModel
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale
import com.armsx2.art.ArcadeMedia
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import java.io.File

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
    // The game whose per-game menu is open. The card shows one game, so "configure" here can
    // only ever mean that one -- which is the whole reason this belongs on the card and not in
    // the top bar, where settings are global and say nothing about what is on screen.
    var menuGame by remember { mutableStateOf<GameInfo?>(null) }

    LaunchedEffect(Unit) {
        viewModel.load(MainActivityRuntime.romsDirs.value, MainActivityRuntime.nativeReady.value)
    }

    // The one game the card shows: the last one played, or the first in the library on a fresh
    // install. There used to be a row of recents under the card whose only job was to choose
    // which game the card showed; without it there is nothing to choose, so this is just the head
    // of the list.
    val hero = remember(state.recentGames, state.allGames) {
        state.recentGames.ifEmpty { state.allGames }.firstOrNull()
    }

    if (showLibrary) {
        PremiumLibrary(
            games = state.allGames,
            scanning = state.scanning,
            onLaunch = { viewModel.launch(it) },
            onRefresh = { viewModel.refresh() },
            onBack = { showLibrary = false },
            modifier = modifier,
        )
        return
    }

    Box(modifier.fillMaxSize()) {
        AuroraBackground(Modifier.fillMaxSize())
        // The System 246/256 menu film over the aurora, faint enough to read a title across.
        // Draws nothing until the file has been fetched, so the aurora is the background on a
        // first run and with no network.
        MenuVideoBackground(Modifier.fillMaxSize())

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
                    onConfigure = { menuGame = hero },
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f)
                        .heightIn(max = 360.dp),
                )
                Spacer(Modifier.height(20.dp))
            } else {
                // With no game to show, nothing claims the middle of the screen and the row of
                // destinations rode up under the greeting -- stranded a third of the way down,
                // anchored to nothing. The card carries the weight when there is one; this
                // carries it when there is not, so the row sits at the foot either way.
                Spacer(Modifier.weight(1f))
            }

            DestinationRow(
                onLibrary = { showLibrary = true },
                onNavigate = onNavigate,
            )
        }

        // Last in the Box, so it covers the card it belongs to.
        menuGame?.let { game ->
            GameContextMenu(
                game = game,
                onDismiss = { menuGame = null },
                onPlay = { viewModel.launch(it) },
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
        GlyphButton(Arc.settings, id = "home.settings", phase = 0.00f, onClick = onSettings)
        Spacer(Modifier.width(8.dp))
        GlyphButton(Arc.menu, id = "home.menu", phase = 0.42f, onClick = onOpenMenu)
    }
}

@Composable
private fun GlyphButton(
    @androidx.annotation.DrawableRes icon: Int,
    id: String,
    phase: Float = 0f,
    onClick: () -> Unit,
) {
    Box(
        Modifier
            .size(38.dp)
            .material(MaterialLevel.Thin, RoundedCornerShape(Radii.pill))
            .silverTrace(Radii.pill, phase = phase)
            .controllerFocusable(id, RoundedCornerShape(Radii.pill), onConfirm = onClick)
            .clickable { onClick() },
        contentAlignment = Alignment.Center,
    ) {
        ArcIcon(icon, tint = Palette.labelSecondary, size = 19.dp)
    }
}

@Composable
private fun HeroCard(
    game: GameInfo,
    onPlay: () -> Unit,
    onConfigure: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val title = game.displayTitle(EnglishTitles.enabled.value)
    val context = LocalContext.current
    val heroVersion = HeroArt.version.intValue
    val hero = remember(game.uri, heroVersion) { HeroArt.fileFor(context, game) }

    Box(
        modifier
            .clip(RoundedCornerShape(Radii.card))
            .material(MaterialLevel.UltraThin, RoundedCornerShape(Radii.card), elevation = 18.dp),
    ) {
        if (hero != null) {
            // The key art takes the right two thirds and is then washed out towards the left, so
            // the title and the button sit on flat colour and stay readable whatever the picture
            // happens to be doing behind them.
            SubcomposeAsyncImage(
                model = ImageRequest.Builder(context)
                    .data(hero)
                    .memoryCacheKey(fileCacheKey(hero))
                    .diskCacheKey(fileCacheKey(hero))
                    .crossfade(true)
                    .build(),
                contentDescription = null,
                contentScale = ContentScale.Crop,
                alignment = Alignment.CenterEnd,
                modifier = Modifier.fillMaxHeight().fillMaxWidth(0.74f).align(Alignment.CenterEnd),
            )
            Box(
                Modifier.matchParentSize().background(
                    Brush.horizontalGradient(
                        0.00f to Palette.ground,
                        0.32f to Palette.ground.copy(alpha = 0.95f),
                        0.60f to Palette.ground.copy(alpha = 0.42f),
                        1.00f to Color.Transparent,
                    ),
                ),
            )
        } else {
            // No key art yet: the oversized mark bleeding off the right edge, as before, so the
            // wide card still has a second focal point instead of dead space.
            Image(
                painter = painterResource(R.drawable.namco_2x6),
                contentDescription = null,
                contentScale = ContentScale.Fit,
                modifier = Modifier
                    .align(Alignment.CenterEnd)
                    .fillMaxWidth(0.44f)
                    .alpha(0.05f)
                    .padding(end = 12.dp),
            )
        }

        // The card's height is whatever the page has left over (weight(1f), capped at 360dp), and
        // in landscape on a short screen that is well under what this content wants -- which had
        // it clipped rather than fitted: the Play label lost its lower half and the play stats
        // never drew at all. So measure the box and drop the content that is least load-bearing
        // until it fits, in that order. The button is the one thing that never gives ground.
        BoxWithConstraints(Modifier.fillMaxSize()) {
            val roomy = maxHeight >= 215.dp
            val tight = maxHeight < 178.dp
            val pad = if (tight) 13.dp else 20.dp
            val gap = if (tight) 8.dp else 16.dp

            Row(
                Modifier.fillMaxSize().padding(pad),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Box(
                    Modifier
                        .fillMaxHeight()
                        .aspectRatio(CoverAspect)
                        .clip(RoundedCornerShape(Radii.tile)),
                ) {
                    CoverArt(game, Modifier.fillMaxSize())
                    BoardTag(
                        boardFor(context, game.serial),
                        Modifier.align(Alignment.TopStart).padding(6.dp),
                    )
                }

                Spacer(Modifier.width(if (tight) 16.dp else 22.dp))

                Column(Modifier.weight(1f)) {
                    Text("CONTINUAR", style = Type.eyebrow, color = Palette.labelTertiary)
                    Spacer(Modifier.height(if (tight) 3.dp else 6.dp))
                    // The game's own logo where the pack has one, set as type where it does not.
                    // The logos are the game's real wordmark, which no font can stand in for, so
                    // this is a straight upgrade of the same line rather than an addition -- both
                    // say the title, and showing both would say it twice.
                    GameLogo(
                        game = game,
                        height = if (tight) 34.dp else 52.dp,
                        fallback = {
                            Text(
                                title,
                                style = if (tight) Type.title2 else Type.title1,
                                color = Palette.label,
                                maxLines = if (roomy) 2 else 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                        },
                    )
                    Spacer(Modifier.height(if (tight) 2.dp else 4.dp))
                    Text(
                        metaLine(game, boardFor(context, game.serial)),
                        style = Type.footnote,
                        color = Palette.labelSecondary,
                    )
                    Spacer(Modifier.height(gap))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        PlayButton(onPlay)
                        Spacer(Modifier.width(10.dp))
                        // Beside Play, because it configures the game Play would start. The top
                        // bar's cog opens the emulator's own settings and always did; this one
                        // has a game attached and only ever touches that game's own layer.
                        GlyphButton(Arc.settings, id = "home.hero.settings", phase = 0.7f, onClick = onConfigure)
                    }
                    // Play time is the first thing to go: it is the only line here the player can
                    // read somewhere else (the library's detail pane).
                    if (roomy) {
                        Spacer(Modifier.height(gap))
                        PlayStats(game)
                    }
                }
            }
        }
    }
}

/**
 * Time played and last session, on the card. Real numbers from [PlayTime]: the featured card
 * would otherwise be the one place in the launcher that says nothing about the game you have
 * actually been playing. Hidden entirely until there is something to report, so a fresh install
 * does not show two zeroes.
 */
@Composable
private fun PlayStats(game: GameInfo) {
    PlayTime.revision.value
    val serial = game.serial
    val seconds = PlayTime.playedSeconds(serial)
    val last = PlayTime.lastPlayedMillis(serial)
    if (seconds <= 0L && last <= 0L) return

    Column(
        Modifier
            .material(MaterialLevel.Thin, RoundedCornerShape(Radii.chip))
            .padding(horizontal = 16.dp, vertical = 11.dp),
        verticalArrangement = Arrangement.spacedBy(5.dp),
    ) {
        if (seconds > 0L) StatLine(Arc.performance, "Tempo de jogo: " + PlayTime.formatPlayed(seconds))
        if (last > 0L) StatLine(Arc.refresh, PlayTime.formatLastPlayed(last))
    }
}

@Composable
private fun StatLine(@androidx.annotation.DrawableRes icon: Int, text: String) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        ArcIcon(icon, tint = Palette.accentBright, size = 14.dp)
        Spacer(Modifier.width(9.dp))
        Text(
            text,
            style = Type.footnote,
            color = Palette.labelSecondary,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
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
            .controllerFocusable("home.play", RoundedCornerShape(Radii.pill), onConfirm = { pressed = true; onPlay() })
            .clickable { pressed = true; onPlay() }
            .padding(start = 10.dp, end = 24.dp, top = 10.dp, bottom = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        // The glyph rides in its own white disc rather than sitting bare on the fill: at this
        // size a lone triangle on a red pill reads as decoration, the disc makes it a button.
        Box(
            Modifier.size(26.dp).clip(CircleShape).background(Color.White),
            contentAlignment = Alignment.Center,
        ) { ArcIcon(Arc.play, tint = Palette.accent, size = 12.dp) }
        Spacer(Modifier.width(11.dp))
        Text("Jogar", style = Type.headline, color = Color.White)
    }
}

@Composable
private fun DestinationRow(onLibrary: () -> Unit, onNavigate: (AppRoute) -> Unit) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        // Staggered phases: five lights starting together would read as a progress bar.
        Destination("Biblioteca", Arc.library, Modifier.weight(1f), current = true, phase = 0.00f) { onLibrary() }
        Destination("Memory Cards", Arc.memcard, Modifier.weight(1f), phase = 0.17f) { onNavigate(AppRoute.MemoryCardManager()) }
        Destination("BIOS", Arc.bios, Modifier.weight(1f), phase = 0.34f) { onNavigate(AppRoute.BiosManager()) }
        Destination("Controles", Arc.controls, Modifier.weight(1f), phase = 0.51f) { onNavigate(AppRoute.ControllerManager) }
        Destination("Saves", Arc.saves, Modifier.weight(1f), phase = 0.68f) { onNavigate(AppRoute.SaveManager) }
    }
}

@Composable
private fun Destination(
    label: String,
    @androidx.annotation.DrawableRes icon: Int,
    modifier: Modifier = Modifier,
    current: Boolean = false,
    phase: Float = 0f,
    onClick: () -> Unit,
) {
    var pressed by remember { mutableStateOf(false) }
    val scale by animateFloatAsState(
        if (pressed) 0.97f else 1f,
        spring(stiffness = Spring.StiffnessMediumLow), label = "destScale",
    )
    Row(
        modifier
            .scale(scale)
            .material(MaterialLevel.Thin, RoundedCornerShape(Radii.chip))
            .then(
                if (current) {
                    Modifier.border(1.dp, Palette.accentBright, RoundedCornerShape(Radii.chip))
                } else {
                    Modifier
                },
            )
            .silverTrace(Radii.chip, phase = phase)
            .controllerFocusable("home.dest.$label", RoundedCornerShape(Radii.chip), onConfirm = { pressed = true; onClick() })
            .clickable { pressed = true; onClick() }
            .padding(horizontal = 14.dp, vertical = 13.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        ArcIcon(icon, tint = Palette.accentBright, size = 18.dp)
        Spacer(Modifier.width(9.dp))
        Text(label, style = Type.footnote, color = Palette.label, maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}

/**
 * The shape every cover slot is cut to.
 *
 * 2:3, because that is what both sources actually produce -- the arcade set renders its cases at
 * 582x889 and SteamGridDB's grids are 600x900. The slots were 0.72, which is wider, so Crop
 * scaled each cover to fill the width and took roughly a tenth of its height off the top and
 * bottom: the case art lost its spine label and its footer.
 */
internal const val CoverAspect = 2f / 3f

/**
 * Cover art, with a branded fallback: arcade titles have no cover in the online database, so
 * rather than an empty rectangle they get the NAMCO mark over a tinted field.
 */
@Composable
fun CoverArt(game: GameInfo, modifier: Modifier = Modifier) {
    val context = LocalContext.current
    // A custom cover — hand-picked, or fetched from SteamGridDB — always wins: it is the art the
    // user actually chose for this game.
    val coverVersion = com.armsx2.CustomCovers.version.value
    val custom = remember(coverVersion, game.uri) {
        com.armsx2.CustomCovers.matchIn(com.armsx2.CustomCovers.loadAll(context), game)
    }
    // The cover repo is keyed by console disc serials. An arcade gameid (NMxxxxx) has no entry
    // there, so don't even ask — go straight to the branded fallback.
    val cover = game.coverUrl?.takeUnless { game.serial?.startsWith("NM") == true }

    Box(
        modifier.background(Brush.linearGradient(listOf(Color(0xFF17171B), Color(0xFF0E0E11)))),
        contentAlignment = Alignment.Center,
    ) {
        if (custom != null) {
            SubcomposeAsyncImage(
                model = ImageRequest.Builder(context)
                    .data(custom)
                    .memoryCacheKey(fileCacheKey(custom))
                    .diskCacheKey(fileCacheKey(custom))
                    .crossfade(true)
                    .build(),
                contentDescription = game.displayTitle(EnglishTitles.enabled.value),
                // Fit, not Crop: the slot is 2:3 and so is the art, but a source that is a
                // little off should letterbox by a pixel rather than lose an edge.
                contentScale = ContentScale.Fit,
                modifier = Modifier.fillMaxSize(),
                error = { NamcoPlate() },
                loading = { NamcoPlate() },
            )
        } else if (cover == null) {
            NamcoPlate()
        } else {
            SubcomposeAsyncImage(
                model = ImageRequest.Builder(context).data(cover).crossfade(true).build(),
                contentDescription = game.displayTitle(EnglishTitles.enabled.value),
                contentScale = ContentScale.Fit,
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

internal fun metaLine(game: GameInfo, board: String? = null): String = buildString {
    val serial = game.serial
    // NMxxxxx is a System 246/256 gameid; anything else is a console disc. Which of the two
    // boards when we know -- they are not interchangeable, and "246/256" is the one answer that
    // is never true of any particular game.
    if (serial != null && serial.startsWith("NM")) append(boardName(board)) else append("PlayStation 2")
    if (serial != null) { append("  ·  "); append(serial) }
}

private fun nowHhMm(): String = SimpleDateFormat("HH:mm", Locale.getDefault()).format(Date())

private fun greeting(): String = when (Calendar.getInstance().get(Calendar.HOUR_OF_DAY)) {
    in 5..11 -> "Bom dia"
    in 12..17 -> "Boa tarde"
    else -> "Boa noite"
}

/**
 * A cache key that changes when the FILE changes.
 *
 * The image loader keys a local file by its path alone, so a cover replaced in place keeps
 * serving whatever was decoded the first time -- including a bitmap decoded from a partial
 * download, which then survives in memory long after the file on disk is complete. Folding the
 * size and timestamp in makes a rewritten file a different request.
 */
internal fun fileCacheKey(file: java.io.File): String =
    file.path + ":" + file.lastModified() + ":" + file.length()
