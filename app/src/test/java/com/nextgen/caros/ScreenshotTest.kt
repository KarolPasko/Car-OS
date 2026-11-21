package com.nextgen.caros

import android.Manifest
import android.content.Context
import android.graphics.Bitmap
import android.location.Location
import android.location.LocationManager
import android.os.SystemClock
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onRoot
import androidx.test.core.app.ApplicationProvider
import com.nextgen.caros.cameras.Verification
import com.nextgen.caros.data.ThemeMode
import com.nextgen.caros.ui.maps.LocalMapRendering
import com.nextgen.caros.ui.settings.OnboardingScreen
import com.nextgen.caros.ui.settings.SettingsSection
import com.nextgen.caros.ui.shell.AppShell
import com.nextgen.caros.ui.shell.Screen
import com.nextgen.caros.ui.shell.Wallpaper
import com.nextgen.caros.ui.theme.NextGenTheme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.io.File
import java.io.FileOutputStream

/**
 * Renders real screens on the JVM at common head-unit resolutions and writes PNGs to
 * app/build/screenshots. The map itself is replaced by a placeholder (MapLibre is native code).
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(application = TestApp::class, qualifiers = "w1024dp-h600dp-land-mdpi")
class ScreenshotTest {

    @get:Rule val compose = createAndroidComposeRule<androidx.activity.ComponentActivity>()

    private val outDir = File("build/screenshots").apply { mkdirs() }

    private fun feedLocation(speedMps: Float = 13.4f) {
        val app = ApplicationProvider.getApplicationContext<android.app.Application>()
        shadowOf(app).grantPermissions(Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION)
        appContainer.location.start()
        val lm = app.getSystemService(Context.LOCATION_SERVICE) as LocationManager
        shadowOf(lm).setProviderEnabled(LocationManager.GPS_PROVIDER, true)
        shadowOf(lm).simulateLocation(Location(LocationManager.GPS_PROVIDER).apply {
            latitude = 51.5007; longitude = -0.1246; speed = speedMps; bearing = 90f; accuracy = 5f
            time = System.currentTimeMillis(); elapsedRealtimeNanos = SystemClock.elapsedRealtimeNanos()
        })
    }

    private fun shoot(name: String, theme: ThemeMode = ThemeMode.DARK, content: @Composable () -> Unit) {
        // The UI has intentional never-ending animations (clock, visualiser), so drive time manually.
        compose.mainClock.autoAdvance = false
        compose.setContent {
            val s by appContainer.settings.settings.collectAsState()
            CompositionLocalProvider(LocalMapRendering provides false) {
                NextGenTheme(s.copy(themeMode = theme)) {
                    androidx.compose.foundation.layout.Box {
                        Wallpaper()
                        content()
                    }
                }
            }
        }
        repeat(25) { compose.mainClock.advanceTimeBy(100); Thread.sleep(20) }
        val view = compose.activity.window.decorView
        val bmp = Bitmap.createBitmap(view.width, view.height, Bitmap.Config.ARGB_8888)
        compose.runOnUiThread { view.draw(android.graphics.Canvas(bmp)) }
        FileOutputStream(File(outDir, "$name.png")).use { bmp.compress(Bitmap.CompressFormat.PNG, 100, it) }
    }

    @Test fun home() { feedLocation(); shoot("01-home") { AppShell(Screen.HOME) } }
    @Test fun homeLight() { feedLocation(); shoot("02-home-light", ThemeMode.LIGHT) { AppShell(Screen.HOME) } }
    @Test fun maps() { feedLocation(); shoot("03-maps") { AppShell(Screen.MAPS) } }
    @Test fun music() { shoot("04-music") { AppShell(Screen.MUSIC) } }
    @Test fun apps() { shoot("05-apps") { AppShell(Screen.APPS) } }
    @Test fun settingsCameras() { shoot("06-settings-cameras") { AppShell(Screen.SETTINGS, SettingsSection.CAMERAS) } }
    @Test fun settingsAppearance() { shoot("07-settings-appearance") { AppShell(Screen.SETTINGS, SettingsSection.APPEARANCE) } }
    @Test fun settingsMusic() { shoot("08-settings-music") { AppShell(Screen.SETTINGS, SettingsSection.MUSIC) } }
    @Test fun onboarding() { shoot("09-onboarding") { OnboardingScreen {} } }

    @Config(qualifiers = "w1280dp-h480dp-land-mdpi")
    @Test fun homeUltraWide() { feedLocation(); shoot("10-home-1280x480") { AppShell(Screen.HOME) } }

    @Config(qualifiers = "w1920dp-h720dp-land-mdpi")
    @Test fun homeWide() { feedLocation(); shoot("11-home-1920x720") { AppShell(Screen.HOME) } }

    @Test fun csvImportParsing() {
        val csv = "latitude;longitude;type;maxspeed;direction\n51.5;-0.12;fixed;30 mph;90\n# comment\n51.6;-0.13;red light;;\nbad;row;;;\n"
        val list = appContainer.cameras.parseCsv(csv, "Test", Verification.VERIFIED)
        assertEquals(2, list.size)
        assertEquals("30 mph", list[0].maxspeed)
        assertEquals(90.0, list[0].direction!!, 0.0)
        assertEquals(com.nextgen.caros.cameras.CameraType.RED_LIGHT, list[1].type)
    }
}
