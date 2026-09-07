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
 * BIOS images the app can fetch, from the project's repository.
 *
 * Every other way into the BIOS folder starts with a file the player already has; this is the one
 * that does not, which is why it appears both here and in the first-run wizard. It draws nothing
 * until the catalogue is fetched, so a device with no network sees the screen it always saw.
 *
 * A downloader, and only that: a name and Baixar, or Remover once it is here. Choosing which BIOS
 * to boot from happens in one place, the list below, where every other BIOS on the device is
 * already chosen from — a second selector up here would be a second answer to the same question.
 *
 * A downloaded image reaches that list even when the core does not recognise it yet, which is the
 * arrangement that makes an open BIOS possible to work on: it sits with the others and is picked
 * the same way.
 */
@Composable
fun OpenBiosSection(onChanged: () -> Unit = {}) {
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

    val downloaded = remember(entries, refresh) {
        entries.filter { OpenBiosRepo.status(context, it) !is OpenBiosRepo.Status.Missing }
            .map { it.file }
            .toSet()
    }

    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(20.dp),
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.42f),
    ) {
        Column(Modifier.padding(16.dp)) {
            Text(
                "Baixar BIOS ONLINE",
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Bold,
            )
            Spacer(Modifier.height(12.dp))

            entries.forEachIndexed { i, entry ->
                if (i > 0) Spacer(Modifier.height(8.dp))
                // key(), because of the remember inside: without it the slots are positional and
                // a catalogue that changes length hands one row's state to another.
                key(entry.file) {
                    OpenBiosRow(
                        title = entry.name,
                        downloaded = entry.file in downloaded,
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
                    )
                }
            }

            // Only ever a real failure, so it is not one of the lines that sits there.
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
    downloaded: Boolean,
    busy: Boolean,
    onDownload: () -> Unit,
    onRemove: () -> Unit,
) {
    Row(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .padding(horizontal = 12.dp, vertical = 11.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Text(
            title,
            color = Color.White,
            fontSize = 15.sp,
            fontWeight = FontWeight.Medium,
            modifier = Modifier.padding(end = 12.dp).weight(1f),
        )
        Pill(
            when {
                busy -> "…"
                downloaded -> "Remover"
                else -> "Baixar"
            },
            enabled = !busy,
        ) { if (downloaded) onRemove() else onDownload() }
    }
}

@Composable
private fun Pill(label: String, enabled: Boolean = true, onClick: () -> Unit) {
    Box(
        Modifier
            .clip(RoundedCornerShape(50))
            .background(Color(0x22FFFFFF))
            .clickable(enabled = enabled) { onClick() }
            .padding(horizontal = 12.dp, vertical = 6.dp),
    ) {
        Text(label, color = Color.White, fontSize = 12.sp, fontWeight = FontWeight.Medium)
    }
}
