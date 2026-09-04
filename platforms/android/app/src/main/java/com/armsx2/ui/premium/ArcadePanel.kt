package com.armsx2.ui.premium

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay
import kr.co.iefriends.pcsx2.NativeApp

/**
 * The cabinet controls a System 246/256 has and a DualShock2 does not: coin slot, START, and the
 * TEST / SERVICE switches. Without these a board with blank SRAM sits on its backup-error screen
 * forever — TEST is how you initialise it — and no game can be started without a credit.
 *
 * Collapsed to a single chip so it stays out of the way; it only exists at all while an arcade
 * game is running.
 */

// Matches JvsUiButton in native-lib.cpp.
private const val JVS_START = 0
private const val JVS_SERVICE = 1

// DIP index 0 is the Test switch (ACJV::GetTestModeDIPSwitch).
private const val DIP_TEST = 0

@Composable
fun ArcadePanel(modifier: Modifier = Modifier) {
    var isArcade by remember { mutableStateOf(false) }
    var expanded by remember { mutableStateOf(false) }
    var testOn by remember { mutableStateOf(false) }

    // The VM decides this, and it only becomes true once a game has booted far enough for ACJV
    // to be live — so poll rather than reading once.
    LaunchedEffect(Unit) {
        while (true) {
            isArcade = runCatching { NativeApp.jvsIsArcade() }.getOrDefault(false)
            if (isArcade) testOn = runCatching { NativeApp.jvsGetDipSwitchState(DIP_TEST) }.getOrDefault(false)
            delay(1000)
        }
    }

    if (!isArcade) return

    Box(modifier.fillMaxSize().statusBarsPadding()) {
        Row(
            Modifier
                .align(Alignment.TopCenter)
                .padding(top = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            if (!expanded) {
                Chip("ARCADE", accent = false) { expanded = true }
            } else {
                MomentaryChip("FICHA") { pressed ->
                    // Coin is an event, not a held switch: fire once on press.
                    if (pressed) runCatching { NativeApp.jvsInsertCoin(0) }
                }
                MomentaryChip("START") { pressed ->
                    runCatching { NativeApp.jvsSetButton(0, JVS_START, pressed) }
                }
                MomentaryChip("SERVICE") { pressed ->
                    runCatching { NativeApp.jvsSetButton(0, JVS_SERVICE, pressed) }
                }
                // TEST is a switch on the real cabinet, so it latches here too.
                Chip(if (testOn) "TEST ▪" else "TEST", accent = testOn) {
                    runCatching { NativeApp.jvsToggleDipSwitch(DIP_TEST) }
                    testOn = runCatching { NativeApp.jvsGetDipSwitchState(DIP_TEST) }.getOrDefault(!testOn)
                }
                IconChip(Arc.close) { expanded = false }
            }
        }
    }
}

/** Chip variant carrying an icon instead of a word — the close affordance. */
@Composable
private fun IconChip(@androidx.annotation.DrawableRes icon: Int, onClick: () -> Unit) {
    Box(
        Modifier
            .clip(RoundedCornerShape(Radii.pill))
            .material(MaterialLevel.Thick, RoundedCornerShape(Radii.pill))
            .clickable { onClick() }
            .padding(horizontal = 12.dp, vertical = 8.dp),
    ) {
        ArcIcon(icon, tint = Palette.label, size = 14.dp)
    }
}

@Composable
private fun Chip(label: String, accent: Boolean, onClick: () -> Unit) {
    Box(
        Modifier
            .clip(RoundedCornerShape(Radii.pill))
            .then(
                if (accent) Modifier.background(Palette.accent)
                else Modifier.material(MaterialLevel.Thick, RoundedCornerShape(Radii.pill)),
            )
            .clickable { onClick() }
            .padding(horizontal = 14.dp, vertical = 8.dp),
    ) {
        Text(label, style = Type.caption, color = if (accent) Color.White else Palette.label, fontSize = 12.sp)
    }
}

/** A chip that reports press and release, for the switches the board reads as held. */
@Composable
private fun MomentaryChip(label: String, onPressChange: (Boolean) -> Unit) {
    var down by remember { mutableStateOf(false) }
    Box(
        Modifier
            .clip(RoundedCornerShape(Radii.pill))
            .then(
                if (down) Modifier.background(Palette.accentBright)
                else Modifier.material(MaterialLevel.Thick, RoundedCornerShape(Radii.pill)),
            )
            .pointerInput(Unit) {
                detectTapGestures(
                    onPress = {
                        down = true
                        onPressChange(true)
                        // Hold for as long as the finger is down, then release the switch.
                        tryAwaitRelease()
                        down = false
                        onPressChange(false)
                    },
                )
            }
            .padding(horizontal = 14.dp, vertical = 8.dp),
    ) {
        Text(label, style = Type.caption, color = if (down) Color.White else Palette.label, fontSize = 12.sp)
    }
}
