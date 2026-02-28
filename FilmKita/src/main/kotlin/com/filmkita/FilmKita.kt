package com.filmkita

import com.lagradost.cloudstream3.*
import com.lagradost.cloudstream3.LoadResponse.Companion.addScore
import com.lagradost.cloudstream3.LoadResponse.Companion.addTrailer
import com.lagradost.cloudstream3.utils.*
import org.jsoup.nodes.Element
import java.net.URI

class FilmKita : MainAPI() {

    override var mainUrl = "https://s1.iix.llc"
    override var name = "FilmKita🪅"
    override var lang = "id"
    override val hasMainPage = true

    override val supportedTypes =
        setOf(TvType.Movie, TvType.TvSeries, TvType.Anime, TvType.AsianDrama)

    override val mainPage = mainPageOf(
        "category/box-office/page/%d/" to "Box Office",
        "category/serial-tv/page/%d/" to "Serial TV",
        "category/animation/page/%d/" to "Animasi",
        "country/korea/page/%d/" to "Serial TV Korea",
        "country/indonesia/page/%d/" to "Serial TV Indonesia",
    )

    override suspend fun getMainPage(page: Int, request: MainPageRequest): HomePageResponse {
        val document = app.get("$mainUrl/${request.data.format(page)}").document
        val home = document.select("article.item").mapNotNull { it.toSearchResult() }
        return newHomePageResponse(request.name, home)
    }

    override suspend fun search(query: String): List<SearchResponse> {
        val document = app.get(
            "$mainUrl?s=$query&post_type[]=post&post_type[]=tv"
        ).document
        return document.select("article.item").mapNotNull { it.toSearchResult() }
    }

    override suspend fun load(url: String): LoadResponse {

        val document = app.get(url).document

        val title = document.selectFirst("h1.entry-title")
            ?.text()?.trim().orEmpty()

        val poster = fixUrlNull(
            document.selectFirst("figure.pull-left img")?.getImageAttr()
        )

        val tags = document.select("strong:contains(Genre) ~ a").eachText()

        val description =
            document.selectFirst("div[itemprop=description] p")?.text()

        val trailer =
            document.selectFirst("a.gmr-trailer-popup")?.attr("href")

        val rating =
            document.selectFirst("span[itemprop=ratingValue]")
                ?.text()?.toDoubleOrNull()

        val recommendations =
            document.select("article.item.col-md-20")
                .mapNotNull { it.toRecommendResult() }

        val tvType = if (url.contains("/tv/")) TvType.TvSeries else TvType.Movie

        if (tvType == TvType.Movie) {
            return newMovieLoadResponse(title, url, TvType.Movie, url) {
                this.posterUrl = poster
                this.plot = description
                this.tags = tags
                this.recommendations = recommendations
                addScore(rating)
                addTrailer(trailer, referer = mainUrl, addRaw = true)
            }
        }

        val episodes = document
            .select("div.gmr-listseries a.button.button-shadow")
            .mapIndexedNotNull { index, eps ->
                val href = fixUrl(eps.attr("href"))
                val name = eps.text()
                newEpisode(href) {
                    this.name = name
                    this.episode = index + 1
                }
            }

        return newTvSeriesLoadResponse(title, url, TvType.TvSeries, episodes) {
            this.posterUrl = poster
            this.plot = description
            this.tags = tags
            this.recommendations = recommendations
            addScore(rating)
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

        document.select("ul.muvipro-player-tabs li a").forEach { tab ->
            val tabUrl = fixUrl(tab.attr("href"))
            val tabDoc = app.get(tabUrl).document
            val tabIframe = tabDoc
                .selectFirst("div.gmr-embed-responsive iframe")
                ?.getIframeAttr()
                ?.let { httpsify(it) }
            tabIframe?.let {
                loadExtractor(it, base, subtitleCallback, callback)
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
            hasAttr("srcset") -> attr("abs:srcset").substringBefore(" ")
            else -> attr("abs:src")
        }
    }

    private fun Element?.getIframeAttr(): String? {
        return this?.attr("data-litespeed-src")
            ?.takeIf { it.isNotEmpty() }
            ?: this?.attr("src")
    }

    private fun getBaseUrl(url: String): String {
        return URI(url).let { "${it.scheme}://${it.host}" }
    }

    private fun Element.toSearchResult(): SearchResponse? {
        val title = selectFirst("h2.entry-title > a")?.text() ?: return null
        val href = fixUrl(selectFirst("a")!!.attr("href"))
        val posterUrl = fixUrlNull(selectFirst("img")?.getImageAttr())

        return newMovieSearchResponse(title, href, TvType.Movie) {
            this.posterUrl = posterUrl
        }
    }

    private fun Element.toRecommendResult(): SearchResponse? {
        val title = selectFirst("h2.entry-title > a")?.text() ?: return null
        val href = selectFirst("h2.entry-title > a")?.attr("href") ?: return null
        val posterUrl = selectFirst("img")?.attr("src")

        return newMovieSearchResponse(title, href, TvType.Movie) {
            this.posterUrl = fixUrlNull(posterUrl)
        }
    }
}
