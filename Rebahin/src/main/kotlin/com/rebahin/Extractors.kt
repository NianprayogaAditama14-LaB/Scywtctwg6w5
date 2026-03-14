package com.rebahin

import com.lagradost.cloudstream3.*
import com.lagradost.cloudstream3.utils.*
import org.json.JSONArray
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
            data = mapOf(
                "hash" to id
            )
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

        val html = app.get(url, referer = referer ?: mainUrl).text

        val unpacked = try {
            val packed = Regex(
                """eval\(function\(p,a,c,k,e,d.*?\)\)""",
                RegexOption.DOT_MATCHES_ALL
            ).find(html)?.value

            packed?.let { JsUnpacker(it).unpack() } ?: html

        } catch (e: Exception) {
            html
        }

        val sourcesJson = Regex("""sources\s*:\s*(\[[^\]]+\])""")
            .find(unpacked)?.groupValues?.get(1)

        if (sourcesJson != null) {

            val arr = JSONArray(sourcesJson)

            for (i in 0 until arr.length()) {

                val obj = arr.getJSONObject(i)
                val link = obj.optString("file")
                val label = obj.optString("label")

                if (link.isNotEmpty()) {

                    val fixed = if (link.startsWith("/")) {
                        "$mainUrl$link"
                    } else link

                    callback.invoke(
                        newExtractorLink(
                            source = name,
                            name = label.ifEmpty { name },
                            url = fixed,
                            type = ExtractorLinkType.M3U8
                        ).apply {
                            headers = mapOf(
                                "Referer" to url,
                                "Origin" to mainUrl,
                                "User-Agent" to USER_AGENT
                            )
                        }
                    )
                }
            }

            return
        }

        val regex = Regex("""["']hls\d["']\s*:\s*["']([^"']+)""")

        regex.findAll(unpacked).forEach {

            val link = it.groupValues[1]

            val fixed = if (link.startsWith("/")) {
                "$mainUrl$link"
            } else link

            callback.invoke(
                newExtractorLink(
                    source = name,
                    name = name,
                    url = fixed,
                    type = ExtractorLinkType.M3U8
                ).apply {
                    headers = mapOf(
                        "Referer" to url,
                        "Origin" to mainUrl,
                        "User-Agent" to USER_AGENT
                    )
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

        val playlist = Regex("""https://[^"' ]+\.txt""")
            .find(html)
            ?.value

        if (playlist != null) {

            callback.invoke(
                newExtractorLink(
                    source = name,
                    name = name,
                    url = playlist,
                    type = ExtractorLinkType.M3U8
                ).apply {
                    this.headers = headers
                }
            )
        }
    }
}