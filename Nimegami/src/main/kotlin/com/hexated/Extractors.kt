package com.hexated

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
        val res = app.get(url, referer = referer).document
        val video = res.select("video#player source").attr("src")
        if (video.isBlank()) return

        callback.invoke(
            newExtractorLink(name, name, video, INFER_TYPE) {
                this.referer = referer ?: mainUrl
            }
        )
    }
}

open class Dlgan : ExtractorApi() {
    override val name = "Dlgan"
    override val mainUrl = "https://dlgan.space"
    override val requiresReferer = true

    override suspend fun getUrl(
        url: String,
        referer: String?,
        subtitleCallback: (SubtitleFile) -> Unit,
        callback: (ExtractorLink) -> Unit
    ) {
        val id = url.substringAfterLast("/").substringBefore("?")
        val json = app.get("$mainUrl/streaming.php?proxy=1&id=$id", referer = referer).parsedSafe<DlganResponse>() ?: return
        val video = json.data?.stream_url ?: json.data?.direct_url ?: return

        callback.invoke(
            newExtractorLink(name, name, video, INFER_TYPE) {
                this.referer = referer ?: mainUrl
            }
        )
    }

    data class DlganData(val stream_url: String?, val direct_url: String?)
    data class DlganResponse(val data: DlganData?)
}
