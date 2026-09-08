package com.armsx2.ui.settings

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.text.KeyboardOptions
import com.armsx2.config.Settings
import com.armsx2.data.library.LanUpload
import com.armsx2.ui.InGameOverlay
import kotlinx.coroutines.delay
import kr.co.iefriends.pcsx2.NativeApp

private const val LOCAL_LINK = "Local Link"

/**
 * Dois aparelhos, um cabo — o cabo sendo o Wi-Fi da casa.
 *
 * A cabinet that links to another cabinet does it over its own Ethernet port, and the board's
 * Ethernet is emulated: this app already carries a layer-2 tunnel that carries those frames
 * between devices over UDP ([LocalLinkAdapter] in the core). Every piece of it was here — the
 * adapter, the settings, the reader and writer — and none of it had a single row on screen, so
 * from inside the app the feature did not exist.
 *
 * What this is NOT, said plainly because the difference costs people hours: it is not netplay.
 * Each device runs its own machine, at its own speed, and the two only see each other the way two
 * cabinets on a bench would. A game with no link mode of its own gains nothing from it.
 *
 * The numbers are per install, not per game — the address of the other phone is a property of the
 * room you are in, not of the game you chose.
 */
@Composable
fun LocalLinkSection(state: MutableState<Settings>) {
    val s = state.value
    fun apply(updated: Settings) = InGameOverlay.saveSettings(updated)

    val on = s.dev9EthEnable && s.dev9EthApi == LOCAL_LINK

    CollapsibleSection("Link entre gabinetes") {
        HelpText(
            "Liga dois aparelhos na mesma Wi-Fi como se fossem dois gabinetes ligados pelo cabo " +
                "de rede. Cada um roda o próprio jogo; isto não junta as duas telas nem sincroniza " +
                "nada por conta própria — só vale para jogo que já tinha link de fábrica.",
            modifier = Modifier.padding(horizontal = 6.dp),
        )
        SettingsDivider()
        ToggleRow(
            "Ligar o link",
            on,
            description = "Liga a placa de rede da placa arcade e a aponta para o outro aparelho.",
        ) { enable ->
            apply(
                s.copy(
                    dev9EthEnable = enable,
                    // Sair do link devolve o adaptador ao que serve para internet, em vez de
                    // deixar "Local Link" escolhido com a rede desligada.
                    dev9EthApi = if (enable) LOCAL_LINK else "Sockets",
                ),
            )
        }

        if (on) {
            SettingsDivider()
            SegmentedRow(
                "Este aparelho",
                listOf("Anfitrião", "Convidado"),
                if (s.localLinkHost) 0 else 1,
                description = "Um anfitrião por sala. Ele repassa os quadros para todos os outros.",
            ) { index ->
                val host = index == 0
                apply(
                    s.copy(
                        localLinkHost = host,
                        // O anfitrião é sempre o 1; convidado nunca pode ser, porque é o número
                        // que deriva o MAC e o IP emulados e dois iguais se atropelam.
                        localLinkPeerId = if (host) 1 else s.localLinkPeerId.coerceAtLeast(2),
                    ),
                )
            }

            SettingsDivider()
            if (s.localLinkHost) {
                HostAddresses()
            } else {
                LinkTextRow(
                    label = "Endereço do anfitrião",
                    hint = "O IP que aparece no outro aparelho, por exemplo 192.168.0.12",
                    value = s.localLinkAddress,
                    numeric = true,
                ) { apply(s.copy(localLinkAddress = it.trim())) }
            }

            SettingsDivider()
            LinkTextRow(
                label = "Código da sala",
                hint = "O mesmo texto nos dois aparelhos. Evita que duas salas na mesma Wi-Fi se " +
                    "misturem — não é segurança de verdade.",
                value = s.localLinkRoomCode,
            ) { apply(s.copy(localLinkRoomCode = it.trim())) }

            if (!s.localLinkHost) {
                SettingsDivider()
                IntSliderRow(
                    "Número deste convidado",
                    s.localLinkPeerId.coerceIn(2, 8),
                    min = 2,
                    max = 8,
                    description = "Precisa ser diferente em cada convidado da sala.",
                ) { apply(s.copy(localLinkPeerId = it)) }
            }

            SettingsDivider()
            LinkTraffic()

            SettingsDivider()
            IntSliderRow(
                "Porta",
                s.localLinkPort.coerceIn(1024, 65535),
                min = 1024,
                max = 65535,
                description = "Igual nos dois. Só mude se algo mais já usar esta.",
            ) { apply(s.copy(localLinkPort = it)) }
        }
    }
}

