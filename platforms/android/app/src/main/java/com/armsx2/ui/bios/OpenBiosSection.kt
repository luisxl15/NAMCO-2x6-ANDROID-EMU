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

/**
 * The open BIOS images, offered from the project's repository.
 *
 * Every other way into this screen starts with a file the player already has; this is the one
 * that does not. It draws nothing until the catalogue is fetched, so a device with no network
 * sees the screen it has always seen.
 *
 * What it says about a download is the core's own verdict, not a promise. An open BIOS in
 * progress will be "not recognised" for a long time, and the section says exactly that rather
 * than installing it quietly and leaving the player to discover a black screen.
 */
@Composable
fun OpenBiosSection(onInstalled: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var refresh by remember { mutableIntStateOf(0) }
    var busy by remember { mutableStateOf<String?>(null) }
    var message by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(Unit) {
        withContext(Dispatchers.IO) { runCatching { OpenBiosRepo.ensureIndex() } }
    }

    val entries = OpenBiosRepo.index.value
    if (entries.isEmpty()) return

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
                "Imagens do repositório do projeto. Depois de baixar, o próprio emulador diz o " +
                    "que reconheceu nelas.",
                color = Color(0xFF8B93A3), fontSize = 12.sp,
            )
            Spacer(Modifier.height(12.dp))

            entries.forEachIndexed { i, entry ->
                if (i > 0) Spacer(Modifier.height(8.dp))
                // key(), because of the remember inside: without it the slots are positional and
                // a catalogue that changes length hands one row's state to another.
                key(entry.file) {
                    val installed = remember(refresh, entry.file) {
                        OpenBiosRepo.isInstalled(context, entry)
                    }
                    OpenBiosRow(
                        title = entry.name,
                        subtitle = listOfNotNull(
                            entry.note.takeIf { it.isNotBlank() },
                            entry.bytes.takeIf { it > 0 }?.let { "%.1f MB".format(it / 1048576.0) },
                        ).joinToString("  ·  "),
                        installed = installed,
                        busy = busy == entry.file,
                    ) {
                        busy = entry.file
                        scope.launch {
                            if (installed) {
                                withContext(Dispatchers.IO) { OpenBiosRepo.remove(context, entry) }
                                message = "${entry.name} removida."
                            } else {
                                val outcome = withContext(Dispatchers.IO) {
                                    OpenBiosRepo.install(context, entry)
                                }
                                message = when (outcome) {
                                    is OpenBiosRepo.Outcome.Installed ->
                                        "${entry.name}: ${outcome.description}"
                                    is OpenBiosRepo.Outcome.NotRecognised ->
                                        "${entry.name} foi baixada, mas o emulador ainda não a " +
                                            "reconhece como BIOS — falta a directory de ROM " +
                                            "(entrada RESET) e o ROMVER. O arquivo ficou salvo."
                                    is OpenBiosRepo.Outcome.Failed -> outcome.why
                                }
                            }
                            busy = null
                            refresh++
                            onInstalled()
                        }
                    }
                }
            }

            message?.let {
                Spacer(Modifier.height(10.dp))
                Text(it, color = Color(0xFF9AA3B2), fontSize = 12.sp)
            }
        }
    }
}

@Composable
private fun OpenBiosRow(
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
            .padding(horizontal = 12.dp, vertical = 11.dp),
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
                    installed -> "Remover"
                    else -> "Baixar"
                },
                color = if (installed) Color(0xFF4ADE80) else Color.White,
                fontSize = 12.sp,
                fontWeight = FontWeight.Medium,
            )
        }
    }
}
