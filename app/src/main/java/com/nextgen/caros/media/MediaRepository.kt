package com.nextgen.caros.media

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.media.AudioManager
import android.media.MediaMetadata
import android.media.session.MediaController
import android.media.session.MediaSessionManager
import android.media.session.PlaybackState
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.provider.Settings
import android.view.KeyEvent
import androidx.core.app.NotificationManagerCompat
import com.nextgen.caros.data.SettingsRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

data class NowPlaying(
    val packageName: String,
    val appName: String,
    val title: String,
    val artist: String,
    val album: String,
    val art: Bitmap?,
    val artUri: String?,
    val durationMs: Long,
    val positionMs: Long,
    val positionUpdatedAt: Long,
    val speed: Float,
    val isPlaying: Boolean,
    val canSkipNext: Boolean,
    val canSkipPrev: Boolean,
    val canSeek: Boolean,
) {
    /** Extrapolated playback position. */
    fun positionNow(): Long {
        if (!isPlaying) return positionMs
        val p = positionMs + ((SystemClock.elapsedRealtime() - positionUpdatedAt) * speed).toLong()
        return if (durationMs > 0) p.coerceIn(0, durationMs) else p
    }
}

data class MediaUiState(
    val hasAccess: Boolean = false,
    val nowPlaying: NowPlaying? = null,
    val sessions: List<String> = emptyList(),
)

/**
 * Reads and controls whatever media app is active (Spotify, YouTube Music, local players…) using
 * the platform MediaSession APIs. Requires the user to grant notification-listener access; without
 * it, transport buttons still work via standard media key events.
 */
