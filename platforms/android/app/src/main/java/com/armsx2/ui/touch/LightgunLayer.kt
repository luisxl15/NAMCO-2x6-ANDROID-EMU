package com.armsx2.ui.touch

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.PointerId
import androidx.compose.ui.input.pointer.changedToDown
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.res.painterResource
import androidx.compose.material3.Icon
import com.armsx2.input.Lightgun
import com.armsx2.ui.premium.Arc
import kotlinx.coroutines.delay
import kr.co.iefriends.pcsx2.NativeApp

/**
 * Touchscreen aiming for the GunCon 2.
 *
 * Full-screen and it DOES consume its pointers, unlike [GestureLayer] — when the lightgun is on,
 * a touch on empty screen area IS the gun, so there is nothing else for it to belong to. It still
 * ignores a DOWN some widget already claimed, so the on-screen gun buttons and the pause button
 * keep working.
 *
 * Aim tracks the finger continuously rather than only on tap: pointing and then firing is how
 * these games are played, and it lets you lead a target before pulling the trigger.
 */
@Composable
fun LightgunLayer(widthPx: Float, heightPx: Float) {
    // Arcade gun games turn this on by themselves -- see Lightgun.arcade. Polled because ACJV is
    // not live until the game has booted, and stops being live when the VM does.
    LaunchedEffect(Unit) {
        while (true) {
            Lightgun.refreshArcade()
            delay(1000)
        }
    }
    if (!Lightgun.active) return
    if (widthPx <= 0f || heightPx <= 0f) return

    Box(
        Modifier
            .fillMaxSize()
            .pointerInput(widthPx, heightPx) {
                var aiming: PointerId? = null
                awaitPointerEventScope {
                    while (true) {
                        val ev = awaitPointerEvent()
                        for (ch in ev.changes) {
                            if (ch.changedToDown()) {
                                // A widget above us already owns this finger (gun buttons, pause).
                                if (ch.isConsumed || aiming != null) continue
                                aiming = ch.id
                                Lightgun.aim(ch.position.x, ch.position.y)
                                // Aim BEFORE the trigger, in that order: the core samples the
                                // pointer when the trigger goes down, so firing first would shoot
                                // at wherever the previous shot landed.
                                Lightgun.trigger(true, ch.position.x, ch.position.y, widthPx, heightPx)
                                ch.consume()
                                continue
                            }
                            if (ch.id != aiming) continue
                            if (ch.pressed) {
                                Lightgun.aim(ch.position.x, ch.position.y)
                                ch.consume()
                            } else {
                                Lightgun.trigger(false, ch.position.x, ch.position.y, widthPx, heightPx)
                                aiming = null
                                ch.consume()
                            }
                        }
                    }
                }
            },
    )
}

/**
 * The gun's own buttons, down the right edge — A/B/C plus Start, Select and Recalibrate.
 *
 * Composed ABOVE [LightgunLayer] so pressing one is a button press, not a shot at that part of the
 * screen. Recalibrate is included because several of these games open with a calibration step and
 * there is otherwise no way to satisfy it.
 */
@Composable
fun LightgunButtons() {
    if (!Lightgun.active) return
    Box(Modifier.fillMaxSize()) {
        Column(
            Modifier.align(Alignment.CenterEnd).padding(end = 10.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            if (Lightgun.arcade.value) {
                // A cabinet has no A/B/C and no calibration step -- it has a trigger, a reload,
                // and the start button on the panel. Reload gets an icon rather than a word: the
                // touch-an-edge gesture works too, but nothing on screen would have said so.
                GunIconButton(Arc.reset) { down ->
                    Lightgun.arcadeButton(NativeApp.JVS_GUN_RELOAD, down)
                }
                GunButton("START", wide = true) { down ->
                    Lightgun.arcadeButton(NativeApp.JVS_GUN_START, down)
                }
            } else {
                Lightgun.overlayButtons().forEach { (bind, label) ->
                    GunButton(label) { down -> Lightgun.button(bind, down) }
                }
            }
        }
    }
}

@Composable
private fun GunButton(label: String, wide: Boolean = false, onPress: (Boolean) -> Unit) {
    val opacity = TouchControls.opacity.floatValue
    val shape = if (wide) RoundedCornerShape(23.dp) else CircleShape
    Box(
        Modifier
            .then(if (wide) Modifier.width(74.dp).height(46.dp) else Modifier.size(46.dp))
            .background(Color(0x33000000).copy(alpha = 0.35f * opacity), shape)
            .border(1.dp, Color.White.copy(alpha = 0.45f * opacity), shape)
            .pointerInput(label) {
                awaitPointerEventScope {
                    while (true) {
                        val ev = awaitPointerEvent()
                        val ch = ev.changes.firstOrNull { it.changedToDown() }
                        if (ch != null) {
                            onPress(true)
                            TouchControls.noteTouchInteraction()
                            // Consume so the aim layer beneath does not also read this as a shot.
                            ch.consume()
                            val id = ch.id
                            // Follow this pointer by id until it lifts, so sliding off the button
                            // still releases it.
                            while (true) {
                                val next = awaitPointerEvent()
                                val nc = next.changes.firstOrNull { it.id == id }
                                nc?.consume()
                                if (nc == null || !nc.pressed) break
                            }
                            onPress(false)
                        }
                    }
                }
            },
        contentAlignment = Alignment.Center,
    ) {
        Text(
            label,
            color = Color.White.copy(alpha = opacity),
            fontSize = if (label.length > 3) 10.sp else 15.sp,
            fontWeight = FontWeight.Bold,
        )
    }
}

/** Same button, carrying an icon — for a control whose name would not fit or would not travel. */
@Composable
private fun GunIconButton(@androidx.annotation.DrawableRes icon: Int, onPress: (Boolean) -> Unit) {
    val opacity = TouchControls.opacity.floatValue
    Box(
        Modifier
            .size(46.dp)
            .background(Color(0x33000000).copy(alpha = 0.35f * opacity), CircleShape)
            .border(1.dp, Color.White.copy(alpha = 0.45f * opacity), CircleShape)
            .pointerInput(icon) {
                awaitPointerEventScope {
                    while (true) {
                        val ev = awaitPointerEvent()
                        val ch = ev.changes.firstOrNull { it.changedToDown() }
                        if (ch != null) {
                            onPress(true)
                            TouchControls.noteTouchInteraction()
                            ch.consume()
                            val id = ch.id
                            while (true) {
                                val next = awaitPointerEvent()
                                val nc = next.changes.firstOrNull { it.id == id }
                                nc?.consume()
                                if (nc == null || !nc.pressed) break
                            }
                            onPress(false)
                        }
                    }
                }
            },
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            painterResource(icon),
            contentDescription = null,
            tint = Color.White.copy(alpha = opacity),
            modifier = Modifier.size(22.dp),
        )
    }
}
