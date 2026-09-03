package com.armsx2.ui.premium

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
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
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.armsx2.navigation.AppRoute

private data class MoreItem(val label: String, val glyph: String, val route: AppRoute)
private data class MoreGroup(val title: String, val items: List<MoreItem>)

private val GROUPS = listOf(
    MoreGroup(
        "SISTEMA",
        listOf(
            MoreItem("Configurações", "⚙", AppRoute.Settings()),
            MoreItem("BIOS", "◈", AppRoute.BiosManager()),
            MoreItem("Memory Cards", "▤", AppRoute.MemoryCardManager()),
            MoreItem("Controles", "◎", AppRoute.ControllerManager),
            MoreItem("Saves", "▧", AppRoute.SaveManager),
        ),
    ),
    MoreGroup(
        "CONTEÚDO",
        listOf(
            MoreItem("Capas", "▣", AppRoute.Artwork),
            MoreItem("Retroconquistas", "★", AppRoute.Achievements),
            MoreItem("Patches", "✦", AppRoute.PatchManager),
            MoreItem("Texturas", "▩", AppRoute.TextureManager),
        ),
    ),
    MoreGroup(
        "APP",
        listOf(
            MoreItem("Novidades", "✧", AppRoute.News),
            MoreItem("Amigos", "◍", AppRoute.Friends),
            MoreItem("Idioma", "◐", AppRoute.Language),
            MoreItem("Sobre", "ⓘ", AppRoute.About),
        ),
    ),
)

/**
 * Replaces the stock navigation drawer while the premium launcher is on. Same destinations, but
 * a glass sheet on the launcher's own material instead of a light Material drawer that clashed
 * with everything around it.
 */
@Composable
fun PremiumMoreSheet(
    visible: Boolean,
    onDismiss: () -> Unit,
    onNavigate: (AppRoute) -> Unit,
) {
    AnimatedVisibility(visible = visible, enter = fadeIn(), exit = fadeOut()) {
        Box(
            Modifier
                .fillMaxSize()
                .background(Palette.scrim)
                // Tap-out to close, without a ripple.
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null,
                ) { onDismiss() },
        )
    }

    AnimatedVisibility(
        visible = visible,
        enter = slideInHorizontally(spring(stiffness = Spring.StiffnessMediumLow)) { it },
        exit = slideOutHorizontally(spring(stiffness = Spring.StiffnessMediumLow)) { it },
    ) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.CenterEnd) {
            Column(
                Modifier
                    .fillMaxHeight()
                    .width(340.dp)
                    .statusBarsPadding()
                    .navigationBarsPadding()
                    .padding(14.dp)
                    .material(MaterialLevel.Thick, RoundedCornerShape(Radii.card), elevation = 24.dp)
                    // Swallow taps so they don't reach the dismiss scrim behind.
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null,
                    ) {},
            ) {
                LazyColumn(
                    Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(top = 58.dp, bottom = 18.dp),
                ) {
                    GROUPS.forEach { group ->
                        item(key = group.title) {
                            Text(
                                group.title,
                                style = Type.eyebrow,
                                color = Palette.labelTertiary,
                                modifier = Modifier.padding(start = 20.dp, top = 10.dp, bottom = 8.dp),
                            )
                        }
                        items(group.items.size, key = { group.title + it }) { i ->
                            val item = group.items[i]
                            MoreRow(item) { onDismiss(); onNavigate(item.route) }
                        }
                        item(key = group.title + "-gap") { Spacer(Modifier.height(10.dp)) }
                    }
                }
            }
        }
    }
}

@Composable
private fun MoreRow(item: MoreItem, onClick: () -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .clickable { onClick() }
            .padding(horizontal = 20.dp, vertical = 13.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.Start,
    ) {
        Text(item.glyph, color = Palette.accentBright, fontSize = 16.sp, modifier = Modifier.width(30.dp))
        Text(item.label, style = Type.body, color = Palette.label)
    }
}
