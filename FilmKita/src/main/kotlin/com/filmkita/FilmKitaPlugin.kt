
package com.filmkita

import com.lagradost.cloudstream3.plugins.CloudstreamPlugin
import com.lagradost.cloudstream3.plugins.Plugin

@CloudstreamPlugin
class FilmKitaPlugin : Plugin() {
    override fun load() {
        registerMainAPI(FilmKita())
    }
}
