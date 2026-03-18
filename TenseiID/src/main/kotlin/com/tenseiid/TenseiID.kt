package com.tenseiid

import org.jsoup.nodes.Element
import com.lagradost.cloudstream3.*
import com.lagradost.cloudstream3.utils.*
import org.jsoup.Jsoup

class TenseiID : MainAPI() {
    override var mainUrl = "https://tensei.club"
    override var name = "TenseiID"
    override val hasMainPage = true
    override var lang = "id"
    override val hasDownloadSupport = true
    override val supportedTypes = setOf(TvType.Anime, TvType.Movie)

    override val mainPage = mainPageOf(
        "" to "Rilis Terbaru",
        "rec" to "Rekomendasi",
        "pop" to "Seri Populer",
    )

    override suspend fun getMainPage(page: Int, request: MainPageRequest): HomePageResponse {
        val document = app.get("$mainUrl/${request.data}&page=$page").document
        val home = document.select("div.listupd > article").mapNotNull { it.toSearchResult() }

        return newHomePageResponse(
            listOf(
                HomePageList(
                    name = request.name,
                    list = home,
                    isHorizontalImages = false
                )
            ),
            hasNext = true
        )
    }

    private fun Element.toSearchResult(): SearchResponse {
        val title = this.select("div.bsx > a").attr("title")
        val href = fixUrl(this.select("div.bsx > a").attr("href"))
        val posterUrl = fixUrlNull(this.select("div.bsx > a img").attr("src"))

        return newAnimeSearchResponse(title, href, TvType.Anime) {
            this.posterUrl = posterUrl
        }
    }

    override suspend fun search(query: String, page: Int): SearchResponseList {
        val document = app.get("$mainUrl/page/$page/?s=$query").document
        return document.select("div.listupd > article")
            .mapNotNull { it.toSearchResult() }
            .toNewSearchResponseList()
    }

    override suspend fun load(url: String): LoadResponse {
        val document = app.get(url).document

        val title = document.selectFirst("h1.entry-title")?.text()?.trim().orEmpty()
        val poster = document.select("div.thumb img").attr("src")
            .ifEmpty { document.selectFirst("meta[property=og:image]")?.attr("content").orEmpty() }

        val description = document.selectFirst("div.entry-content")?.text()?.trim()

        val isMovie = document.select(".spe").text().contains("Movie", true)

        return if (!isMovie) {
            val episodeRegex = Regex("(\\d+)")
            val episodes = document.select("div.eplister > ul > li").map {
                val href = it.select("a").attr("href")
                val epText = it.selectFirst("div.epl-num")?.text().orEmpty()
                val epNum = episodeRegex.find(epText)?.groupValues?.get(1)?.toIntOrNull()

                newEpisode(href) {
                    episode = epNum
                    name = epNum?.let { "Episode $it" } ?: epText
                }
            }

            newTvSeriesLoadResponse(title, url, TvType.Anime, episodes.reversed()) {
                this.posterUrl = poster
                this.plot = description
            }
        } else {
            val playUrl = document.selectFirst("div.eplister a")?.attr("href").orEmpty()

            newMovieLoadResponse(title, url, TvType.Movie, playUrl) {
                this.posterUrl = poster
                this.plot = description
            }
        }
    }

    override suspend fun loadLinks(
        data: String,
        isCasting: Boolean,
        subtitleCallback: (SubtitleFile) -> Unit,
        callback: (ExtractorLink) -> Unit
    ): Boolean {

        val document = app.get(data).document
        val added = mutableSetOf<String>()

        document.select("div.player-embed iframe").forEach {
            val url = it.attr("src")
            if (url.contains(".mp4") && added.add(url)) {
                callback(
                    newExtractorLink(
                        source = "Kuro",
                        name = "Kuro",
                        url = url,
                        type = null
                    ) {
                        this.quality = getQualityFromUrl(url)
                        this.headers = mapOf(
                            "Referer" to mainUrl,
                            "User-Agent" to USER_AGENT
                        )
                    }
                )
            }
        }

        document.select(".mobius option").forEach {
            val base64 = it.attr("value")
            if (base64.isEmpty()) return@forEach

            val decoded = base64Decode(base64)
            val doc = Jsoup.parse(decoded)
            val url = doc.select("iframe").attr("src")

            if (url.contains(".mp4") && added.add(url)) {
                callback(
                    newExtractorLink(
                        source = "Kuro",
                        name = "Kuro",
                        url = url,
                        type = null
                    ) {
                        this.quality = getQualityFromUrl(url)
                        this.headers = mapOf(
                            "Referer" to mainUrl,
                            "User-Agent" to USER_AGENT
                        )
                    }
                )
            }
        }

        document.select(".soraurlx").forEach {
            val url = it.select("a").attr("href")
            val qualityText = it.selectFirst("strong")?.text().orEmpty()

            if (url.contains(".mp4") && added.add(url)) {
                callback(
                    newExtractorLink(
                        source = "Kuro",
                        name = "Kuro",
                        url = url,
                        type = null
                    ) {
                        this.quality = getQualityFromText(qualityText)
                    }
                )
            }
        }

        return true
    }

    private fun getQualityFromUrl(url: String): Int {
        return when {
            url.contains("360") -> Qualities.P360.value
            url.contains("480") -> Qualities.P480.value
            url.contains("720") -> Qualities.P720.value
            url.contains("1080") -> Qualities.P1080.value
            else -> Qualities.Unknown.value
        }
    }

    private fun getQualityFromText(text: String): Int {
        return when {
            text.contains("360") -> Qualities.P360.value
            text.contains("480") -> Qualities.P480.value
            text.contains("720") -> Qualities.P720.value
            text.contains("1080") -> Qualities.P1080.value
            else -> Qualities.Unknown.value
        }
    }
}
