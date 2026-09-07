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
import androidx.compose.material3.LinearProgressIndicator
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
import com.armsx2.ui.settings.controllerFocusable
import com.armsx2.data.library.AcgameWizard
import com.armsx2.data.library.ArcadeCompat
import com.armsx2.data.library.ArcadeZipInstall
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

    // Installing straight from the archive the game arrived in. Held here rather than in its own
    // sheet because it is the same job as the list below -- get a folder into the library with a
    // manifest beside it -- and the two answers to "why is my game not here" belong together.
    var zip by remember { mutableStateOf<ArcadeZipInstall.Preview?>(null) }
    var zipUri by remember { mutableStateOf<android.net.Uri?>(null) }
    var reading by remember { mutableStateOf(false) }
    var installing by remember { mutableStateOf(false) }
    var installedBytes by remember { mutableStateOf(0L) }

    // A whole folder of archives. Building a collection is not a one-file job -- ten downloads
    // sit in one folder and installing them one tap at a time is the same work ten times.
    var batch by remember { mutableStateOf<List<ArcadeZipInstall.Result>?>(null) }
    var batchAt by remember { mutableStateOf<String?>(null) }
    var batchIndex by remember { mutableStateOf(0) }
    var batchTotal by remember { mutableStateOf(0) }

    val folderZipPicker = androidx.activity.compose.rememberLauncherForActivityResult(
        androidx.activity.result.contract.ActivityResultContracts.OpenDocumentTree(),
    ) { tree ->
        if (tree == null) return@rememberLauncherForActivityResult
        if (ArcadeZipInstall.romsDir() == null) {
            note = "Nenhuma pasta de ROMs com caminho de arquivo real e gravável."
            return@rememberLauncherForActivityResult
        }
        note = null
        scope.launch {
            val archives = withContext(Dispatchers.IO) {
                runCatching {
                    androidx.documentfile.provider.DocumentFile.fromTreeUri(context, tree)
                        ?.listFiles().orEmpty()
                        .filter { !it.isDirectory }
                        .mapNotNull { doc -> doc.name?.let { doc.uri to it } }
                        .filter { (_, name) ->
                            name.endsWith(".zip", true) || ArcadeZipInstall.unsupported(name)
                        }
                        .sortedBy { it.second.lowercase() }
                }.getOrDefault(emptyList())
            }
            if (archives.isEmpty()) {
                note = "Nenhum arquivo compactado nessa pasta."
                return@launch
            }
            batchTotal = archives.size
            batchIndex = 0
            batch = null
            batchAt = archives.first().second
            val results = withContext(Dispatchers.IO) {
                ArcadeZipInstall.installAll(
                    context,
                    archives,
                    onArchive = { i, name -> batchIndex = i; batchAt = name },
                    onProgress = {},
                )
            }
            batchAt = null
            batch = results
            found = withContext(Dispatchers.IO) { AcgameWizard.findCandidates(context) }
            onCreated()
        }
    }

    val picker = androidx.activity.compose.rememberLauncherForActivityResult(
        androidx.activity.result.contract.ActivityResultContracts.OpenDocument(),
    ) { picked ->
        if (picked == null) return@rememberLauncherForActivityResult
        val name = displayNameOf(context, picked)
        if (ArcadeZipInstall.unsupported(name)) {
            note = "Só consigo abrir .zip. Extraia o .7z antes e copie a pasta."
        } else {
            note = null
            zipUri = picked
            reading = true
            scope.launch {
                // Every way this can decline has its own sentence -- see ArcadeZipInstall.Look.
                // One "nothing to install" covering all of them says only that something,
                // somewhere, is wrong.
                val look = withContext(Dispatchers.IO) {
                    runCatching { ArcadeZipInstall.inspect(context, picked, name) }
                        .getOrElse { ArcadeZipInstall.Look.Refused(it.message ?: "Falha ao ler o arquivo.") }
                }
                reading = false
                when (look) {
                    is ArcadeZipInstall.Look.Ready -> zip = look.preview
                    is ArcadeZipInstall.Look.Refused -> note = look.why
                }
            }
        }
    }

    // Claim the D-pad while this is up. The registry keeps an exclusive layer stack precisely so
    // a modal's selection cannot walk out through its own scrim onto the screen behind it -- and
    // without claiming one, every press here would be moving the library underneath a dialog the
    // user is looking at. LocalNavLayer is what tells each control below which layer it is in;
    // position in the tree is the only spelling of that which cannot be got wrong.
    val navLayer = "add-game"
    androidx.compose.runtime.DisposableEffect(Unit) {
        com.armsx2.ui.settings.SettingsControllerNav.pushLayer(navLayer)
        onDispose { com.armsx2.ui.settings.SettingsControllerNav.popLayer(navLayer) }
    }

    // Back closes this, not the screen behind it. Without it the key falls through to the
    // app's own handler and opens the drawer over a dialog that is still up.
    androidx.activity.compose.BackHandler(onBack = onDismiss)

    LaunchedEffect(Unit) {
        found = withContext(Dispatchers.IO) { AcgameWizard.findCandidates(context) }
        loading = false
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
                if (zip == null && !installing && batchAt == null) {
                    Box(
                        Modifier
                            .clip(RoundedCornerShape(Radii.pill))
                            .material(MaterialLevel.Thin, RoundedCornerShape(Radii.pill))
                            .controllerFocusable("add.zipfolder", RoundedCornerShape(Radii.pill)) {
                                folderZipPicker.launch(null)
                            }
                            .clickable { folderZipPicker.launch(null) }
                            .padding(horizontal = 16.dp, vertical = 10.dp),
                    ) {
                        Text("Pasta de .zip", style = Type.subheadline, color = Palette.label)
                    }
                    Spacer(Modifier.width(10.dp))
                }
                if (zip == null && !installing && batchAt == null) {
                    Box(
                        Modifier
                            .clip(RoundedCornerShape(Radii.pill))
                            .material(MaterialLevel.Thin, RoundedCornerShape(Radii.pill))
                            .controllerFocusable("add.zip", RoundedCornerShape(Radii.pill)) {
                                picker.launch(arrayOf("application/zip", "application/octet-stream", "*/*"))
                            }
                            .clickable {
                                picker.launch(arrayOf("application/zip", "application/octet-stream", "*/*"))
                            }
                            .padding(horizontal = 16.dp, vertical = 10.dp),
                    ) {
                        Text(
                            if (reading) "Lendo..." else "Instalar de um .zip",
                            style = Type.subheadline, color = Palette.label,
                        )
                    }
                    Spacer(Modifier.width(10.dp))
                }
                Box(
                    Modifier
                        .size(36.dp)
                        .clip(RoundedCornerShape(Radii.pill))
                        .material(MaterialLevel.Thin, RoundedCornerShape(Radii.pill))
                        .controllerFocusable("add.close", RoundedCornerShape(Radii.pill), onConfirm = onDismiss)
                        .clickable { onDismiss() },
                    contentAlignment = Alignment.Center,
                ) { ArcIcon(Arc.close, tint = Palette.labelSecondary, size = 14.dp) }
            }

            Spacer(Modifier.height(18.dp))

            when {
                batchAt != null -> BatchProgress(batchAt!!, batchIndex, batchTotal)
                batch != null -> BatchReport(batch!!) { batch = null }
                zip != null -> ZipPanel(
                    preview = zip!!,
                    installing = installing,
                    written = installedBytes,
                    onCancel = { zip = null; zipUri = null; installedBytes = 0L },
                    onInstall = {
                        val uri = zipUri ?: return@ZipPanel
                        val preview = zip ?: return@ZipPanel
                        installing = true
                        installedBytes = 0L
                        scope.launch {
                            val error = withContext(Dispatchers.IO) {
                                // Every 64KB block reports; a 2 GB game is thirty thousand of
                                // them, and a Compose state written that often recomposes the
                                // sheet faster than the copy runs. Once every 4 MB is still a
                                // bar that moves.
                                var lastShown = 0L
                                ArcadeZipInstall.install(context, uri, preview) { written ->
                                    if (written - lastShown >= (4L shl 20)) {
                                        lastShown = written
                                        installedBytes = written
                                    }
                                }
                            }
                            installing = false
                            if (error == null) {
                                zip = null
                                zipUri = null
                                note = "${preview.gameId} instalado."
                                found = withContext(Dispatchers.IO) { AcgameWizard.findCandidates(context) }
                                onCreated()
                            } else {
                                note = error
                            }
                        }
                    },
                )
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
                                        AcgameWizard.create(context, candidate)
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
}

