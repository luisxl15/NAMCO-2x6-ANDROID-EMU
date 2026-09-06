package com.armsx2.ui.emulation

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Switch
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.armsx2.input.AndroidGyroscopeInput
import com.armsx2.input.Lightgun
import com.armsx2.input.LightgunAim
import com.armsx2.runtime.MainActivityRuntime
import kotlinx.coroutines.delay

/**
 * The gun, for the games that have one.
 *
 * One switch and one button, in the pause menu and only while a gun game runs — the same rule the
 * patch list and the wheel follow. Aiming by pointing is the kind of thing nobody finds in a
 * settings screen and everybody finds here, one tap from the game it changes.
 */
@Composable
fun ArcadeGunSection() {
    val context = LocalContext.current
    var gun by remember { mutableStateOf(false) }
    var aiming by remember { mutableStateOf(LightgunAim.enabled.value) }
    // What the device can actually do. A phone with no gyroscope aims by tilt, which is a
    // different feel and worth saying out loud rather than letting the player wonder why the
    // crosshair snaps back when they hold still.
    val kind = remember { AndroidGyroscopeInput.resolveKind(context, 1) }

    LaunchedEffect(Unit) {
        while (true) {
            Lightgun.refreshArcade()
            gun = Lightgun.active
            delay(1000)
        }
    }

    if (!gun) return

    SectionCard("Arma") {
        Column(Modifier.fillMaxWidth()) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text("Mirar apontando o aparelho", color = Color.White, fontSize = 15.sp)
                    Text(
                        when (kind) {
                            AndroidGyroscopeInput.KIND_NONE ->
                                "Este aparelho não tem sensor para isso."
                            AndroidGyroscopeInput.KIND_GYRO ->
                                "O toque vira só o gatilho: a mira segue o giroscópio e o dedo " +
                                    "para de tapar o alvo."
                            else ->
                                "Sem giroscópio aqui, então a mira segue a inclinação — ela volta " +
                                    "ao centro quando o aparelho volta a ficar reto."
                        },
                        color = Color(0xFF8B93A3), fontSize = 11.sp,
                    )
                }
                Switch(
                    checked = aiming,
                    enabled = kind != AndroidGyroscopeInput.KIND_NONE,
                    onCheckedChange = {
                        aiming = it
                        LightgunAim.setEnabled(it)
                    },
                )
            }
            if (aiming) {
                Spacer(Modifier.height(10.dp))
                Text(
                    "Centralizar a mira",
                    color = Color.White,
                    fontSize = 13.sp,
                    modifier = Modifier
                        .clip(RoundedCornerShape(50))
                        .background(Color(0x22FFFFFF))
                        .clickable {
                            // The hook the aim layer publishes re-zeroes the SENSOR as well as
                            // the crosshair; calling only the second would leave the phone's
                            // idea of level where it was and the crosshair would slide straight
                            // back off centre.
                            LightgunAim.recenter()
                            MainActivityRuntime.gyroRecenterHook?.invoke()
                        }
                        .padding(horizontal = 14.dp, vertical = 8.dp),
                )
                Spacer(Modifier.height(6.dp))
                Text(
                    "Segure o aparelho como quer segurá-lo e toque aqui. Sensibilidade e inversão " +
                        "dos eixos ficam nas configurações de controle.",
                    color = Color(0xFF8B93A3), fontSize = 11.sp,
                )
            }
        }
    }
}
