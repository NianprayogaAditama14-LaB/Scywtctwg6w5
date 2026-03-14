package com.hexated

import com.fasterxml.jackson.annotation.JsonProperty
import com.lagradost.cloudstream3.*
import com.lagradost.cloudstream3.utils.*

open class Mitedrive : ExtractorApi() {
    override val name = "Mitedrive"
    override val mainUrl = "https://mitedrive.com"
    override val requiresReferer = false

    override suspend fun getUrl(
        url: String,
        referer: String?,
        subtitleCallback: (SubtitleFile) -> Unit,
        callback: (ExtractorLink) -> Unit
    ) {
        val id = url.substringAfterLast("/")
        val video = app.post(
            "https://api.mitedrive.com/api/view/$id",
            referer = "$mainUrl/",
            data = mapOf("slug" to id)
        ).parsedSafe<Response>()?.data?.url ?: return

        callback.invoke(
            newExtractorLink(name, name, video, INFER_TYPE) {
                this.referer = "$mainUrl/"
            }
        )
    }

    data class Data(@JsonProperty("original_url") val url: String? = null)
    data class Response(@JsonProperty("data") val data: Data? = null)
}

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
            }
        )
    }
}

open class Videogami : ExtractorApi() {
    override val name = "Videogami"
    override val mainUrl = "https://video.nimegami.id"
    override val requiresReferer = false

    override suspend fun getUrl(
        url: String,
        referer: String?,
        subtitleCallback: (SubtitleFile) -> Unit,
        callback: (ExtractorLink) -> Unit
    ) {
        val decoded = base64Decode(url.substringAfter("url="))
        val id = decoded.substringAfterLast("/")
        loadExtractor("https://hxfile.co/embed-$id.html", "$mainUrl/", subtitleCallback, callback)
    }
}

open class Krakenfiles : ExtractorApi() {
    override val name = "Krakenfiles"
    override val mainUrl = "https://krakenfiles.com"
    override val requiresReferer = true

    override suspend fun getUrl(
        url: String,
        referer: String?,
        subtitleCallback: (SubtitleFile) -> Unit,
        callback: (ExtractorLink) -> Unit
    ) {
        val doc = app.get(url, referer = "$mainUrl/").document
        val video = doc.selectFirst("video source")?.attr("src")
            ?: Regex("""https://[^"]*krakencloud[^"]*""").find(doc.html())?.value
            ?: return

        callback.invoke(
            newExtractorLink(name, name, video, INFER_TYPE) {
                this.referer = "$mainUrl/"
                this.quality = Qualities.P1080.value
            }
        )
    }
}