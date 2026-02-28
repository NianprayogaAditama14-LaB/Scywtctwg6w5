package com.filmkita

import com.lagradost.cloudstream3.*
import com.lagradost.cloudstream3.LoadResponse.Companion.addActors
import com.lagradost.cloudstream3.LoadResponse.Companion.addScore
import com.lagradost.cloudstream3.LoadResponse.Companion.addTrailer
import com.lagradost.cloudstream3.utils.*
import java.net.URI
import org.jsoup.nodes.Element

open class FilmKita : MainAPI() {

    override var mainUrl = "https://s1.iix.llc"
    override var name = "FilmKita🪅"
    override val hasMainPage = true
    override var lang = "id"

    override val supportedTypes =
        setOf(TvType.Movie, TvType.TvSeries, TvType.Anime, TvType.AsianDrama)

    override val mainPage =
        mainPageOf(
            "category/box-office/page/%d/" to "Box Office",
            "category/serial-tv/page/%d/" to "Serial TV",
            "category/animation/page/%d/" to "Animasi",
            "country/korea/page/%d/" to "Serial TV Korea",
            "country/indonesia/page/%d/" to "Serial TV Indonesia",
        )

    override suspend fun getMainPage(page: Int, request: MainPageRequest): HomePageResponse {
        val data = request.data.format(page)
        val document = app.get("$mainUrl/$data").document
        val home = document.select("article.item").mapNotNull { it.toSearchResult() }
        return newHomePageResponse(request.name, home)
    }

    private fun Element.toSearchResult(): SearchResponse? {
        val title = selectFirst("h2.entry-title > a")?.text()?.trim() ?: return null
        val href = fixUrl(selectFirst("a")!!.attr("href"))
        val ratingText = selectFirst("div.gmr-rating-item")?.ownText()?.trim()
        val posterUrl = fixUrlNull(selectFirst("a > img")?.getImageAttr())?.fixImageQuality()
        val quality = select("div.gmr-qual, div.gmr-quality-item > a")
            .text().trim().replace("-", "")

        return if (quality.isEmpty()) {
            val episode =
                Regex("Episode\\s?([0-9]+)")
                    .find(title)
                    ?.groupValues?.getOrNull(1)
                    ?.toIntOrNull()
                    ?: select("div.gmr-numbeps > span").text().toIntOrNull()

            newAnimeSearchResponse(title, href, TvType.TvSeries) {
                this.posterUrl = posterUrl
                addSub(episode)
                this.score = Score.from10(ratingText?.toDoubleOrNull())
            }
        } else {
            newMovieSearchResponse(title, href, TvType.Movie) {
                this.posterUrl = posterUrl
                addQuality(quality)
                this.score = Score.from10(ratingText?.toDoubleOrNull())
            }
        }
    }

    override suspend fun search(query: String): List<SearchResponse> {
        val document =
            app.get("$mainUrl?s=$query&post_type[]=post&post_type[]=tv", timeout = 50L).document
        return document.select("article.item").mapNotNull { it.toSearchResult() }
    }

