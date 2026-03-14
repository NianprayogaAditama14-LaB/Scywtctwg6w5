package com.hexated

import com.lagradost.cloudstream3.app
import com.lagradost.cloudstream3.utils.*
import org.jsoup.nodes.Document

class Berkasdrive : ExtractorApi() {
    override val name = "Berkasdrive"
    override val mainUrl = "https://dl.berkasdrive.com"
    override val requiresReferer = false

    private fun getQuality(url: String): Int {
        return Regex("(\\d{3,4})p").find(url)?.groupValues?.get(1)?.toIntOrNull()
            ?: Qualities.Unknown.value
    }

    override suspend fun getUrl(
        url: String,
        referer: String?,
        subtitleCallback: (SubtitleFile) -> Unit,
        callback: (ExtractorLink) -> Unit
    ) {

        val doc: Document = app.get(url).document

        doc.select("video source").forEach {
            val video = it.attr("src")
            if (video.isNotBlank()) {
                callback.invoke(
                    newExtractorLink(
                        source = name,
                        name = "Berkasdrive",
                        url = video,
                        type = ExtractorLinkType.MP4
                    ) {
                        this.referer = "https://dl.berkasdrive.com/"
                        this.quality = getQuality(video)
                    }
                )
            }
        }

        doc.select(".daftar_server li").forEach {
            val video = it.attr("data-url")
            if (video.isNotBlank()) {
                callback.invoke(
                    newExtractorLink(
                        source = name,
                        name = "Berkasdrive",
                        url = video,
                        type = ExtractorLinkType.MP4
                    ) {
                        this.referer = "https://dl.berkasdrive.com/"
                        this.quality = getQuality(video)
                    }
                )
            }
        }
    }
}