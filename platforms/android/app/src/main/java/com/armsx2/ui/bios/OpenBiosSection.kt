package com.armsx2.ui.bios

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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.armsx2.data.library.OpenBiosRepo
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

/**
 * The open BIOS images, offered from the project's repository.
 *
 * Every other way into the BIOS folder starts with a file the player already has; this is the one
 * that does not, which is why it appears both here and in the first-run wizard. It draws nothing
 * until the catalogue is fetched, so a device with no network sees the screen it always saw.
 *
 * Each row carries its own verdict, in a few words. The paragraph explaining what "not
 * recognised" means is printed ONCE under the list, however many images are in that state —
 * repeating it per row says the same thing twice and makes two unfinished images look like two
 * separate problems.
 *
 * [onUse] is "make this the active BIOS", and it is only ever offered for an image the core
 * recognises. The BIOS manager routes it to its own select; the wizard routes it to the step's
 * import, so downloading one there completes the step.
 */
@Composable
fun OpenBiosSection(onUse: (File) -> Unit, onChanged: () -> Unit = {}) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var refresh by remember { mutableIntStateOf(0) }
    var busy by remember { mutableStateOf<String?>(null) }
    var failure by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(Unit) {
        withContext(Dispatchers.IO) { runCatching { OpenBiosRepo.ensureIndex() } }
    }

    val entries = OpenBiosRepo.index.value
    if (entries.isEmpty()) return

    val statuses = remember(entries, refresh) {
        entries.associate { it.file to OpenBiosRepo.status(context, it) }
    }
    val anyUnusable = statuses.values.any { it is OpenBiosRepo.Status.Unusable }

    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(20.dp),
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.42f),
    ) {
        Column(Modifier.padding(16.dp)) {
            Text(
                "BIOS de código aberto",
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Bold,
            )
            Spacer(Modifier.height(4.dp))
            Text(
                "Baixadas para a pasta BIOS, na raiz dos seus dados do emulador.",
                color = Color(0xFF8B93A3), fontSize = 12.sp,
            )
            Spacer(Modifier.height(12.dp))

            entries.forEachIndexed { i, entry ->
                if (i > 0) Spacer(Modifier.height(8.dp))
                // key(), because of the remember inside: without it the slots are positional and
                // a catalogue that changes length hands one row's state to another.
                key(entry.file) {
                    val status = statuses[entry.file] ?: OpenBiosRepo.Status.Missing
                    OpenBiosRow(
                        title = entry.name,
                        subtitle = listOfNotNull(
                            entry.note.takeIf { it.isNotBlank() },
                            entry.bytes.takeIf { it > 0 }?.let { "%.1f MB".format(it / 1048576.0) },
                        ).joinToString("  ·  "),
                        status = status,
                        bootable = OpenBiosRepo.bootableFile(context, entry),
                        busy = busy == entry.file,
                        onDownload = {
                            busy = entry.file
                            failure = null
                            scope.launch {
                                val result = withContext(Dispatchers.IO) {
                                    OpenBiosRepo.install(context, entry)
                                }
                                if (result is OpenBiosRepo.Status.Failed) failure = result.why
                                busy = null
                                refresh++
                                onChanged()
                            }
                        },
                        onRemove = {
                            busy = entry.file
                            scope.launch {
                                withContext(Dispatchers.IO) { OpenBiosRepo.remove(context, entry) }
                                busy = null
                                refresh++
                                onChanged()
                            }
                        },
                        onUse = { file -> onUse(file); onChanged() },
                    )
                }
            }

            // Once, however many images are in this state.
            if (anyUnusable) {
                Spacer(Modifier.height(12.dp))
                Text(
                    "\"Não reconhecida\" quer dizer que falta a estrutura que o emulador procura: " +
                        "a directory de ROM (entrada RESET) e o ROMVER. Dá para selecionar assim " +
                        "mesmo — o jogo provavelmente não vai iniciar, mas é assim que se vê até " +
                        "onde a imagem chega.",
                    color = Color(0xFF8B93A3), fontSize = 11.sp,
                )
            }
            failure?.let {
                Spacer(Modifier.height(10.dp))
                Text(it, color = Color(0xFFFF6B6B), fontSize = 12.sp)
            }
        }
    }
}

@Composable
private fun OpenBiosRow(
    title: String,
    subtitle: String,
    status: OpenBiosRepo.Status,
    /** Where the core would read it from, for the "use it anyway" case. */
    bootable: File,
    busy: Boolean,
    onDownload: () -> Unit,
    onRemove: () -> Unit,
    onUse: (File) -> Unit,
) {
    Row(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .padding(horizontal = 12.dp, vertical = 11.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Column(Modifier.padding(end = 12.dp).weight(1f)) {
            Text(title, color = Color.White, fontSize = 15.sp, fontWeight = FontWeight.Medium)
            val line = when (status) {
                is OpenBiosRepo.Status.Ready -> status.description
                is OpenBiosRepo.Status.Unusable -> "Não reconhecida pelo emulador"
                else -> subtitle
            }
            if (line.isNotBlank()) {
                Text(
                    line,
                    color = if (status is OpenBiosRepo.Status.Ready) Color(0xFF4ADE80) else Color(0xFF8B93A3),
                    fontSize = 11.sp,
                )
            }
        }
        Row(verticalAlignment = Alignment.CenterVertically) {
            // Offered for anything downloaded, recognised or not. An image the emulator cannot
            // read yet is exactly the one its author needs to point the emulator at -- refusing
            // to select it would take away the only way to see how far it gets. The row already
            // says which of the two this is.
            val file = when (status) {
                is OpenBiosRepo.Status.Ready -> status.file
                is OpenBiosRepo.Status.Unusable -> bootable
                else -> null
            }
            if (file != null && !busy) {
                Pill(if (status is OpenBiosRepo.Status.Ready) "Usar" else "Usar assim mesmo", accent = true) {
                    onUse(file)
                }
                Spacer(Modifier.width(8.dp))
            }
            val downloaded = status is OpenBiosRepo.Status.Ready || status is OpenBiosRepo.Status.Unusable
            Pill(
                when {
                    busy -> "…"
                    downloaded -> "Remover"
                    else -> "Baixar"
                },
                accent = false,
                enabled = !busy,
            ) { if (downloaded) onRemove() else onDownload() }
        }
    }
}

@Composable
private fun Pill(label: String, accent: Boolean, enabled: Boolean = true, onClick: () -> Unit) {
    Box(
        Modifier
            .clip(RoundedCornerShape(50))
            .background(if (accent) Color(0x2240E070) else Color(0x22FFFFFF))
            .clickable(enabled = enabled) { onClick() }
            .padding(horizontal = 12.dp, vertical = 6.dp),
    ) {
        Text(
            label,
            color = if (accent) Color(0xFF4ADE80) else Color.White,
            fontSize = 12.sp,
            fontWeight = FontWeight.Medium,
        )
    }
}
