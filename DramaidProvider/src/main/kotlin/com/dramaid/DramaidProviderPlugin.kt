
package com.dramaid

import android.content.Context
import com.lagradost.cloudstream3.plugins.CloudstreamPlugin
import com.lagradost.cloudstream3.plugins.Plugin

@CloudstreamPlugin
class DramaidProviderPlugin : Plugin() {
    override fun load(context: Context) {
        registerMainAPI(DramaIdProvider())
        registerExtractorAPI(BerkasDriveExtractor())
    }
}