/** The archive's own name, for the "is this a .7z" test and the gameid fallback. */
private fun displayNameOf(context: android.content.Context, uri: android.net.Uri): String {
    if (uri.scheme == "file") return uri.lastPathSegment.orEmpty()
    return runCatching {
        context.contentResolver.query(uri, null, null, null, null)?.use { c ->
            val i = c.getColumnIndex(android.provider.OpenableColumns.DISPLAY_NAME)
            if (i >= 0 && c.moveToFirst()) c.getString(i) else null
        }
    }.getOrNull() ?: uri.lastPathSegment.orEmpty()
}

/**
 * What the archive holds, before anything is written.
 *
 * Shown rather than just extracting: the destination folder and the gameid are decisions, and
 * both are guessed from names inside a file the player did not write. Getting the gameid wrong
 * produces a game that installs cleanly and never boots, so it is on screen before the copy
 * starts, not in a log afterwards.
 */
@Composable
private fun ZipPanel(
    preview: ArcadeZipInstall.Preview,
    installing: Boolean,
    written: Long,
    onCancel: () -> Unit,
    onInstall: () -> Unit,
) {
    Column(Modifier.fillMaxSize()) {
        Text(preview.title ?: preview.gameId, style = Type.title3, color = Palette.label)
        Spacer(Modifier.height(3.dp))
        Text(
            preview.gameId + "  ·  " + preview.files.size + " arquivos  ·  " + human(preview.totalBytes),
            style = Type.footnote, color = Palette.labelTertiary,
        )
        Spacer(Modifier.height(12.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            PieceChip("Mídia", preview.media)
            PieceChip("Dongle", preview.dongle)
            PieceChip("ELF", preview.elf)
        }
        Spacer(Modifier.height(12.dp))
        Text(
            "Vai para " + preview.landsIn.absolutePath,
            style = Type.caption, color = Palette.labelSecondary,
        )
        if (preview.hasManifest) {
            Spacer(Modifier.height(4.dp))
            Text(
                "O arquivo já traz o próprio .acgame; ele será mantido.",
                style = Type.caption, color = Palette.labelSecondary,
            )
        }
        if (!preview.complete) {
            Spacer(Modifier.height(8.dp))
            Text(
                "Falta pelo menos um arquivo que a placa procura no boot. Dá para instalar, mas " +
                    "provavelmente não vai iniciar.",
                style = Type.footnote, color = Palette.accentBright,
            )
        }
        if (preview.alreadyThere) {
            Spacer(Modifier.height(8.dp))
            Text(
                "Já existe uma pasta com esse nome na biblioteca.",
                style = Type.footnote, color = Palette.accentBright,
            )
        }

        Spacer(Modifier.height(18.dp))
        if (installing) {
            val total = preview.totalBytes
            if (total > 0L) {
                LinearProgressIndicator(
                    progress = { (written.toFloat() / total).coerceIn(0f, 1f) },
                    modifier = Modifier.fillMaxWidth(),
                    color = Palette.accentBright,
                )
            } else {
                LinearProgressIndicator(Modifier.fillMaxWidth(), color = Palette.accentBright)
            }
            Spacer(Modifier.height(8.dp))
            Text(
                human(written) + (if (total > 0L) " de " + human(total) else "") + " copiados",
                style = Type.footnote, color = Palette.labelSecondary,
            )
        } else {
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Box(
                    Modifier
                        .clip(RoundedCornerShape(Radii.pill))
                        .material(MaterialLevel.Thin, RoundedCornerShape(Radii.pill))
                        .controllerFocusable("zip.cancel", RoundedCornerShape(Radii.pill), onConfirm = onCancel)
                        .clickable(onClick = onCancel)
                        .padding(horizontal = 20.dp, vertical = 11.dp),
                ) { Text("Cancelar", style = Type.subheadline, color = Palette.label) }
                if (!preview.alreadyThere) {
                    Box(
                        Modifier
                            .clip(RoundedCornerShape(Radii.pill))
                            .background(Palette.accent)
                            .controllerFocusable("zip.install", RoundedCornerShape(Radii.pill), onConfirm = onInstall)
                            .clickable(onClick = onInstall)
                            .padding(horizontal = 20.dp, vertical = 11.dp),
                    ) { Text("Instalar", style = Type.subheadline, color = Color.White) }
                }
            }
        }
    }
}

