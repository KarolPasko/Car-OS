package com.nextgen.caros.link

import android.app.Activity
import android.content.Intent
import android.os.Bundle
import android.widget.Toast
import com.nextgen.caros.MainActivity
import com.nextgen.caros.appContainer
import com.nextgen.caros.data.PhoneLinkRole
import kotlinx.coroutines.launch

/**
 * Receives "Share" from Google Maps / Apple Maps links / any app, and geo: links.
 * On a phone in Phone Link mode the place is sent to the car; otherwise it opens here.
 */
class ShareReceiverActivity : Activity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val text = when (intent?.action) {
            Intent.ACTION_SEND -> listOfNotNull(intent.getStringExtra(Intent.EXTRA_SUBJECT), intent.getStringExtra(Intent.EXTRA_TEXT)).joinToString("\n")
            Intent.ACTION_VIEW -> intent.dataString
            else -> null
        }
        if (text.isNullOrBlank()) { finish(); return }
        val c = appContainer
        val app = applicationContext
        c.appScope.launch {
            val place = runCatching { ShareParser.toPlace(text, c.location.state.value.latLon) }.getOrNull()
            if (place == null) {
                Toast.makeText(app, "Couldn't find a location in what was shared", Toast.LENGTH_LONG).show()
                return@launch
            }
            val link = c.phoneLink.state.value
            if (link.role == PhoneLinkRole.PHONE && link.status == LinkStatus.CONNECTED && c.phoneLink.sendDestination(place)) {
                Toast.makeText(app, "Sent \"${place.name}\" to ${link.peerName ?: "your head unit"}", Toast.LENGTH_LONG).show()
            } else {
                c.navigation.preview(place)
                UiEvents.request(UiEvents.Target.MAPS)
                app.startActivity(Intent(app, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP))
                if (link.role == PhoneLinkRole.PHONE) Toast.makeText(app, "Not connected to the head unit – opened here instead", Toast.LENGTH_LONG).show()
            }
        }
        finish()
    }
}
