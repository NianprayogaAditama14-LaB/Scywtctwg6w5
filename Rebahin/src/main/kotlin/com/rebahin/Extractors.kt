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
    override val mainUrl = "https://imaxstreams.net"
    override val requiresReferer = true

    override suspend fun getUrl(
        url: String,
        referer: String?,
        subtitleCallback: (SubtitleFile) -> Unit,
        callback: (ExtractorLink) -> Unit
    ) {

        val headers = mapOf(
            "Referer" to "$mainUrl/",
            "Origin" to mainUrl,
            "User-Agent" to USER_AGENT
        )

        val html = app.get(url, headers = headers).text

        val unpacked = try {
            val packed = Regex("""eval\(function\(p,a,c,k,e,d.*?\)\)""", RegexOption.DOT_MATCHES_ALL)
                .find(html)?.value
            packed?.let { JsUnpacker(it).unpack() } ?: html
        } catch (e: Exception) {
            html
        }

        val m3u8Regex = Regex("""https?:\/\/[^"' ]+\.m3u8[^"' ]*""")

        val links = m3u8Regex.findAll(unpacked).map { it.value }.toSet()

        if (links.isEmpty()) return

        links.forEach { link ->

            callback.invoke(
                newExtractorLink(
                    source = name,
                    name = "Imax",
                    url = link,
                    type = ExtractorLinkType.M3U8
                ).apply {
                    this.headers = headers
                }
            )
        }
    }
}

class ImaxDirectExtractor : ExtractorApi() {

    override val name = "ImaxDirect"
    override val mainUrl = "https://imaxstreams.net"
    override val requiresReferer = true

    override suspend fun getUrl(
        url: String,
        referer: String?,
        subtitleCallback: (SubtitleFile) -> Unit,
        callback: (ExtractorLink) -> Unit
    ) {

        val headers = mapOf(
            "Referer" to "$mainUrl/",
            "Origin" to mainUrl,
            "User-Agent" to USER_AGENT
        )

        val html = app.get(url, headers = headers).text

        Regex("""https://[^"' ]+\.m3u8[^"' ]*""")
            .findAll(html)
            .map { it.value }
            .distinct()
            .forEach { link ->

                callback.invoke(
                    newExtractorLink(
                        source = name,
                        name = name,
                        url = link,
                        type = ExtractorLinkType.M3U8
                    ).apply {
                        this.headers = headers
                    }
                )
            }
    }
}