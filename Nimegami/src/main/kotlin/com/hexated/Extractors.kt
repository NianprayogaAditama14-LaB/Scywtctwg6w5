package com.hexated

import com.fasterxml.jackson.annotation.JsonProperty
import com.lagradost.cloudstream3.*
import com.lagradost.cloudstream3.utils.*

open class Berkasdrive : ExtractorApi() {
    override val name = "Berkasdrive"
    override val mainUrl = "https://dl.berkasdrive.com"
    override val requiresReferer = true

    override suspend fun getUrl(
        url: String,
        referer: String?,
        subtitleCallback: (SubtitleFile) -> Unit,
        callback: (ExtractorLink) -> Unit
    ) {
        val doc = app.get(url, referer = referer).document
        val video = doc.selectFirst("video source")?.attr("src")
            ?: Regex("""https?://[^"]+\.mp4""").find(doc.html())?.value
            ?: return

        callback.invoke(
            newExtractorLink(name, name, video, INFER_TYPE) {
                this.referer = referer ?: mainUrl
                this.quality = extractQuality(video)
            }
        )
    }
}

open class Dlgan : ExtractorApi() {
    override val name = "Dlgan"
    override val mainUrl = "https://dlgan.space"
    override val requiresReferer = false

    data class DlganResponse(
        @JsonProperty("stream_url") val streamUrl: String? = null
    )

    override suspend fun getUrl(
        url: String,
        referer: String?,
        subtitleCallback: (SubtitleFile) -> Unit,
        callback: (ExtractorLink) -> Unit
    ) {
        val id = url.substringAfterLast("/")
        val json = app.get(
            "$mainUrl/streaming.php?proxy=1&id=$id",
            referer = "$mainUrl/"
        ).parsedSafe<DlganResponse>() ?: return

        val video = json.streamUrl ?: return

        callback.invoke(
            newExtractorLink(name, name, video, INFER_TYPE) {
                this.referer = "$mainUrl/"
                this.quality = extractQuality(video)
            }
        )
    }
}