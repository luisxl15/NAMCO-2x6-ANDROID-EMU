package com.armsx2

import android.content.Intent
import android.os.Bundle
import android.view.View
import android.view.animation.DecelerateInterpolator
import android.widget.ImageView
import androidx.activity.ComponentActivity
import androidx.activity.OnBackPressedCallback
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat

/**
 * Boot splash: the wordmark, fading up over a loading indicator, once per process, then Main.
 *
 * This used to play a bundled intro video of the emulator this was forked from — the wrong
 * branding, and an mp4 decode spun up at the slowest moment in the app's life, which is why it
 * needed error and timeout escapes to keep a bad codec from stranding the user on black. Two
 * views and a fade need none of that; the only timing left is how long it shows.
 *
 * Tapping still skips it, and it shows once per process, so coming back from a game does not
 * replay it.
 */
class BootSplashActivity : ComponentActivity() {
    private var launchedMain = false
    private var rootView: View? = null
    private val timeoutRunnable = Runnable { launchMainAndFinish() }

    override fun onCreate(savedInstanceState: Bundle?) {
        // The manifest theme (Theme.ARMSX2.Boot) already paints the window black,
        // matching the video's black FrameLayout — no per-theme override, so a
        // light-mode device never flashes white before the first decoded frame.
        super.onCreate(savedInstanceState)
        applyImmersiveUi()

        if (playedThisProcess) {
            launchMainAndFinish()
            return
        }
        playedThisProcess = true

        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() = launchMainAndFinish()
        })

        setContentView(R.layout.activity_boot_splash)
        rootView = findViewById(R.id.boot_splash_root)
        rootView?.apply {
            setOnClickListener { launchMainAndFinish() }
            postDelayed(timeoutRunnable, SPLASH_MS)
        }

        // Fade and settle, rather than a hard cut: the window is already black, so the mark
        // arriving is the only motion on screen and an instant appearance reads as a flicker.
        findViewById<ImageView?>(R.id.boot_splash_logo)?.apply {
            alpha = 0f
            scaleX = 0.94f
            scaleY = 0.94f
            animate()
                .alpha(1f)
                .scaleX(1f)
                .scaleY(1f)
                .setDuration(520L)
                .setInterpolator(DecelerateInterpolator())
                .start()
        }
    }

    override fun onDestroy() {
        rootView?.removeCallbacks(timeoutRunnable)
        super.onDestroy()
    }

    override fun onWindowFocusChanged(hasFocus: Boolean) {
        super.onWindowFocusChanged(hasFocus)
        if (hasFocus) applyImmersiveUi()
    }

    private fun applyImmersiveUi() {
        WindowCompat.setDecorFitsSystemWindows(window, false)
        WindowInsetsControllerCompat(window, window.decorView).apply {
            hide(WindowInsetsCompat.Type.systemBars())
            systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
        }
    }

    private fun launchMainAndFinish() {
        if (launchedMain) return
        launchedMain = true
        rootView?.removeCallbacks(timeoutRunnable)
        val launch = Intent(this, Main::class.java)
        intent?.let { source ->
            launch.action = source.action
            if (source.data != null || source.type != null) launch.setDataAndType(source.data, source.type)
            source.categories?.forEach(launch::addCategory)
            source.extras?.let(launch::putExtras)
            source.clipData?.let(launch::setClipData)
            launch.addFlags(
                source.flags and (
                    Intent.FLAG_GRANT_READ_URI_PERMISSION or
                        Intent.FLAG_GRANT_WRITE_URI_PERMISSION or
                        Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION or
                        Intent.FLAG_GRANT_PREFIX_URI_PERMISSION
                    ),
            )
        }
        launch.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP)
        startActivity(launch)
        finish()
        // overrideActivityTransition is API 34 (Android 14); on 13 and below it
        // throws NoSuchMethodError (crashed the splash on the Retroid). Fall back to
        // the deprecated overridePendingTransition there.
        if (android.os.Build.VERSION.SDK_INT >= 34) {
            overrideActivityTransition(OVERRIDE_TRANSITION_CLOSE, 0, 0)
        } else {
            @Suppress("DEPRECATION")
            overridePendingTransition(0, 0)
        }
    }

    private companion object {
        var playedThisProcess = false

        /** Long enough to read the mark, short enough that nobody waits on it. */
        const val SPLASH_MS = 1700L
    }
}