/**
 * O que o túnel carregou até agora.
 *
 * Sem isto a feature é impossível de depurar: dois celulares, um jogo que não mostra nada, e
 * nenhuma forma de saber se o link caiu, se o código da sala está diferente ou se o jogo
 * simplesmente nunca liga para ninguém. Três números respondem as três perguntas.
 */
@Composable
private fun LinkTraffic() {
    var packed by remember { mutableStateOf(0L) }
    LaunchedEffect(Unit) {
        while (true) {
            packed = runCatching { NativeApp.localLinkStats() }.getOrDefault(0L)
            delay(1000)
        }
    }
    val peers = ((packed shr 44) and 0xFFF).toInt()
    val sent = (packed shr 22) and 0x3FFFFF
    val received = packed and 0x3FFFFF

    Column(Modifier.fillMaxWidth().padding(horizontal = 6.dp, vertical = 10.dp)) {
        Text("Tráfego", style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold)
        Spacer(Modifier.height(4.dp))
        Text(
            "$sent quadros enviados · $received recebidos · $peers aparelho(s) na sala",
            style = MaterialTheme.typography.titleSmall,
        )
        Spacer(Modifier.height(4.dp))
        Text(
            when {
                peers > 0 && received > 0 -> "Os dois lados estão se falando."
                peers > 0 -> "O outro aparelho respondeu, mas ainda não veio quadro nenhum dele."
                sent > 0 -> "Este aparelho está falando sozinho: confira endereço, porta e código."
                else -> "Nada ainda. Os números só andam com um jogo aberto que use rede."
            },
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

/**
 * O endereço que o convidado precisa digitar.
 *
 * Mostrado aqui em vez de mandar o jogador procurar nas configurações do Android, e pela mesma
 * lista que o envio pelo PC já usa — inclusive a ordem, que prefere o Wi-Fi: um celular pode ter
 * vários endereços ao mesmo tempo e o primeiro que o sistema lista não é necessariamente o da
 * rede em que o outro aparelho está.
 */
@Composable
private fun HostAddresses() {
    val addresses = remember { LanUpload.localAddresses() }
    Column(Modifier.fillMaxWidth().padding(horizontal = 6.dp, vertical = 10.dp)) {
        Text(
            "Endereço deste aparelho",
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.SemiBold,
        )
        Spacer(Modifier.height(4.dp))
        if (addresses.isEmpty()) {
            Text(
                "Sem rede local. Ligue a Wi-Fi (ou o ponto de acesso) antes de hospedar.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.error,
            )
        } else {
            addresses.forEach {
                Text(it, style = MaterialTheme.typography.titleMedium)
            }
            Spacer(Modifier.height(4.dp))
            Text(
                "Digite este endereço no outro aparelho, em Convidado.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

/** Uma linha de texto livre, que os widgets de configuração não tinham. */
@Composable
private fun LinkTextRow(
    label: String,
    hint: String,
    value: String,
    numeric: Boolean = false,
    onChange: (String) -> Unit,
) {
    // Editado localmente e escrito a cada tecla: gravar no fim da edição exigiria um evento de
    // "terminei" que um teclado de celular não dá de forma confiável.
    var text by remember(value) { mutableStateOf(value) }
    Column(Modifier.fillMaxWidth().padding(horizontal = 6.dp, vertical = 8.dp)) {
        Text(label, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold)
        Spacer(Modifier.height(6.dp))
        OutlinedTextField(
            value = text,
            onValueChange = {
                text = it
                onChange(it)
            },
            singleLine = true,
            keyboardOptions = KeyboardOptions(
                capitalization = KeyboardCapitalization.None,
                keyboardType = if (numeric) {
                    androidx.compose.ui.text.input.KeyboardType.Number
                } else {
                    androidx.compose.ui.text.input.KeyboardType.Text
                },
            ),
            modifier = Modifier.fillMaxWidth(),
        )
        Spacer(Modifier.height(4.dp))
        Text(
            hint,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}
