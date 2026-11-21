package com.nextgen.caros.data

import android.content.Context
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.core.stringSetPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.flow.first

private val Context.dataStore by preferencesDataStore(name = "nextgen_settings")

enum class ThemeMode(val label: String) { DARK("Dark"), LIGHT("Light"), SYSTEM("Automatic") }

enum class AccentColor(val label: String, val argb: Long, val argb2: Long) {
    ICE("Ice", 0xFF7DD3FC, 0xFF38BDF8),
    CYAN("Cyan", 0xFF22D3EE, 0xFF3B82F6),
    BLUE("Blue", 0xFF3B82F6, 0xFF6366F1),
    PURPLE("Purple", 0xFFA855F7, 0xFF6366F1),
    AURORA("Aurora", 0xFF2DD4BF, 0xFFA855F7),
    MAGENTA("Magenta", 0xFFEC4899, 0xFF8B5CF6),
    AMBER("Amber", 0xFFF59E0B, 0xFFEF4444),
}

enum class DashboardLayout(val label: String, val description: String) {
    CLUSTER("Cluster", "Instrument cluster with gauge, drive info, controls, weather and music"),
    STANDARD("Standard", "Clock, speed, map preview, music and weather"),
    MAP_FOCUS("Map focus", "Large live map with compact widgets"),
    MINIMAL("Minimal", "Clock, speed and music only"),
}

enum class ClockStyle(val label: String) { DIGITAL("Digital"), DIGITAL_SECONDS("Digital + seconds"), ANALOG("Analog") }

enum class SpeedUnit(val label: String, val perMps: Double) {
    KMH("km/h", 3.6), MPH("mph", 2.2369362920544);
    fun fromMps(mps: Double) = mps * perMps
}

enum class MapStyle(val label: String, val darkUrl: String, val lightUrl: String, val note: String) {
    OPENFREEMAP(
        "OpenFreeMap",
        "https://tiles.openfreemap.org/styles/liberty",
        "https://tiles.openfreemap.org/styles/liberty",
        "Free OpenStreetMap vector tiles, no key required",
    ),
    CARTO(
        "CARTO Basemaps",
        "https://basemaps.cartocdn.com/gl/dark-matter-gl-style/style.json",
        "https://basemaps.cartocdn.com/gl/positron-gl-style/style.json",
        "Dark Matter / Positron – free for non-commercial use",
    ),
    CUSTOM("Custom style URL", "", "", "Any MapLibre style JSON URL (e.g. MapTiler with your key)"),
}

enum class WallpaperPreset(val label: String, val colors: List<Long>) {
    MIDNIGHT("Midnight", listOf(0xFF000000, 0xFF05070C, 0xFF0A0F18)),
    NEBULA("Nebula", listOf(0xFF05070D, 0xFF0B1636, 0xFF1C0B3A)),
    OCEAN("Deep ocean", listOf(0xFF020617, 0xFF06283D, 0xFF0B4F6C)),
    AURORA("Aurora", listOf(0xFF03140F, 0xFF0A2A2A, 0xFF21114A)),
    GRAPHITE("Graphite", listOf(0xFF0A0A0B, 0xFF151518, 0xFF1F1F24)),
    SUNSET("Sunset drive", listOf(0xFF0E0716, 0xFF2A0E2E, 0xFF3D1A12)),
    IMAGE("Custom image", emptyList()),
}

enum class PhoneLinkRole(val label: String) { OFF("Off"), HEAD_UNIT("Head unit"), PHONE("Phone") }

enum class PhoneGpsMode(val label: String, val description: String) {
    FALLBACK("When needed", "Use the phone's GPS only while the head unit has no fresh fix"),
    ALWAYS("Always", "Prefer the phone's GPS whenever it's connected"),
    NEVER("Never", "Ignore location sent by the phone"),
}

