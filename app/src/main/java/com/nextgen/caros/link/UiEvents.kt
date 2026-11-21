package com.nextgen.caros.link

import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow

/** Requests from outside the Compose tree (share intents, phone link) to show a screen. */
object UiEvents {
    enum class Target { MAPS, SETTINGS_LINK }
    private val _requests = MutableSharedFlow<Target>(extraBufferCapacity = 4)
    val requests: SharedFlow<Target> = _requests.asSharedFlow()
    fun request(t: Target) { _requests.tryEmit(t) }
}
