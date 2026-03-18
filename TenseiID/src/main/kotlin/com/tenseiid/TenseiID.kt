package com.tenseiid

import org.jsoup.nodes.Element
import com.lagradost.cloudstream3.*
import com.lagradost.cloudstream3.utils.*
import org.jsoup.Jsoup

class TenseiID : MainAPI() {
    override var mainUrl = "https://tensei.club"
    override var name = "TenseiID"
    override val hasMainPage = true
    override var lang = "en"
    override val hasDownloadSupport = true
    override val supportedTypes = setOf(TvType.Movie, TvType.Anime)

    override val mainPage = mainPageOf(
        "anime/?status=ongoing&order=update" to "Recently Updated",
        "anime/?status=ongoing&order&order=popular" to "Popular",
        "anime/?" to "Donghua",
        "anime/?status=&type=movie&page=" to "Movies",
        "anime/?sub=raw" to "Anime (RAW)"
    )

    override suspend fun getMainPage(page: Int, request: MainPageRequest): HomePageResponse {
        val document = app.get("$mainUrl/${request.data}&page=$page").document
        val home = document.select("div.listupd > article").mapNotNull { it.toSearchResult() }
        return newHomePageResponse(
            list = HomePageList(
                name = request.name,
                list = home,
                isHorizontalImages = false
            ),
            hasNext = true
        )
    }

    private fun Element.toSearchResult(): SearchResponse {
        val title = this.select("div.bsx > a").attr("title")
        val href = fixUrl(this.select("div.bsx > a").attr("href"))
        val posterUrl = fixUrlNull(this.select("div.bsx > a img").attr("src"))
        return newMovieSearchResponse(title, href, TvType.Movie) {
            this.posterUrl = posterUrl
        }
    }

    override suspend fun search(query: String, page: Int): SearchResponseList {
        val document = app.get("${mainUrl}/page/$page/?s=$query").document
        return document.select("div.listupd > article")
            .mapNotNull { it.toSearchResult() }
            .toNewSearchResponseList()
    }

    override suspend fun load(url: String): LoadResponse {
        val document = app.get(url).document
        val title = document.selectFirst("h1.entry-title")?.text()?.trim().toString()
        val href = document.selectFirst("div.eplister > ul > li a")?.attr("href") ?: ""
        val poster = document.select("div.thumb img").attr("src")
            .ifEmpty { document.selectFirst("meta[property=og:image]")?.attr("content")?.trim().toString() }
        val description = document.selectFirst("div.entry-content")?.text()?.trim()
        val type = document.selectFirst(".spe")?.text().toString()
        val tvtag = if (type.contains("Movie")) TvType.Movie else TvType.TvSeries

        return if (tvtag == TvType.TvSeries) {
            val episodeRegex = Regex("(\\d+)")
            val episodes = document.select("div.eplister > ul > li").map { info ->
                val href1 = info.select("a").attr("href")
                val posterr = info.selectFirst("a img")?.attr("src") ?: ""
                val epText = info.selectFirst("div.epl-num")?.text().orEmpty()
                val epnum = episodeRegex.find(epText)?.groupValues?.get(1)?.toIntOrNull()

                newEpisode(href1) {
                    this.episode = epnum
                    this.name = epnum?.let { "Episode $it" } ?: epText
                    this.posterUrl = posterr
                }
            }

            newTvSeriesLoadResponse(title, url, TvType.Anime, episodes.reversed()) {
                this.posterUrl = poster
                this.plot = description
            }
        } else {
            newMovieLoadResponse(title, url, TvType.Movie, href) {
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

        document.select(".mobius option").forEach {
            val base64 = it.attr("value")
            if (base64.isEmpty()) return@forEach
            val decoded = base64Decode(base64)
            val doc = Jsoup.parse(decoded)
            val url = doc.select("iframe").attr("src")
            if (url.contains(".mp4") && added.add(url)) {
                val quality = when {
                    url.contains("360") -> Qualities.P360.value
                    url.contains("480") -> Qualities.P480.value
                    url.contains("720") -> Qualities.P720.value
                    url.contains("1080") -> Qualities.P1080.value
                    else -> Qualities.Unknown.value
                }
                callback(
                    newExtractorLink(
                        "Kuro",
                        "Kuro",
                        url,
                        "",
                        quality,
                        false
                    ).apply {
                        headers = mapOf(
                            "Referer" to mainUrl,
                            "User-Agent" to USER_AGENT
                        )
                    }
                )
            }
        }

        document.select(".soraurlx").forEach {
            val url = it.select("a").attr("href")
            if (url.contains(".mp4") && added.add(url)) {
                val qualityText = it.selectFirst("strong")?.text().orEmpty()
                val quality = when {
                    qualityText.contains("360") -> Qualities.P360.value
                    qualityText.contains("480") -> Qualities.P480.value
                    qualityText.contains("720") -> Qualities.P720.value
                    qualityText.contains("1080") -> Qualities.P1080.value
                    else -> Qualities.Unknown.value
                }
                callback(
                    newExtractorLink(
                        "Kuro",
                        "Kuro",
                        url,
                        "",
                        quality,
                        false
                    )
                )
            }
        }

        return true
    }
}
