package com.armsx2.ui.emulation

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.armsx2.input.ArcadeCredits
import kotlinx.coroutines.delay
import kr.co.iefriends.pcsx2.NativeApp

/**
 * Quem coloca a ficha.
 *
 * Off by default and asked for one game at a time, because the coin is part of what the machine
 * is — a cabinet that starts itself is not the same object. The switch is here for the tenth
 * launch of the same game, not as the way the app behaves out of the box.
 *
 * The wording is careful on purpose: this is not the board's free play, which lives in the TEST
 * menu and is per game. The board goes on counting coins; the app just drops them in.
 */
@Composable
fun ArcadeCreditsSection(serial: String?) {
    var isArcade by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) {
        while (true) {
            isArcade = runCatching { NativeApp.jvsIsArcade() }.getOrDefault(false)
            delay(1000)
        }
    }

    var on by remember(serial) { mutableStateOf(ArcadeCredits.enabled(serial)) }
    var coins by remember(serial) { mutableIntStateOf(ArcadeCredits.count(serial)) }

    if (!isArcade) return

    SectionCard("Fichas") {
        Column(Modifier.fillMaxWidth()) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text("Colocar ficha sozinho", color = Color.White, fontSize = 15.sp)
                    Text(
                        "Desligado, você insere a ficha como num gabinete de verdade. Ligado, o " +
                            "app insere assim que a placa sobe — a placa continua contando " +
                            "moeda igual; o free play mesmo fica no menu TEST do jogo.",
                        color = Color(0xFF8B93A3), fontSize = 11.sp,
                    )
                }
                Switch(
                    checked = on,
                    onCheckedChange = {
                        on = it
                        ArcadeCredits.setEnabled(serial, it)
                    },
                )
            }
            if (on) {
                Spacer(Modifier.height(12.dp))
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text("Quantas fichas", color = Color.White, fontSize = 15.sp)
                        Text(
                            "Quantos créditos entram no início. Vale só para este jogo.",
                            color = Color(0xFF8B93A3), fontSize = 11.sp,
                        )
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        CoinStep("−", enabled = coins > 1) {
                            coins -= 1
                            ArcadeCredits.setCount(serial, coins)
                        }
                        Text(
                            "$coins",
                            color = Color.White,
                            fontSize = 16.sp,
                            fontWeight = FontWeight.SemiBold,
                            modifier = Modifier.padding(horizontal = 4.dp),
                        )
                        CoinStep("+", enabled = coins < 9) {
                            coins += 1
                            ArcadeCredits.setCount(serial, coins)
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun CoinStep(label: String, enabled: Boolean, onClick: () -> Unit) {
    Box(
        Modifier
            .size(34.dp)
            .background(Color(0x1FFFFFFF), RoundedCornerShape(10.dp))
            .then(if (enabled) Modifier.clickable(onClick = onClick) else Modifier),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            label,
            color = if (enabled) Color.White else Color(0x4DFFFFFF),
            fontSize = 17.sp,
            fontWeight = FontWeight.SemiBold,
        )
    }
}
