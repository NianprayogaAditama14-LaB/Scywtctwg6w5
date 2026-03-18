
package com.tenseiid

import com.lagradost.cloudstream3.plugins.BasePlugin
import com.lagradost.cloudstream3.plugins.CloudstreamPlugin
import com.lagradost.cloudstream3.extractors.Dailymotion

@CloudstreamPlugin
class TenseiIDProvider: BasePlugin() {
    override fun load() {
        registerMainAPI(TenseiID())
    }
}