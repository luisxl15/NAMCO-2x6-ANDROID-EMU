package com.armsx2.ui.emulation

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.armsx2.art.ArcadePatches
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import androidx.compose.runtime.rememberCoroutineScope
import kotlinx.coroutines.withContext
import kr.co.iefriends.pcsx2.NativeApp

/**
 * The widescreen patches for the game that is running.
 *
 * In the pause menu and nowhere else, because "which patches exist" has no answer without a game
 * — and the answer is per game, so a list on a settings screen would be a list of patches for
 * other people's games.
 *
 * Only this game's, and that is a correctness rule rather than tidiness. PCSX2 finds patches by
 * filename; a pnach installed under another game's id is not ignored, it is applied, writing one
 * game's addresses into another's memory.
 */
@Composable
fun ArcadePatchSection(serial: String?) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var refresh by remember { mutableIntStateOf(0) }
    var busy by remember { mutableStateOf<String?>(null) }
    var message by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(Unit) {
        withContext(Dispatchers.IO) { runCatching { ArcadePatches.ensureIndex() } }
    }

    // Read so the list recomposes when the index lands.
    ArcadePatches.index.value
    val entries = remember(serial, ArcadePatches.index.value) { ArcadePatches.forGame(serial) }
    if (entries.isEmpty()) return

    SectionCard("Patches") {
        Column(Modifier.fillMaxWidth()) {
            entries.forEachIndexed { i, entry ->
                if (i > 0) Spacer(Modifier.height(6.dp))
                val installed = remember(refresh, entry.installName) {
                    ArcadePatches.isInstalled(context, entry)
                }
                PatchRow(
                    title = entry.title,
                    subtitle = listOfNotNull(
                        entry.author.takeIf { it.isNotBlank() },
                        entry.groups.joinToString(" · ").takeIf { it.isNotBlank() },
                    ).joinToString("  —  "),
                    installed = installed,
                    busy = busy == entry.installName,
                ) {
                    busy = entry.installName
                    scope.launch {
                        val problem = withContext(Dispatchers.IO) {
                            if (installed) {
                                ArcadePatches.remove(context, entry)
                                null
                            } else {
                                ArcadePatches.install(context, entry)
                            }
                        }
                        if (problem == null) {
                            // Writing the file is not enough: a patch is inert until its group
                            // name is in the enable list, so reload the folder and switch this
                            // one's groups on. Removing does the reverse.
                            withContext(Dispatchers.IO) {
                                runCatching { NativeApp.reloadPatches() }
                                val names = entry.groups.toTypedArray()
                                if (names.isNotEmpty()) {
                                    runCatching {
                                        NativeApp.setEnabledPatches(
                                            false,
                                            names,
                                            if (installed) emptyArray() else names,
                                            null,
                                        )
                                    }
                                }
                            }
                        }
                        message = problem
                            ?: if (installed) {
                                "Patch removido. Reinicie o jogo."
                            } else {
                                "Patch instalado e ativado. Reinicie o jogo se não aplicar."
                            }
                        busy = null
                        refresh++
                    }
                }
            }
            message?.let {
                Spacer(Modifier.height(8.dp))
                Text(it, color = Color(0xFF9AA3B2), fontSize = 12.sp)
            }
        }
    }
}

@Composable
private fun PatchRow(
    title: String,
    subtitle: String,
    installed: Boolean,
    busy: Boolean,
    onToggle: () -> Unit,
) {
    Row(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .clickable(enabled = !busy) { onToggle() }
            .padding(horizontal = 14.dp, vertical = 11.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Column(Modifier.padding(end = 12.dp)) {
            Text(title, color = Color.White, fontSize = 15.sp, fontWeight = FontWeight.Medium)
            if (subtitle.isNotBlank()) {
                Text(subtitle, color = Color(0xFF8B93A3), fontSize = 11.sp)
            }
        }
        Box(
            Modifier
                .clip(RoundedCornerShape(50))
                .background(if (installed) Color(0x2240E070) else Color(0x22FFFFFF))
                .padding(horizontal = 12.dp, vertical = 6.dp),
        ) {
            Text(
                when {
                    busy -> "…"
                    installed -> "Instalado"
                    else -> "Baixar"
                },
                color = if (installed) Color(0xFF4ADE80) else Color.White,
                fontSize = 12.sp,
                fontWeight = FontWeight.Medium,
            )
        }
    }
}
