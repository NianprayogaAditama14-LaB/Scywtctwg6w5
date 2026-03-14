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
                "Referer" to url
            ),
            data = mapOf("hash" to id)
        ).text

        val json = JSONObject(response)
        val securedLink = json.optString("securedLink")

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
        val html = app.get(url, referer = mainUrl).text

        val evalScript = Regex("""eval\(function\(p,a,c,k,e,d.*?\)\)""", RegexOption.DOT_MATCHES_ALL)
            .find(html)
            ?.value

        val unpacked = if (evalScript != null) {
            JsUnpacker(evalScript).unpack() ?: html
        } else {
            html
        }

        val m3u8 = Regex("""https?://[A-Za-z0-9.-]+\.acek-cdn\.com[^\s"'<>]+\.m3u8[^\s"'<>]*""")
            .find(unpacked)
            ?.value ?: return

        val link = newExtractorLink(
            source = name,
            name = "Acek CDN",
            url = m3u8,
            type = ExtractorLinkType.M3U8
        )

        link.quality = Qualities.Unknown.value
        link.headers = mapOf(
            "Referer" to mainUrl,
            "Origin" to mainUrl,
            "User-Agent" to USER_AGENT
        )

        callback.invoke(link)
    }
}