data class AppSettings(
    val themeMode: ThemeMode = ThemeMode.DARK,
    val accent: AccentColor = AccentColor.ICE,
    val dashboardLayout: DashboardLayout = DashboardLayout.CLUSTER,
    val clockStyle: ClockStyle = ClockStyle.DIGITAL,
    val use24h: Boolean = true,
    val speedUnit: SpeedUnit = SpeedUnit.MPH,
    val mapStyle: MapStyle = MapStyle.CARTO,
    val customMapStyleUrl: String = "",
    val map3d: Boolean = true,
    val followZoom: Int = 16,
    val routingServer: String = "https://router.project-osrm.org",
    val overpassServer: String = "https://overpass-api.de/api/interpreter",
    val speedLimitsEnabled: Boolean = true,
    val overspeedWarning: Boolean = true,
    val overspeedTolerance: Int = 2,
    val voiceGuidance: Boolean = true,
    val alertSounds: Boolean = true,
    val showNotificationsBanner: Boolean = true,
    val camerasEnabled: Boolean = true,
    val cameraAlertDistanceM: Int = 500,
    val showUnverifiedCameras: Boolean = true,
    val alertForUnverified: Boolean = false,
    val cameraDownloadRadiusKm: Int = 50,
    val preferredMediaPackage: String = "com.spotify.music",
    val showVisualiser: Boolean = true,
    val spotifyClientId: String = "",
    val wallpaper: WallpaperPreset = WallpaperPreset.MIDNIGHT,
    val wallpaperUri: String = "",
    val wallpaperDim: Int = 55,
    val favourites: List<String> = listOf(),
    val customShortcuts: String = "[]",
    val homeLauncherEnabled: Boolean = false,
    val keepScreenOn: Boolean = true,
    val onboardingDone: Boolean = false,
    val phoneLinkRole: PhoneLinkRole = PhoneLinkRole.HEAD_UNIT,
    val phoneLinkDevice: String = "",
    val phoneGpsMode: PhoneGpsMode = PhoneGpsMode.FALLBACK,
    val sharePhoneLocation: Boolean = true,
)

/** Persists every user preference with Jetpack DataStore so they survive restarts. */
class SettingsRepository(private val context: Context, scope: CoroutineScope) {

    private object K {
        val theme = stringPreferencesKey("theme")
        val accent = stringPreferencesKey("accent")
        val layout = stringPreferencesKey("layout")
        val clock = stringPreferencesKey("clock")
        val use24h = booleanPreferencesKey("use24h")
        val unit = stringPreferencesKey("unit")
        val mapStyle = stringPreferencesKey("mapStyle")
        val customMap = stringPreferencesKey("customMapStyleUrl")
        val map3d = booleanPreferencesKey("map3d")
        val followZoom = intPreferencesKey("followZoom")
        val routing = stringPreferencesKey("routingServer")
        val overpass = stringPreferencesKey("overpassServer")
        val limits = booleanPreferencesKey("speedLimitsEnabled")
        val overspeed = booleanPreferencesKey("overspeedWarning")
        val overspeedTol = intPreferencesKey("overspeedTolerance")
        val voice = booleanPreferencesKey("voiceGuidance")
        val sounds = booleanPreferencesKey("alertSounds")
        val banners = booleanPreferencesKey("banners")
        val cams = booleanPreferencesKey("camerasEnabled")
        val camDist = intPreferencesKey("cameraAlertDistance")
        val showUnverified = booleanPreferencesKey("showUnverified")
        val alertUnverified = booleanPreferencesKey("alertUnverified")
        val camRadius = intPreferencesKey("cameraRadius")
        val mediaPkg = stringPreferencesKey("mediaPackage")
        val visualiser = booleanPreferencesKey("visualiser")
        val spotifyId = stringPreferencesKey("spotifyClientId")
        val wallpaper = stringPreferencesKey("wallpaper")
        val wallpaperUri = stringPreferencesKey("wallpaperUri")
        val wallpaperDim = intPreferencesKey("wallpaperDim")
        val favourites = stringPreferencesKey("favouritesOrdered")
        val favouritesLegacy = stringSetPreferencesKey("favourites")
        val shortcuts = stringPreferencesKey("customShortcuts")
        val homeLauncher = booleanPreferencesKey("homeLauncher")
        val keepOn = booleanPreferencesKey("keepScreenOn")
        val onboarding = booleanPreferencesKey("onboardingDone")
        val linkRole = stringPreferencesKey("phoneLinkRole")
        val linkDevice = stringPreferencesKey("phoneLinkDevice")
        val phoneGps = stringPreferencesKey("phoneGpsMode")
        val shareLoc = booleanPreferencesKey("sharePhoneLocation")
    }

