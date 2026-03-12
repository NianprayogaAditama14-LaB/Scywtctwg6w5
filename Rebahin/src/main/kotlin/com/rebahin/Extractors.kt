package com.rebahin

import com.lagradost.cloudstream3.ExtractorApi
import com.lagradost.cloudstream3.utils.ExtractorLink
import com.lagradost.cloudstream3.utils.M3u8Helper
import com.lagradost.cloudstream3.utils.SubtitleFile
import com.lagradost.cloudstream3.networking.app
import com.lagradost.cloudstream3.networking.USER_AGENT
import org.json.JSONObject

class EmbedPyroxExtractor : ExtractorApi() {

    override val name = "EmbedPyrox"
    override val mainUrl = "https://embedpyrox.xyz"
    override val requiresReferer = true

    override suspend fun getUrl(
        url: String,
        referer: String?,
        subtitleCallback: (SubtitleFile) -> Unit,
        callback: (ExtractorLink) -> Unit
    ) {
        val id = Regex("""([a-f0-9]{32})""").find(url)?.value ?: return
        val api = "$mainUrl/player/index.php?data=$id&do=getVideo"

        val res = app.post(
            api,
            headers = mapOf(
                "Origin" to mainUrl,
                "Referer" to (referer ?: mainUrl),
                "X-Requested-With" to "XMLHttpRequest",
                "Content-Type" to "application/x-www-form-urlencoded; charset=UTF-8",
                "User-Agent" to USER_AGENT
            ),
            data = mapOf(
                "hash" to id,
                "r" to (referer ?: "")
            )
        ).text

        val json = JSONObject(res)
        val m3u8 = json.optString("securedLink")
        if (m3u8.isNullOrEmpty()) return

        M3u8Helper.generateM3u8(
            name = name,
            url = m3u8,
            referer = referer ?: mainUrl,
            callback = callback
        )
    }
}
