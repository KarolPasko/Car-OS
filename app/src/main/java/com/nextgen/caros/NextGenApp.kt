package com.nextgen.caros

import android.app.Application
import com.nextgen.caros.apps.AppsRepository
import com.nextgen.caros.cameras.CameraRepository
import com.nextgen.caros.data.SettingsRepository
import com.nextgen.caros.location.LocationRepository
import com.nextgen.caros.media.MediaRepository
import com.nextgen.caros.nav.NavigationManager
import com.nextgen.caros.speedlimit.SpeedLimitRepository
import com.nextgen.caros.spotify.SpotifyRepository
import com.nextgen.caros.system.BluetoothRepository
import com.nextgen.caros.weather.WeatherRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.Dispatchers
import org.maplibre.android.MapLibre

/**
 * Simple manual dependency container. Every repository is a process-wide singleton so the
 * dashboard, map, alerts and the foreground navigation service all observe the same state.
 */
open class NextGenApp : Application() {

    lateinit var container: AppContainer
        private set

    override fun onCreate() {
        super.onCreate()
        instance = this
        initMaps()
        container = AppContainer(this)
    }

    /** Overridden in JVM UI tests, where MapLibre's native library is unavailable. */
    protected open fun initMaps() {
        MapLibre.getInstance(this)
    }

    companion object {
        lateinit var instance: NextGenApp
            private set
    }
}

class AppContainer(app: Application) {
    val appScope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)

    val settings = SettingsRepository(app, appScope)
    val location = LocationRepository(app)
    val weather = WeatherRepository(appScope, location, settings)
    val speedLimits = SpeedLimitRepository(appScope, location, settings)
    val cameras = CameraRepository(app, appScope, location, settings)
    val navigation = NavigationManager(app, appScope, location, settings)
    val media = MediaRepository(app, appScope, settings)
    val spotify = SpotifyRepository(app, appScope, settings)
    val apps = AppsRepository(app, appScope, settings)
    val bluetooth = BluetoothRepository(app, appScope)
}

/** Convenience accessor for Compose code. */
val appContainer: AppContainer get() = NextGenApp.instance.container
