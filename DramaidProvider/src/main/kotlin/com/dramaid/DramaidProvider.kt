package com.dramaid

import android.util.Base64
import com.lagradost.cloudstream3.*
import com.lagradost.cloudstream3.utils.*

class DramaIdProvider : MainAPI() {
    override var mainUrl = "https://drama-id.com"
    override var name = "DramaID"
    override val hasMainPage = true
    override var lang = "id"

    override val supportedTypes = setOf(TvType.AsianDrama)

    override val mainPage = mainPageOf(
        "" to "Terbaru",
        "/status-drama/ongoing/" to "Ongoing",
        "/status-drama/complete/" to "Completed",
        "/negara/korea-selatan/" to "Korea",
        "/negara/china/" to "China",
        "/negara/japan/" to "Japan"
    )

    override suspend fun getMainPage(page: Int, request: MainPageRequest): HomePageResponse {
        val url = if (request.data.isEmpty()) "$mainUrl/page/$page/" else "$mainUrl${request.data}page/$page/"
        val doc = app.get(url).document
        val home = doc.select("h3.title_post").mapNotNull {
            val anchor = it.selectFirst("a") ?: return@mapNotNull null
            val href = fixUrl(anchor.attr("href"))
            val title = anchor.text().trim()
            val poster = it.parent()?.selectFirst("img")?.attr("src")
            newTvSeriesSearchResponse(title, href) { this.posterUrl = poster }
        }
        return newHomePageResponse(HomePageList(request.name, home), hasNext = doc.selectFirst("link[rel=next]") != null)
    }

    override suspend fun load(url: String): LoadResponse {
        val doc = app.get(url).document
        val title = doc.selectFirst("h1")?.text()?.trim() ?: "No Title"
        val poster = doc.selectFirst("img")?.attr("src")
        val episodes = doc.select(".daftar-episode a").mapIndexed { index, el ->
            val epUrl = fixUrl(el.attr("href"))
            val epName = el.selectFirst(".title_episode")?.text()?.trim() ?: "Episode ${index + 1}"
            newEpisode(epUrl) { this.name = epName; this.episode = index + 1 }
        }
        return newTvSeriesLoadResponse(title, url, TvType.AsianDrama, episodes) { this.posterUrl = poster }
    }

    override suspend fun loadLinks(
        data: String,
        isCasting: Boolean,
        subtitleCallback: (SubtitleFile) -> Unit,
        callback: (ExtractorLink) -> Unit
    ): Boolean {
        val doc = app.get(data).document
        val encodedList = doc.select(".streaming-box [data]").mapNotNull { it.attr("data").takeIf { d -> d.isNotBlank() } }
        encodedList.forEach { base64 ->
            try {
                val decoded = String(Base64.decode(base64, Base64.DEFAULT))
                val iframe = Regex("""src=["'](.*?)["']""").find(decoded)?.groupValues?.get(1)
                if (iframe != null) loadExtractor(iframe, data, subtitleCallback, callback)
            } catch (_: Exception) {}
        }
        return true
    }
}