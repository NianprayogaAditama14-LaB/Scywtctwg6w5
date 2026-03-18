package com.dramaid

import android.util.Base64
import android.net.Uri
import com.lagradost.cloudstream3.*
import com.lagradost.cloudstream3.utils.*
import org.json.JSONObject

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

            newTvSeriesSearchResponse(title, href) {
                this.posterUrl = poster
            }
        }.distinctBy { it.url }

        return newHomePageResponse(listOf(HomePageList(request.name, home)))
    }

    override suspend fun search(query: String): List<SearchResponse> {
        val doc = app.get("$mainUrl/?s=${query.replace(" ", "+")}").document
        return doc.select("h3.title_post").mapNotNull {
            val a = it.selectFirst("a") ?: return@mapNotNull null
            val href = fixUrl(a.attr("href"))
            val title = a.text().trim()
            val poster = it.parent()?.selectFirst("img")?.attr("src")

            newTvSeriesSearchResponse(title, href) {
                this.posterUrl = poster
            }
        }.distinctBy { it.url }
    }

    override suspend fun load(url: String): LoadResponse {
        val doc = app.get(url).document

        val title = doc.selectFirst("h1.single-title, h2.single-title")?.text()?.trim() ?: "No Title"
        val poster = doc.selectFirst(".thumbnail_single img, .daftar-foto img")?.attr("src")
        val plot = doc.select(".synopsis p").joinToString("\n") { it.text() }

        val typeText = doc.select(".info li:contains(Tipe)").text()
        val isMovie = typeText.contains("Movie", true)
        val year = doc.select(".info li:contains(Tahun)").text().toIntOrNull()
        val status = if (doc.select(".info li:contains(Status)").text().contains("ongoing", true))
            ShowStatus.Ongoing else ShowStatus.Completed
        val score = doc.select(".info li:contains(Skor)").text().replace(",", ".").substringBefore("/").toDoubleOrNull()?.let { Score.from10(it) }
        val tags = doc.select(".info li a").map { it.text() }

        if (isMovie) {
            return newMovieLoadResponse(title, url, TvType.Movie, url) {
                this.posterUrl = poster
                this.plot = plot
                this.year = year
                this.score = score
                this.tags = tags
            }
        }

        val episodes = doc.select(".daftar-episode a").mapIndexed { i, el ->
            newEpisode(fixUrl(el.attr("href"))) {
                this.name = "Episode ${i + 1}"
                this.episode = i + 1
            }
        }

        return newTvSeriesLoadResponse(title, url, TvType.AsianDrama, episodes) {
            this.posterUrl = poster
            this.plot = plot
            this.year = year
            this.showStatus = status
            this.score = score
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

        val elements = doc.select(".resolusi-list li")

        for (el in elements) {
            val encoded = el.attr("data")
            if (encoded.isBlank()) continue

            try {
                val jsonStr = String(Base64.decode(encoded, Base64.DEFAULT))
                val obj = JSONObject(jsonStr)

                val resolution = obj.optString("resolution")
                val links = obj.getJSONArray("links")

                for (i in 0 until links.length()) {
                    var url = links.getJSONObject(i).getString("url")
                    url = url.replace("\\/", "/")
                    val id = Uri.parse(url).getQueryParameter("id") ?: continue
                    val apiRes = app.get("https://api.dlgan.space/api.php?id=$id").text
                    val direct = JSONObject(apiRes).optString("direct_url")
                    if (direct.isNotEmpty()) {
                        found = true
                        
                        callback.invoke(
                            newExtractorLink("DramaID", "DramaID", direct, ExtractorLinkType.VIDEO) {
                                this.quality = getQualityFromName(resolution)
                            }
                        )
                    }
                }
            } catch (_: Exception) {}
        }

        return found
    }
}
