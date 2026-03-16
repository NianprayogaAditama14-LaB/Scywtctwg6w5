package com.filmkita

import android.util.Base64
import com.lagradost.cloudstream3.*
import com.lagradost.cloudstream3.utils.*

class HlsTerea : ExtractorApi() {

    override val name = "HlsTerea"
    override val mainUrl = "https://hls-terea.layarwibu.com"
    override val requiresReferer = false

    override suspend fun getUrl(
        url: String,
        referer: String?,
        subtitleCallback: (SubtitleFile) -> Unit,
        callback: (ExtractorLink) -> Unit
    ) {
        val encoded = url.substringAfter("/player2/").substringBefore("?")
        val decoded = try {
            String(Base64.decode(encoded, Base64.DEFAULT))
        } catch (_: Exception) {
            return
        }

        callback(
            newExtractorLink(
                name,
                name,
                decoded,
                ExtractorLinkType.M3U8
            ) {
                quality = Qualities.Unknown.value
            }
        )
    }
}

class LayarWibu : ExtractorApi() {

    override val name = "LayarWibu"
    override val mainUrl = "https://hls-bekop.layarwibu.com"
    override val requiresReferer = false

    override suspend fun getUrl(
        url: String,
        referer: String?,
        subtitleCallback: (SubtitleFile) -> Unit,
        callback: (ExtractorLink) -> Unit
    ) {
        val encoded = url.substringAfter("/player2/").substringBefore("?")
        val decoded = try {
            String(Base64.decode(encoded, Base64.DEFAULT))
        } catch (_: Exception) {
            return
        }

        callback(
            newExtractorLink(
                name,
                name,
                decoded,
                ExtractorLinkType.M3U8
            ) {
                quality = Qualities.Unknown.value
            }
        )
    }
}

class Minochinos : ExtractorApi() {

    override val name = "Minochinos"
    override val mainUrl = "https://minochinos.com"
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
                "Referer" to url,
                "User-Agent" to USER_AGENT
            )
        )

        val html = res.text

        val hls4 = Regex("""hls4":"([^"]+)""").find(html)?.groupValues?.get(1)
        val hls3 = Regex("""hls3":"([^"]+)""").find(html)?.groupValues?.get(1)
        val hls2 = Regex("""hls2":"([^"]+)""").find(html)?.groupValues?.get(1)

        val video = hls4 ?: hls3 ?: hls2 ?: return

        val finalLink =
            if (video.startsWith("/"))
                "$mainUrl$video"
            else
                video

        callback(
            newExtractorLink(
                name,
                name,
                finalLink,
                ExtractorLinkType.M3U8
            ) {
                headers = mapOf(
                    "Referer" to url
                )
                quality = Qualities.Unknown.value
            }
        )
    }
}
