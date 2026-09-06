package com.armsx2.ui.premium

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import com.armsx2.data.library.ArcadePreflight

/**
 * What is missing, said before the black screen instead of after it.
 *
 * Shown only when the check found something the loader itself will refuse — a warning belongs on
 * the game's page, not between a player and a game they asked for. "Iniciar mesmo assim" is
 * always offered: this is a list of guesses about someone else's files, and being wrong must cost
 * a tap rather than a game.
 */
@Composable
fun PreflightDialogHost() {
    // ★ Above the early return, and it has to be: this fork has already paid once for a remember
    // that some compositions reach and others do not. BackHandler carries its own, which is why
    // it is called unconditionally and told when to be enabled instead.
    val scroll = rememberScrollState()
    val held = ArcadePreflight.held.value
    BackHandler(enabled = held != null) { ArcadePreflight.dismiss() }
    if (held == null) return

    Box(
        Modifier
            .fillMaxSize()
            .background(Palette.scrim)
            // Swallow taps so nothing behind the sheet reacts to a miss.
            .clickable(enabled = false) {},
        contentAlignment = Alignment.Center,
    ) {
        Column(
            Modifier
                .widthIn(max = 460.dp)
                .padding(horizontal = 22.dp)
                .clip(RoundedCornerShape(Radii.card))
                .background(Palette.ground)
                .padding(20.dp),
        ) {
            Text("NÃO DÁ PARA INICIAR", style = Type.eyebrow, color = Palette.accentBright)
            Spacer(Modifier.height(6.dp))
            Text(held.title, style = Type.headline, color = Palette.label)
            Spacer(Modifier.height(4.dp))
            Text(
                "Faltam arquivos que a placa procura no boot. Sem eles o jogo abre numa tela preta " +
                    "e não diz por quê.",
                style = Type.footnote,
                color = Palette.labelSecondary,
            )
            Spacer(Modifier.height(14.dp))

            Column(
                Modifier
                    .heightIn(max = 320.dp)
                    .verticalScroll(scroll),
            ) {
                held.report.problems.forEachIndexed { i, p ->
                    if (i > 0) Spacer(Modifier.height(10.dp))
                    Row(Modifier.fillMaxWidth()) {
                        Text(
                            if (p.blocking) "■" else "▫",
                            style = Type.footnote,
                            color = if (p.blocking) Palette.accentBright else Palette.labelTertiary,
                            modifier = Modifier.padding(end = 9.dp),
                        )
                        Column {
                            Text(p.what, style = Type.subheadline, color = Palette.label)
                            Text(p.fix, style = Type.footnote, color = Palette.labelSecondary)
                        }
                    }
                }
            }

            Spacer(Modifier.height(18.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                DialogAction("Voltar", strong = true) { ArcadePreflight.dismiss() }
                DialogAction("Iniciar mesmo assim", strong = false) { held.proceed() }
            }
        }
    }
}

@Composable
private fun DialogAction(label: String, strong: Boolean, onClick: () -> Unit) {
    Text(
        label,
        style = Type.caption,
        color = Palette.label,
        modifier = Modifier
            .clip(RoundedCornerShape(Radii.pill))
            .background(if (strong) Palette.accent else Palette.materialThin)
            .clickable { onClick() }
            .padding(horizontal = 16.dp, vertical = 10.dp),
    )
}
