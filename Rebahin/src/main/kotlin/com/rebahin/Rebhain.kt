package com.rebahin

import com.lagradost.cloudstream3.*
import com.lagradost.cloudstream3.LoadResponse.Companion.addActors
import com.lagradost.cloudstream3.LoadResponse.Companion.addScore
import com.lagradost.cloudstream3.LoadResponse.Companion.addTrailer
import com.lagradost.cloudstream3.utils.*
import java.net.URI
import org.jsoup.nodes.Element

class Rebahin : MainAPI() {

    companion object {
        var context: android.content.Context? = null
    }

    override var mainUrl = "https://wiapr.com"
    override var name = "Rebahin🐝"
    override val hasMainPage = true
    override var lang = "id"

    private var directUrl: String? = null

    override val supportedTypes =
        setOf(TvType.Movie, TvType.TvSeries, TvType.Anime, TvType.AsianDrama)

    override val mainPage = mainPageOf(
        "/page/%d/?s&search=advanced&post_type=movie&index&orderby&genre&movieyear&country&quality=" to "Update Terbaru",
        "category/box-office/page/%d/" to "Box Office",
        "category/serial-tv/page/%d/" to "Serial TV",
        "category/anime/page/%d/" to "Anime",
        "category/animation/page/%d/" to "Animasi",
        "category/donghua/page/%d/" to "Donghua",
        "country/korea/page/%d/" to "Serial TV Korea",
        "country/indonesia/page/%d/" to "Serial TV Indonesia",
    )

    override suspend fun getMainPage(page: Int, request: MainPageRequest): HomePageResponse {
        context?.let { StarPopupHelper.showStarPopupIfNeeded(it) }

        val document = app.get("$mainUrl/${request.data.format(page)}").document

        val home = document.select("article.item").mapNotNull { it.toSearchResult() }

        return newHomePageResponse(request.name, home)
    }

