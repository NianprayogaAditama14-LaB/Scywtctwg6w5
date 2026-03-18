package com.dramaid

import android.util.Base64
import com.lagradost.cloudstream3.*
import com.lagradost.cloudstream3.utils.*

class DramaIdProvider : MainAPI() {
    override var mainUrl = "https://drama-id.com"
    override var name = "DramaID"
    override val hasMainPage = true
    override var lang = "id"

    override val supportedTypes = setOf(TvType.AsianDrama, TvType.Movie)

    override val mainPage = mainPageOf(
        "" to "Drama Terbaru",
        "/status-drama/ongoing/" to "Ongoing",
        "/status-drama/complete/" to "Drama Completed",
        "/genre/romance/" to "Romance",
        "/genre/sci-fi/" to "Sci-Fi",
        "/negara/korea-selatan/" to "Drama Korea",
        "/negara/china/" to "Drama China",
        "/negara/japan/" to "Drama Jepang",
        "/genre/tokusatsu/" to "Tokusatsu",
    )

    override suspend fun getMainPage(page: Int, request: MainPageRequest): HomePageResponse {
        val url = if (request.data.isBlank()) "$mainUrl/page/$page/" else "$mainUrl${request.data}page/$page/"
        val doc = app.get(url).document

        val home = doc.select("div.post_index article").mapNotNull { el ->
            val a = el.selectFirst("h3.title_post a") ?: return@mapNotNull null
            val href = fixUrl(a.attr("href")).substringBefore("?").trimEnd('/')

            if (href.contains("#") || href.contains("javascript") || href.contains("/episode/")) return@mapNotNull null

            val title = a.text().replace("Subtitle Indonesia", "").trim()
            val poster = el.selectFirst("img")?.attr("src")

            val episodeText = el.select("ul li:contains(Episode)").text()
            val latestEp = Regex("(\\d+)$").find(episodeText)?.groupValues?.getOrNull(1)
            val badge = if (!latestEp.isNullOrBlank()) "HD Sub Ep $latestEp" else "HD Sub Indo"

            newTvSeriesSearchResponse(title, href) {
                this.posterUrl = poster
                this.addQuality(badge)
            }
        }.distinctBy { it.url }.take(20)

        return newHomePageResponse(
            listOf(HomePageList(request.name, home)),
            hasNext = doc.selectFirst("link[rel=next]") != null
        )
    }

    override suspend fun search(query: String): List<SearchResponse> {
        val doc = app.get("$mainUrl/?s=${query.replace(" ", "+")}").document

        return doc.select("h3.title_post").mapNotNull {
            val a = it.selectFirst("a") ?: return@mapNotNull null
            val href = fixUrl(a.attr("href"))

            if (href.contains("#") || href.contains("javascript")) return@mapNotNull null

            val title = a.text().trim()
            val poster = it.parent()?.selectFirst(".thumbnail img")?.attr("src")

            val episodeText = it.parent()?.select("ul li:contains(Episode)")?.text()
            val latestEp = Regex("(\\d+)$").find(episodeText ?: "")?.groupValues?.getOrNull(1)
            val badge = if (!latestEp.isNullOrBlank()) "HD Sub Ep $latestEp" else "HD Sub Indo"

            newTvSeriesSearchResponse(title, href) {
                this.posterUrl = poster
                this.addQuality(badge)
            }
        }.distinctBy { it.url }
    }

    override suspend fun load(url: String): LoadResponse {
        val doc = app.get(url).document

        val title = doc.selectFirst("h1.single-title, h2.single-title")?.text()?.trim() ?: "No Title"
        val poster = doc.selectFirst(".thumbnail_single img, .daftar-foto img")?.attr("src")
        val plotText = doc.select(".synopsis p").joinToString("\n") { it.text() }.trim()

        val infoMap = doc.select(".info ul li").associate {
            val key = it.selectFirst("strong")?.text()?.replace(":", "")?.trim() ?: ""
            val value = it.select("a").joinToString(", ") { a -> a.text() }.ifEmpty { it.ownText().trim() }
            key to value
        }

        val type = infoMap["Tipe"]?.lowercase()
        val isMovie = type?.contains("movie") == true

        val year = infoMap["Tahun"]?.toIntOrNull()
        val status = if (infoMap["Status"]?.contains("ongoing", true) == true)
            ShowStatus.Ongoing else ShowStatus.Completed

        val score = infoMap["Skor"]?.replace(",", ".")
            ?.substringBefore("/")
            ?.toDoubleOrNull()
            ?.let { Score.from10(it) }

        val tags = doc.select(".info ul li a").map { it.text() }

        if (isMovie) {
            return newMovieLoadResponse(title, url, TvType.Movie, url) {
                this.posterUrl = poster
                this.plot = plotText
                this.year = year
                this.score = score
                this.tags = tags
            }
        }

        val episodes = doc.select(".daftar-episode a").mapIndexed { index, el ->
            newEpisode(fixUrl(el.attr("href"))) {
                this.name = "Episode ${index + 1}"
                this.episode = index + 1
            }
        }

        return newTvSeriesLoadResponse(title, url, TvType.AsianDrama, episodes) {
            this.posterUrl = poster
            this.plot = plotText
            this.year = year
            this.score = score
            this.showStatus = status
            this.tags = tags
        }
    }

    override suspend fun loadLinks(
        data: String,
        isCasting: Boolean,
        subtitleCallback: (SubtitleFile) -> Unit,
        callback: (ExtractorLink) -> Unit
    ): Boolean {

        val doc = app.get(data).document
        var found = false

        doc.select(".streaming-box, .resolusi-list li").forEach { el ->
            val base64 = el.attr("data")
            if (base64.isBlank()) return@forEach

            found = true
            loadExtractor(base64, data, subtitleCallback, callback)
        }

        return found
    }
}