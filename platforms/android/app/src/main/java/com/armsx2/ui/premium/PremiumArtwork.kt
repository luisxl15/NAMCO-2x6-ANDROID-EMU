package com.armsx2.ui.premium

import androidx.compose.foundation.background
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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.armsx2.CustomCovers
import com.armsx2.EnglishTitles
import com.armsx2.GameInfo
import com.armsx2.art.SteamGridDb
import kotlinx.coroutines.launch

/** Route entry: supplies the scanned library to [PremiumArtwork]. */
@Composable
fun PremiumArtworkRoute(
    onBack: () -> Unit,
    viewModel: com.armsx2.ui.home.HomeViewModel = androidx.lifecycle.viewmodel.compose.viewModel(),
) {
    androidx.compose.runtime.LaunchedEffect(Unit) {
        viewModel.load(
            com.armsx2.runtime.MainActivityRuntime.romsDirs.value,
            com.armsx2.runtime.MainActivityRuntime.nativeReady.value,
        )
    }
    PremiumArtwork(games = viewModel.state.value.allGames, onBack = onBack)
}

/**
 * Cover art from SteamGridDB. The console cover repo is keyed by disc serial and has nothing for
 * an arcade gameid, so this is how a System 246/256 title gets real box art instead of the
 * branded fallback plate.
 */
@Composable
fun PremiumArtwork(
    games: List<GameInfo>,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var key by remember { mutableStateOf(SteamGridDb.apiKey.value) }
    var status by remember { mutableStateOf<String?>(null) }
    var busy by remember { mutableStateOf(false) }
    val coverVersion = CustomCovers.version.value
    val covers = remember(coverVersion) { CustomCovers.loadAll(context) }

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
                    ArcIcon(Arc.chevronLeft, tint = Palette.labelSecondary, size = 16.dp)
                    Spacer(Modifier.width(7.dp))
                    Text("Início", style = Type.footnote, color = Palette.labelSecondary)
                }
                Spacer(Modifier.width(20.dp))
                Text("Capas", style = Type.title1, color = Palette.label)
            }

            Spacer(Modifier.height(18.dp))

            // API key.
            Column(
                Modifier
                    .fillMaxWidth()
                    .material(MaterialLevel.UltraThin, RoundedCornerShape(Radii.card))
                    .padding(18.dp),
            ) {
                Text("SteamGridDB", style = Type.headline, color = Palette.label)
                Spacer(Modifier.height(3.dp))
                Text(
                    "Cole sua chave da API pessoal. Você a obtém em steamgriddb.com → perfil → preferences → API.",
                    style = Type.footnote, color = Palette.labelSecondary,
                )
                Spacer(Modifier.height(12.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    OutlinedTextField(
                        value = key,
                        onValueChange = { key = it },
                        singleLine = true,
                        placeholder = { Text("chave da API", style = Type.footnote) },
                        modifier = Modifier.weight(1f),
                    )
                    Spacer(Modifier.width(12.dp))
                    PillButton("Salvar") {
                        SteamGridDb.setKey(key)
                        status = if (SteamGridDb.configured) "Chave salva." else "Chave removida."
                    }
                }
            }

            Spacer(Modifier.height(14.dp))

            Row(verticalAlignment = Alignment.CenterVertically) {
                PillButton(if (busy) "Baixando…" else "Baixar capas faltantes", enabled = !busy) {
                    busy = true
                    status = null
                    scope.launch {
                        var ok = 0
                        var fail = 0
                        var lastError: String? = null
                        for (g in games) {
                            // Never overwrite art the user already has — this button is for
                            // filling gaps, not for replacing choices.
                            if (CustomCovers.matchIn(covers, g) != null) continue
                            SteamGridDb.fetchCover(context, g)
                                .onSuccess { ok++ }
                                .onFailure { fail++; lastError = SteamGridDb.describe(it) }
                        }
                        busy = false
                        status = when {
                            ok == 0 && fail == 0 -> "Todos os jogos já têm capa."
                            fail == 0 -> "$ok capa(s) baixada(s)."
                            else -> "$ok baixada(s), $fail sem sucesso. ${lastError.orEmpty()}"
                        }
                    }
                }
                status?.let {
                    Spacer(Modifier.width(14.dp))
                    Text(it, style = Type.footnote, color = Palette.labelSecondary)
                }
            }

            Spacer(Modifier.height(18.dp))

            LazyColumn(
                Modifier.fillMaxSize(),
                contentPadding = PaddingValues(bottom = 24.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                items(games, key = { it.uri.toString() }) { game ->
                    ArtworkRow(
                        game = game,
                        hasCover = CustomCovers.matchIn(covers, game) != null,
                        onFetch = {
                            scope.launch {
                                status = null
                                SteamGridDb.fetchCover(context, game)
                                    .onSuccess { status = "Capa atualizada: ${game.displayTitle(EnglishTitles.enabled.value)}" }
                                    .onFailure { status = SteamGridDb.describe(it) }
                            }
                        },
                        onRemove = { CustomCovers.remove(context, game) },
                    )
                }
            }
        }
    }
}

@Composable
private fun ArtworkRow(
    game: GameInfo,
    hasCover: Boolean,
    onFetch: () -> Unit,
    onRemove: () -> Unit,
) {
    Row(
        Modifier
            .fillMaxWidth()
            .material(MaterialLevel.UltraThin, RoundedCornerShape(Radii.tile))
            .padding(12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            Modifier
                .height(64.dp)
                .aspectRatio(0.72f)
                .clip(RoundedCornerShape(8.dp)),
        ) {
            CoverArt(game, Modifier.fillMaxSize())
        }
        Spacer(Modifier.width(14.dp))
        Column(Modifier.weight(1f)) {
            Text(
                game.displayTitle(EnglishTitles.enabled.value),
                style = Type.headline, color = Palette.label,
                maxLines = 1, overflow = TextOverflow.Ellipsis,
            )
            Text(
                if (hasCover) "Capa personalizada" else "Sem capa",
                style = Type.footnote,
                color = if (hasCover) Palette.labelSecondary else Palette.labelTertiary,
            )
        }
        if (hasCover) {
            PillButton("Remover", filled = false, onClick = onRemove)
            Spacer(Modifier.width(8.dp))
        }
        PillButton(if (hasCover) "Trocar" else "Buscar", onClick = onFetch)
    }
}

@Composable
private fun PillButton(
    label: String,
    filled: Boolean = true,
    enabled: Boolean = true,
    onClick: () -> Unit,
) {
    val bg = if (filled) Palette.accent else Color.Transparent
    Box(
        Modifier
            .clip(RoundedCornerShape(Radii.pill))
            .let { if (filled) it.background(bg) else it.material(MaterialLevel.Thin, RoundedCornerShape(Radii.pill)) }
            .clickable(enabled = enabled) { onClick() }
            .padding(horizontal = 18.dp, vertical = 10.dp),
    ) {
        Text(
            label,
            style = Type.footnote,
            color = if (!enabled) Palette.labelTertiary else if (filled) Color.White else Palette.label,
        )
    }
}
