package com.hexated

import com.lagradost.cloudstream3.*
import com.lagradost.cloudstream3.SubtitleFile
import com.lagradost.cloudstream3.utils.*
import org.json.JSONObject

class DlganExtractor : ExtractorApi() {
    override val name = "Dlgan"
    override val mainUrl = "https://dlgan.space/"
    override val requiresReferer = false

    override suspend fun getUrl(
        url: String,
        referer: String?,
        subtitleCallback: (SubtitleFile) -> Unit,
        callback: (ExtractorLink) -> Unit
    ) {
        val html = app.get(url, headers = mapOf("Referer" to (referer ?: mainUrl))).text

        Regex("""stream_url":"(https:[^"]+)""").findAll(html).forEach { match ->
            val stream = match.groupValues[1]
                .replace("\\/", "/")
                .replace("\\u0026", "&")

            val quality = Regex("""(\d{3,4}p)""").find(stream)?.value

            callback.invoke(
                newExtractorLink(name, "$name ${quality ?: ""}", stream, ExtractorLinkType.VIDEO) {
                    this.referer = referer ?: mainUrl
                    this.quality = getQualityFromName(quality)
                    this.headers = mapOf("Referer" to (referer ?: mainUrl))
                }
            )
        }
    }
}

class BerkasDriveExtractor : ExtractorApi() {
    override val name = "BerkasDrive"
    override val mainUrl = "https://dl.berkasdrive.com"
    override val requiresReferer = false

    override suspend fun getUrl(
        url: String,
        referer: String?,
        subtitleCallback: (SubtitleFile) -> Unit,
        callback: (ExtractorLink) -> Unit
    ) {
        val id = Regex("id=([a-zA-Z0-9]+)").find(url)?.groupValues?.get(1) ?: return
        val api = "$mainUrl/new/streaming.php?action=stream-worker&id=$id"

        val response = app.get(
            api,
            headers = mapOf(
                "User-Agent" to "Mozilla/5.0",
                "Referer" to "$mainUrl/"
            )
        ).text

        val json = JSONObject(response)
        if (!json.getBoolean("ok")) return

        val videoUrl = json.getString("url").replace("\\/", "/")
        val quality = Regex("""(\d{3,4}p)""").find(videoUrl)?.value

        callback.invoke(
            newExtractorLink(
                name,
                "$name ${quality ?: ""}",
                videoUrl,
                ExtractorLinkType.VIDEO
            ) {
                this.referer = "$mainUrl/"
                this.quality = getQualityFromName(quality)
                this.headers = mapOf(
                    "Referer" to "$mainUrl/",
                    "User-Agent" to "Mozilla/5.0"
                )
            }
        )
    }
}

class MiteDriveExtractor : ExtractorApi() {
    override val name = "MiteDrive"
    override val mainUrl = "https://stor.halahgan.com"
    override val requiresReferer = true

    override suspend fun getUrl(
        url: String,
        referer: String?,
        subtitleCallback: (SubtitleFile) -> Unit,
        callback: (ExtractorLink) -> Unit
    ) {
        if (url.contains("stor.halahgan.com")) {
            val quality = Regex("""(\d{3,4}p)""").find(url)?.value

            callback.invoke(
                newExtractorLink(
                    name,
                    "$name ${quality ?: ""}",
                    url,
                    ExtractorLinkType.VIDEO
                ) {
                    this.referer = referer ?: "https://nimegami.id/"
                    this.quality = getQualityFromName(quality)
                    this.headers = mapOf(
                        "Referer" to (referer ?: "https://nimegami.id/"),
                        "User-Agent" to "Mozilla/5.0"
                    )
                }
            )
            return
        }

        val html = app.get(url, headers = mapOf("Referer" to (referer ?: "https://nimegami.id/"))).text

        Regex("""https://stor\.halahgan\.com/dl/public/[^"']+\.mp4""")
            .findAll(html)
            .forEach {
                val mp4 = it.value
                val quality = Regex("""(\d{3,4}p)""").find(mp4)?.value

                callback.invoke(
                    newExtractorLink(
                        name,
                        "$name ${quality ?: ""}",
                        mp4,
                        ExtractorLinkType.VIDEO
                    ) {
                        this.referer = referer ?: "https://nimegami.id/"
                        this.quality = getQualityFromName(quality)
                        this.headers = mapOf(
                            "Referer" to (referer ?: "https://nimegami.id/"),
                            "User-Agent" to "Mozilla/5.0"
                        )
                    }
                )
            }
    }
}