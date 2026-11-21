package com.nextgen.caros.spotify

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.util.Base64
import androidx.browser.customtabs.CustomTabsIntent
import com.nextgen.caros.BuildConfig
import com.nextgen.caros.data.SettingsRepository
import com.nextgen.caros.net.Http
import com.nextgen.caros.net.HttpException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.longOrNull
import okhttp3.FormBody
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.security.MessageDigest
import java.security.SecureRandom

data class SpotifyPlaylist(val id: String, val name: String, val uri: String, val imageUrl: String?, val tracks: Int, val owner: String)

/** What Spotify reports as playing on the user's account (any device), from the Web API. */
data class SpotifyNow(
    val title: String,
    val artist: String,
    val album: String,
    val artUrl: String?,
    val progressMs: Long,
    val durationMs: Long,
    val isPlaying: Boolean,
    val deviceName: String?,
    val fetchedAt: Long = android.os.SystemClock.elapsedRealtime(),
) {
    fun positionNow(): Long = if (!isPlaying) progressMs
    else (progressMs + android.os.SystemClock.elapsedRealtime() - fetchedAt).coerceAtMost(durationMs)
}

data class SpotifyState(
    val clientIdConfigured: Boolean = false,
    val connected: Boolean = false,
    val busy: Boolean = false,
    val displayName: String? = null,
    val premium: Boolean = false,
    val playlists: List<SpotifyPlaylist> = emptyList(),
    val message: String? = null,
    val spotifyInstalled: Boolean = false,
    val now: SpotifyNow? = null,
)

/**
 * Spotify integration through Spotify's official, supported mechanisms only:
 *  - Sign-in uses Spotify's own Authorization Code + PKCE flow in a browser tab. The app never
 *    sees or stores the user's Spotify password; only OAuth tokens are kept (app-private storage).
 *  - The Spotify Web API lists the user's playlists and (Premium only) starts playback.
 *  - Now-playing info and transport controls come from Android's MediaSession of the Spotify
 *    app (see MediaRepository), which works for Free and Premium accounts.
 */
