package com.armsx2.ui.premium

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.armsx2.EnglishTitles
import com.armsx2.GameInfo
import com.armsx2.navigation.AppRoute
import com.armsx2.navigation.UiNavigator
import com.armsx2.runtime.MainActivityRuntime
import com.armsx2.ui.settings.controllerFocusable

/**
 * Everything that belongs to ONE game, reached from the game itself.
 *
 * The premium launcher had no way in: settings opened globally from the top bar, the BIOS manager
 * from the drawer, and neither knew which game you were looking at. So setting a thing for one
 * game meant setting it for every game and remembering to put it back — or booting the game first
 * and going through the pause menu, which is a strange way to configure something you have not
 * started yet.
 *
 * Nothing here writes a global setting. The per-game tier is a sparse override stored under
 * `config.game.<serial>`: only the keys you actually change are written, and reading a game's
 * settings is the global set with those laid over it. Anything you never touch here keeps
 * following the emulator's own settings, including later changes to them — which is the point,
 * and the reason this is not a copy of the global set.
 */
@Composable
fun GameContextMenu(game: GameInfo, onDismiss: () -> Unit, onPlay: (GameInfo) -> Unit) {
    val title = game.displayTitle(EnglishTitles.enabled.value)
    val scroll = rememberScrollState()

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
                // Swallow taps: without this the sheet's own background counts as "outside".
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null,
                ) {}
                .padding(20.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    Modifier
                        .height(76.dp)
                        .aspectRatio(CoverAspect)
                        .clip(RoundedCornerShape(Radii.control)),
                ) {
                    CoverArt(game, Modifier.fillMaxSize())
                }
                Spacer(Modifier.width(14.dp))
                Column(Modifier.weight(1f)) {
                    Text(
                        title,
                        style = Type.headline, color = Palette.label,
                        maxLines = 2, overflow = TextOverflow.Ellipsis,
                    )
                    Spacer(Modifier.height(3.dp))
                    Text(
                        game.serial.orEmpty(),
                        style = Type.caption, color = Palette.labelTertiary,
                    )
                }
            }

            Spacer(Modifier.height(16.dp))
            Text("SÓ PARA ESTE JOGO", style = Type.eyebrow, color = Palette.labelTertiary)
            Spacer(Modifier.height(10.dp))

            Column(
                Modifier.fillMaxWidth().verticalScroll(scroll),
                verticalArrangement = androidx.compose.foundation.layout.Arrangement.spacedBy(8.dp),
            ) {
                MenuRow("Jogar") {
                    onDismiss()
                    onPlay(game)
                }
                MenuRow("Configurações deste jogo") {
                    onDismiss()
                    UiNavigator.navigate(AppRoute.Settings(game = game))
                }
                MenuRow("BIOS deste jogo") {
                    onDismiss()
                    UiNavigator.navigate(AppRoute.BiosManager(game))
                }
                MenuRow("Memory cards deste jogo") {
                    onDismiss()
                    UiNavigator.navigate(AppRoute.MemoryCardManager(game))
                }
                // The Save Manager works on a game that is not running, but only if it is told
                // which one -- contextGame is exactly that hand-off.
                MenuRow("Estados salvos") {
                    onDismiss()
                    MainActivityRuntime.contextGame.value = game
                    UiNavigator.navigate(AppRoute.SaveManager)
                }
            }

            Spacer(Modifier.height(14.dp))
            Text(
                "O que você não mexer aqui continua seguindo as configurações do emulador.",
                style = Type.caption, color = Palette.labelSecondary,
            )
        }
    }
}

@Composable
private fun MenuRow(label: String, onClick: () -> Unit) {
    Box(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(Radii.tile))
            .material(MaterialLevel.UltraThin, RoundedCornerShape(Radii.tile))
            .controllerFocusable("gamemenu.$label", RoundedCornerShape(Radii.tile), onConfirm = onClick)
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 14.dp),
    ) {
        Text(label, style = Type.subheadline, color = Palette.label)
    }
}
