package com.armsx2.diag

import android.app.ActivityManager
import android.content.Context
import android.os.Build
import android.os.Debug
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.material3.Text
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.armsx2.BuildConfig
import com.armsx2.GpuInfo
import com.armsx2.runtime.MainActivityRuntime
import kotlinx.coroutines.delay
import kr.co.iefriends.pcsx2.NativeApp
import java.util.Locale

/**
 * The on-screen performance monitor, in this project's own presentation.
 *
 * PCSX2's OSD is a line of abbreviations along the top edge -- "187 PRIM | 37 DRW | 0 BAR | 2 TC"
 * -- written for someone debugging the renderer, on a desktop, with room for it. On a phone held
 * in two hands it is a strip of jargon across the game.
 *
 * This says the same things as a panel: what the machine is, then the three numbers that answer
 * "is it running properly", then a graph, because a slowdown is a shape over time and no single
 * number shows one. Modelled on Apple's Game Porting Toolkit overlay, which is the clearest thing
 * of its kind: fixed-width columns, one accent colour, and the graph doing the work.
 *
 * It replaces the native OSD rather than sitting next to it -- two overlays saying the same
 * numbers in different words is worse than either alone -- so turning this on switches PCSX2's own
 * to Off, and turning it back off restores whichever mode was there before.
 */
object PerfMonitor {

    private const val KEY = "perf.monitor"
    private const val KEY_PREV_OSD = "perf.monitor.prevOsd"
    private const val SAMPLE_MS = 200L
    private const val HISTORY = 64

    /** Accent, kept to one colour: the graph and the figures that vary. */
    private val ACCENT = Color(0xFFFF8A3D)
    private val TEXT = Color(0xFFEAEEF5)
    private val DIM = Color(0xFF9AA3B2)

    /** Held as a MutableState rather than a `by` property: the same shape [Lightgun] uses, and it
     *  keeps the JVM name `setEnabled` free for the function below. */
    val enabled = mutableStateOf(false)

    fun load() {
        enabled.value =
            runCatching { MainActivityRuntime.prefs.getBoolean(KEY, false) }.getOrDefault(false)
    }

    fun setEnabled(on: Boolean) {
        if (on == enabled.value) return
        enabled.value = on
        runCatching { MainActivityRuntime.prefs.edit().putBoolean(KEY, on).apply() }
        val overlay = com.armsx2.ui.InGameOverlay
        if (on) {
            // Remember what PCSX2's own OSD was set to, so turning this off puts it back rather
            // than leaving the player with no stats at all and no idea why.
            runCatching {
                MainActivityRuntime.prefs.edit()
                    .putString(KEY_PREV_OSD, overlay.osdMode.value.name).apply()
            }
            overlay.setOsdMode(com.armsx2.ui.InGameOverlay.OsdMode.Off)
        } else {
            val prev = runCatching { MainActivityRuntime.prefs.getString(KEY_PREV_OSD, null) }
                .getOrNull()
            val mode = com.armsx2.ui.InGameOverlay.OsdMode.entries.firstOrNull { it.name == prev }
                ?: com.armsx2.ui.InGameOverlay.OsdMode.Custom
            overlay.setOsdMode(mode)
        }
    }

    private class Live {
        var fps = 0f
        var vps = 0f
        var speed = 0f
        var ee = 0f
        var gs = 0f
        var gpu = 0f
        var frameMs = 0f
        var appMb = 0
    }

    @Composable
    fun Overlay() {
        if (!enabled.value) return
        val context = LocalContext.current
        val live = remember { mutableStateOf(Live()) }
        val history = remember { mutableStateListOf<Float>() }
        val machine = remember { machineLines(context) }

        LaunchedEffect(Unit) {
            while (true) {
                val l = Live()
                runCatching {
                    l.fps = NativeApp.getFPS()
                    l.vps = NativeApp.getVPS()
                    l.speed = NativeApp.getEmuSpeedPercent()
                    l.ee = NativeApp.getCpuThreadUsage()
                    l.gs = NativeApp.getGsThreadUsage()
                    l.gpu = NativeApp.getGpuUsage()
                    l.frameMs = NativeApp.getAverageFrameTime()
                }
                l.appMb = runCatching {
                    val mi = Debug.MemoryInfo()
                    Debug.getMemoryInfo(mi)
                    mi.totalPss / 1024
                }.getOrDefault(0)
                live.value = l
                history.add(l.frameMs)
                while (history.size > HISTORY) history.removeAt(0)
                delay(SAMPLE_MS)
            }
        }

        val l = live.value
        Box(Modifier.fillMaxSize().padding(10.dp), contentAlignment = Alignment.TopStart) {
            Column(
                Modifier
                    .width(224.dp)
                    .clip(RoundedCornerShape(10.dp))
                    .background(Color(0xD90B0D11))
                    .padding(horizontal = 12.dp, vertical = 10.dp),
            ) {
                machine.forEach { (left, right) -> Line(left, right, accentRight = true) }

                Spacer(Modifier.height(9.dp))
                Line("FPS", fmt(l.fps, 2))
                Line("VPS", fmt(l.vps, 2))
                Line("Quadro", fmt(l.frameMs, 2) + "ms")

                Spacer(Modifier.height(6.dp))
                Graph(history)

                Spacer(Modifier.height(9.dp))
                Text(
                    "Namco System 246 EMU ${BuildConfig.VERSION_NAME}",
                    color = DIM, fontSize = 9.sp, fontFamily = FontFamily.Monospace,
                )
                Spacer(Modifier.height(5.dp))
                Line("Velocidade", pct(l.speed))
                Line("EE", pct(l.ee))
                Line("GS", pct(l.gs))
                Line("GPU", pct(l.gpu))
                Line("App", "${l.appMb}MB")
            }
        }
    }