    private inline fun <reified E : Enum<E>> Preferences.enum(key: Preferences.Key<String>, def: E): E =
        this[key]?.let { v -> enumValues<E>().firstOrNull { it.name == v } } ?: def

    private fun Preferences.toSettings(): AppSettings {
        val d = AppSettings()
        return AppSettings(
            themeMode = enum(K.theme, d.themeMode),
            accent = enum(K.accent, d.accent),
            dashboardLayout = enum(K.layout, d.dashboardLayout),
            clockStyle = enum(K.clock, d.clockStyle),
            use24h = this[K.use24h] ?: d.use24h,
            speedUnit = enum(K.unit, d.speedUnit),
            mapStyle = enum(K.mapStyle, d.mapStyle),
            customMapStyleUrl = this[K.customMap] ?: d.customMapStyleUrl,
            map3d = this[K.map3d] ?: d.map3d,
            followZoom = this[K.followZoom] ?: d.followZoom,
            routingServer = this[K.routing] ?: d.routingServer,
            overpassServer = this[K.overpass] ?: d.overpassServer,
            speedLimitsEnabled = this[K.limits] ?: d.speedLimitsEnabled,
            overspeedWarning = this[K.overspeed] ?: d.overspeedWarning,
            overspeedTolerance = this[K.overspeedTol] ?: d.overspeedTolerance,
            voiceGuidance = this[K.voice] ?: d.voiceGuidance,
            alertSounds = this[K.sounds] ?: d.alertSounds,
            showNotificationsBanner = this[K.banners] ?: d.showNotificationsBanner,
            camerasEnabled = this[K.cams] ?: d.camerasEnabled,
            cameraAlertDistanceM = this[K.camDist] ?: d.cameraAlertDistanceM,
            showUnverifiedCameras = this[K.showUnverified] ?: d.showUnverifiedCameras,
            alertForUnverified = this[K.alertUnverified] ?: d.alertForUnverified,
            cameraDownloadRadiusKm = this[K.camRadius] ?: d.cameraDownloadRadiusKm,
            preferredMediaPackage = this[K.mediaPkg] ?: d.preferredMediaPackage,
            showVisualiser = this[K.visualiser] ?: d.showVisualiser,
            spotifyClientId = this[K.spotifyId] ?: d.spotifyClientId,
            wallpaper = enum(K.wallpaper, d.wallpaper),
            wallpaperUri = this[K.wallpaperUri] ?: d.wallpaperUri,
            wallpaperDim = this[K.wallpaperDim] ?: d.wallpaperDim,
            favourites = this[K.favourites]?.split('\n')?.filter { it.isNotBlank() }
                ?: this[K.favouritesLegacy]?.toList() ?: d.favourites,
            customShortcuts = this[K.shortcuts] ?: d.customShortcuts,
            homeLauncherEnabled = this[K.homeLauncher] ?: d.homeLauncherEnabled,
            keepScreenOn = this[K.keepOn] ?: d.keepScreenOn,
            onboardingDone = this[K.onboarding] ?: d.onboardingDone,
            phoneLinkRole = enum(K.linkRole, d.phoneLinkRole),
            phoneLinkDevice = this[K.linkDevice] ?: d.phoneLinkDevice,
            phoneGpsMode = enum(K.phoneGps, d.phoneGpsMode),
            sharePhoneLocation = this[K.shareLoc] ?: d.sharePhoneLocation,
        )
    }

    /** Loaded synchronously once so the first frame already uses the saved theme. */
    private val initial: AppSettings = runBlocking { context.dataStore.data.first().toSettings() }

