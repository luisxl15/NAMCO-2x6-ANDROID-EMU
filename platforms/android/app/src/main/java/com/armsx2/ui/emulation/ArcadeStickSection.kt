package com.armsx2.ui.emulation

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.armsx2.input.ArcadeStick
import com.armsx2.input.ArcadeWheel
import kotlinx.coroutines.delay

/**
 * The analog stick, for the cabinets whose directions are switches.
 *
 * In the pause menu beside the wheel and the gun, and hidden on a driving board for the same
 * reason those are hidden elsewhere: there the stick is already the steering, and this setting
 * would be describing something that does not happen.
 */
@Composable
fun ArcadeStickSection(serial: String?) {
    var driving by remember { mutableStateOf(false) }
    // The board's mode is only known once the .acgame has been resolved, so ask rather than read
    // once -- the same poll the wheel section does.
    LaunchedEffect(Unit) {
        while (true) {
            driving = ArcadeWheel.isDriving()
            delay(1000)
        }
    }

    var on by remember(serial) { mutableStateOf(ArcadeStick.enabled(serial)) }
    var dead by remember(serial) { mutableIntStateOf(ArcadeStick.deadzone(serial)) }

    // Whatever this game was set to last time, back into the core: it is the only thing that
    // carries the setting across a boot.
    LaunchedEffect(serial) { ArcadeStick.apply(serial) }

    if (driving) return

    SectionCard("Analógico") {
        Column(Modifier.fillMaxWidth()) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text("Analógico como alavanca", color = Color.White, fontSize = 15.sp)
                    Text(
                        "O painel do fliperama tem chaves, não analógico, então sem isto o " +
                            "direcional é o único jeito de jogar. O direcional continua valendo.",
                        color = Color(0xFF8B93A3), fontSize = 11.sp,
                    )
                }
                Switch(
                    checked = on,
                    onCheckedChange = {
                        on = it
                        ArcadeStick.setEnabled(serial, it)
                    },
                )
            }
            if (on) {
                Spacer(Modifier.height(12.dp))
                Column(Modifier.fillMaxWidth()) {
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Text("Zona morta", color = Color.White, fontSize = 15.sp)
                        Text("$dead%", color = Color(0xFF9AA3B2), fontSize = 13.sp)
                    }
                    Slider(
                        value = dead.toFloat().coerceIn(10f, 80f),
                        onValueChange = {
                            dead = it.toInt()
                            ArcadeStick.setDeadzone(serial, dead)
                        },
                        valueRange = 10f..80f,
                    )
                    Text(
                        "Quanto o analógico precisa inclinar para a direção valer. Suba se o " +
                            "personagem anda sozinho; desça se a diagonal escapa.",
                        color = Color(0xFF8B93A3), fontSize = 11.sp,
                    )
                }
            }
        }
    }
}
