package com.nextgen.caros.media

import android.service.notification.NotificationListenerService
import com.nextgen.caros.appContainer

/**
 * Registered only so the user can grant "Notification access", which is Android's supported
 * permission for reading and controlling other apps' media sessions. Notification contents are
 * not read, stored or transmitted.
 */
class MediaListenerService : NotificationListenerService() {
    override fun onListenerConnected() {
        super.onListenerConnected()
        runCatching { appContainer.media.refreshAccess() }
    }

    override fun onListenerDisconnected() {
        super.onListenerDisconnected()
        runCatching { appContainer.media.refreshAccess() }
    }
}
