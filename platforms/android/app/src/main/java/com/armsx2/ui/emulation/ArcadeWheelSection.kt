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
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.armsx2.input.ArcadeWheel
import kotlinx.coroutines.delay

/**
 * Steering, for the cabinets that have one.
 *
 * In the pause menu and only while a driving board is running, for the same reason the patch list
 * is: a deadzone means nothing to a fighting game, and a slider you can only judge by driving
 * belongs where you can go back to driving in one tap.
 */
@Composable
fun ArcadeWheelSection(serial: String?) {
    var driving by remember { mutableStateOf(false) }
    // The mode is only known once the .acgame has been resolved and ACJV is live, so ask rather
    // than reading once -- the same poll the arcade panel does.
    LaunchedEffect(Unit) {
        while (true) {
            driving = ArcadeWheel.isDriving()
            delay(1000)
        }
    }

    var dead by remember(serial) { mutableIntStateOf(ArcadeWheel.deadzone(serial)) }
    var gain by remember(serial) { mutableIntStateOf(ArcadeWheel.sensitivity(serial)) }
    var tilt by remember(serial) { mutableStateOf(ArcadeWheel.tiltSteering(serial)) }

    // Whatever this game was set to last time, back into the core. Cheap, and it is the only
    // thing that carries the numbers across a boot.
    LaunchedEffect(serial, driving) {
        if (driving) ArcadeWheel.apply(serial)
    }

    if (!driving) return

    SectionCard("Volante") {
        Column(Modifier.fillMaxWidth()) {
            WheelSlider(
                title = "Zona morta",
                reading = "$dead%",
                hint = "Quanto do centro é ignorado. Suba se o carro puxa sozinho com o analógico parado.",
                value = dead.toFloat(),
                range = 0f..40f,
            ) {
                dead = it.toInt()
                ArcadeWheel.setDeadzone(serial, dead)
            }
            Spacer(Modifier.height(10.dp))
            WheelSlider(
                title = "Sensibilidade",
                reading = "$gain%",
                hint = "Quanto de esterço vale o analógico no fim do curso.",
                value = gain.toFloat(),
                range = 50f..250f,
            ) {
                gain = it.toInt()
                ArcadeWheel.setSensitivity(serial, gain)
            }
            Spacer(Modifier.height(12.dp))
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text("Esterçar inclinando o celular", color = Color.White, fontSize = 15.sp)
                    Text(
                        "Usa os sensores do aparelho no lugar do analógico. A zona morta acima " +
                            "vale aqui também — nenhum celular fica perfeitamente reto na mão.",
                        color = Color(0xFF8B93A3), fontSize = 11.sp,
                    )
                }
                Switch(
                    checked = tilt,
                    onCheckedChange = {
                        tilt = it
                        ArcadeWheel.setTiltSteering(serial, it)
                    },
                )
            }
        }
    }
}

@Composable
private fun WheelSlider(
    title: String,
    reading: String,
    hint: String,
    value: Float,
    range: ClosedFloatingPointRange<Float>,
    onValue: (Float) -> Unit,
) {
    Column(Modifier.fillMaxWidth()) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text(title, color = Color.White, fontSize = 15.sp)
            Text(reading, color = Color(0xFF9AA3B2), fontSize = 13.sp)
        }
        Slider(value = value.coerceIn(range.start, range.endInclusive), onValueChange = onValue, valueRange = range)
        Text(hint, color = Color(0xFF8B93A3), fontSize = 11.sp)
    }
}
