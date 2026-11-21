package com.nextgen.caros.ui.music

import android.graphics.Bitmap
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.palette.graphics.Palette
import coil.imageLoader
import coil.request.ImageRequest
import coil.request.SuccessResult
import com.nextgen.caros.appContainer
import com.nextgen.caros.spotify.SpotifyRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

val SpotifyGreen = Color(0xFF1ED760)
val SpotifyBlack = Color(0xFF121212)

/**
 * What the music widgets show. Prefers the local MediaSession (the app actually playing on this
 * head unit); falls back to Spotify's Web API "currently playing" (e.g. music on the phone).
 */
data class PlayerView(
    val title: String,
    val artist: String,
    val art: Bitmap?,
    val artUrl: String?,
    val durationMs: Long,
    val positionNow: () -> Long,
    val isPlaying: Boolean,
    val isSpotify: Boolean,
    val source: String,
    val canSeek: Boolean,
    private val fromSession: Boolean,
) {
    fun playPause() = if (fromSession) appContainer.media.playPause() else appContainer.spotify.control(if (isPlaying) "pause" else "play")
    fun next() = if (fromSession) appContainer.media.next() else appContainer.spotify.control("next")
    fun previous() = if (fromSession) appContainer.media.previous() else appContainer.spotify.control("previous")
    fun seekBy(ms: Long) { if (fromSession) appContainer.media.seekBy(ms) }
}

@Composable
fun rememberPlayerView(): PlayerView? {
    val media by appContainer.media.state.collectAsState()
    val sp by appContainer.spotify.state.collectAsState()
    val np = media.nowPlaying
    if (np != null) return PlayerView(
        np.title, np.artist, np.art, np.artUri, np.durationMs, { np.positionNow() }, np.isPlaying,
        isSpotify = np.packageName == SpotifyRepository.SPOTIFY_PACKAGE,
        source = np.appName, canSeek = np.canSeek, fromSession = true,
    )
    val s = sp.now ?: return null
    return PlayerView(
        s.title, s.artist, null, s.artUrl, s.durationMs, { s.positionNow() }, s.isPlaying,
        isSpotify = true, source = "Spotify" + (s.deviceName?.let { " · $it" } ?: ""), canSeek = false, fromSession = false,
    )
}

/** Dominant/vibrant colour of the artwork, for Spotify-style tinted cards. */
@Composable
fun rememberArtColor(art: Bitmap?, artUrl: String?, fallback: Color): Color {
    val ctx = LocalContext.current
    var color by remember { mutableStateOf(fallback) }
    LaunchedEffect(art, artUrl) {
        val bmp = art ?: artUrl?.let { url ->
            val r = ctx.imageLoader.execute(ImageRequest.Builder(ctx).data(url).allowHardware(false).size(128).build())
            ((r as? SuccessResult)?.drawable as? android.graphics.drawable.BitmapDrawable)?.bitmap
        }
        color = if (bmp == null) fallback else withContext(Dispatchers.Default) {
            val p = Palette.from(bmp).maximumColorCount(16).generate()
            val sw = p.darkVibrantSwatch ?: p.vibrantSwatch ?: p.darkMutedSwatch ?: p.dominantSwatch
            sw?.let { Color(it.rgb) } ?: fallback
        }
    }
    return color
}
