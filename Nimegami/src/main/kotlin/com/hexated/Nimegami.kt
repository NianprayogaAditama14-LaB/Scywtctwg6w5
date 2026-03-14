package com.hexated

import com.fasterxml.jackson.annotation.JsonProperty
import com.lagradost.cloudstream3.*
import com.lagradost.cloudstream3.LoadResponse.Companion.addAniListId
import com.lagradost.cloudstream3.LoadResponse.Companion.addMalId
import com.lagradost.cloudstream3.LoadResponse.Companion.addTrailer
import com.lagradost.cloudstream3.utils.*
import com.lagradost.cloudstream3.utils.AppUtils.tryParseJson
import org.jsoup.nodes.Element
import org.jsoup.select.Elements

class Nimegami : MainAPI() {

    override var mainUrl = "https://nimegami.id"
    override var name = "Nimegami"
    override val hasMainPage = true
    override var lang = "id"

    override val supportedTypes = setOf(
        TvType.Anime,
        TvType.AnimeMovie,
        TvType.OVA
    )

    override val mainPage = mainPageOf(
        "" to "Updated Anime",
        "/type/tv" to "Anime",
        "/type/movie" to "Movie",
        "/type/ona" to "ONA",
    )

    override suspend fun getMainPage(
        page: Int,
        request: MainPageRequest
    ): HomePageResponse {

        val document =
            app.get("$mainUrl${request.data}/page/$page").document

        val home = document.select(
            "div.post-article article, div.archive article"
        ).mapNotNull { it.toSearchResult() }

        return newHomePageResponse(
            HomePageList(request.name, home),
            hasNext = true
        )
    }

    private fun Element.toSearchResult(): AnimeSearchResponse? {

        val href = fixUrl(this.selectFirst("a")!!.attr("href"))
        val title = this.selectFirst("h2 a")?.text() ?: return null

        val posterUrl =
            (this.selectFirst("noscript img")
                ?: this.selectFirst("img"))?.attr("src")

        return newAnimeSearchResponse(title, href, TvType.Anime) {
            this.posterUrl = posterUrl
        }
    }

    override suspend fun search(query: String): List<SearchResponse> {

        val searchResponse = mutableListOf<SearchResponse>()

        for (i in 1..2) {

            val res = app.get(
                "$mainUrl/page/$i/?s=$query&post_type=post"
            ).document.select("div.archive article")
                .mapNotNull { it.toSearchResult() }

            searchResponse.addAll(res)
        }

        return searchResponse
    }

    override suspend fun load(url: String): LoadResponse {

        val document = app.get(url).document
        val table = document.select("div#Info table tbody")

        val title = table.getContent("Judul").text()

        val poster =
            document.selectFirst("div.coverthumbnail img")
                ?.attr("src")

        val description =
            document.select("div#Sinopsis p")
                .text()
                .trim()

        val tags =
            table.getContent("Kategori").select("a")
                .map { it.text() }

        val year =
            table.getContent("Musim / Rilis")
                .text()
                .filter { it.isDigit() }
                .toIntOrNull()

        val type = TvType.Anime

        val episodes =
            document.select("div.list_eps_stream li")
                .mapNotNull { ep ->

                    val data = ep.attr("data")
                    if (data.isBlank()) return@mapNotNull null

                    val episode =
                        Regex("Episode\\s?(\\d+)")
                            .find(ep.text())
                            ?.groupValues?.getOrNull(1)
                            ?.toIntOrNull()

                    newEpisode(data) {
                        this.episode = episode
                    }
                }

        return newAnimeLoadResponse(title, url, type) {

            posterUrl = poster
            plot = description
            this.year = year
            this.tags = tags

            addEpisodes(DubStatus.Subbed, episodes)
        }
    }

    override suspend fun loadLinks(
        data: String,
        isCasting: Boolean,
        subtitleCallback: (SubtitleFile) -> Unit,
        callback: (ExtractorLink) -> Unit
    ): Boolean {

        if (data.isBlank()) return false

        val decoded = try {
            base64Decode(data)
        } catch (_: Exception) {
            data
        }

        val clean =
            decoded.replace("\\/", "/")

        val sources =
            tryParseJson<ArrayList<Sources>>(clean)
                ?: return false

        sources.forEach { source ->
            source.url?.forEach { url ->

                if (url.contains("dlgan.space")) {
                    extractDlgan(url, source.format, callback)
                }
            }
        }

        return true
    }

    private suspend fun extractDlgan(
        url: String,
        quality: String?,
        callback: (ExtractorLink) -> Unit
    ) {

        val html = app.get(
            url,
            headers = mapOf(
                "Referer" to mainUrl
            )
        ).text

        val streamUrl =
            Regex("""stream_url":"(https:[^"]+)""")
                .find(html)
                ?.groupValues?.get(1)
                ?.replace("\\/", "/")
                ?.replace("\\u0026", "&")

        if (streamUrl.isNullOrBlank()) return

        val q = getQualityFromName(quality)

        callback.invoke(
            newExtractorLink(
                "Nimegami",
                "Nimegami $quality",
                streamUrl,
                ExtractorLinkType.VIDEO
            ) {
                this.quality = q
                this.headers = mapOf(
                    "Referer" to "https://dlgan.space/"
                )
            }
        )
    }

    private fun Elements.getContent(css: String): Elements {
        return this.select("tr:contains($css) td:last-child")
    }

    data class Sources(
        @JsonProperty("format")
        val format: String? = null,

        @JsonProperty("url")
        val url: ArrayList<String>? = arrayListOf()
    )
}
