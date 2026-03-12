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

        val res = app.post(
            "$mainUrl/player/index.php?data=$id&do=getVideo",
            headers = mapOf(
                "User-Agent" to USER_AGENT,
                "X-Requested-With" to "XMLHttpRequest",
                "Referer" to (referer ?: url),
                "Origin" to mainUrl
            ),
            data = mapOf(
                "hash" to id,
                "r" to ""
            )
        ).text

        val json = JSONObject(res)
        val stream = json.optString("securedLink")

        if (stream.isEmpty()) return

        M3u8Helper.generateM3u8(
            name,
            stream,
            referer ?: mainUrl,
            null,
            callback
        )
    }
}