    override suspend fun load(url: String): LoadResponse {

        val desktopHeaders = mapOf(
            "User-Agent" to "Mozilla/5.0 (Windows NT 10.0; Win64; x64)"
        )

        val document = app.get(url, headers = desktopHeaders).document

        val title = document.selectFirst("h1.entry-title")
            ?.text()
            ?.substringBefore("Season")
            ?.substringBefore("Episode")
            ?.trim()
            .orEmpty()

        val poster =
            fixUrlNull(document.selectFirst("figure.pull-left > img")?.getImageAttr())
                ?.fixImageQuality()

        val tags = document.select("strong:contains(Genre) ~ a").eachText()

        val year =
            document.select("div.gmr-moviedata strong:contains(Year:) > a")
                ?.text()?.trim()?.toIntOrNull()

        val tvType = if (url.contains("/tv/")) TvType.TvSeries else TvType.Movie

        val description =
            document.selectFirst("div[itemprop=description] > p")?.text()?.trim()

        val trailer =
            document.selectFirst("ul.gmr-player-nav li a.gmr-trailer-popup")
                ?.attr("href")

        val rating =
            document.selectFirst("div.gmr-meta-rating > span[itemprop=ratingValue]")
                ?.text()?.trim()

        val actors =
            document.select("div.gmr-moviedata").last()
                ?.select("span[itemprop=actors]")?.map {
                    it.select("a").text()
                }

        val duration =
            document.selectFirst("div.gmr-moviedata span[property=duration]")
                ?.text()?.replace(Regex("\\D"), "")?.toIntOrNull()

        val recommendations =
            document.select("article.item.col-md-20")
                .mapNotNull { it.toRecommendResult() }

        if (tvType == TvType.Movie) {
            return newMovieLoadResponse(title, url, TvType.Movie, url) {
                this.posterUrl = poster
                this.year = year
                this.plot = description
                this.tags = tags
                this.duration = duration ?: 0
                this.recommendations = recommendations
                addScore(rating)
                addActors(actors)
                addTrailer(trailer, referer = mainUrl, addRaw = true)
            }
        }

        val seriesUrl =
            document.selectFirst("a.button.button-shadow.active")?.attr("href")
                ?: url.substringBefore("/eps/")

        val seriesDoc = app.get(seriesUrl, headers = desktopHeaders).document

        var episodeCounter = 1

        val episodes =
            seriesDoc.select("div.gmr-listseries a.button.button-shadow")
                .mapNotNull { eps ->
                    val href = fixUrl(eps.attr("href")).trim()
                    val name = eps.text().trim()

                    if (!name.contains("Eps", true)) return@mapNotNull null

                    val season =
                        Regex("""S(\d+)\s*Eps""", RegexOption.IGNORE_CASE)
                            .find(name)
                            ?.groupValues?.getOrNull(1)
                            ?.toIntOrNull() ?: 1

                    newEpisode(href) {
                        this.name = name
                        this.season = season
                        this.episode = episodeCounter++
                    }
                }

        return newTvSeriesLoadResponse(title, url, TvType.TvSeries, episodes) {
            this.posterUrl = poster
            this.year = year
            this.plot = description
            this.tags = tags
            this.duration = duration ?: 0
            this.recommendations = recommendations
            addScore(rating)
            addActors(actors)
            addTrailer(trailer, referer = mainUrl, addRaw = true)
        }
    }

    override suspend fun loadLinks(
        data: String,
        isCasting: Boolean,
        subtitleCallback: (SubtitleFile) -> Unit,
        callback: (ExtractorLink) -> Unit
    ): Boolean {

        val document = app.get(data).document
        val base = getBaseUrl(data)

        val iframe = document
            .selectFirst("div.gmr-embed-responsive iframe")
            ?.getIframeAttr()
            ?.let { httpsify(it) }

        iframe?.let {
            loadExtractor(it, base, subtitleCallback, callback)
        }

        document.select("ul.muvipro-player-tabs li a").forEach { ele ->
            val tabUrl = fixUrl(ele.attr("href"))
            val tabDoc = app.get(tabUrl).document
            val tabIframe = tabDoc
                .selectFirst("div.gmr-embed-responsive iframe")
                ?.getIframeAttr()
                ?.let { httpsify(it) }

            tabIframe?.let {
                loadExtractor(it, base, subtitleCallback, callback)
            }
        }

        document.select("ul.gmr-download-list li a").forEach { linkEl ->
            val downloadUrl = linkEl.attr("href")
            if (downloadUrl.isNotBlank()) {
                loadExtractor(downloadUrl, base, subtitleCallback, callback)
            }
        }

        return true
    }

    override val extractorApis = listOf(
        LayarWibuExtractor()
    )

    private fun Element.getImageAttr(): String {
        return when {
            hasAttr("data-src") -> attr("abs:data-src")
            hasAttr("data-lazy-src") -> attr("abs:data-lazy-src")
            hasAttr("srcset") -> attr("abs:srcset").substringBefore(" ")
            else -> attr("abs:src")
        }
    }

    private fun Element?.getIframeAttr(): String? {
        return this?.attr("data-litespeed-src")
            ?.takeIf { it.isNotEmpty() }
            ?: this?.attr("src")
    }

    private fun String?.fixImageQuality(): String? {
        if (this == null) return null
        val regex = Regex("(-\\d*x\\d*)").find(this)?.groupValues?.get(0) ?: return this
        return replace(regex, "")
    }

    private fun getBaseUrl(url: String): String {
        return URI(url).let { "${it.scheme}://${it.host}" }
    }

    private fun Element.toRecommendResult(): SearchResponse? {
        val title = selectFirst("h2.entry-title > a")?.text()?.trim() ?: return null
        val href = selectFirst("h2.entry-title > a")?.attr("href")?.trim() ?: return null
        val posterUrl = selectFirst("div.content-thumbnail img")?.attr("src")

        return newMovieSearchResponse(title, href, TvType.Movie) {
            this.posterUrl = fixUrlNull(posterUrl)
        }
    }
}