    private fun Element.toSearchResult(): SearchResponse? {
        val title = selectFirst("h2.entry-title > a")?.text()?.trim() ?: return null
        val href = fixUrl(selectFirst("a")!!.attr("href"))
        val posterUrl = fixUrlNull(selectFirst("a > img")?.getImageAttr()).fixImageQuality()

        val ratingText = selectFirst("div.gmr-rating-item")?.ownText()?.trim()

        val quality =
            select("div.gmr-qual, div.gmr-quality-item > a")
                .text()
                .trim()
                .replace("-", "")

        return if (quality.isEmpty()) {

            val episode =
                Regex("Episode\\s?([0-9]+)")
                    .find(title)
                    ?.groupValues
                    ?.getOrNull(1)
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

    // ======== Tambahkan toRecommendResult ========
    private fun Element.toRecommendResult(): SearchResponse? {
        val title = selectFirst("h2.entry-title > a")?.text()?.trim() ?: return null
        val href = selectFirst("h2.entry-title > a")?.attr("href")?.trim() ?: return null
        val img = selectFirst("div.content-thumbnail img")
        val posterUrl = img?.attr("src")
            ?.ifBlank { img.attr("data-src") }
            ?.ifBlank { img.attr("srcset")?.split(" ")?.firstOrNull() }

        return newMovieSearchResponse(title, href, TvType.Movie) {
            this.posterUrl = fixUrlNull(posterUrl)
        }
    }
    // ============================================

    override suspend fun search(query: String): List<SearchResponse> {
        val document =
            app.get("$mainUrl?s=$query&post_type[]=post&post_type[]=tv").document

        return document.select("article.item").mapNotNull { it.toSearchResult() }
    }

    override suspend fun load(url: String): LoadResponse {

        val fetch = app.get(url)
        directUrl = getBaseUrl(fetch.url)

        val document = fetch.document

        val title =
            document.selectFirst("h1.entry-title")
                ?.text()
                ?.substringBefore("Season")
                ?.substringBefore("Episode")
                ?.trim()
                .toString()

        val poster =
            fixUrlNull(document.selectFirst("figure.pull-left > img")?.getImageAttr())
                ?.fixImageQuality()

        val tags = document.select("strong:contains(Genre) ~ a").eachText()

        val year =
            document.select("div.gmr-moviedata strong:contains(Year:) > a")
                .text()
                .trim()
                .toIntOrNull()

        val tvType = if (url.contains("/tv/")) TvType.TvSeries else TvType.Movie

        val description =
            document.selectFirst("div[itemprop=description] > p")
                ?.text()
                ?.trim()

        val trailer =
            document.selectFirst("ul.gmr-player-nav li a.gmr-trailer-popup")
                ?.attr("href")

        val rating =
            document.selectFirst("div.gmr-meta-rating > span[itemprop=ratingValue]")
                ?.text()

        val actors =
            document.select("div.gmr-moviedata")
                .lastOrNull()
                ?.select("span[itemprop=actors]")
                ?.map { it.select("a").text() }

        val duration =
            document.selectFirst("div.gmr-moviedata span[property=duration]")
                ?.text()
                ?.replace(Regex("\\D"), "")
                ?.toIntOrNull()

        val recommendations =
            document.select("article.item.col-md-20")
                .mapNotNull { it.toRecommendResult() }

        return if (tvType == TvType.TvSeries) {

            val episodes =
                document.select("div.vid-episodes a, div.gmr-listseries a")
                    .map { eps ->

                        val href = fixUrl(eps.attr("href"))
                        val name = eps.text()

                        val episode =
                            name.split(" ")
                                .lastOrNull()
                                ?.filter { it.isDigit() }
                                ?.toIntOrNull()

                        val season =
                            name.split(" ")
                                .firstOrNull()
                                ?.filter { it.isDigit() }
                                ?.toIntOrNull()

                        newEpisode(href) {
                            this.name = name
                            this.episode = episode
                            this.season = season
                        }
                    }

            newTvSeriesLoadResponse(title, url, TvType.TvSeries, episodes) {
                this.posterUrl = poster
                this.year = year
                this.plot = description
                this.tags = tags
                addScore(rating)
                addActors(actors)
                this.recommendations = recommendations
                this.duration = duration ?: 0
                addTrailer(trailer)
            }

        } else {

            newMovieLoadResponse(title, url, TvType.Movie, url) {
                this.posterUrl = poster
                this.year = year
                this.plot = description
                this.tags = tags
                addScore(rating)
                addActors(actors)
                this.recommendations = recommendations
                this.duration = duration ?: 0
                addTrailer(trailer)
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
        val id = document.selectFirst("#muvipro_player_content_id")?.attr("data-id")

        val servers = document.select("ul.muvipro-player-tabs li a")

        servers.amap { ele ->

            val tab = ele.attr("href").removePrefix("#")

            val serverDoc = if (!id.isNullOrEmpty()) {

                app.post(
                    "$directUrl/wp-admin/admin-ajax.php",
                    data = mapOf(
                        "action" to "muvipro_player_content",
                        "tab" to tab,
                        "post_id" to id
                    ),
                    timeout = 60
                ).document

            } else {

                app.get(fixUrl(ele.attr("href"))).document
            }

            val iframe =
                serverDoc.select("iframe").firstOrNull()
                    ?.attr("src")
                    ?.let { httpsify(it) }
                    ?: return@amap

            loadExtractor(iframe, "$directUrl/", subtitleCallback, callback)
        }

        document.select("ul.gmr-download-list li a").forEach { link ->
            val href = link.attr("href")
            if (href.isNotBlank()) {
                loadExtractor(href, data, subtitleCallback, callback)
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

    private fun String?.fixImageQuality(): String? {
        if (this == null) return null
        val regex = Regex("(-\\d*x\\d*)").find(this)?.groupValues?.get(0) ?: return this
        return this.replace(regex, "")
    }

    private fun getBaseUrl(url: String): String {
        return URI(url).let { "${it.scheme}://${it.host}" }
    }
}
