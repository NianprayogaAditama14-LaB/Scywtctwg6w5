package com.rebahin

import com.lagradost.cloudstream3.app
import com.lagradost.cloudstream3.utils.*

class VidhideExtractor : ExtractorApi() {

    override val name = "VidHide"
    override val mainUrl = "https://vidhidehub.com"
    override val requiresReferer = true

    override suspend fun getUrl(
        url: String,
        referer: String?,
        subtitleCallback: (SubtitleFile) -> Unit,
        callback: (ExtractorLink) -> Unit
    ) {

        val res = app.get(
            url,
            headers = mapOf(
                "Referer" to mainUrl,
                "User-Agent" to USER_AGENT
            )
        )

        val html = res.text

        val stream = Regex("""hls\d["']?\s*:\s*["']([^"']+)""")
            .find(html)
            ?.groupValues
            ?.get(1)
            ?: return

        val finalUrl =
            if (stream.startsWith("/")) "$mainUrl$stream"
            else stream

        M3u8Helper.generateM3u8(
            name,
            finalUrl,
            mainUrl,
            callback
        )
    }
}