private fun human(bytes: Long): String = when {
    bytes <= 0L -> "tamanho desconhecido"
    bytes >= 1L shl 30 -> "%.1f GB".format(bytes.toDouble() / (1L shl 30))
    bytes >= 1L shl 20 -> "%.0f MB".format(bytes.toDouble() / (1L shl 20))
    else -> "%.0f KB".format(bytes.toDouble() / 1024.0)
}

/** While a folder is installing: which archive, and how far along the list. */
@Composable
private fun BatchProgress(name: String, index: Int, total: Int) {
    Column(Modifier.fillMaxSize()) {
        Text("Instalando ${index + 1} de $total", style = Type.title3, color = Palette.label)
        Spacer(Modifier.height(6.dp))
        Text(name, style = Type.footnote, color = Palette.labelSecondary, maxLines = 2, overflow = TextOverflow.Ellipsis)
        Spacer(Modifier.height(16.dp))
        LinearProgressIndicator(
            progress = { if (total == 0) 0f else (index.toFloat() / total).coerceIn(0f, 1f) },
            modifier = Modifier.fillMaxWidth(),
            color = Palette.accentBright,
        )
    }
}

/**
 * What became of each archive.
 *
 * Every one gets a line, the refused ones included and with their reason. A folder of downloads
 * always has something odd in it — another console's game, a half-finished file, a .7z — and
 * "8 instalados" alone leaves you counting to work out which two are missing.
 */
