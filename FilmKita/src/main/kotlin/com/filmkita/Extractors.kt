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

        val encoded = url.substringAfter("/player2/")
            .substringBefore("?")

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
                this.quality = Qualities.P1080.value
            }
        )
    }
}