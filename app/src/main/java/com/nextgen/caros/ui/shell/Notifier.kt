package com.nextgen.caros.ui.shell

import androidx.compose.ui.graphics.vector.ImageVector
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

data class Banner(val title: String, val body: String? = null, val icon: ImageVector? = null, val id: Long = System.nanoTime())

/** Small in-app notification system for unobtrusive, auto-dismissing banners. */
object Notifier {
    private val _banner = MutableStateFlow<Banner?>(null)
    val banner: StateFlow<Banner?> = _banner.asStateFlow()
    fun show(title: String, body: String? = null, icon: ImageVector? = null) { _banner.value = Banner(title, body, icon) }
    fun dismiss(id: Long) { if (_banner.value?.id == id) _banner.value = null }
}
