package com.filmkita

import android.util.Base64
import com.lagradost.cloudstream3.utils.ExtractorApi
import com.lagradost.cloudstream3.utils.ExtractorLink
import com.lagradost.cloudstream3.utils.Qualities
import com.lagradost.cloudstream3.utils.newExtractorLink

class LayarWibuExtractor : ExtractorApi() {

    override val name = "LayarWibu"
    override val mainUrl = "https://hls-terea.layarwibu.com"
    override val requiresReferer = false

    override suspend fun getUrl(
        url: String,
        referer: String?
    ): List<ExtractorLink>? {

        val encoded = url.substringAfterLast("/")
        val decoded = String(Base64.decode(encoded, Base64.DEFAULT))

        return listOf(
            newExtractorLink(
                source = name,
                name = name,
                url = decoded,
                referer = "",
                quality = Qualities.P1080.value,
                isM3u8 = true
            )
        )
    }
}
