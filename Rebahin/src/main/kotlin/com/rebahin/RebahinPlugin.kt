

package com.rebahin

import android.content.Context
import com.lagradost.cloudstream3.plugins.CloudstreamPlugin
import com.lagradost.cloudstream3.plugins.Plugin

@CloudstreamPlugin
class RebahinPlugin : Plugin() {
    override fun load(context: Context) {
        Rebahin.context = context
        registerMainAPI(Rebahin())
        registerExtractorAPI(VidhideExtractor())
    }
}