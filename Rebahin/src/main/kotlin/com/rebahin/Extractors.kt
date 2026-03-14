package com.rebahin

import com.lagradost.cloudstream3.*
import com.lagradost.cloudstream3.utils.*

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
                "Referer" to url
            ),
            data = mapOf("hash" to id)
        ).text

        val securedLink = org.json.JSONObject(response).optString("securedLink")

        if (securedLink.isNotEmpty()) {
            callback.invoke(
                newExtractorLink(
                    source = name,
                    name = name,
                    url = securedLink
                )
            )
        }
    }
}

class ImaxStreamsExtractor : ExtractorApi() {
    override val name = "ImaxStreams"
    override val mainUrl = "https://imaxstreams.com"
    override val requiresReferer = true

    override suspend fun getUrl(
        url: String,
        referer: String?,
        subtitleCallback: (SubtitleFile) -> Unit,
        callback: (ExtractorLink) -> Unit
    ) {
        val html = app.get(
            url,
            headers = mapOf("User-Agent" to USER_AGENT),
            referer = mainUrl
        ).text

        val evalRegex = Regex("""eval\(function\(p,a,c,k,e.*?\)\)""", RegexOption.DOT_MATCHES_ALL)

        var unpacked = html

        evalRegex.findAll(html).forEach { match ->
            val decoded = JsUnpacker().unpack(match.value)
            if (decoded != null) {
                unpacked += decoded
            }
        }

        val m3u8Regex = Regex("""https://[A-Za-z0-9.-]+\.acek-cdn\.com[^\s"'<>]+\.m3u8[^\s"'<>]*""")

        val m3u8 = m3u8Regex.find(unpacked)?.value ?: return

        callback.invoke(
            newExtractorLink(
                source = name,
                name = "Acek CDN",
                url = m3u8
            ) {
                isM3u8 = true
                quality = Qualities.Unknown.value
                headers = mapOf(
                    "Referer" to "https://imaxstreams.com/",
                    "Origin" to "https://imaxstreams.com",
                    "User-Agent" to USER_AGENT
                )
            }
        )
    }
}
