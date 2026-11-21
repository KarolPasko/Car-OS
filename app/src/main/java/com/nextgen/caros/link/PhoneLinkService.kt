package com.nextgen.caros.link

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

/** Keeps the phone-side link (and GPS sharing) alive while the phone screen is off. */
class PhoneLinkService : Service() {
    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val nm = getSystemService(NotificationManager::class.java)
        if (Build.VERSION.SDK_INT >= 26 && nm.getNotificationChannel(CHANNEL) == null) {
            nm.createNotificationChannel(NotificationChannel(CHANNEL, "Phone Link", NotificationManager.IMPORTANCE_LOW))
        }
        val pi = PendingIntent.getActivity(this, 1, Intent(this, MainActivity::class.java), PendingIntent.FLAG_IMMUTABLE)
        val n = NotificationCompat.Builder(this, CHANNEL)
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setContentTitle("Phone Link active")
            .setContentText("Sharing location and destinations with your head unit")
            .setOngoing(true).setContentIntent(pi).build()
        val type = if (Build.VERSION.SDK_INT >= 29)
            ServiceInfo.FOREGROUND_SERVICE_TYPE_CONNECTED_DEVICE or ServiceInfo.FOREGROUND_SERVICE_TYPE_LOCATION else 0
        runCatching { ServiceCompat.startForeground(this, 77, n, type) }
            .recoverCatching { if (Build.VERSION.SDK_INT >= 29) ServiceCompat.startForeground(this, 77, n, ServiceInfo.FOREGROUND_SERVICE_TYPE_CONNECTED_DEVICE) }
            .onFailure { stopSelf() }
        return START_STICKY
    }

    companion object {
        private const val CHANNEL = "phone_link"
        private var running = false
        fun setRunning(ctx: Context, on: Boolean) {
            if (on == running) return
            running = on
            val i = Intent(ctx, PhoneLinkService::class.java)
            runCatching {
                if (on) { if (Build.VERSION.SDK_INT >= 26) ctx.startForegroundService(i) else ctx.startService(i) }
                else ctx.stopService(i)
            }
        }
    }
}
