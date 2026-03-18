package com.Anoboy

import com.lagradost.api.Log
import com.fasterxml.jackson.annotation.JsonProperty
import com.lagradost.cloudstream3.SubtitleFile
import com.lagradost.cloudstream3.app
import com.lagradost.cloudstream3.utils.*

class YourUpload : ExtractorApi() {
    override val name = "YourUpload"
    override val mainUrl = "https://www.yourupload.com"
    override val requiresReferer = false

    override suspend fun getUrl(
        url: String,
        referer: String?,
        subtitleCallback: (SubtitleFile) -> Unit,
        callback: (ExtractorLink) -> Unit
    ) {
        try {
            val doc = app.get(url).document
            val quality = getQualityFromName(doc.title())

            doc.select("script").forEach { script ->
                val data = script.data()

                if (data.contains("jwplayerOptions")) {
                    val json = data
                        .substringAfter("jwplayerOptions = {")
                        .substringBefore("};")
                        .replace("file", "\"file\"")
                        .replace("'", "\"")

                    val parsed = tryParseJson<Response>("{$json}") ?: return@forEach

                    callback.invoke(
                        newExtractorLink(name, name, parsed.file) {
                            this.referer = url
                            this.quality = quality
                        }
                    )
                }
            }

        } catch (e: Exception) {
            Log.e(name, e.message ?: "")
        }
    }

    data class Response(
        @JsonProperty("file") val file: String
    )
}

class KrakenFiles : ExtractorApi() {
    override val name = "KrakenFiles"
    override val mainUrl = "https://krakenfiles.com"
    override val requiresReferer = true

    override suspend fun getUrl(
        url: String,
        referer: String?,
        subtitleCallback: (SubtitleFile) -> Unit,
        callback: (ExtractorLink) -> Unit
    ) {
        try {
            val res = app.get(
                url,
                headers = mapOf(
                    "User-Agent" to USER_AGENT,
                    "Referer" to (referer ?: mainUrl)
                )
            )

            val doc = res.document

            val video = doc.selectFirst("video source")?.attr("src")
                ?: doc.selectFirst("video")?.attr("data-src-url")
                ?: return

            val quality = when {
                video.contains("1080") -> Qualities.P1080.value
                video.contains("720") -> Qualities.P720.value
                else -> Qualities.Unknown.value
            }

            callback.invoke(
                newExtractorLink(name, name, video) {
                    this.referer = "https://krakenfiles.com/"
                    this.quality = quality
                    this.headers = mapOf(
                        "User-Agent" to USER_AGENT,
                        "Referer" to "https://krakenfiles.com/"
                    )
                }
            )

        } catch (e: Exception) {
            Log.e(name, e.message ?: "")
        }
    }
}