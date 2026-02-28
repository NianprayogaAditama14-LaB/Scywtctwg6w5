package com.filmkita

import com.lagradost.cloudstream3.*
import com.lagradost.cloudstream3.LoadResponse.Companion.addActors
import com.lagradost.cloudstream3.LoadResponse.Companion.addScore
import com.lagradost.cloudstream3.LoadResponse.Companion.addTrailer
import com.lagradost.cloudstream3.MainAPI
import com.lagradost.cloudstream3.SearchResponse
import com.lagradost.cloudstream3.TvType
import com.lagradost.cloudstream3.mainPageOf
import com.lagradost.cloudstream3.utils.*
import org.jsoup.nodes.Element
import java.net.URI

class FilmKita : MainAPI() {

    override var mainUrl = "https://s1.iix.llc"
    override var name = "FilmKita🪅"
    override val hasMainPage = true
    override var lang = "id"

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

    private fun Element.toSearchResult(): SearchResponse? {
        val title = selectFirst("h2.entry-title > a")?.text()?.trim() ?: return null
        val href = fixUrl(selectFirst("a")!!.attr("href"))
        val ratingText = selectFirst("div.gmr-rating-item")?.ownText()?.trim()
        val posterUrl = fixUrlNull(selectFirst("a > img")?.getImageAttr()).fixImageQuality()
        val quality = select("div.gmr-qual, div.gmr-quality-item > a")
            .text().trim().replace("-", "")

        return if (quality.isEmpty()) {
            newAnimeSearchResponse(title, href, TvType.TvSeries) {
                this.posterUrl = posterUrl
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
            app.get("$mainUrl/?s=$query&post_type[]=post&post_type[]=tv").document
        return document.select("article.item").mapNotNull { it.toSearchResult() }
    }

    override suspend fun load(url: String): LoadResponse {
        val document = app.get(url).document

        val title = document.selectFirst("h1.entry-title")?.text()?.trim().orEmpty()
        val poster =
            fixUrlNull(document.selectFirst("figure.pull-left img")?.getImageAttr())
                ?.fixImageQuality()

        val tags = document.select("strong:contains(Genre) ~ a").eachText()
        val year = document.select("strong:contains(Year:) ~ a")
            .text().toIntOrNull()

        val description =
            document.selectFirst("div[itemprop=description] > p")?.text()?.trim()

        val rating =
            document.selectFirst("span[itemprop=ratingValue]")?.text()?.trim()

        val actors =
            document.select("span[itemprop=actors] a").map { it.text() }

        val trailer =
            document.selectFirst("a.gmr-trailer-popup")?.attr("href")

        return newMovieLoadResponse(title, url, TvType.Movie, url) {
            this.posterUrl = poster
            this.year = year
            this.plot = description
            this.tags = tags
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

        document.select("iframe").forEach { iframe ->
            val link = iframe.getIframeAttr()?.let { httpsify(it) } ?: return@forEach
            loadExtractor(link, data, subtitleCallback, callback)
        }

        document.select("ul.gmr-download-list li a").forEach { linkEl ->
            val downloadUrl = linkEl.attr("href")
            if (downloadUrl.isNotBlank()) {
                loadExtractor(downloadUrl, data, subtitleCallback, callback)
            }
        }

        return true
    }

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
}
