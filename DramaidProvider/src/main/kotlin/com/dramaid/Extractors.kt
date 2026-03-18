package com.dramaid

import android.util.Base64
import com.lagradost.cloudstream3.*
import com.lagradost.cloudstream3.utils.*

class BerkasDriveExtractor : ExtractorApi() {
    override val name = "BerkasDrive"
    override val mainUrl = "https://dlgan.space"
    override val requiresReferer = false

    override suspend fun getUrl(
        url: String,
        referer: String?,
        subtitleCallback: (SubtitleFile) -> Unit,
        callback: (ExtractorLink) -> Unit
    ) {
        val data = Regex("""data\s*=\s*["']([^"']+)""")
            .find(url)
            ?.groupValues?.getOrNull(1)

        if (data != null) {
            val decoded = String(Base64.decode(data, Base64.DEFAULT))
            val links = Regex("""https?:\/\/dlgan\.space\/\?id=[a-zA-Z0-9]+""")
                .findAll(decoded)
                .map { it.value }
                .distinct()

            links.forEach { link ->
                loadFromDlgan(link, callback)
            }
            return
        }

        val id = Regex("""id=([a-zA-Z0-9]+)""")
            .find(url)
            ?.groupValues?.getOrNull(1)
            ?: return

        loadFromDlgan("$mainUrl/?id=$id", callback)
    }

    private suspend fun loadFromDlgan(
        url: String,
        callback: (ExtractorLink) -> Unit
    ) {
        val id = Regex("""id=([a-zA-Z0-9]+)""")
            .find(url)
            ?.groupValues?.getOrNull(1)
            ?: return

        val api = "https://api.dlgan.space/api.php?id=$id"
        val res = app.get(api).parsedSafe<Map<String, Any>>() ?: return

        val stream = res["stream_url"]?.toString()
        val direct = res["direct_url"]?.toString()
        val nameFile = res["name"]?.toString()

        val finalUrl = stream ?: direct ?: return

        val qualityText = Regex("""(\d{3,4}p)""")
            .find(nameFile ?: finalUrl)
            ?.value

        callback(
            newExtractorLink(
                name,
                "$name ${qualityText ?: ""}",
                finalUrl,
                ExtractorLinkType.VIDEO
            ) {
                this.referer = "https://dlgan.space/"
                this.quality = getQualityFromName(qualityText)
                this.headers = mapOf(
                    "Referer" to "https://dlgan.space/",
                    "User-Agent" to USER_AGENT
                )
            }
        )
    }
}