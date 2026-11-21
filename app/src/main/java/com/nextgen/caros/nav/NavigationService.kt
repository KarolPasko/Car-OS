package com.nextgen.caros.nav

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat
import androidx.core.app.ServiceCompat
import com.nextgen.caros.MainActivity
import com.nextgen.caros.R
import com.nextgen.caros.appContainer
import com.nextgen.caros.location.Geo

/**
 * Foreground service that keeps GPS updates and guidance alive while the driver switches to
 * another app (e.g. Spotify). Shows the next manoeuvre in an ongoing notification.
 */
class NavigationService : Service() {

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        ensureChannel(this)
        val n = build(this, "Navigation active", "Calculating…")
        val type = if (Build.VERSION.SDK_INT >= 29) ServiceInfo.FOREGROUND_SERVICE_TYPE_LOCATION else 0
        runCatching { ServiceCompat.startForeground(this, NOTIF_ID, n, type) }
            .onFailure { stopSelf(); return START_NOT_STICKY }
        appContainer.location.start()
        return START_NOT_STICKY
    }

    companion object {
        private const val CHANNEL = "navigation"
        private const val NOTIF_ID = 42
        private var lastText = ""

        fun ensureChannel(ctx: Context) {
            if (Build.VERSION.SDK_INT >= 26) {
                val nm = ctx.getSystemService(NotificationManager::class.java)
                if (nm.getNotificationChannel(CHANNEL) == null) {
                    nm.createNotificationChannel(
                        NotificationChannel(CHANNEL, "Navigation", NotificationManager.IMPORTANCE_LOW).apply {
                            description = "Turn-by-turn guidance while NextGen Car OS is in the background"
                            setShowBadge(false)
                        }
                    )
                }
            }
        }

        private fun build(ctx: Context, title: String, text: String): Notification {
            val pi = PendingIntent.getActivity(
                ctx, 0, Intent(ctx, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP),
                PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
            )
            return NotificationCompat.Builder(ctx, CHANNEL)
                .setSmallIcon(R.drawable.ic_launcher_foreground)
                .setContentTitle(title)
                .setContentText(text)
                .setOngoing(true)
                .setOnlyAlertOnce(true)
                .setCategory(NotificationCompat.CATEGORY_NAVIGATION)
                .setContentIntent(pi)
                .build()
        }

        fun update(ctx: Context, p: NavProgress, imperial: Boolean) {
            val step = p.nextStep ?: return
            val title = "${Geo.formatDistance(p.distanceToNextM, imperial)} · ${step.shortInstruction}"
            val text = step.instruction
            if (title + text == lastText) return
            lastText = title + text
            runCatching {
                ctx.getSystemService(NotificationManager::class.java).notify(NOTIF_ID, build(ctx, title, text))
            }
        }
    }
}
