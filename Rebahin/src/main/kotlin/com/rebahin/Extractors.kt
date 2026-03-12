package com.rebahin

import com.lagradost.cloudstream3.*
import com.lagradost.cloudstream3.utils.*
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
        val id = url.substringAfterLast("/")

        val response = app.post(
            "$mainUrl/player/index.php?data=$id&do=getVideo",
            headers = mapOf(
                "User-Agent" to USER_AGENT,
                "X-Requested-With" to "XMLHttpRequest",
                "Referer" to (referer ?: mainUrl),
                "Origin" to mainUrl
            ),
            data = mapOf(
                "hash" to id,
                "r" to ""
            )
        ).text

        val json = JSONObject(response)
        val streamUrl = json.optString("securedLink")
        if (streamUrl.isEmpty()) return

        callback(newExtractorLink(name, streamUrl))
    }
}