class MediaRepository(
    private val context: Context,
    private val scope: CoroutineScope,
    private val settings: SettingsRepository,
) {
    private val msm = context.getSystemService(Context.MEDIA_SESSION_SERVICE) as MediaSessionManager
    private val audio = context.getSystemService(Context.AUDIO_SERVICE) as AudioManager
    private val component = ComponentName(context, MediaListenerService::class.java)
    private val handler = Handler(Looper.getMainLooper())

    private val _state = MutableStateFlow(MediaUiState())
    val state: StateFlow<MediaUiState> = _state.asStateFlow()

    private var controllers: List<MediaController> = emptyList()
    private var active: MediaController? = null
    private var listening = false

    private val sessionsListener = MediaSessionManager.OnActiveSessionsChangedListener { list ->
        onSessions(list ?: emptyList())
    }

    private val callback = object : MediaController.Callback() {
        override fun onPlaybackStateChanged(state: PlaybackState?) = onSessions(controllers)
        override fun onMetadataChanged(metadata: MediaMetadata?) = publish()
        override fun onSessionDestroyed() = onSessions(controllers.filter { it != active })
    }

    init { refreshAccess() }

    fun hasAccess(): Boolean =
        NotificationManagerCompat.getEnabledListenerPackages(context).contains(context.packageName)

    fun refreshAccess() {
        val ok = hasAccess()
        if (ok && !listening) {
            runCatching {
                msm.addOnActiveSessionsChangedListener(sessionsListener, component, handler)
                listening = true
                onSessions(msm.getActiveSessions(component))
            }
        } else if (!ok && listening) {
            runCatching { msm.removeOnActiveSessionsChangedListener(sessionsListener) }
            listening = false
            active?.unregisterCallback(callback)
            active = null
        }
        _state.value = _state.value.copy(hasAccess = ok)
        publish()
    }

    private fun onSessions(list: List<MediaController>) {
        controllers = list
        val preferred = settings.current.preferredMediaPackage
        val pick = list.firstOrNull { it.playbackState?.state == PlaybackState.STATE_PLAYING }
            ?: list.firstOrNull { it.packageName == preferred }
            ?: list.firstOrNull()
        if (pick?.sessionToken != active?.sessionToken) {
            active?.unregisterCallback(callback)
            active = pick
            pick?.registerCallback(callback, handler)
        }
        publish()
    }

    private fun appLabel(pkg: String) = runCatching {
        val pm = context.packageManager
        pm.getApplicationLabel(pm.getApplicationInfo(pkg, 0)).toString()
    }.getOrDefault(pkg)

    private fun publish() {
        val c = active
        val md = c?.metadata
        val ps = c?.playbackState
        val np = if (c != null && md != null) {
            val actions = ps?.actions ?: 0L
            NowPlaying(
                packageName = c.packageName,
                appName = appLabel(c.packageName),
                title = md.getString(MediaMetadata.METADATA_KEY_TITLE) ?: md.getString(MediaMetadata.METADATA_KEY_DISPLAY_TITLE) ?: "Unknown title",
                artist = md.getString(MediaMetadata.METADATA_KEY_ARTIST) ?: md.getString(MediaMetadata.METADATA_KEY_ALBUM_ARTIST)
                    ?: md.getString(MediaMetadata.METADATA_KEY_DISPLAY_SUBTITLE) ?: "",
                album = md.getString(MediaMetadata.METADATA_KEY_ALBUM) ?: "",
                art = md.getBitmap(MediaMetadata.METADATA_KEY_ALBUM_ART) ?: md.getBitmap(MediaMetadata.METADATA_KEY_ART)
                    ?: md.getBitmap(MediaMetadata.METADATA_KEY_DISPLAY_ICON),
                artUri = md.getString(MediaMetadata.METADATA_KEY_ALBUM_ART_URI) ?: md.getString(MediaMetadata.METADATA_KEY_ART_URI),
                durationMs = md.getLong(MediaMetadata.METADATA_KEY_DURATION).coerceAtLeast(0),
                positionMs = ps?.position ?: 0,
                positionUpdatedAt = ps?.lastPositionUpdateTime?.takeIf { it > 0 } ?: SystemClock.elapsedRealtime(),
                speed = ps?.playbackSpeed?.takeIf { it > 0f } ?: 1f,
                isPlaying = ps?.state == PlaybackState.STATE_PLAYING || ps?.state == PlaybackState.STATE_BUFFERING,
                canSkipNext = actions and PlaybackState.ACTION_SKIP_TO_NEXT != 0L || actions == 0L,
                canSkipPrev = actions and PlaybackState.ACTION_SKIP_TO_PREVIOUS != 0L || actions == 0L,
                canSeek = actions and PlaybackState.ACTION_SEEK_TO != 0L,
            )
        } else null
        _state.value = _state.value.copy(
            nowPlaying = np,
            sessions = controllers.map { it.packageName }.distinct(),
        )
    }

    // ------------------------------------------------------------------ controls

    private fun key(code: Int) {
        audio.dispatchMediaKeyEvent(KeyEvent(KeyEvent.ACTION_DOWN, code))
        audio.dispatchMediaKeyEvent(KeyEvent(KeyEvent.ACTION_UP, code))
    }

    fun playPause() {
        val c = active
        if (c != null) {
            if (c.playbackState?.state == PlaybackState.STATE_PLAYING) c.transportControls.pause() else c.transportControls.play()
        } else key(KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE)
    }

    fun next() { active?.transportControls?.skipToNext() ?: key(KeyEvent.KEYCODE_MEDIA_NEXT) }
    fun previous() { active?.transportControls?.skipToPrevious() ?: key(KeyEvent.KEYCODE_MEDIA_PREVIOUS) }
    fun seekTo(ms: Long) { active?.transportControls?.seekTo(ms) }

    fun selectSession(pkg: String) {
        controllers.firstOrNull { it.packageName == pkg }?.let {
            active?.unregisterCallback(callback)
            active = it
            it.registerCallback(callback, handler)
            publish()
        }
    }

    fun openActiveApp(): Boolean {
        val pkg = active?.packageName ?: settings.current.preferredMediaPackage
        val i = context.packageManager.getLaunchIntentForPackage(pkg) ?: return false
        context.startActivity(i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        return true
    }

    fun notificationAccessIntent(): Intent =
        Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
}
