package com.armsx2.ui.premium

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.armsx2.data.library.AcgameWizard
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Turns folders that are already in the ROM directory into playable entries.
 *
 * A game arrives as a folder — the CHD, the security dongle and proverb.elf — and until now the
 * last step was writing its `.acgame` by hand. Not a hard file, but it has to be exactly right:
 * the gameid drives the dongle lookup, the SRAM path and the JVS input mode, and a typo boots to
 * a black screen with nothing to say why.
 *
 * Everything that file needs is readable from the folder, so this lists what it found, says
 * plainly whether each piece is there, and writes the manifest. What it will not do is guess past
 * a missing dongle: a board without one cannot boot, and a manifest that points at a file which
 * is not there just moves the failure later.
 */
@Composable
fun AddGameSheet(onDismiss: () -> Unit, onCreated: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    var loading by remember { mutableStateOf(true) }
    var found by remember { mutableStateOf<List<AcgameWizard.Candidate>>(emptyList()) }
    var done by remember { mutableStateOf(setOf<String>()) }
    var note by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(Unit) {
        found = withContext(Dispatchers.IO) { AcgameWizard.findCandidates(context) }
        loading = false
    }

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
                .fillMaxWidth(0.78f)
                .fillMaxHeight(0.84f)
                .clip(RoundedCornerShape(Radii.card))
                .background(Palette.groundDeep.copy(alpha = 0.97f))
                .material(MaterialLevel.Thin, RoundedCornerShape(Radii.card), elevation = 26.dp)
                .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) {}
                .padding(24.dp),
        ) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text("Adicionar jogo", style = Type.title2, color = Palette.label)
                    Text(
                        "Pastas na sua biblioteca que ainda não têm manifesto.",
                        style = Type.footnote, color = Palette.labelSecondary,
                    )
                }
                Box(
                    Modifier
                        .size(36.dp)
                        .clip(RoundedCornerShape(Radii.pill))
                        .material(MaterialLevel.Thin, RoundedCornerShape(Radii.pill))
                        .clickable { onDismiss() },
                    contentAlignment = Alignment.Center,
                ) { ArcIcon(Arc.close, tint = Palette.labelSecondary, size = 14.dp) }
            }

            Spacer(Modifier.height(18.dp))

            when {
                loading -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator(Modifier.size(26.dp), strokeWidth = 2.dp, color = Palette.accentBright)
                }
                found.isEmpty() -> NothingToAdd()
                else -> LazyColumn(
                    Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(bottom = 12.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    items(found, key = { it.gameId + it.folderName }) { candidate ->
                        CandidateRow(
                            candidate = candidate,
                            created = candidate.gameId in done,
                            onCreate = {
                                scope.launch {
                                    val error = withContext(Dispatchers.IO) {
                                        AcgameWizard.create(
                                            context,
                                            candidate,
                                            candidate.title ?: candidate.gameId,
                                            // 246 and 256 differ in the BIOS they want; the
                                            // database does not record which, and 256 boards run
                                            // 246 titles, so the safer default is the newer one.
                                            board = "256",
                                        )
                                    }
                                    if (error == null) {
                                        done = done + candidate.gameId
                                        note = null
                                        onCreated()
                                    } else {
                                        note = error
                                    }
                                }
                            },
                        )
                    }
                }
            }

            note?.let {
                Spacer(Modifier.height(10.dp))
                Text(it, style = Type.footnote, color = Palette.accentBright)
            }
        }
    }
}

@Composable
private fun NothingToAdd() {
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            ArcIcon(Arc.check, tint = Palette.labelTertiary, size = 30.dp)
            Spacer(Modifier.height(14.dp))
            Text("Nada para adicionar", style = Type.title3, color = Palette.label)
            Spacer(Modifier.height(6.dp))
            Text(
                "Copie a pasta do jogo (o .chd, o dongle .ps2 e o proverb.elf) para a pasta de ROMs e abra isto de novo.",
                style = Type.footnote, color = Palette.labelSecondary,
            )
        }
    }
}

@Composable
private fun CandidateRow(
    candidate: AcgameWizard.Candidate,
    created: Boolean,
    onCreate: () -> Unit,
) {
    Row(
        Modifier
            .fillMaxWidth()
            .material(MaterialLevel.UltraThin, RoundedCornerShape(Radii.tile))
            .padding(horizontal = 18.dp, vertical = 15.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(
                candidate.title ?: candidate.folderName,
                style = Type.headline, color = Palette.label,
                maxLines = 1, overflow = TextOverflow.Ellipsis,
            )
            Spacer(Modifier.height(3.dp))
            Text(
                candidate.gameId + "  ·  " + candidate.folderName,
                style = Type.footnote, color = Palette.labelTertiary,
            )
            Spacer(Modifier.height(9.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                PieceChip("Mídia", candidate.media)
                PieceChip("Dongle", candidate.dongle)
                PieceChip("ELF", candidate.elf)
            }
        }
        Spacer(Modifier.width(16.dp))
        when {
            created -> Row(verticalAlignment = Alignment.CenterVertically) {
                ArcIcon(Arc.check, tint = Palette.accentBright, size = 16.dp)
                Spacer(Modifier.width(8.dp))
                Text("Adicionado", style = Type.footnote, color = Palette.accentBright)
            }
            // A dongle is not optional: the board reads it as memory card slot 1 during boot.
            !candidate.complete -> Text(
                "Faltam arquivos",
                style = Type.footnote, color = Palette.labelTertiary,
            )
            else -> Box(
                Modifier
                    .clip(RoundedCornerShape(Radii.pill))
                    .background(Palette.accent)
                    .clickable(onClick = onCreate)
                    .padding(horizontal = 20.dp, vertical = 11.dp),
            ) { Text("Adicionar", style = Type.subheadline, color = Color.White) }
        }
    }
}

/** One expected file: its name when present, struck as missing when not. */
@Composable
private fun PieceChip(label: String, value: String?) {
    val ok = value != null
    Row(
        Modifier
            .clip(RoundedCornerShape(Radii.pill))
            .background(Palette.materialUltraThin)
            .padding(horizontal = 11.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        ArcIcon(
            if (ok) Arc.check else Arc.close,
            tint = if (ok) Palette.accentBright else Palette.labelTertiary,
            size = 11.dp,
        )
        Spacer(Modifier.width(7.dp))
        Text(
            if (ok) value!! else "$label ausente",
            style = Type.caption,
            color = if (ok) Palette.labelSecondary else Palette.labelTertiary,
            maxLines = 1, overflow = TextOverflow.Ellipsis,
        )
    }
}
