package com.dramaid

import android.util.Base64
import com.lagradost.cloudstream3.*
import com.lagradost.cloudstream3.utils.*
import com.google.gson.JsonObject

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
        val decodedHtml = try {
            String(Base64.decode(url, Base64.DEFAULT))
        } catch (e: Exception) {
            url
        }

        val iframe = Regex("""src="([^"]+)"""").find(decodedHtml)?.groupValues?.getOrNull(1) ?: return
        val encodedId = Regex("""id=([^&]+)""").find(iframe)?.groupValues?.getOrNull(1) ?: return

        val dlganUrl = try {
            String(Base64.decode(encodedId, Base64.DEFAULT))
        } catch (e: Exception) {
            return
        }

        val id = Regex("""id=([^&]+)""").find(dlganUrl)?.groupValues?.getOrNull(1) ?: return
        val api = "https://api.dlgan.space/api.php?id=$id"
        val json = app.get(api).parsedSafe<JsonObject>() ?: return
        val video = json.get("direct_url")?.asString ?: return

        val qualityText = Regex("""(\d{3,4}p)""").find(video)?.value

        callback(
            newExtractorLink(name, "$name ${qualityText ?: ""}", video, ExtractorLinkType.VIDEO) {
                this.quality = getQualityFromName(qualityText)
                this.headers = mapOf("User-Agent" to USER_AGENT)
            }
        )
    }
}