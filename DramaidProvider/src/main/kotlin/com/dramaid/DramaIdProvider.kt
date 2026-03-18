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
        val url = if (request.data.isBlank()) {
            "$mainUrl/page/$page/"
        } else {
            "$mainUrl${request.data}page/$page/"
        }

        val doc = app.get(url).document

        val home = doc.select("div.post_index article").mapNotNull { el ->

            val a = el.selectFirst("h3.title_post a") ?: return@mapNotNull null
            val href = fixUrl(a.attr("href"))
                .substringBefore("?")
                .trimEnd('/')

            if (
                href.contains("#") ||
                href.contains("javascript") ||
                href.contains("/episode/")
            ) return@mapNotNull null

            val title = a.text()
                .replace("Subtitle Indonesia", "")
                .trim()

            val poster = el.selectFirst("img")?.attr("src")

            val durationText = el.selectFirst("li:contains(Duration)")?.text()
            val duration = durationText
                ?.substringAfter(":")
                ?.replace("hr.", "jam")
                ?.replace("hr", "jam")
                ?.replace("min.", "menit")
                ?.replace("min", "menit")
                ?.trim()

            val finalTitle = if (!duration.isNullOrBlank()) {
                "$title • $duration"
            } else title

            newTvSeriesSearchResponse(finalTitle, href) {
                this.posterUrl = poster
                this.addQuality("HD")
            }

        }
        .distinctBy { it.url }
        .take(20)

        return newHomePageResponse(
            listOf(HomePageList(request.name, home)),
            hasNext = doc.selectFirst("link[rel=next]") != null
        )
    }

    override suspend fun search(query: String): List<SearchResponse> {
        val url = "$mainUrl/?s=${query.replace(" ", "+")}"
        val doc = app.get(url).document

        return doc.select("h3.title_post").mapNotNull {
            val a = it.selectFirst("a") ?: return@mapNotNull null
            val href = fixUrl(a.attr("href"))

            if (
                href.contains("#") ||
                href.contains("javascript")
            ) return@mapNotNull null

            val title = a.text().trim()
            val poster = it.parent()?.selectFirst(".thumbnail img")?.attr("src")

            newTvSeriesSearchResponse(title, href) {
                this.posterUrl = poster
                this.addQuality("HD")
            }
        }.distinctBy { it.url }
    }

    override suspend fun load(url: String): LoadResponse {
        val doc = app.get(url).document

        val title = doc.selectFirst("h1.single-title, h2.single-title")
            ?.text()?.trim() ?: "No Title"

        val poster = doc.selectFirst(".thumbnail_single img, .daftar-foto img")?.attr("src")

        val plotText = doc.select(".synopsis p")
            .joinToString("\n") { it.text() }
            .trim()

        val infoMap = doc.select(".info ul li").associate {
            val key = it.selectFirst("strong")?.text()?.replace(":", "")?.trim() ?: ""
            val value = it.select("a").joinToString(", ") { a -> a.text() }
                .ifEmpty { it.ownText().trim() }
            key to value
        }

        val type = infoMap["Tipe"]?.lowercase()
        val isMovie = type?.contains("movie") == true

        val year = infoMap["Tahun"]?.toIntOrNull()

        val status = when {
            infoMap["Status"]?.contains("ongoing", true) == true -> ShowStatus.Ongoing
            else -> ShowStatus.Completed
        }

        // ✅ FINAL FIX SCORE (NO ERROR)
        val score = infoMap["Skor"]
            ?.replace(",", ".")
            ?.substringBefore("/")
            ?.toDoubleOrNull()
            ?.let { Score.from10(it) }

        // ✅ FIX DURATION
        val duration = infoMap["Durasi"]
            ?.replace("min.", "")
            ?.replace("min", "")
            ?.replace("jam", "")
            ?.trim()
            ?.toIntOrNull()

        val tags = doc.select(".info ul li a").map { it.text() }

        if (isMovie) {
            return newMovieLoadResponse(title, url, TvType.Movie, url) {
                this.posterUrl = poster
                this.plot = plotText
                this.year = year
                this.score = score
                this.duration = duration
                this.tags = tags
            }
        }

        val episodes = doc.select(".daftar-episode a").mapIndexed { index, el ->
            val epUrl = fixUrl(el.attr("href"))

            newEpisode(epUrl) {
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
            this.duration = duration
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
