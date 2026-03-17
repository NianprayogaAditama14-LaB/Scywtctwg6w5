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
        val url = if (request.data.isEmpty()) {
            "$mainUrl/page/$page/"
        } else {
            "$mainUrl${request.data}page/$page/"
        }

        val doc = app.get(url).document

        val home = doc.select("h3.title_post").mapNotNull {
            val a = it.selectFirst("a") ?: return@mapNotNull null
            val title = a.text().trim()
            val href = fixUrl(a.attr("href"))

            val poster = it.parent()?.selectFirst("img")?.attr("data-src")
                ?: it.parent()?.selectFirst("img")?.attr("src")

            newTvSeriesSearchResponse(title, href) {
                this.posterUrl = poster
            }
        }

        return newHomePageResponse(
            HomePageList(request.name, home),
            hasNext = doc.selectFirst("link[rel=next]") != null
        )
    }

    override suspend fun load(url: String): LoadResponse {
        val doc = app.get(url).document

        val title = doc.selectFirst("h1")?.text()?.trim() ?: "No Title"

        val poster = doc.selectFirst(".thumb img")?.attr("data-src")
            ?: doc.selectFirst(".thumb img")?.attr("src")

        val plot = doc.selectFirst(".entry-content p")
            ?.text()?.trim()

        val episodes = doc.select(".daftar-episode a").mapIndexed { index, el ->
            val epUrl = fixUrl(el.attr("href"))

            newEpisode(epUrl) {
                this.name = "Episode ${index + 1}"
                this.episode = index + 1
            }
        }

        return newTvSeriesLoadResponse(title, url, TvType.AsianDrama, episodes) {
            this.posterUrl = poster
            this.plot = plot
        }
    }

    override suspend fun loadLinks(
        data: String,
        isCasting: Boolean,
        subtitleCallback: (SubtitleFile) -> Unit,
        callback: (ExtractorLink) -> Unit
    ): Boolean {

        val doc = app.get(data).document

        doc.select(".streaming-box").forEach {
            val base64 = it.attr("data")
            if (base64.isNotBlank()) {
                try {
                    val decoded = String(Base64.decode(base64, Base64.DEFAULT))
                    val iframe = Regex("""src=["'](.*?)["']""")
                        .find(decoded)?.groupValues?.get(1)

                    if (iframe != null) {
                        loadExtractor(iframe, data, subtitleCallback, callback)
                    }
                } catch (_: Exception) {}
            }
        }

        doc.select(".resolusi-list li").forEach { el ->
            val base64 = el.attr("data")
            if (base64.isBlank()) return@forEach

            try {
                val json = String(Base64.decode(base64, Base64.DEFAULT))

                Regex("""https?:\/\/dlgan\.space\/\?id=[a-zA-Z0-9]+""")
                    .findAll(json)
                    .map { it.value }
                    .distinct()
                    .forEach { link ->
                        loadExtractor(link, data, subtitleCallback, callback)
                    }

            } catch (_: Exception) {}
        }

        return true
    }
}