class SpotifyRepository(
    private val context: Context,
    private val scope: CoroutineScope,
    private val settings: SettingsRepository,
) {
    private val prefs = context.getSharedPreferences("spotify_auth", Context.MODE_PRIVATE)
    private val _state = MutableStateFlow(SpotifyState())
    val state: StateFlow<SpotifyState> = _state.asStateFlow()

    companion object {
        const val SPOTIFY_PACKAGE = "com.spotify.music"
        private const val SCOPES = "user-read-private user-read-playback-state user-modify-playback-state " +
            "user-read-currently-playing playlist-read-private playlist-read-collaborative user-library-read"
    }

    val clientId: String get() = settings.current.spotifyClientId.ifBlank { BuildConfig.SPOTIFY_CLIENT_ID }

    init {
        scope.launch {
            settings.settings.collect { refreshFlags() }
        }
        // Poll "currently playing" while signed in (cheap; 5 s like Spotify's own widgets).
        scope.launch {
            while (true) {
                if (_state.value.connected) pollNowPlaying()
                delay(5000)
            }
        }
        if (prefs.getString("refresh_token", null) != null) {
            _state.value = _state.value.copy(connected = true)
            loadProfile()
        }
    }

    fun refreshFlags() {
        _state.value = _state.value.copy(
            clientIdConfigured = clientId.isNotBlank(),
            spotifyInstalled = context.packageManager.getLaunchIntentForPackage(SPOTIFY_PACKAGE) != null,
        )
    }

    // ------------------------------------------------------------------ auth (PKCE)

    fun login(activityContext: Context) {
        if (clientId.isBlank()) {
            _state.value = _state.value.copy(message = "Add your Spotify Client ID in Settings › Music first.")
            return
        }
        val verifierBytes = ByteArray(64).also { SecureRandom().nextBytes(it) }
        val verifier = b64(verifierBytes)
        val challenge = b64(MessageDigest.getInstance("SHA-256").digest(verifier.toByteArray()))
        val stateParam = b64(ByteArray(16).also { SecureRandom().nextBytes(it) })
        prefs.edit().putString("pkce_verifier", verifier).putString("pkce_state", stateParam).apply()
        val uri = Uri.parse("https://accounts.spotify.com/authorize").buildUpon()
            .appendQueryParameter("client_id", clientId)
            .appendQueryParameter("response_type", "code")
            .appendQueryParameter("redirect_uri", BuildConfig.SPOTIFY_REDIRECT_URI)
            .appendQueryParameter("code_challenge_method", "S256")
            .appendQueryParameter("code_challenge", challenge)
            .appendQueryParameter("state", stateParam)
            .appendQueryParameter("scope", SCOPES)
            .build()
        runCatching { CustomTabsIntent.Builder().setShowTitle(true).build().launchUrl(activityContext, uri) }
            .onFailure {
                activityContext.startActivity(Intent(Intent.ACTION_VIEW, uri).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
            }
    }

    private fun b64(bytes: ByteArray) = Base64.encodeToString(bytes, Base64.URL_SAFE or Base64.NO_PADDING or Base64.NO_WRAP)

    /** Called by MainActivity with the nextgencaros://spotify-callback redirect. */
    fun handleRedirect(uri: Uri) {
        val error = uri.getQueryParameter("error")
        if (error != null) { _state.value = _state.value.copy(message = "Spotify sign-in cancelled ($error)"); return }
        val code = uri.getQueryParameter("code") ?: return
        if (uri.getQueryParameter("state") != prefs.getString("pkce_state", null)) {
            _state.value = _state.value.copy(message = "Sign-in rejected: state mismatch. Please try again.")
            return
        }
        val verifier = prefs.getString("pkce_verifier", null) ?: return
        scope.launch {
            _state.value = _state.value.copy(busy = true)
            runCatching {
                tokenRequest(
                    FormBody.Builder()
                        .add("grant_type", "authorization_code")
                        .add("code", code)
                        .add("redirect_uri", BuildConfig.SPOTIFY_REDIRECT_URI)
                        .add("client_id", clientId)
                        .add("code_verifier", verifier)
                        .build()
                )
            }.onSuccess {
                prefs.edit().remove("pkce_verifier").remove("pkce_state").apply()
                _state.value = _state.value.copy(connected = true, message = null)
                loadProfile()
            }.onFailure {
                _state.value = _state.value.copy(busy = false, message = "Spotify sign-in failed: ${it.message}")
            }
        }
    }

    private suspend fun tokenRequest(body: FormBody) {
        val req = Request.Builder().url("https://accounts.spotify.com/api/token").post(body).build()
        val o = Http.json.parseToJsonElement(Http.execute(req)).jsonObject
        val access = o["access_token"]?.jsonPrimitive?.contentOrNull ?: error("No access token")
        val expires = o["expires_in"]?.jsonPrimitive?.longOrNull ?: 3600
        val e = prefs.edit()
            .putString("access_token", access)
            .putLong("expires_at", System.currentTimeMillis() + (expires - 60) * 1000)
        o["refresh_token"]?.jsonPrimitive?.contentOrNull?.let { e.putString("refresh_token", it) }
        e.apply()
    }

    private suspend fun accessToken(): String {
        val token = prefs.getString("access_token", null)
        if (token != null && System.currentTimeMillis() < prefs.getLong("expires_at", 0)) return token
        val refresh = prefs.getString("refresh_token", null) ?: error("Not signed in")
        tokenRequest(
            FormBody.Builder().add("grant_type", "refresh_token").add("refresh_token", refresh).add("client_id", clientId).build()
        )
        return prefs.getString("access_token", null) ?: error("Not signed in")
    }

    fun logout() {
        prefs.edit().clear().apply()
        _state.value = SpotifyState(clientIdConfigured = clientId.isNotBlank(), spotifyInstalled = _state.value.spotifyInstalled)
    }

    // ------------------------------------------------------------------ Web API

    private suspend fun api(method: String, path: String, body: String? = null): String {
        val b = Request.Builder().url("https://api.spotify.com/v1$path").header("Authorization", "Bearer ${accessToken()}")
        val rb = body?.toRequestBody("application/json".toMediaType())
        when (method) {
            "GET" -> b.get()
            "PUT" -> b.put(rb ?: ByteArray(0).toRequestBody(null))
            "POST" -> b.post(rb ?: ByteArray(0).toRequestBody(null))
        }
        return Http.execute(b.build())
    }

    fun loadProfile() {
        scope.launch {
            _state.value = _state.value.copy(busy = true)
            runCatching {
                val me = Http.json.parseToJsonElement(api("GET", "/me")).jsonObject
                val pl = Http.json.parseToJsonElement(api("GET", "/me/playlists?limit=40")).jsonObject
                val playlists = pl["items"]?.jsonArray?.mapNotNull { it as? JsonObject }?.map { p ->
                    SpotifyPlaylist(
                        id = p["id"]!!.jsonPrimitive.content,
                        name = p["name"]?.jsonPrimitive?.contentOrNull ?: "Playlist",
                        uri = p["uri"]!!.jsonPrimitive.content,
                        imageUrl = (p["images"] as? kotlinx.serialization.json.JsonArray)?.firstOrNull()
                            ?.jsonObject?.get("url")?.jsonPrimitive?.contentOrNull,
                        tracks = (p["tracks"] as? JsonObject)?.get("total")?.jsonPrimitive?.intOrNull ?: 0,
                        owner = (p["owner"] as? JsonObject)?.get("display_name")?.jsonPrimitive?.contentOrNull ?: "",
                    )
                } ?: emptyList()
                Triple(me["display_name"]?.jsonPrimitive?.contentOrNull, me["product"]?.jsonPrimitive?.contentOrNull, playlists)
            }.onSuccess { (name, product, playlists) ->
                _state.value = _state.value.copy(
                    busy = false, connected = true, displayName = name, premium = product == "premium", playlists = playlists,
                )
            }.onFailure {
                val msg = if (it is HttpException && it.code == 403)
                    "Spotify refused access. In the Spotify Developer Dashboard, add your account under User Management."
                else "Couldn't load Spotify library: ${it.message}"
                _state.value = _state.value.copy(busy = false, message = msg)
            }
        }
    }

    /**
     * Plays a playlist. Premium accounts are controlled through the Web API; otherwise (or if no
     * Spotify device is active) the playlist is opened in the official Spotify app.
     */
    fun play(uri: String) {
        scope.launch {
            if (_state.value.premium) {
                val ok = runCatching { api("PUT", "/me/player/play", """{"context_uri":"$uri"}""") }.isSuccess
                if (ok) return@launch
                // No active device: wake the Spotify app on this head unit and retry once.
                openInSpotify(uri)
                delay(3500)
                runCatching {
                    val devices = Http.json.parseToJsonElement(api("GET", "/me/player/devices")).jsonObject["devices"]?.jsonArray
                    val dev = devices?.map { it.jsonObject }?.firstOrNull { it["is_active"]?.jsonPrimitive?.booleanOrNull == true }
                        ?: devices?.map { it.jsonObject }?.firstOrNull { it["type"]?.jsonPrimitive?.contentOrNull == "Smartphone" }
                        ?: devices?.firstOrNull()?.jsonObject
                    val id = dev?.get("id")?.jsonPrimitive?.contentOrNull ?: error("No device")
                    api("PUT", "/me/player/play?device_id=$id", """{"context_uri":"$uri"}""")
                }
            } else {
                openInSpotify(uri)
            }
        }
    }

    private suspend fun pollNowPlaying() {
        runCatching {
            val body = api("GET", "/me/player?additional_types=episode")
            if (body.isBlank()) { _state.value = _state.value.copy(now = null); return }
            val o = Http.json.parseToJsonElement(body).jsonObject
            val item = o["item"] as? JsonObject ?: run { _state.value = _state.value.copy(now = null); return }
            val images = ((item["album"] as? JsonObject)?.get("images") ?: (item["images"]))
            val art = (images as? kotlinx.serialization.json.JsonArray)?.firstOrNull()?.jsonObject?.get("url")?.jsonPrimitive?.contentOrNull
            val artists = (item["artists"] as? kotlinx.serialization.json.JsonArray)?.mapNotNull {
                it.jsonObject["name"]?.jsonPrimitive?.contentOrNull
            }?.joinToString(", ") ?: (item["show"] as? JsonObject)?.get("name")?.jsonPrimitive?.contentOrNull ?: ""
            SpotifyNow(
                title = item["name"]?.jsonPrimitive?.contentOrNull ?: "",
                artist = artists,
                album = (item["album"] as? JsonObject)?.get("name")?.jsonPrimitive?.contentOrNull ?: "",
                artUrl = art,
                progressMs = o["progress_ms"]?.jsonPrimitive?.longOrNull ?: 0,
                durationMs = item["duration_ms"]?.jsonPrimitive?.longOrNull ?: 0,
                isPlaying = o["is_playing"]?.jsonPrimitive?.booleanOrNull ?: false,
                deviceName = (o["device"] as? JsonObject)?.get("name")?.jsonPrimitive?.contentOrNull,
            )
        }.onSuccess { _state.value = _state.value.copy(now = it) }
    }

    /** Web API transport controls (Spotify allows these for Premium accounts only). */
    fun control(action: String) {
        scope.launch {
            val ok = runCatching {
                when (action) {
                    "play" -> api("PUT", "/me/player/play")
                    "pause" -> api("PUT", "/me/player/pause")
                    "next" -> api("POST", "/me/player/next")
                    "previous" -> api("POST", "/me/player/previous")
                }
            }.isSuccess
            if (!ok && !_state.value.premium) openInSpotify()
            delay(400); pollNowPlaying()
        }
    }

    fun openInSpotify(uri: String? = null): Boolean {
        val pm = context.packageManager
        val intent = if (uri != null) Intent(Intent.ACTION_VIEW, Uri.parse(uri)).setPackage(SPOTIFY_PACKAGE)
        else pm.getLaunchIntentForPackage(SPOTIFY_PACKAGE)
        if (intent == null || intent.resolveActivity(pm) == null) {
            _state.value = _state.value.copy(message = "The Spotify app isn't installed on this head unit.")
            return false
        }
        context.startActivity(intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        return true
    }

    fun clearMessage() { _state.value = _state.value.copy(message = null) }
}
