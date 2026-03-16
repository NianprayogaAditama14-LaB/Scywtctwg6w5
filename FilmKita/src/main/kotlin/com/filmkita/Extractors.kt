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

        val res = app.get(url, referer = mainUrl)
        val html = res.text

        val packed = Regex("""eval\(function\(p,a,c,k,e,d\).*?\)\)""")
            .find(html)?.value ?: return

        val unpacked = JsUnpacker(packed).unpack()

        val hls = Regex("""hls2":"(https:[^"]+)""")
            .find(unpacked)
            ?.groupValues?.get(1)
            ?.replace("\\/", "/")
            ?: return

        callback(
            newExtractorLink(
                name,
                name,
                hls,
                ExtractorLinkType.M3U8
            ) {
                quality = Qualities.Unknown.value
                headers = mapOf(
                    "Referer" to "$mainUrl/"
                )
            }
        )
    }
}