
package com.filmkita

import android.content.Context
import com.lagradost.cloudstream3.plugins.CloudstreamPlugin
import com.lagradost.cloudstream3.plugins.Plugin

@CloudstreamPlugin
class FilmKitaPlugin : Plugin() {
    override fun load(context: Context) {
        registerMainAPI(FilmKita())
        registerExtractorAPI(LayarWibuExtractor())
    }
}
