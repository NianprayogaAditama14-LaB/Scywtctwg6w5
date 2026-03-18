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
        "/" to "Drama Terbaru",
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
        val url = if (request.data == "/") {
            mainUrl
        } else {
            "$mainUrl${request.data}page/$page/"
        }

        val doc = app.get(url).document

        val home = if (request.data == "/") {

            val latestSection = doc.select("h2.title_index")
                .firstOrNull { it.text().contains("Drama Terbaru", true) }
                ?.parent()

            latestSection
                ?.select("div.post_index > div.style_post_1 > article")
                ?.mapNotNull { el ->

                    val a = el.selectFirst("h3.title_post a") ?: return@mapNotNull null
                    val href = fixUrl(a.attr("href"))
                        .substringBefore("?")
                        .trimEnd('/')

                    val title = a.text()
                        .replace("Subtitle Indonesia", "")
                        .trim()

                    val poster = el.selectFirst("img")?.attr("src")

                    val duration = el.selectFirst("li:contains(Duration)")?.text()
                        ?.substringAfter(":")
                        ?.replace("hr.", "jam")
                        ?.replace("min.", "menit")
                        ?.replace("min", "menit")
                        ?.trim()

                    newTvSeriesSearchResponse(title, href) {
                        this.posterUrl = poster
                        this.plot = duration
                    }
                }
                ?.distinctBy { it.url }
                ?.take(20)
                ?: emptyList()

        } else {
            doc.select("h3.title_post").mapNotNull {
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
                }
            }.distinctBy { it.url }.take(20)
        }

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
            }
        }.distinctBy { it.url }
    }

    override suspend fun load(url: String): LoadResponse {
        val doc = app.get(url).document

        val title = doc.selectFirst("h1.single-title, h2.single-title")
            ?.text()?.trim() ?: "No Title"

        val poster = doc.selectFirst(".thumbnail_single img, .daftar-foto img")?.attr("src")

        val plot = doc.select(".synopsis p")
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

        val duration = infoMap["Durasi"]
            ?.replace("min.", "")
            ?.replace("min", "")
            ?.replace("m", "")
            ?.trim()
            ?.toIntOrNull()

        val tags = doc.select(".info ul li a").map { it.text() }

        if (isMovie) {
            val episode = newEpisode(url) {
                this.name = "Putar Film"
                this.episode = 1
            }

            return newTvSeriesLoadResponse(title, url, TvType.Movie, listOf(episode)) {
                this.posterUrl = poster
                this.plot = plot
                this.year = year
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
            this.plot = plot
            this.year = year
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
