package com.armsx2.ui.premium

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
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
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.armsx2.GameInfo
import com.armsx2.art.ArcadeBezel
import com.armsx2.art.ArcadeMedia
import com.armsx2.art.ArcadePatches
import com.armsx2.data.library.ArcadePreflight
import com.armsx2.data.library.ArcadeRepair
import com.armsx2.runtime.MainActivityRuntime
import androidx.compose.runtime.rememberCoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

/**
 * What is actually set up for this game, in one place.
 *
 * Which BIOS it will boot with, whether a patch is installed, which bezel it will use, whether the
 * cover is one the player chose. Every one of those lives on a different screen, and none of them
 * says which game it applies to once you are looking at it — so the only way to know what a
 * particular game was going to do was to remember.
 *
 * Read-only except the bezel, which is here because it is the one of the four with no screen of
 * its own at all.
 */
@Composable
fun GameSetupCard(game: GameInfo) {
    val context = LocalContext.current
    val serial = game.serial
    var refresh by remember { mutableIntStateOf(0) }

    val settings = remember(serial, refresh) {
        runCatching { com.armsx2.config.ConfigStore.resolveForGame(game.settingsKey) }.getOrNull()
    }

    // The same resolution the launcher does: the game's own pick, else the global one.
    val bios = remember(serial, refresh) {
        settings?.biosFilename?.takeIf { it.isNotBlank() }
            ?: MainActivityRuntime.bios.value?.takeIf { it.isNotEmpty() }?.let { File(it).name }
    }

    var patch by remember(serial, refresh) { mutableStateOf<String?>(null) }
    LaunchedEffect(serial, refresh) {
        withContext(Dispatchers.IO) { runCatching { ArcadePatches.ensureIndex() } }
        patch = ArcadePatches.installedFor(context, serial)?.title
    }

    val bezelFile = remember(serial, refresh) { ArcadeBezel.fileFor(context, serial) }
    val bezelCustom = remember(serial, refresh) { ArcadeBezel.hasOverride(context, serial) }
    val coverCustom = remember(serial, refresh, com.armsx2.CustomCovers.version.value) {
        com.armsx2.CustomCovers.matchIn(com.armsx2.CustomCovers.loadAll(context), game) != null
    }

    // Off the main thread: this one reads the manifest and stats the game's files.
    val scope = rememberCoroutineScope()
    val launchPath = remember(game.uri) {
        if (game.uri.scheme == "file") game.uri.path ?: game.uri.toString() else game.uri.toString()
    }
    var report by remember(serial, refresh) { mutableStateOf<ArcadePreflight.Report?>(null) }
    var repairs by remember(serial, refresh) { mutableStateOf<List<ArcadeRepair.Change>>(emptyList()) }
    LaunchedEffect(serial, refresh, launchPath) {
        report = withContext(Dispatchers.IO) {
            runCatching { ArcadePreflight.inspect(context, launchPath) }.getOrNull()
        }
        repairs = withContext(Dispatchers.IO) {
            runCatching { ArcadeRepair.plan(context, launchPath) }.getOrDefault(emptyList())
        }
    }

    val picker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri: Uri? ->
        if (uri != null && ArcadeBezel.setOverride(context, serial, uri)) refresh++
    }

    Column(Modifier.fillMaxWidth()) {
        Text("CONFIGURAÇÃO", style = Type.eyebrow, color = Palette.labelTertiary)
        Spacer(Modifier.height(9.dp))

        SetupLine("BIOS", bios ?: "nenhuma escolhida")
        SetupLine("Patch", patch ?: "nenhum")
        SetupLine("Capa", if (coverCustom) "personalizada" else "padrão")
        SetupLine(
            "Bezel",
            when {
                !ArcadeBezel.enabled.value -> "desligado"
                bezelCustom -> "seu arquivo"
                bezelFile != null -> "do pacote"
                ArcadeMedia.hasBezel(serial) -> "ainda não baixado"
                else -> "não há para este jogo"
            },
        )

        // The preview is the point of putting the bezel here: it is the one setting whose result
        // you cannot picture from its name, and until now there was no way to see it without
        // booting the game.
        bezelFile?.let { file ->
            Spacer(Modifier.height(10.dp))
            Box(
                Modifier
                    .fillMaxWidth()
                    .aspectRatio(16f / 9f)
                    .clip(RoundedCornerShape(Radii.tile))
                    .background(Palette.ground),
            ) {
                AsyncImage(
                    model = ImageRequest.Builder(context)
                        .data(file)
                        .memoryCacheKey(fileCacheKey(file))
                        .diskCacheKey(fileCacheKey(file))
                        .build(),
                    contentDescription = null,
                    contentScale = ContentScale.Fit,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        }

        Spacer(Modifier.height(9.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            SetupAction("Trocar bezel") { picker.launch(arrayOf("image/*")) }
            if (bezelCustom) {
                SetupAction("Usar o do pacote") {
                    ArcadeBezel.clearOverride(context, serial)
                    refresh++
                }
            }
        }

        // Whether this game can actually boot. The same check that stops the launch, shown here
        // where there is room to read it -- and shown even when it only found warnings, which the
        // launch does not interrupt for.
        report?.let { r ->
            Spacer(Modifier.height(16.dp))
            Text("VERIFICAÇÃO", style = Type.eyebrow, color = Palette.labelTertiary)
            Spacer(Modifier.height(7.dp))
            if (r.ok) {
                Text("Tudo o que a placa procura está no lugar.", style = Type.footnote, color = Palette.labelSecondary)
            } else {
                r.problems.forEachIndexed { i, p ->
                    if (i > 0) Spacer(Modifier.height(8.dp))
                    Row(Modifier.fillMaxWidth()) {
                        Text(
                            if (p.blocking) "■" else "▫",
                            style = Type.footnote,
                            color = if (p.blocking) Palette.accentBright else Palette.labelTertiary,
                            modifier = Modifier.padding(end = 8.dp),
                        )
                        Column {
                            Text(p.what, style = Type.footnote, color = Palette.label)
                            Text(p.fix, style = Type.caption, color = Palette.labelSecondary)
                        }
                    }
                }
            }

            // Only for the ones whose answer is sitting in the folder: the file is there under a
            // different name than the manifest gives. The original is kept as .acgame.bak.
            if (repairs.isNotEmpty()) {
                Spacer(Modifier.height(10.dp))
                SetupAction("Corrigir manifesto (${repairs.size})") {
                    scope.launch {
                        withContext(Dispatchers.IO) {
                            ArcadeRepair.apply(context, launchPath, repairs)
                        }
                        refresh++
                    }
                }
            }
        }
    }
}

@Composable
private fun SetupLine(label: String, value: String) {
    Row(
        Modifier.fillMaxWidth().padding(vertical = 3.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(label, style = Type.footnote, color = Palette.labelSecondary)
        Text(value, style = Type.footnote, color = Palette.label)
    }
}

@Composable
private fun SetupAction(label: String, onClick: () -> Unit) {
    Text(
        label,
        style = Type.caption,
        color = Palette.label,
        modifier = Modifier
            .clip(RoundedCornerShape(Radii.pill))
            .background(Palette.materialThin)
            .clickable { onClick() }
            .padding(horizontal = 12.dp, vertical = 7.dp),
    )
}
