package com.armsx2.ui.about

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.armsx2.ui.common.GlassPanel
import com.armsx2.ui.premium.Arc
import com.armsx2.ui.premium.ArcIcon

/**
 * Who made this, and what it stands on.
 *
 * The About screen credited the two projects underneath and said nothing about the one in front:
 * a reader could not tell whether this app was a rebrand of ARMSX2 or its own work. It is its own
 * work — the arcade launcher, the JVS input layer on Android, the manifest tooling, the media and
 * patch catalogues — and the three repositories below are what it builds on, each named and
 * linked rather than mentioned in passing.
 */
@Composable
fun ProjectLinks(modifier: Modifier = Modifier) {
    val uris = LocalUriHandler.current
    GlassPanel(modifier) {
        Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text(
                "Projeto e código",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.primary,
            )
            Text(
                "Este emulador é desenvolvido por luisxl15. O launcher, a camada de arcade no " +
                    "Android e tudo o que é específico deste projeto são trabalho dele, sobre as " +
                    "três bases de código aberto abaixo.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(2.dp))
            LinkRow(
                "luisxl15",
                "Desenvolvedor deste emulador",
                "https://github.com/luisxl15",
            ) { uris.openUri(it) }
            LinkRow(
                "PS2Homebrew-arcade/pcsx2x6",
                "De onde vem o suporte ao System 246/256",
                "https://github.com/PS2Homebrew-arcade/pcsx2x6",
            ) { uris.openUri(it) }
            LinkRow(
                "ARMSX2/ARMSX2",
                "Launcher Android e recompilador ARM64",
                "https://github.com/ARMSX2/ARMSX2",
            ) { uris.openUri(it) }
            LinkRow(
                "PCSX2/pcsx2",
                "O emulador de PlayStation 2 do qual os dois derivam",
                "https://github.com/PCSX2/pcsx2",
            ) { uris.openUri(it) }
        }
    }
}

@Composable
private fun LinkRow(title: String, detail: String, url: String, onOpen: (String) -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .clickable { onOpen(url) }
            .padding(vertical = 7.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(
                title,
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                detail,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
        }
        Spacer(Modifier.width(10.dp))
        ArcIcon(Arc.external, tint = MaterialTheme.colorScheme.primary, size = 15.dp)
    }
}