@Composable
private fun BatchReport(results: List<ArcadeZipInstall.Result>, onDone: () -> Unit) {
    val ok = results.count { it.ok }
    Column(Modifier.fillMaxSize()) {
        Text(
            "$ok de ${results.size} instalados",
            style = Type.title3, color = Palette.label,
        )
        Spacer(Modifier.height(12.dp))
        LazyColumn(
            Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            items(results, key = { it.archive }) { r ->
                Row(
                    Modifier
                        .fillMaxWidth()
                        .material(MaterialLevel.UltraThin, RoundedCornerShape(Radii.tile))
                        .padding(horizontal = 14.dp, vertical = 11.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    ArcIcon(
                        if (r.ok) Arc.check else Arc.close,
                        tint = if (r.ok) Palette.accentBright else Palette.labelTertiary,
                        size = 13.dp,
                    )
                    Spacer(Modifier.width(12.dp))
                    Column(Modifier.weight(1f)) {
                        Text(
                            r.archive,
                            style = Type.footnote, color = Palette.label,
                            maxLines = 1, overflow = TextOverflow.Ellipsis,
                        )
                        Text(r.message, style = Type.caption, color = Palette.labelSecondary)
                    }
                }
            }
        }
        Spacer(Modifier.height(14.dp))
        Box(
            Modifier
                .clip(RoundedCornerShape(Radii.pill))
                .background(Palette.accent)
                .controllerFocusable("batch.done", RoundedCornerShape(Radii.pill), onConfirm = onDone)
                .clickable(onClick = onDone)
                .padding(horizontal = 22.dp, vertical = 11.dp),
        ) { Text("Pronto", style = Type.subheadline, color = Color.White) }
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
            // What the manifest will say, and where it came from. Worth showing rather than
            // silently deciding: the board is the field that decides whether it boots at all.
            candidate.compat?.let { c ->
                Spacer(Modifier.height(8.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    CompatBadge(c.status)
                    Spacer(Modifier.width(10.dp))
                    Text(
                        listOfNotNull(
                            c.board.takeIf { it.isNotBlank() }?.let { "System $it" },
                            c.media.takeIf { it.isNotBlank() },
                        ).joinToString("  ·  "),
                        style = Type.caption, color = Palette.labelTertiary,
                    )
                }
                explain(c.status)?.let {
                    Spacer(Modifier.height(6.dp))
                    Text(it, style = Type.caption, color = Palette.labelSecondary, maxLines = 2)
                }
                if (c.note.isNotBlank()) {
                    Spacer(Modifier.height(6.dp))
                    Text(humanNote(c.note), style = Type.caption, color = Palette.accentBright, maxLines = 3)
                }
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
                    .controllerFocusable("add.${candidate.gameId}", RoundedCornerShape(Radii.pill), onConfirm = onCreate)
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