    val settings: StateFlow<AppSettings> = context.dataStore.data
        .map { it.toSettings() }
        .stateIn(scope, SharingStarted.Eagerly, initial)

    val current: AppSettings get() = settings.value

    private val writeScope = scope

    private fun write(block: (androidx.datastore.preferences.core.MutablePreferences) -> Unit) {
        writeScope.launch { context.dataStore.edit { block(it) } }
    }

    fun setTheme(v: ThemeMode) = write { it[K.theme] = v.name }
    fun setAccent(v: AccentColor) = write { it[K.accent] = v.name }
    fun setLayout(v: DashboardLayout) = write { it[K.layout] = v.name }
    fun setClockStyle(v: ClockStyle) = write { it[K.clock] = v.name }
    fun setUse24h(v: Boolean) = write { it[K.use24h] = v }
    fun setSpeedUnit(v: SpeedUnit) = write { it[K.unit] = v.name }
    fun setMapStyle(v: MapStyle) = write { it[K.mapStyle] = v.name }
    fun setCustomMapStyleUrl(v: String) = write { it[K.customMap] = v.trim() }
    fun setMap3d(v: Boolean) = write { it[K.map3d] = v }
    fun setFollowZoom(v: Int) = write { it[K.followZoom] = v }
    fun setRoutingServer(v: String) = write { it[K.routing] = v.trim().trimEnd('/') }
    fun setOverpassServer(v: String) = write { it[K.overpass] = v.trim() }
    fun setSpeedLimitsEnabled(v: Boolean) = write { it[K.limits] = v }
    fun setOverspeedWarning(v: Boolean) = write { it[K.overspeed] = v }
    fun setOverspeedTolerance(v: Int) = write { it[K.overspeedTol] = v }
    fun setVoiceGuidance(v: Boolean) = write { it[K.voice] = v }
    fun setAlertSounds(v: Boolean) = write { it[K.sounds] = v }
    fun setBanners(v: Boolean) = write { it[K.banners] = v }
    fun setCamerasEnabled(v: Boolean) = write { it[K.cams] = v }
    fun setCameraAlertDistance(v: Int) = write { it[K.camDist] = v }
    fun setShowUnverified(v: Boolean) = write { it[K.showUnverified] = v }
    fun setAlertForUnverified(v: Boolean) = write { it[K.alertUnverified] = v }
    fun setCameraRadius(v: Int) = write { it[K.camRadius] = v }
    fun setPreferredMediaPackage(v: String) = write { it[K.mediaPkg] = v }
    fun setVisualiser(v: Boolean) = write { it[K.visualiser] = v }
    fun setSpotifyClientId(v: String) = write { it[K.spotifyId] = v.trim() }
    fun setWallpaper(v: WallpaperPreset) = write { it[K.wallpaper] = v.name }
    fun setWallpaperUri(v: String) = write { it[K.wallpaperUri] = v; it[K.wallpaper] = WallpaperPreset.IMAGE.name }
    fun setWallpaperDim(v: Int) = write { it[K.wallpaperDim] = v }
    fun setFavourites(v: List<String>) = write { it[K.favourites] = v.joinToString("\n") }
    fun toggleFavourite(id: String) {
        val cur = current.favourites
        setFavourites(if (id in cur) cur - id else cur + id)
    }
    fun setCustomShortcuts(json: String) = write { it[K.shortcuts] = json }
    fun setHomeLauncher(v: Boolean) = write { it[K.homeLauncher] = v }
    fun setKeepScreenOn(v: Boolean) = write { it[K.keepOn] = v }
    fun setOnboardingDone(v: Boolean) = write { it[K.onboarding] = v }
    fun setPhoneLinkRole(v: PhoneLinkRole) = write { it[K.linkRole] = v.name }
    fun setPhoneLinkDevice(v: String) = write { it[K.linkDevice] = v }
    fun setPhoneGpsMode(v: PhoneGpsMode) = write { it[K.phoneGps] = v.name }
    fun setSharePhoneLocation(v: Boolean) = write { it[K.shareLoc] = v }
}
