package com.armsx2.diag

import android.app.ActivityManager
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.os.Build
import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.armsx2.BuildConfig
import com.armsx2.GpuInfo
import com.armsx2.config.ConfigStore
import kotlinx.coroutines.delay
import kr.co.iefriends.pcsx2.NativeApp
import java.util.Locale

/**
 * A performance report the player can take themselves.
 *
 * "It slows down on my phone" is not something this project can act on, and the figures that would
 * make it actionable -- which chip, which GPU driver, which renderer, and whether the EE, the GS
 * or the GPU is the one saturated -- are spread across the OSD, the settings screens and the
 * device's own About page. Asking someone to read all of that off and type it out is asking for
 * the half of it they happened to notice.
 *
 * So this samples the emulator while it is actually running -- not paused behind a menu, which is
 * where every other reading in the app is taken from -- and hands back one block of text to paste.
 *
 * It deliberately reports the WORST as well as the average. A slowdown that shows up for two
 * seconds in a fight is invisible in a ten-second mean, and that is exactly the shape of the
 * complaint this exists to answer.
 */
object PerfReport {

    private const val SAMPLE_MS = 250L

    /** Seconds left while sampling, 0 when not running. Drives the on-screen countdown. */
    var secondsLeft by mutableIntStateOf(0)
        private set

    /** The finished report, shown in a sheet until dismissed. */
    var result by mutableStateOf<String?>(null)
        private set

    // A counter, not a flag. The overlay's effect is keyed on this, and an effect must never
    // write its own key: clearing a flag from inside cancelled the very coroutine that cleared it,
    // which is exactly what "the coroutine scope left the composition" meant the first time.
    private var requestId by mutableIntStateOf(0)

    /** Ask for a run. The overlay picks this up once the game is running again. */
    fun start() {
        if (secondsLeft == 0 && result == null) requestId++
    }

    fun dismiss() {
        result = null
    }

    private class Track(val name: String, val unit: String, val worstIsLow: Boolean) {
        private var sum = 0.0
        private var n = 0
        private var worst = if (worstIsLow) Double.MAX_VALUE else -Double.MAX_VALUE

        fun add(v: Float) {
            if (v.isNaN() || v.isInfinite()) return
            sum += v
            n++
            worst = if (worstIsLow) minOf(worst, v.toDouble()) else maxOf(worst, v.toDouble())
        }

        fun line(): String {
            if (n == 0) return "  %-14s sem leitura".format(name)
            val avg = sum / n
            val label = if (worstIsLow) "min" else "max"
            return String.format(
                Locale.US,
                "  %-14s media %6.1f%s   %s %6.1f%s",
                name, avg, unit, label, worst, unit,
            )
        }
    }

