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
        val id = Regex("id=([a-zA-Z0-9+/=]+)").find(url)?.groupValues?.get(1) ?: return
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
    override val mainUrl = "https://mitedrive.com"
    override val requiresReferer = false

    override suspend fun getUrl(
        url: String,
        referer: String?,
        subtitleCallback: (SubtitleFile) -> Unit,
        callback: (ExtractorLink) -> Unit
    ) {
        val slug = url.substringAfterLast("/")
        val token = base64Encode(base64Encode("""{"ip":"1.1.1.1"}"""))

        val jsonData = """
            {
                "slug":"$slug",
                "csrf_token":"$token"
            }
        """.trimIndent().toByteArray()

        val response = app.post(
            "https://api.mitedrive.com/api/view/$slug",
            requestBody = jsonData,
            headers = mapOf(
                "Content-Type" to "application/json",
                "User-Agent" to "Mozilla/5.0"
            )
        ).parsedSafe<Map<String, Any>>() ?: return

        val data = response["data"] as? Map<*, *> ?: return
        val urls = data["sources"] as? List<*> ?: listOf(data["original_url"])

        urls.forEach { item ->
            val videoUrl = (item as? String ?: (item as? Map<*, *>)?.get("file")?.toString()) ?: return@forEach
            val qualityName = (item as? Map<*, *>)?.get("label")?.toString() ?: getQualityFromName(videoUrl)
            val fixedUrl = videoUrl.replace("[", "%5B").replace("]", "%5D")

            callback(
                newExtractorLink(name, "$name ${qualityName ?: ""}", fixedUrl, ExtractorLinkType.VIDEO) {
                    this.quality = getQualityFromName(qualityName)
                    this.headers = mapOf(
                        "User-Agent" to "Mozilla/5.0",
                        "Accept" to "*/*",
                        "Connection" to "keep-alive"
                    )
                }
            )
        }
    }
}
