package com.armsx2.ui.premium

import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.armsx2.EnglishTitles
import com.armsx2.GameInfo

/** The full library as a cover grid, on the same material system as the home. */
@Composable
fun PremiumLibrary(
    games: List<GameInfo>,
    onLaunch: (GameInfo) -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Box(modifier.fillMaxSize()) {
        AuroraBackground(Modifier.fillMaxSize())

        Column(Modifier.fillMaxSize().padding(horizontal = 34.dp, vertical = 22.dp)) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Row(
                    Modifier
                        .material(MaterialLevel.Thin, RoundedCornerShape(Radii.pill))
                        .clickable { onBack() }
                        .padding(horizontal = 16.dp, vertical = 9.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text("‹", color = Palette.labelSecondary, fontSize = 18.sp)
                    Spacer(Modifier.width(7.dp))
                    Text("Início", style = Type.footnote, color = Palette.labelSecondary)
                }
                Spacer(Modifier.width(20.dp))
                Text("Biblioteca", style = Type.title1, color = Palette.label)
                Spacer(Modifier.weight(1f))
                Text(
                    "${games.size} ${if (games.size == 1) "jogo" else "jogos"}",
                    style = Type.footnote, color = Palette.labelTertiary,
                )
            }

            Spacer(Modifier.height(20.dp))

            if (games.isEmpty()) {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text("Nenhum jogo encontrado", style = Type.title3, color = Palette.label)
                        Spacer(Modifier.height(6.dp))
                        Text(
                            "Adicione uma pasta de jogos nas Configurações.",
                            style = Type.footnote, color = Palette.labelSecondary,
                            textAlign = TextAlign.Center,
                        )
                    }
                }
            } else {
                LazyVerticalGrid(
                    columns = GridCells.Adaptive(minSize = 116.dp),
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(bottom = 24.dp),
                    horizontalArrangement = Arrangement.spacedBy(16.dp),
                    verticalArrangement = Arrangement.spacedBy(20.dp),
                ) {
                    items(games, key = { it.uri.toString() }) { game ->
                        LibraryCard(game, onLaunch)
                    }
                }
            }
        }
    }
}

@Composable
private fun LibraryCard(game: GameInfo, onLaunch: (GameInfo) -> Unit) {
    var pressed by remember { mutableStateOf(false) }
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
                .clickable { pressed = true; onLaunch(game) },
        ) {
            CoverArt(game, Modifier.fillMaxSize())
        }
        Spacer(Modifier.height(9.dp))
        Text(
            title, style = Type.caption, color = Palette.label,
            maxLines = 2, overflow = TextOverflow.Ellipsis, textAlign = TextAlign.Center,
        )
    }
}
