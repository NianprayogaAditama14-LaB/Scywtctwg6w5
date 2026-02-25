package com.bstation

import com.lagradost.cloudstream3.plugins.CloudstreamPlugin
import com.lagradost.cloudstream3.plugins.Plugin

@CloudstreamPlugin
class BstationProviderPlugin : Plugin() {
    override fun load(context: android.content.Context) {
        // Daftarkan provider utama
        registerMainAPI(BstationProvider())
    }
}
