package com.filmkita

import android.util.Base64
import com.lagradost.cloudstream3.utils.ExtractorApi
import com.lagradost.cloudstream3.utils.ExtractorLink
import com.lagradost.cloudstream3.utils.Qualities
import com.lagradost.cloudstream3.utils.SubtitleFile

class LayarWibuExtractor : ExtractorApi() {

    override val name = "LayarWibu"
    override val mainUrl = "https://hls-terea.layarwibu.com"
    override val requiresReferer = false

    override suspend fun getUrl(
        url: String,
        referer: String?,
        subtitleCallback: (SubtitleFile) -> Unit,
        callback: (ExtractorLink) -> Unit
    ) {
        val encoded = url.substringAfterLast("/")
        val decoded = String(Base64.decode(encoded, Base64.DEFAULT))

        callback.invoke(
            ExtractorLink(
                name,
                name,
                decoded,
                mainUrl,
                Qualities.P1080.value,
                true
            )
        )
    }
}
