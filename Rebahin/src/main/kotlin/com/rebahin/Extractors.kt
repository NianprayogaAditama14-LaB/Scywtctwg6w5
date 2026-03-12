package com.rebahin

import com.lagradost.cloudstream3.ExtractorApi
import com.lagradost.cloudstream3.utils.ExtractorLink
import com.lagradost.cloudstream3.utils.M3u8Helper
import com.lagradost.cloudstream3.utils.SubtitleFile
import com.lagradost.cloudstream3.utils.app

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
        val res = app.get(url, headers = mapOf("User-Agent" to "Mozilla/5.0"))
        val html = res.text

        val hls4 = Regex("""["']hls4["']\s*:\s*["']([^"']+)""").find(html)?.groupValues?.get(1)
        val hls3 = Regex("""["']hls3["']\s*:\s*["']([^"']+)""").find(html)?.groupValues?.get(1)
        val hls2 = Regex("""["']hls2["']\s*:\s*["']([^"']+)""").find(html)?.groupValues?.get(1)

        val stream = hls4 ?: hls3 ?: hls2 ?: return

        val finalLink = if (stream.startsWith("/")) mainUrl + stream else stream

        M3u8Helper.generateM3u8(
            source = name,
            streamUrl = finalLink,
            referer = referer ?: mainUrl,
            callback = callback
        )
    }
}
