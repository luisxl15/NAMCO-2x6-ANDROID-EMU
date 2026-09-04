package com.armsx2.ui.settings

import androidx.compose.foundation.BorderStroke
import android.os.Build
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.graphics.Color
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import android.widget.Toast
import com.armsx2.i18n.I18n
import com.armsx2.i18n.str
import com.armsx2.navigation.AppRoute
import com.armsx2.navigation.UiNavigator
import com.armsx2.runtime.MainActivityRuntime
import com.armsx2.ui.theme.BootLogoPreferences
import com.armsx2.ui.theme.ThemeMode
import com.armsx2.ui.theme.ThemePreferences
import com.armsx2.ui.theme.LauncherOrientationPreferences
import com.armsx2.ui.theme.ToolbarPositionPreferences
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.ui.graphics.Brush
import com.armsx2.ui.theme.LibraryBackgroundColorPreferences
import com.armsx2.ui.theme.LibraryChromePreferences
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import com.armsx2.ui.premium.Arc
import com.armsx2.ui.premium.ArcIcon
import com.armsx2.ui.premium.Palette
import com.armsx2.ui.premium.Type
import java.io.File

@Composable
fun AppTab() {
    val currentLanguage = I18n.languages.firstOrNull { it.code == I18n.current }
    val appContext = LocalContext.current

    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(2.dp),
    ) {
        // No updater: this is an arcade fork, and the upstream releases it would offer are a
        // different emulator that would replace the System 246/256 support with a plain PS2 build.

        DisclosureRow(
            id = "app.language",
            label = str("app.language"),
            value = if (I18n.selected == I18n.SYSTEM_CODE) str("app.language.system")
                    else currentLanguage?.nativeName ?: "English",
            onClick = { UiNavigator.navigate(AppRoute.Language) },
        )

        // Removed: theme picker, animated backdrop (XMB wave / Flurry / screensavers), bar and
        // background colours, boot logo, toolbar position and the library grid controls. Every one
        // of them fed either the stock HomeScreen or the Material colour scheme, and the premium
        // launcher owns both -- PremiumScheme wins in Theme.kt and PremiumHome draws its own
        // backdrop -- so they were switches wired to nothing.

        BackupRestoreRows()

        ToggleRow(
            label = str("app.blockHome"),
            value = com.armsx2.ui.ScreenPinning.enabled.value,
            description = str("app.blockHome.desc"),
            onChange = { com.armsx2.ui.ScreenPinning.set(it) },
        )

        ToggleRow(
            label = str("secondScreen.label"),
            value = com.armsx2.SecondScreen.enabled.value,
            description = str("secondScreen.desc"),
            onChange = { com.armsx2.SecondScreen.set(appContext, it) },
        )

        if (com.armsx2.SecondScreen.enabled.value) {
            ToggleRow(
                label = str("secondScreen.moveOsd"),
                value = com.armsx2.SecondScreen.moveOsd.value,
                description = str("secondScreen.moveOsd.desc"),
                onChange = { com.armsx2.SecondScreen.setMoveOsd(it) },
            )

            // The panel now takes its colours from whichever theme is selected, so this is only
            // about the GROUND behind the tiles: the theme's own, the library's backdrop for
            // continuity with the screen it sits beside, or black for an OLED second display.
            if (com.armsx2.SecondScreen.ignoredDisplays.value.isNotEmpty()) {
                Row(
                    Modifier.fillMaxWidth().padding(top = 4.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    val clear = { com.armsx2.SecondScreen.clearIgnoredDisplays() }
                    OutlinedButton(
                        onClick = clear,
                        modifier = Modifier.controllerFocusable("secondScreen.displays.reset", onConfirm = clear),
                    ) {
                        Text(
                            str("secondScreen.displays.reset") +
                                " (" + com.armsx2.SecondScreen.ignoredDisplays.value.size + ")",
                        )
                    }
                }
            }

            ToggleRow(
                label = str("secondScreen.topBar"),
                value = com.armsx2.SecondScreen.topBar.value,
                description = str("secondScreen.topBar.desc"),
                onChange = { com.armsx2.SecondScreen.setTopBar(it) },
            )

            val panelBgPicker = rememberLauncherForActivityResult(
                ActivityResultContracts.OpenDocument()
            ) { picked -> picked?.let { com.armsx2.SecondScreen.setBackgroundImage(appContext, it) } }

            SegmentedRow(
                label = str("secondScreen.background"),
                options = listOf(
                    str("secondScreen.background.theme"),
                    str("secondScreen.background.library"),
                    str("secondScreen.background.black"),
                    str("secondScreen.background.custom"),
                ),
                selectedIndex = com.armsx2.SecondScreen.background.value,
                onChange = {
                    // Picking "Custom" with nothing chosen yet opens the picker rather than
                    // selecting a mode that would render as the theme ground and look broken.
                    if (it == com.armsx2.SecondScreen.BG_CUSTOM &&
                        com.armsx2.SecondScreen.backgroundUri.value == null
                    ) {
                        panelBgPicker.launch(arrayOf("image/*"))
                    } else {
                        com.armsx2.SecondScreen.setBackground(it)
                    }
                },
            )
            if (com.armsx2.SecondScreen.background.value == com.armsx2.SecondScreen.BG_CUSTOM) {
                Row(
                    Modifier.fillMaxWidth().padding(top = 4.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    val pick = { panelBgPicker.launch(arrayOf("image/*")) }
                    OutlinedButton(
                        onClick = pick,
                        modifier = Modifier.controllerFocusable("secondScreen.background.choose", onConfirm = pick),
                    ) { Text(str("secondScreen.background.choose")) }
                }
            }

            // Only worth showing once a thermal tile is actually on the panel — otherwise it is
            // a control over something invisible.
            if (com.armsx2.SecondScreenLayout.tiles().any {
                    it == com.armsx2.SecondScreenTile.CPU_TEMP ||
                        it == com.armsx2.SecondScreenTile.GPU_TEMP ||
                        it == com.armsx2.SecondScreenTile.BATTERY_TEMP
                }
            ) {
                val seconds = listOf(1, 2, 3, 5)
                SegmentedRow(
                    label = str("secondScreen.tempInterval"),
                    options = seconds.map { "${it}s" },
                    selectedIndex = seconds.indexOf(com.armsx2.SecondScreen.tempIntervalSec.value)
                        .coerceAtLeast(0),
                    onChange = { com.armsx2.SecondScreen.setTempInterval(seconds[it]) },
                )
            }

            // Panel layout editor. Chips for what is on the panel, arrows for the order, a column
            // count for the shape of the grid. Asked for as "a grid which can be filled with boxes
            // containing the things one need" (NiceRon) — the point is that the panel is different
            // for a save-scummer and for someone watching frame pacing, so it cannot be one fixed
            // arrangement.
            //
            // Reading the generation subscribes this whole block, so a toggle re-renders the chips
            // AND the order list without either one owning the state.
            com.armsx2.SecondScreenLayout.generation.intValue
            val placed = com.armsx2.SecondScreenLayout.tiles()
            Text(
                str("secondScreen.layout"),
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.padding(top = 12.dp),
            )
            Text(
                str("secondScreen.layout.desc"),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(bottom = 8.dp),
            )
            FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                com.armsx2.SecondScreenTile.entries.forEach { tile ->
                    val toggle = {
                        com.armsx2.SecondScreenLayout.toggle(tile)
                        com.armsx2.SecondScreen.rebuild()
                    }
                    FilterChip(
                        selected = tile in placed,
                        onClick = toggle,
                        label = { Text(str(tile.labelKey)) },
                        shape = RoundedCornerShape(11.dp),
                        modifier = Modifier.controllerFocusable(
                            "secondScreen.tile.${tile.id}",
                            RoundedCornerShape(11.dp),
                            onConfirm = toggle,
                        ),
                    )
                }
            }
            // Order, one row per placed tile. Only shown once there is something to order.
            if (placed.size > 1) {
                placed.forEachIndexed { index, tile ->
                    Row(
                        Modifier.fillMaxWidth().padding(vertical = 2.dp),
                        verticalAlignment = androidx.compose.ui.Alignment.CenterVertically,
                    ) {
                        Text(
                            "${index + 1}. ${str(tile.labelKey)}",
                            style = MaterialTheme.typography.bodyMedium,
                            modifier = Modifier.weight(1f),
                        )
                        val up = {
                            com.armsx2.SecondScreenLayout.move(tile, -1)
                            com.armsx2.SecondScreen.rebuild()
                        }
                        val down = {
                            com.armsx2.SecondScreenLayout.move(tile, 1)
                            com.armsx2.SecondScreen.rebuild()
                        }
                        OutlinedButton(
                            onClick = up,
                            enabled = index > 0,
                            modifier = Modifier.controllerFocusable(
                                "secondScreen.up.${tile.id}",
                                RoundedCornerShape(11.dp),
                                onConfirm = up,
                            ),
                        ) { ArcIcon(Arc.chevronUp, tint = Palette.label, size = 15.dp) }
                        Spacer(Modifier.width(6.dp))
                        OutlinedButton(
                            onClick = down,
                            enabled = index < placed.lastIndex,
                            modifier = Modifier.controllerFocusable(
                                "secondScreen.down.${tile.id}",
                                RoundedCornerShape(11.dp),
                                onConfirm = down,
                            ),
                        ) { ArcIcon(Arc.chevronDown, tint = Palette.label, size = 15.dp) }
                    }
                }
            }
            IntSliderRow(
                label = str("secondScreen.layout.columns"),
                value = com.armsx2.SecondScreenLayout.columns(),
                min = 1,
                max = 6,
                onChange = {
                    com.armsx2.SecondScreenLayout.setColumns(it)
                    com.armsx2.SecondScreen.rebuild()
                },
            )
            // Columns already decide width (tiles split the row equally), so this is the other
            // axis. 0 keeps the old behaviour of being exactly as tall as the text.
            IntSliderRow(
                label = str("secondScreen.layout.tileHeight"),
                value = com.armsx2.SecondScreenLayout.tileHeight(),
                min = 0,
                max = 160,
                onChange = {
                    com.armsx2.SecondScreenLayout.setTileHeight(it)
                    com.armsx2.SecondScreen.rebuild()
                },
            )
            val resetLayout = {
                com.armsx2.SecondScreenLayout.reset()
                com.armsx2.SecondScreen.rebuild()
            }
            OutlinedButton(
                onClick = resetLayout,
                modifier = Modifier
                    .padding(top = 4.dp)
                    .controllerFocusable(
                        "secondScreen.layout.reset",
                        RoundedCornerShape(11.dp),
                        onConfirm = resetLayout,
                    ),
            ) { Text(str("secondScreen.layout.reset")) }
        }

        // Companion-app access to the recently-played list, over the RecentGamesContentProvider.
        // Off by default and deliberately so: the provider is exported without a permission (it
        // has to be, for a third-party companion to reach it), so the list — including the file
        // URIs, which carry your folder layout — is only readable once you say so.
        //
        // The switch reads "is anything being shared right now", not just the share-with-everything
        // flag, because an app can also be allowed on its own from its consent prompt. Were it
        // wired to the flag alone it would sit at off while a companion was actively reading, and
        // there would be no control left to turn that off with.
        run {
            val shareKey = com.armsx2.data.library.RecentGamesContentProvider.KEY_SHARE_ENABLED
            val prefs = com.armsx2.runtime.MainActivityRuntime.prefs
            val shareRecent = remember {
                mutableStateOf(
                    prefs.getBoolean(shareKey, false) ||
                        com.armsx2.data.library.RecentGamesAccess.grantedPackages(prefs).isNotEmpty(),
                )
            }
            ToggleRow(
                label = str("app.shareRecentGames"),
                value = shareRecent.value,
                description = str("app.shareRecentGames.desc"),
            ) { on ->
                shareRecent.value = on
                // commit(), not apply(): the reader is a DIFFERENT process that can be queried
                // the moment this returns, and apply() only guarantees the in-memory value.
                runCatching {
                    prefs.edit().putBoolean(shareKey, on).commit()
                }
                if (!on) {
                    // Off has to mean off. Leaving the per-app grants behind would keep whoever
                    // already asked reading the library from a switch the user just turned off.
                    com.armsx2.data.library.RecentGamesAccess.revokeAll(prefs)
                }
                // Either direction is the user revisiting the decision, so earlier refusals stop
                // counting: a companion that was told no once can ask again.
                com.armsx2.data.library.RecentGamesAccess.clearDeclined(prefs)
            }
        }

        ToggleRow(
            label = str("app.batteryWarnings"),
            value = com.armsx2.BatteryWatcher.enabled.value,
            description = str("app.batteryWarnings.desc"),
            onChange = { com.armsx2.BatteryWatcher.set(it) },
        )

        ToggleRow(
            label = str("app.libraryMusic"),
            value = com.armsx2.LibraryMusic.enabled.value,
            description = str("app.libraryMusic.desc"),
            onChange = { com.armsx2.LibraryMusic.set(appContext, it) },
        )
        if (com.armsx2.LibraryMusic.enabled.value) {
            IntSliderRow(
                label = str("app.libraryMusic.volume"),
                value = com.armsx2.LibraryMusic.volumePercent.value,
                min = 0,
                max = 100,
                valueFormatter = { "$it%" },
                onChange = { com.armsx2.LibraryMusic.setVolume(it) },
            )
            // Custom track: plays a file the user picked from their own device. The app never
            // ships or redistributes it — same model as importing a texture pack or skin.
            val musicPicker = rememberLauncherForActivityResult(
                ActivityResultContracts.OpenDocument()
            ) { uri ->
                if (uri != null) {
                    val name = androidx.documentfile.provider.DocumentFile
                        .fromSingleUri(appContext, uri)?.name ?: "Custom track"
                    com.armsx2.LibraryMusic.setCustomTrack(appContext, uri, name)
                }
            }
            val custom = com.armsx2.LibraryMusic.customName.value
            Text(
                if (custom != null) str("app.libraryMusic.current").format(custom)
                else str("app.libraryMusic.default"),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(start = 4.dp, top = 2.dp),
            )
            Row(
                Modifier.fillMaxWidth().padding(top = 4.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                val pick = { musicPicker.launch(arrayOf("audio/*")) }
                OutlinedButton(
                    onClick = pick,
                    modifier = Modifier.controllerFocusable("app.libraryMusic.choose", onConfirm = pick),
                ) { Text(str("app.libraryMusic.choose")) }
                if (custom != null) {
                    val reset = { com.armsx2.LibraryMusic.clearCustomTrack(appContext) }
                    OutlinedButton(
                        onClick = reset,
                        modifier = Modifier.controllerFocusable("app.libraryMusic.reset", onConfirm = reset),
                    ) { Text(str("app.libraryMusic.reset")) }
                }
            }
        }

        // In-game pause music. Separate track and separate toggle from the library's: the pause
        // menu was silent, and people sit in it browsing settings mid-game. Off by default —
        // audio starting when you open a menu is startling if you didn't ask for it.
        ToggleRow(
            label = str("app.pauseMusic"),
            value = com.armsx2.PauseMusic.enabled.value,
            description = str("app.pauseMusic.desc"),
            onChange = { com.armsx2.PauseMusic.set(appContext, it) },
        )
        if (com.armsx2.PauseMusic.enabled.value) {
            IntSliderRow(
                label = str("app.pauseMusic.volume"),
                value = com.armsx2.PauseMusic.volumePercent.value,
                min = 0,
                max = 100,
                valueFormatter = { "$it%" },
                onChange = { com.armsx2.PauseMusic.setVolume(it) },
            )
            val pausePicker = rememberLauncherForActivityResult(
                ActivityResultContracts.OpenDocument()
            ) { uri ->
                if (uri != null) {
                    val name = androidx.documentfile.provider.DocumentFile
                        .fromSingleUri(appContext, uri)?.name ?: "Custom track"
                    com.armsx2.PauseMusic.setCustomTrack(appContext, uri, name)
                }
            }
            val pauseCustom = com.armsx2.PauseMusic.customName.value
            Text(
                if (pauseCustom != null) str("app.pauseMusic.current").format(pauseCustom)
                else str("app.pauseMusic.default"),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(start = 4.dp, top = 2.dp),
            )
            Row(
                Modifier.fillMaxWidth().padding(top = 4.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                val pickPause = { pausePicker.launch(arrayOf("audio/*")) }
                OutlinedButton(
                    onClick = pickPause,
                    modifier = Modifier.controllerFocusable("app.pauseMusic.choose", onConfirm = pickPause),
                ) { Text(str("app.pauseMusic.choose")) }
                if (pauseCustom != null) {
                    val resetPause = { com.armsx2.PauseMusic.clearCustomTrack(appContext) }
                    OutlinedButton(
                        onClick = resetPause,
                        modifier = Modifier.controllerFocusable("app.pauseMusic.reset", onConfirm = resetPause),
                    ) { Text(str("app.pauseMusic.reset")) }
                }
            }
        }

        // Menu sound effects. User-provided like the custom track above — the app ships no sounds;
        // the user imports a folder of named clips (select/back/menu/toggle_on/toggle_off/reset/
        // slider). Opt-in (off by default) since there are no bundled defaults to fall back on.
        ToggleRow(
            label = str("app.menuSfx"),
            value = com.armsx2.MenuSfx.enabled.value,
            description = str("app.menuSfx.desc"),
            onChange = { com.armsx2.MenuSfx.set(appContext, it) },
        )
        if (com.armsx2.MenuSfx.enabled.value) {
            IntSliderRow(
                label = str("app.menuSfx.volume"),
                value = com.armsx2.MenuSfx.volumePercent.value,
                min = 0,
                max = 100,
                valueFormatter = { "$it%" },
                onChange = { com.armsx2.MenuSfx.setVolume(it) },
            )
            val sfxPicker = rememberLauncherForActivityResult(
                ActivityResultContracts.OpenDocumentTree()
            ) { uri ->
                if (uri != null) {
                    val n = com.armsx2.MenuSfx.importFromTree(appContext, uri)
                    Toast.makeText(
                        appContext,
                        if (n > 0) I18n.get("app.menuSfx.imported").format(n)
                        else I18n.get("app.menuSfx.importNone"),
                        Toast.LENGTH_LONG,
                    ).show()
                }
            }
            val pack = com.armsx2.MenuSfx.packName.value
            Text(
                if (pack != null) str("app.menuSfx.current").format(pack) else str("app.menuSfx.none"),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(start = 4.dp, top = 2.dp),
            )
            Text(
                str("app.menuSfx.hint"),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(start = 4.dp, top = 2.dp),
            )
            Row(
                Modifier.fillMaxWidth().padding(top = 4.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                val pick = { sfxPicker.launch(null) }
                OutlinedButton(
                    onClick = pick,
                    modifier = Modifier.controllerFocusable("app.menuSfx.choose", onConfirm = pick),
                ) { Text(str("app.menuSfx.choose")) }
                if (pack != null) {
                    val clear = { com.armsx2.MenuSfx.clear(appContext) }
                    OutlinedButton(
                        onClick = clear,
                        modifier = Modifier.controllerFocusable("app.menuSfx.reset", onConfirm = clear),
                    ) { Text(str("app.menuSfx.reset")) }
                }
            }
        }

        // No rotation setting: the app is locked to landscape (manifest + applyEmulationOrientation),
        // so a portrait option here would be a switch that does nothing.

        // Text entry: our own on-screen keyboard (default, gamepad-navigable) vs the Android IME.
        // Seeded via refreshUseSystemIme() because this row can compose before the keyboard has
        // ever been opened, which is the only other place the preference gets read.
        run {
            remember { com.armsx2.ui.home.LibraryKeyboard.refreshUseSystemIme() }
            ToggleRow(
                label = str("app.keyboard.systemIme"),
                value = com.armsx2.ui.home.LibraryKeyboard.useSystemIme.value,
                description = str("app.keyboard.systemIme.desc"),
                onChange = com.armsx2.ui.home.LibraryKeyboard::setUseSystemIme,
            )
        }

        ClearCacheRow()
    }
}

/** Clear cached, regenerable data: compiled shader/pipeline caches (Vulkan + GL) and
 *  the cover-art image cache. All of it rebuilds automatically, so this only frees space
 *  and forces a clean rebuild — handy after a driver change or if a cache looks corrupt. */
@Composable
private fun ClearCacheRow() {
    val context = LocalContext.current
    var status by remember { mutableStateOf("") }
    ActionRow(
        id = "app.clearCache",
        label = str("app.clearCache"),
        description = status.ifEmpty { str("app.clearCache.desc") },
        onClick = { status = clearAppCaches(context) },
    )
}

/**
 * A row that opens something else, the way an iOS settings row does: label on the left, the
 * current value in secondary text on the right, a chevron to say there is somewhere to go. No
 * icon tile — a coloured square per row turns a settings list into a wall of badges, and the
 * label is already the thing being read.
 */
@Composable
private fun DisclosureRow(id: String, label: String, value: String, onClick: () -> Unit) {
    Surface(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth()
            .drawBehind {
                drawLine(
                    Palette.hairline,
                    Offset(16.dp.toPx(), size.height),
                    Offset(size.width, size.height),
                    strokeWidth = 1f,
                )
            }
            .controllerFocusable(id, RoundedCornerShape(14.dp), onConfirm = onClick),
        shape = RoundedCornerShape(14.dp),
        color = Color.Transparent,
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 4.dp, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(label, style = Type.body, color = Palette.label, modifier = Modifier.weight(1f))
            Text(value, style = Type.callout, color = Palette.labelSecondary)
            Spacer(Modifier.width(6.dp))
            ArcIcon(Arc.chevronRight, tint = Palette.labelTertiary, size = 15.dp)
        }
    }
}

/** A row that performs an action in place. Same shape as [DisclosureRow], without the chevron. */
@Composable
private fun ActionRow(
    id: String,
    label: String,
    description: String,
    enabled: Boolean = true,
    onClick: () -> Unit,
) {
    Surface(
        onClick = onClick,
        enabled = enabled,
        modifier = Modifier.fillMaxWidth()
            .drawBehind {
                drawLine(
                    Palette.hairline,
                    Offset(16.dp.toPx(), size.height),
                    Offset(size.width, size.height),
                    strokeWidth = 1f,
                )
            }
            .controllerFocusable(id, RoundedCornerShape(14.dp), onConfirm = onClick),
        shape = RoundedCornerShape(14.dp),
        color = Color.Transparent,
    ) {
        Column(Modifier.fillMaxWidth().padding(horizontal = 4.dp, vertical = 13.dp)) {
            Text(
                label,
                style = Type.body,
                color = if (enabled) Palette.label else Palette.labelTertiary,
            )
            Spacer(Modifier.height(2.dp))
            Text(description, style = Type.footnote, color = Palette.labelSecondary)
        }
    }
}

/** Export / import everything a reinstall would destroy: save states, memory cards, artwork,
 *  per-game settings, controller profiles, patches and every preference. ROMs and BIOS are left
 *  out — those live outside the app and survive on their own. See [com.armsx2.BackupManager]. */
@Composable
private fun BackupRestoreRows() {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var busy by remember { mutableStateOf(false) }
    var status by remember { mutableStateOf("") }

    // Both directions run on IO: a full data root is tens to hundreds of MB and would jank (or ANR)
    // on the main thread. `busy` blocks a second tap while one is in flight.
    val exporter = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("application/zip")
    ) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult
        busy = true
        status = I18n.get("app.backup.working")
        scope.launch(Dispatchers.IO) {
            val r = runCatching {
                context.contentResolver.openOutputStream(uri)?.use {
                    com.armsx2.BackupManager.export(context, it)
                } ?: com.armsx2.BackupManager.BackupResult(false, "could not open destination")
            }.getOrElse { com.armsx2.BackupManager.BackupResult(false, it.message ?: "failed") }
            withContext(Dispatchers.Main) {
                busy = false
                status = if (r.ok) I18n.get("app.backup.exported").replace("%s", r.detail)
                         else I18n.get("app.backup.failed").replace("%s", r.detail)
                Toast.makeText(context, status, Toast.LENGTH_LONG).show()
            }
        }
    }
    val importer = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument()
    ) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult
        busy = true
        status = I18n.get("app.backup.working")
        scope.launch(Dispatchers.IO) {
            val r = runCatching {
                context.contentResolver.openInputStream(uri)?.use {
                    com.armsx2.BackupManager.restore(context, it)
                } ?: com.armsx2.BackupManager.BackupResult(false, "could not open file")
            }.getOrElse { com.armsx2.BackupManager.BackupResult(false, it.message ?: "failed") }
            withContext(Dispatchers.Main) {
                busy = false
                status = if (r.ok) I18n.get("app.backup.imported").replace("%s", r.detail)
                         else I18n.get("app.backup.failed").replace("%s", r.detail)
                Toast.makeText(context, status, Toast.LENGTH_LONG).show()
                // Preferences are read once at startup, so this process would keep serving the old
                // values and then overwrite the restored XML on its next write. Restart to adopt.
                if (r.ok) MainActivityRuntime.restartApp(context)
            }
        }
    }

    val doExport = { if (!busy) exporter.launch(com.armsx2.BackupManager.suggestedName(context)) }
    val doImport = {
        if (!busy) importer.launch(arrayOf("application/zip", "application/octet-stream"))
    }

    BackupActionRow("app.backup.export", "app.backup.export.desc", status, busy, doExport)
    BackupActionRow("app.backup.import", "app.backup.import.desc", "", busy, doImport)

    // Factory reset. Sits with Backup/Restore because Export is the thing to do first — the
    // prompt says so. Routed through GlobalConfirm rather than a local overlay: this row is
    // inside a scrolling tab, so a scrim drawn here would clip to the row's bounds.
    val doReset = {
        if (!busy) {
            com.armsx2.ui.common.GlobalConfirm.ask(
                title = I18n.get("app.reset.title"),
                message = I18n.get("app.reset.message"),
                confirmLabel = I18n.get("app.reset.confirm"),
                destructive = true,
            ) { MainActivityRuntime.resetAppToDefaults(context) }
        }
    }
    BackupActionRow("app.reset", "app.reset.desc", "", busy, doReset)
}

