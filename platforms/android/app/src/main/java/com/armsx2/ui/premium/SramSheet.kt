package com.armsx2.ui.premium

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.armsx2.GameInfo
import com.armsx2.data.library.ArcadeSram
import com.armsx2.runtime.MainActivityRuntime
import com.armsx2.ui.settings.controllerFocusable
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * The 32 KB the cabinet remembers, and the four things worth doing with it.
 *
 * The rankings are in there, and so is everything the operator set through the TEST menu. Until
 * now the file had exactly one copy and no way to move it: a reinstall, a wiped phone or a card
 * swapped for a bigger one took the score table with it, silently, and the game came back to its
 * backup-error screen as if it had never been played.
 *
 * Backup is allowed while a game runs — the core flushes the SRAM on every pause, so what is on
 * disk is what is on screen. Restore and import are not, and that is not caution: the core keeps
 * its own copy in memory and would write it back over the restored file at the next pause.
 */
@Composable
fun SramSheet(game: GameInfo, onDismiss: () -> Unit) {
    val context = LocalContext.current
    val gameId = game.serial
    val sram = remember(game.uri, gameId) { ArcadeSram.fileFor(game.uri.toString(), gameId) }

    // Bumped after anything that changes the folder, so the list below is re-read.
    var revision by remember { mutableStateOf(0) }
    var note by remember { mutableStateOf<String?>(null) }
    var problem by remember { mutableStateOf<String?>(null) }

    val backups = remember(sram, revision) { ArcadeSram.backups(sram) }
    val present = remember(sram, revision) { ArcadeSram.exists(sram) }
    val running = MainActivityRuntime.currentGame.value != null

    val exporter = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("application/octet-stream"),
    ) { uri ->
        if (uri != null) {
            ArcadeSram.export(context, sram, uri)
                .onSuccess { note = "SRAM exportada."; problem = null }
                .onFailure { problem = it.message; note = null }
        }
    }
    val importer = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) {
            ArcadeSram.import(context, sram, uri, running)
                .onSuccess { note = "SRAM importada."; problem = null; revision++ }
                .onFailure { problem = it.message; note = null }
        }
    }

    androidx.activity.compose.BackHandler(onBack = onDismiss)

    Box(
        Modifier
            .fillMaxSize()
            .background(Palette.scrim)
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
            ) { onDismiss() },
        contentAlignment = Alignment.Center,
    ) {
        Column(
            Modifier
                .widthIn(max = 460.dp)
                .fillMaxWidth(0.86f)
                .clip(RoundedCornerShape(Radii.card))
                .background(Palette.groundDeep.copy(alpha = 0.97f))
                .material(MaterialLevel.Thin, RoundedCornerShape(Radii.card))
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null,
                ) {}
                .padding(20.dp),
        ) {
            Text("Recordes e ajustes da placa", style = Type.headline, color = Palette.label)
            Spacer(Modifier.height(4.dp))
            Text(
                "A SRAM de 32 KB do System 246/256: os recordes, a contagem de fichas e tudo o " +
                    "que foi configurado no menu TEST.",
                style = Type.caption, color = Palette.labelSecondary,
            )

            Spacer(Modifier.height(14.dp))
            Text(statusLine(sram, present), style = Type.caption, color = Palette.labelTertiary)

            Spacer(Modifier.height(14.dp))
            SramButton("Fazer backup agora", enabled = present) {
                ArcadeSram.backup(sram)
                    .onSuccess { note = "Backup de ${it.label} guardado."; problem = null; revision++ }
                    .onFailure { problem = it.message; note = null }
            }
            Spacer(Modifier.height(8.dp))
            SramButton("Exportar para um arquivo", enabled = present) {
                exporter.launch(ArcadeSram.exportName(gameId))
            }
            Spacer(Modifier.height(8.dp))
            SramButton("Importar de um arquivo", enabled = sram != null && !running) {
                importer.launch(arrayOf("*/*"))
            }

            if (backups.isNotEmpty()) {
                Spacer(Modifier.height(16.dp))
                Text("BACKUPS", style = Type.eyebrow, color = Palette.labelTertiary)
                Spacer(Modifier.height(8.dp))
                Column(
                    Modifier.fillMaxWidth().heightIn(max = 220.dp).verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    backups.forEach { snap ->
                        Row(
                            Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(Radii.tile))
                                .material(MaterialLevel.UltraThin, RoundedCornerShape(Radii.tile))
                                .padding(horizontal = 14.dp, vertical = 10.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Text(
                                snap.label,
                                style = Type.caption, color = Palette.label,
                                maxLines = 1, overflow = TextOverflow.Ellipsis,
                                modifier = Modifier.weight(1f),
                            )
                            Spacer(Modifier.width(10.dp))
                            Text(
                                "Restaurar",
                                style = Type.caption,
                                color = if (running) Palette.labelTertiary else Palette.accentBright,
                                modifier = Modifier
                                    .clip(RoundedCornerShape(Radii.control))
                                    .controllerFocusable(
                                        "sram.restore.${snap.file.name}",
                                        RoundedCornerShape(Radii.control),
                                    ) {
                                        restore(sram, snap, running, { note = it; problem = null; revision++ }) {
                                            problem = it; note = null
                                        }
                                    }
                                    .clickable {
                                        restore(sram, snap, running, { note = it; problem = null; revision++ }) {
                                            problem = it; note = null
                                        }
                                    }
                                    .padding(horizontal = 8.dp, vertical = 4.dp),
                            )
                        }
                    }
                }
            }

            note?.let {
                Spacer(Modifier.height(12.dp))
                Text(it, style = Type.caption, color = Palette.label)
            }
            problem?.let {
                Spacer(Modifier.height(12.dp))
                Text(it, style = Type.caption, color = Palette.accentBright)
            }
        }
    }
}

private fun restore(
    sram: File?,
    snapshot: ArcadeSram.Snapshot,
    running: Boolean,
    onOk: (String) -> Unit,
    onFail: (String?) -> Unit,
) {
    ArcadeSram.restore(sram, snapshot, running)
        .onSuccess { onOk("SRAM de ${snapshot.label} restaurada.") }
        .onFailure { onFail(it.message) }
}

/** What is on disk right now, said in one line. */
private fun statusLine(sram: File?, present: Boolean): String = when {
    sram == null ->
        "Este jogo não tem um caminho real que o emulador consiga abrir, então não há SRAM para guardar."
    !present ->
        "Ainda não há nada gravado. A placa grava ao pausar e ao fechar o jogo."
    else -> {
        val when_ = SimpleDateFormat("dd/MM/yyyy HH:mm", Locale.getDefault())
            .format(Date(sram.lastModified()))
        "${sram.length() / 1024} KB, gravada em $when_."
    }
}

@Composable
private fun SramButton(label: String, enabled: Boolean, onClick: () -> Unit) {
    Box(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(Radii.tile))
            .material(MaterialLevel.UltraThin, RoundedCornerShape(Radii.tile))
            .then(
                if (enabled) {
                    Modifier
                        .controllerFocusable("sram.$label", RoundedCornerShape(Radii.tile), onConfirm = onClick)
                        .clickable(onClick = onClick)
                } else {
                    Modifier
                },
            )
            .padding(horizontal = 16.dp, vertical = 13.dp),
    ) {
        Text(
            label,
            style = Type.subheadline,
            color = if (enabled) Palette.label else Palette.labelTertiary,
        )
    }
}