    @Composable
    private fun Line(label: String, value: String, accentRight: Boolean = false) {
        Row(Modifier.fillMaxWidth()) {
            Text(
                label,
                color = TEXT,
                fontSize = 10.sp,
                fontFamily = FontFamily.Monospace,
                modifier = Modifier.weight(1f),
            )
            Text(
                value,
                color = if (accentRight) ACCENT else TEXT,
                fontSize = 10.sp,
                fontWeight = FontWeight.Medium,
                fontFamily = FontFamily.Monospace,
            )
        }
    }

    /**
     * Frame time over the last few seconds.
     *
     * Scaled to the worst sample in view rather than to a fixed ceiling: the interesting thing is
     * the SHAPE -- flat, or spiking -- and a fixed scale flattens the spikes on a fast device and
     * clips them on a slow one. The 16.7ms line is drawn as the reference, so the height still
     * means something absolute.
     */
    @Composable
    private fun Graph(history: List<Float>) {
        Canvas(
            Modifier
                .fillMaxWidth()
                .height(38.dp)
                .clip(RoundedCornerShape(3.dp))
                .background(Color(0x14FFFFFF)),
        ) {
            if (history.size < 2) return@Canvas
            val peak = maxOf(history.max(), 20f)
            val stepX = size.width / (HISTORY - 1).toFloat()

            // Grid: the 16.7ms (60fps) reference, plus its double.
            listOf(16.7f, 33.3f).forEach { ms ->
                if (ms > peak) return@forEach
                val y = size.height - (ms / peak) * size.height
                drawLine(Color(0x22FFFFFF), Offset(0f, y), Offset(size.width, y), strokeWidth = 1f)
            }

            var prev: Offset? = null
            history.forEachIndexed { i, v ->
                val x = i * stepX
                val y = size.height - (v.coerceIn(0f, peak) / peak) * size.height
                val p = Offset(x, y)
                prev?.let { drawLine(ACCENT, it, p, strokeWidth = 1.6f) }
                prev = p
            }
        }
    }

    private fun fmt(v: Float, dp: Int) =
        if (v <= 0f || v.isNaN()) "—" else String.format(Locale.US, "%.${dp}f", v)

    private fun pct(v: Float) =
        if (v.isNaN()) "—" else String.format(Locale.US, "%.0f%%", v)

    /** What the machine is: fixed for the session, so read once. */
    private fun machineLines(context: Context): List<Pair<String, String>> {
        val out = mutableListOf<Pair<String, String>>()
        val soc = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            Build.SOC_MODEL.takeIf { it.isNotBlank() && it != "unknown" }
        } else {
            null
        }
        out += (soc ?: Build.MODEL) to (Build.SUPPORTED_ABIS.firstOrNull().orEmpty())
        out += "Android ${Build.VERSION.RELEASE}" to
            (runCatching { GpuInfo.rendererName() }.getOrNull()?.let { shortGpu(it) } ?: "?")
        val totalGb = runCatching {
            val info = ActivityManager.MemoryInfo()
            (context.getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager).getMemoryInfo(info)
            info.totalMem / (1024.0 * 1024.0 * 1024.0)
        }.getOrNull()
        if (totalGb != null) out += "RAM" to String.format(Locale.US, "%.1fGB", totalGb)
        return out
    }

    /** "Adreno (TM) 740" is the whole width of the panel; "Adreno 740" is the same fact. */
    private fun shortGpu(name: String) = name.replace("(TM)", "").replace("  ", " ").trim()
}
