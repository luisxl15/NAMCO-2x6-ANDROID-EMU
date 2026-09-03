package com.armsx2.ui.emotion

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.armsx2.EnglishTitles
import com.armsx2.GameInfo
import com.armsx2.navigation.AppRoute
import com.armsx2.runtime.MainActivityRuntime
import com.armsx2.ui.home.HomeViewModel
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlinx.coroutines.delay

/**
 * XMB-style home, ported from the Emotion Launcher desktop front-end (ps2-launcher).
 * A single horizontal rail of tiles — recently played games first, then shortcut tiles —
 * over the animated [WaveBackground]. Selecting a game launches it through the same
 * [HomeViewModel] the stock library uses; shortcut tiles route through [onNavigate].
 */

private sealed interface Tile {
    data class Game(val game: GameInfo) : Tile
    data class Shortcut(val label: String, val glyph: String, val route: AppRoute) : Tile
}

@Composable
fun EmotionHome(
    onOpenMenu: () -> Unit,
    onNavigate: (AppRoute) -> Unit,
    modifier: Modifier = Modifier,
    viewModel: HomeViewModel = viewModel(),
) {
    val palette = EmotionTheme.current()
    val state = viewModel.state.value
    val context = LocalContext.current

    LaunchedEffect(Unit) {
        viewModel.load(MainActivityRuntime.romsDirs.value, MainActivityRuntime.nativeReady.value)
    }

    // Recently played lead the rail; if there are none yet, show the whole library so the
    // screen is never empty on a fresh install.
    val gameTiles = remember(state.recentGames, state.allGames) {
        (state.recentGames.ifEmpty { state.allGames }).map { Tile.Game(it) }
    }
    val shortcutTiles = remember {
        listOf(
            Tile.Shortcut("Configurações", "⚙", AppRoute.Settings()),
            Tile.Shortcut("Memory Cards", "▦", AppRoute.MemoryCardManager()),
            Tile.Shortcut("BIOS", "◈", AppRoute.BiosManager()),
            Tile.Shortcut("Controles", "◎", AppRoute.ControllerManager),
        )
    }
    val tiles = remember(gameTiles) { gameTiles + shortcutTiles }

    var selected by remember { mutableIntStateOf(0) }
    if (selected >= tiles.size) selected = (tiles.size - 1).coerceAtLeast(0)
    val listState = rememberLazyListState()
    LaunchedEffect(selected) {
        if (tiles.isNotEmpty()) listState.animateScrollToItem(selected)
    }

    val selectedTile = tiles.getOrNull(selected)

    Box(modifier.fillMaxSize().background(palette.bg0)) {
        WaveBackground(palette, Modifier.fillMaxSize())

        Column(Modifier.fillMaxSize()) {
            TopBar(palette, onOpenMenu)

            Spacer(Modifier.height(28.dp))

            LazyRow(
                state = listState,
                modifier = Modifier.fillMaxWidth().height(196.dp),
                contentPadding = PaddingValues(horizontal = 32.dp),
                horizontalArrangement = Arrangement.spacedBy(18.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                itemsIndexed(tiles) { index, tile ->
                    TileView(
                        tile = tile,
                        palette = palette,
                        selected = index == selected,
                        context = context,
                        onClick = {
                            if (index == selected) activate(tile, viewModel, onNavigate)
                            else selected = index
                        },
                    )
                }
            }

            Spacer(Modifier.height(22.dp))

            Column(Modifier.padding(horizontal = 34.dp)) {
                Text(
                    text = titleOf(selectedTile),
                    color = palette.white,
                    fontFamily = EmotionFont,
                    fontWeight = FontWeight.Bold,
                    fontSize = 34.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Spacer(Modifier.height(4.dp))
                Text(
                    text = subtitleOf(selectedTile),
                    color = palette.text2,
                    fontFamily = EmotionFont,
                    fontSize = 15.sp,
                )
            }

            Spacer(Modifier.weight(1f))

            ButtonHints(palette)
        }
    }
}

private fun activate(tile: Tile, viewModel: HomeViewModel, onNavigate: (AppRoute) -> Unit) {
    when (tile) {
        is Tile.Game -> viewModel.launch(tile.game)
        is Tile.Shortcut -> onNavigate(tile.route)
    }
}

private fun titleOf(tile: Tile?): String = when (tile) {
    is Tile.Game -> tile.game.displayTitle(EnglishTitles.enabled.value)
    is Tile.Shortcut -> tile.label
    null -> "—"
}

private fun subtitleOf(tile: Tile?): String = when (tile) {
    is Tile.Game -> buildString {
        append(tile.game.platform.name)
        tile.game.serial?.let { append("  ·  "); append(it) }
    }
    is Tile.Shortcut -> "Abrir"
    null -> ""
}

@Composable
private fun TopBar(palette: EmotionPalette, onOpenMenu: () -> Unit) {
    var clock by remember { mutableStateOf(currentClock()) }
    LaunchedEffect(Unit) {
        while (true) { clock = currentClock(); delay(10_000) }
    }
    Row(
        Modifier.fillMaxWidth().padding(horizontal = 24.dp, vertical = 16.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text("☰", color = palette.text2, fontSize = 22.sp,
            modifier = Modifier.clickable { onOpenMenu() })
        Spacer(Modifier.width(14.dp))
        Text("△ ○ ✕ □", color = palette.cyan, fontSize = 16.sp, fontFamily = EmotionFont)
        Spacer(Modifier.width(16.dp))
        Text("O que você quer jogar?", color = palette.text1, fontFamily = EmotionFont, fontSize = 16.sp)
        Spacer(Modifier.weight(1f))
        GlassChip(palette) {
            Text("Sony PlayStation 2", color = palette.text1, fontFamily = EmotionFont, fontSize = 13.sp)
        }
        Spacer(Modifier.width(12.dp))
        Text(clock, color = palette.white, fontFamily = EmotionFont, fontWeight = FontWeight.Bold, fontSize = 18.sp)
    }
}

@Composable
private fun GlassChip(palette: EmotionPalette, content: @Composable () -> Unit) {
    Row(
        Modifier
            .clip(RoundedCornerShape(8.dp))
            .background(palette.glass)
            .border(1.dp, palette.glassBorder, RoundedCornerShape(8.dp))
            .padding(horizontal = 12.dp, vertical = 7.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) { content() }
}

@Composable
private fun TileView(
    tile: Tile,
    palette: EmotionPalette,
    selected: Boolean,
    context: android.content.Context,
    onClick: () -> Unit,
) {
    val scale by animateFloatAsState(if (selected) 1.08f else 0.92f, tween(220), label = "tileScale")
    val border by animateColorAsState(
        if (selected) palette.selBorder else palette.glassBorder, tween(220), label = "tileBorder",
    )
    Box(
        Modifier
            .scale(scale)
            .size(width = 132.dp, height = 132.dp)
            .clip(RoundedCornerShape(12.dp))
            .background(Brush.linearGradient(listOf(palette.glassLight, palette.glass)))
            .border(if (selected) 2.dp else 1.dp, border, RoundedCornerShape(12.dp))
            .clickable { onClick() },
        contentAlignment = Alignment.Center,
    ) {
        when (tile) {
            is Tile.Game -> {
                val cover = tile.game.coverUrl
                if (cover != null) {
                    AsyncImage(
                        model = ImageRequest.Builder(context).data(cover).crossfade(true).build(),
                        contentDescription = tile.game.displayTitle(EnglishTitles.enabled.value),
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.fillMaxSize().clip(RoundedCornerShape(12.dp)),
                    )
                } else {
                    Text(
                        tile.game.displayTitle(EnglishTitles.enabled.value).take(2).uppercase(),
                        color = palette.cyan, fontFamily = EmotionFont,
                        fontWeight = FontWeight.Bold, fontSize = 40.sp,
                    )
                }
            }
            is Tile.Shortcut -> Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(tile.glyph, color = palette.cyan, fontSize = 46.sp)
                Spacer(Modifier.height(6.dp))
                Text(tile.label, color = palette.text2, fontFamily = EmotionFont, fontSize = 12.sp)
            }
        }
    }
}

@Composable
private fun ButtonHints(palette: EmotionPalette) {
    Row(
        Modifier.fillMaxWidth().padding(24.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text("Toque para selecionar · toque de novo para abrir",
            color = palette.text3, fontFamily = EmotionFont, fontSize = 12.sp)
        Spacer(Modifier.weight(1f))
        Text("Feito com ♥ por Luis Santos", color = palette.text3, fontFamily = EmotionFont, fontSize = 12.sp)
    }
}

private fun currentClock(): String =
    SimpleDateFormat("HH:mm", Locale.getDefault()).format(Date())
