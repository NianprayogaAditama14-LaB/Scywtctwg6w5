
package com.layarwarna

import android.content.Context
import com.lagradost.cloudstream3.plugins.CloudstreamPlugin
import com.lagradost.cloudstream3.plugins.Plugin

@CloudstreamPlugin
class LayarWarnaPlugin : Plugin() {
    override fun load(context: Context) {
        registerMainAPI(LayarWarna())
    }
}
