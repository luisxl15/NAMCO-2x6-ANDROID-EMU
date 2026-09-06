package com.armsx2.ui.onboarding

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.core.content.edit
import com.armsx2.i18n.AppLanguage
import com.armsx2.i18n.I18n
import com.armsx2.i18n.str
import com.armsx2.runtime.MainActivityRuntime
import com.armsx2.ui.premium.Arc
import com.armsx2.ui.premium.ArcIcon
import com.armsx2.ui.premium.AuroraBackground
import com.armsx2.ui.premium.Branding
import com.armsx2.ui.premium.MaterialLevel
import com.armsx2.ui.premium.Palette
import com.armsx2.ui.premium.Radii
import com.armsx2.ui.premium.Type
import com.armsx2.ui.premium.material

/**
 * Which language, asked before anything else.
 *
 * The setup wizard is five screens of prose about storage folders, BIOS images and where the
 * games live — the densest text in the app and the first thing a new player sees. Until now it
 * came up in whatever the phone's locale resolved to, and the language picker sat in the settings
 * behind the wizard: to read the setup in your own language you had to first finish it in someone
 * else's.
 *
 * Asked once. After that the choice is the ordinary setting, and re-entering setup from the
 * settings screen does not ask again — it is a preference by then, not a question.
 */
object LanguageGate {

    private const val KEY = "ui.languageChosen"

    /** Compose state: choosing flips it and the wizard takes over in the same frame. */
    val needed = mutableStateOf(false)

    fun load() {
        needed.value = !runCatching { MainActivityRuntime.prefs.getBoolean(KEY, false) }
            .getOrDefault(true)
    }

    /** Mark it answered. The language itself is persisted by [I18n]. */
    private fun done() {
        runCatching { MainActivityRuntime.prefs.edit { putBoolean(KEY, true) } }
        needed.value = false
    }

    /**
     * The picker.
     *
     * Every language under its own name, because a list of languages written in a language you do
     * not read is not a list you can use. The system's own choice is offered first and is what
     * happens if the player just presses on — no worse than before, which is the floor this has
     * to clear.
     */
    @Composable
    fun Screen() {
        val context = LocalContext.current
        // Read so a tap recomposes the ticks and the button below.
        val selected = I18n.selected

        Box(Modifier.fillMaxSize().background(Palette.ground)) {
            AuroraBackground(Modifier.fillMaxSize())
            Column(
                Modifier
                    .fillMaxSize()
                    // Same as the wizard behind it: the bars are visible here, and in landscape
                    // the navigation bar is a column down one side -- exactly where this screen's
                    // continue button would otherwise sit.
                    .safeDrawingPadding()
                    .padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Column(Modifier.widthIn(max = 560.dp).fillMaxWidth().weight(1f)) {
                    Text(Branding.name, style = Type.title3, color = Palette.accentBright)
                    Spacer(Modifier.height(10.dp))
                    // Deliberately bilingual, and it stays that way whatever is picked: this is
                    // the one screen whose heading has to be readable BEFORE the choice is made.
                    Text("Idioma · Language", style = Type.largeTitle, color = Palette.label)
                    Spacer(Modifier.height(6.dp))
                    Text(
                        "Escolha o idioma do aplicativo · Choose the app's language",
                        style = Type.footnote,
                        color = Palette.labelSecondary,
                    )
                    Spacer(Modifier.height(18.dp))

                    LazyColumn(
                        Modifier.fillMaxWidth().weight(1f),
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        items(I18n.languages, key = { it.code }) { language ->
                            LanguageRow(
                                language = language,
                                chosen = language.code == selected,
                                onPick = { I18n.setLanguage(context, language.code) },
                            )
                        }
                    }
                }

                Spacer(Modifier.height(16.dp))
                // Its label follows the pick, live — the one place in the app where a setting
                // shows its own effect in the same breath as being set.
                Box(
                    Modifier
                        .clip(RoundedCornerShape(Radii.pill))
                        .background(Palette.accent)
                        .clickable { done() }
                        .padding(horizontal = 34.dp, vertical = 14.dp),
                ) {
                    Text(str("setup.button.next"), style = Type.headline, color = Color.White)
                }
            }
        }
    }

    @Composable
    private fun LanguageRow(language: AppLanguage, chosen: Boolean, onPick: () -> Unit) {
        Row(
            Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(Radii.tile))
                .material(MaterialLevel.UltraThin, RoundedCornerShape(Radii.tile))
                .clickable(onClick = onPick)
                .padding(horizontal = 18.dp, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(Modifier.weight(1f)) {
                Text(
                    // "System" is not a language, so it says what it will actually do.
                    if (language.code == I18n.SYSTEM_CODE) "Padrão do aparelho · Device default"
                    else language.nativeName,
                    style = Type.headline,
                    color = Palette.label,
                )
                if (language.code != I18n.SYSTEM_CODE && language.englishName != language.nativeName) {
                    Text(language.englishName, style = Type.caption, color = Palette.labelTertiary)
                }
            }
            if (chosen) {
                Spacer(Modifier.width(12.dp))
                ArcIcon(Arc.check, tint = Palette.accentBright, size = 16.dp)
            }
        }
    }
}

/** Full-screen host, so the call site reads like the wizard's. */
@Composable
fun LanguageGateScreen() {
    Box(Modifier.fillMaxSize().fillMaxHeight()) { LanguageGate.Screen() }
}