@Composable
private fun BackupActionRow(
    labelKey: String,
    descKey: String,
    status: String,
    busy: Boolean,
    onClick: () -> Unit,
) {
    ActionRow(
        id = labelKey,
        label = str(labelKey),
        description = status.ifEmpty { str(descKey) },
        enabled = !busy,
        onClick = onClick,
    )
}

/** Delete shader/pipeline caches (assetCopyRoot/cache) + the OS cache dir (Coil image
 *  cache, temp files). Returns a human-readable summary for the row + a toast. */
private fun clearAppCaches(context: android.content.Context): String {
    var removed = 0
    var bytes = 0L
    fun wipe(dir: File?) {
        val entries = dir?.listFiles() ?: return
        for (f in entries) {
            val size = if (f.isFile) f.length() else 0L
            val ok = if (f.isDirectory) f.deleteRecursively() else runCatching { f.delete() }.getOrDefault(false)
            if (ok) { removed++; bytes += size }
        }
    }
    // Compiled shader / pipeline caches live under the app-private asset-copy root.
    wipe(File(MainActivityRuntime.assetCopyRoot(context), "cache"))
    // Coil cover-art cache + any transient files in the OS-managed cache dir.
    wipe(context.cacheDir)
    val summary = if (removed > 0) {
        val mb = bytes / (1024.0 * 1024.0)
        if (mb >= 0.1) I18n.get("app.clearCache.done").replace("%s", String.format("%.1f MB", mb))
        else I18n.get("app.clearCache.doneSmall")
    } else {
        I18n.get("app.clearCache.empty")
    }
    Toast.makeText(context, summary, Toast.LENGTH_SHORT).show()
    return summary
}
