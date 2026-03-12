package com.rebahin

import com.lagradost.cloudstream3.ExtractorApi
import com.lagradost.cloudstream3.utils.ExtractorLink
import com.lagradost.cloudstream3.utils.SubtitleFile
import com.lagradost.cloudstream3.utils.USER_AGENT
import com.lagradost.cloudstream3.utils.get
import com.lagradost.cloudstream3.utils.post
import com.lagradost.cloudstream3.TvType
import org.json.JSONObject

class EmbedPyroxExtractor : ExtractorApi() {

    override val name = "EmbedPyrox"

    override suspend fun getLinks(
        url: String,
        subtitlesCallback: (SubtitleFile) -> Unit,
        callback: (ExtractorLink) -> Unit
    ): Boolean {
        val hash = url.substringAfterLast("/").substringBefore("?")
        val baseUrl = "https://embedpyrox.xyz"

        val response = post(
            "$baseUrl/player/index.php?data=$hash&do=getVideo",
            data = "hash=$hash&r=$url",
            headers = mapOf(
                "Origin" to baseUrl,
                "Referer" to url,
                "X-Requested-With" to "XMLHttpRequest",
                "User-Agent" to USER_AGENT,
                "Content-Type" to "application/x-www-form-urlencoded; charset=UTF-8"
            )
        )

        val json = JSONObject(response.text)
        val hlsUrl = json.optString("securedLink").ifEmpty { json.optString("videoSource") }

        if (hlsUrl.isNotEmpty()) {
            callback(
                ExtractorLink(
                    name = "EmbedPyrox",
                    url = hlsUrl,
                    quality = "HLS",
                    type = TvType.Movie
                )
            )
        }

        return true
    }
}
