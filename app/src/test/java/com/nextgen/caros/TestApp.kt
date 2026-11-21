package com.nextgen.caros

/** Robolectric application: identical to the real one minus MapLibre's native initialisation. */
class TestApp : NextGenApp() {
    override fun initMaps() {}
}