    /**
     * Sample for [seconds], then build the text. Runs on the caller's coroutine; the caller is
     * responsible for the game actually being un-paused, which is the whole point.
     */
    private suspend fun collect(context: Context, seconds: Int): String {
        val speed = Track("Velocidade", "%", worstIsLow = true)
        val fps = Track("FPS", "", worstIsLow = true)
        val vps = Track("VPS", "", worstIsLow = true)
        val ee = Track("EE (CPU)", "%", worstIsLow = false)
        val gs = Track("GS", "%", worstIsLow = false)
        val gpu = Track("GPU", "%", worstIsLow = false)
        val frame = Track("Quadro", "ms", worstIsLow = false)

        val ticks = (seconds * 1000L / SAMPLE_MS).toInt()
        for (i in 0 until ticks) {
            secondsLeft = seconds - (i * SAMPLE_MS / 1000L).toInt()
            runCatching {
                speed.add(NativeApp.getEmuSpeedPercent())
                fps.add(NativeApp.getFPS())
                vps.add(NativeApp.getVPS())
                ee.add(NativeApp.getCpuThreadUsage())
                gs.add(NativeApp.getGsThreadUsage())
                gpu.add(NativeApp.getGpuUsage())
                frame.add(NativeApp.getAverageFrameTime())
            }
            delay(SAMPLE_MS)
        }
        secondsLeft = 0

        val s = runCatching { ConfigStore.loadGlobal() }.getOrNull()
        val am = runCatching {
            val info = ActivityManager.MemoryInfo()
            (context.getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager)
                .getMemoryInfo(info)
            info.totalMem / (1024 * 1024 * 1024.0)
        }.getOrNull()

        return buildString {
            appendLine("== Relatorio de desempenho ==")
            appendLine("Namco System 246 EMU ${BuildConfig.VERSION_NAME} (${Build.SUPPORTED_ABIS.firstOrNull().orEmpty()})")
            appendLine("Nucleo: " + runCatching { NativeApp.getBuildVersion() }.getOrDefault("?"))
            appendLine()
            appendLine("Aparelho: ${Build.MANUFACTURER} ${Build.MODEL}")
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                appendLine("SoC: ${Build.SOC_MANUFACTURER} ${Build.SOC_MODEL}")
            }
            appendLine("Android ${Build.VERSION.RELEASE} (API ${Build.VERSION.SDK_INT})")
            appendLine("GPU: " + (runCatching { GpuInfo.rendererName() }.getOrNull() ?: "?"))
            am?.let { appendLine(String.format(Locale.US, "RAM: %.1f GB", it)) }
            appendLine()
            appendLine("Jogo: " + runCatching { NativeApp.getPauseGameTitle() }.getOrDefault(""))
            appendLine("  serial " + runCatching { NativeApp.getGameSerial() }.getOrDefault("") +
                "   CRC " + runCatching { NativeApp.getGameCRC() }.getOrDefault(""))
            if (s != null) {
                appendLine("  renderizador ${s.renderer}   escala ${s.upscaleFloat}x")
            }
            appendLine()
            appendLine("Amostras: ${seconds}s a cada ${SAMPLE_MS}ms, com o jogo rodando")
            appendLine(speed.line())
            appendLine(fps.line())
            appendLine(vps.line())
            appendLine(ee.line())
            appendLine(gs.line())
            appendLine(gpu.line())
            appendLine(frame.line())
            appendLine()
            // Which figure is pinned says which side to look at, and it is the one thing a
            // player cannot be expected to read off a row of percentages.
            appendLine("Leitura: EE alto = emulacao do processador; GS alto = desenho;")
            appendLine("GPU alta = a placa do aparelho. Nenhum alto e velocidade baixa =")
            appendLine("nao e falta de forca bruta, e alguma coisa serializando.")
        }
    }

    /**
     * Mounted once, over the game. Draws nothing until asked for a run.
     *
     * It waits for the pause menu to be gone before it starts: every figure here is zero or stale
     * while the VM is paused, and the menu is where the run is requested from.
     */
    @Composable
    fun Overlay() {
        val context = LocalContext.current

        LaunchedEffect(requestId) {
            if (requestId == 0) return@LaunchedEffect
            // Wait for the pause menu to actually be gone, by polling rather than by keying this
            // effect on its visibility: a key change RESTARTS the effect, which cancelled the run
            // the moment the menu closed -- the one event it was waiting for.
            while (com.armsx2.ui.WindowImpl.overlayVisible.value) delay(100)
            result = try {
                collect(context, 10)
            } catch (e: kotlinx.coroutines.CancellationException) {
                secondsLeft = 0
                throw e // leaving the composition is not a failed measurement
            } catch (e: Throwable) {
                secondsLeft = 0
                "Nao foi possivel medir: ${e.message}"
            }
        }

        if (secondsLeft > 0) {
            Box(Modifier.fillMaxSize().padding(top = 46.dp), contentAlignment = Alignment.TopCenter) {
                Text(
                    "Medindo desempenho… ${secondsLeft}s",
                    color = Color.White,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Medium,
                    modifier = Modifier
                        .clip(RoundedCornerShape(50))
                        .background(Color(0xCC000000))
                        .padding(horizontal = 14.dp, vertical = 7.dp),
                )
            }
        }

        result?.let { text ->
            Box(
                Modifier
                    .fillMaxSize()
                    .background(Color(0xCC000000))
                    .clickable(enabled = false) {},
                contentAlignment = Alignment.Center,
            ) {
                Column(
                    Modifier
                        .fillMaxWidth(0.86f)
                        .clip(RoundedCornerShape(20.dp))
                        .background(Color(0xFF14161A))
                        .padding(20.dp),
                ) {
                    Text(
                        "Relatório de desempenho",
                        color = Color.White,
                        fontSize = 17.sp,
                        fontWeight = FontWeight.SemiBold,
                    )
                    Box(
                        Modifier
                            .padding(top = 12.dp)
                            .heightIn(max = 300.dp)
                            .verticalScroll(rememberScrollState()),
                    ) {
                        Text(
                            text,
                            color = Color(0xFFCFD6E4),
                            fontSize = 11.sp,
                            fontFamily = FontFamily.Monospace,
                        )
                    }
                    Row(
                        Modifier.fillMaxWidth().padding(top = 16.dp),
                        horizontalArrangement = Arrangement.End,
                    ) {
                        SheetButton("Copiar") {
                            runCatching {
                                val cm = context.getSystemService(Context.CLIPBOARD_SERVICE)
                                    as ClipboardManager
                                cm.setPrimaryClip(ClipData.newPlainText("perf", text))
                            }
                            Toast.makeText(context, "Relatório copiado", Toast.LENGTH_SHORT).show()
                        }
                        Box(Modifier.width(10.dp))
                        SheetButton("Fechar") { dismiss() }
                    }
                }
            }
        }
    }

    @Composable
    private fun SheetButton(label: String, onClick: () -> Unit) {
        Text(
            label,
            color = Color.White,
            fontSize = 14.sp,
            fontWeight = FontWeight.SemiBold,
            modifier = Modifier
                .clip(RoundedCornerShape(50))
                .background(Color(0x22FFFFFF))
                .clickable { onClick() }
                .padding(horizontal = 18.dp, vertical = 9.dp),
        )
    }
}
