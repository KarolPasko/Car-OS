package com.nextgen.caros

import android.content.Intent
import android.os.Bundle
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.lifecycle.lifecycleScope
import com.nextgen.caros.ui.shell.NextGenRoot
import com.nextgen.caros.ui.shell.Notifier
import com.nextgen.caros.ui.theme.NextGenTheme
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        WindowCompat.setDecorFitsSystemWindows(window, false)
        immersive()

        val c = appContainer
        lifecycleScope.launch {
            c.settings.settings.collect { s ->
                if (s.keepScreenOn) window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
                else window.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
            }
        }
        lifecycleScope.launch {
            c.system.state.collect { st ->
                window.attributes = window.attributes.apply {
                    screenBrightness = st.brightness ?: WindowManager.LayoutParams.BRIGHTNESS_OVERRIDE_NONE
                }
            }
        }
        handleIntent(intent)

        setContent {
            val settings by c.settings.settings.collectAsState()
            NextGenTheme(settings) { NextGenRoot() }
        }
    }

    /** Full-screen, like a native head-unit UI; bars can still be revealed with a swipe. */
    private fun immersive() {
        WindowInsetsControllerCompat(window, window.decorView).apply {
            hide(WindowInsetsCompat.Type.systemBars())
            systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
        }
    }

    override fun onWindowFocusChanged(hasFocus: Boolean) {
        super.onWindowFocusChanged(hasFocus)
        if (hasFocus) immersive()
    }

    override fun onResume() {
        super.onResume()
        val c = appContainer
        c.location.start()
        c.media.refreshAccess()
        c.bluetooth.start()
        c.spotify.refreshFlags()
        c.system.refresh()
        if (c.phoneLink.state.value.status == com.nextgen.caros.link.LinkStatus.NO_PERMISSION ||
            c.phoneLink.state.value.status == com.nextgen.caros.link.LinkStatus.BT_OFF) c.phoneLink.restart()
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        handleIntent(intent)
    }

    private fun handleIntent(intent: Intent?) {
        val data = intent?.data ?: return
        if (data.scheme == "nextgencaros" && data.host == "spotify-callback") {
            appContainer.spotify.handleRedirect(data)
            Notifier.show("Connecting to Spotify…")
        }
    }
}
